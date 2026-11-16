package com.llpop.koil

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.llpop.koil.data.local.AppDatabase
import com.llpop.koil.data.local.dao.KOiLRecordDao
import com.llpop.koil.data.local.entity.KOiLRecordEntity
import com.llpop.koil.data.settings.AppSettings
import com.llpop.koil.data.sync.BackupCodec
import com.llpop.koil.data.sync.CloudSyncManager
import com.llpop.koil.data.sync.RecordMerger
import com.llpop.koil.data.sync.SyncConfig
import com.llpop.koil.data.sync.SyncReconcile
import com.llpop.koil.data.sync.WebDavClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * 对账的端到端行为（PR ② 评审必查 3/4/5/6 条）：
 * 只算不写、复用对账那次 GET 的 ETag、破坏性覆盖前留副本（只留 3 份）、
 * 「以云端为准」清撤销标记。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class SyncReconcileFlowTest {

    private lateinit var context: Context
    private lateinit var settings: AppSettings
    private lateinit var db: AppDatabase
    private lateinit var dao: KOiLRecordDao
    private lateinit var manager: CloudSyncManager

    /** (method, path) 逐次记录，用来证明「没有多发一次 GET」 */
    private val calls = mutableListOf<Pair<String, String>>()
    private var putIfMatch: String? = null
    private var cloudBody = ""
    private val etag = "\"sync-etag-1\""

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        settings = AppSettings(context)
        // 凭据 DataStore 是进程级单例：不清会把上个用例的密码漏过来
        settings.clearSyncSecretForTest()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.KOiLRecordDao()

        val engine = MockEngine { request ->
            calls.add(request.method.value to request.url.encodedPath)
            when (request.method.value) {
                "GET" -> respond(
                    cloudBody,
                    HttpStatusCode.OK,
                    headersOf(
                        HttpHeaders.ETag to listOf(etag),
                        HttpHeaders.ContentType to listOf("application/json"),
                    ),
                )
                "PUT" -> {
                    putIfMatch = request.headers[HttpHeaders.IfMatch]
                    respond("", HttpStatusCode.NoContent)
                }
                // 405 = 目录已存在（ensureCollection 据此直接上传）
                "MKCOL" -> respond("", HttpStatusCode.MethodNotAllowed)
                else -> respond("", HttpStatusCode.OK)
            }
        }
        manager = CloudSyncManager(settings, dao, WebDavClient(engine), db, context)

        settings.setSyncEnabled(true)
        settings.setSyncUrl(SyncConfig.DEFAULT_URL)
        settings.setSyncUsername(SyncConfig.DEFAULT_USERNAME)
        settings.setSyncAppPassword(SyncConfig.DEFAULT_APP_PASSWORD)
        settings.setSyncPasswordEverSet(true)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun entity(date: String, km: Double, amount: Double = 40.0) = KOiLRecordEntity(
        reoilDate = date,
        mileage = km,
        oilAmount = amount,
        payableAmount = amount * 8.0,
        actualAmount = amount * 8.0,
        oilPrice = 8.0,
        oilGrade = "92",
        province = "广东",
        source = "entry_page",
        isFullTank = true,
    )

    private fun cloudJson(vararg records: KOiLRecordEntity): String =
        BackupCodec.buildBackup(records.toList(), "test")

    @Test
    fun `对账预览只算不写_数据库与云端零变化`() = runBlocking {
        dao.insert(entity("2026-01-01", 1000.0))
        dao.insert(entity("2026-01-02", 1100.0))
        cloudBody = cloudJson(
            entity("2026-01-03", 1200.0),
            entity("2026-01-04", 1300.0),
        )
        val before = dao.getRange("1970-01-01", "2999-12-31").map { it.reoilDate }
        calls.clear()

        val preview = manager.previewReconcile()

        assertNotNull(preview)
        // 只发了一次 GET，没有任何 PUT
        assertEquals(1, calls.count { it.first == "GET" })
        assertEquals(0, calls.count { it.first == "PUT" })
        // 本机记录一条没动
        assertEquals(before, dao.getRange("1970-01-01", "2999-12-31").map { it.reoilDate })
        // 云端独有 2 / 本机独有 2
        assertEquals(2, preview!!.stats.cloudOnlyCount)
        assertEquals(2, preview.stats.localOnlyCount)
    }

    @Test
    fun `合并复用对账那次GET的ETag_不额外发GET`() = runBlocking {
        dao.insert(entity("2026-02-01", 1000.0))
        cloudBody = cloudJson(entity("2026-02-02", 1100.0))

        val preview = manager.previewReconcile()!!
        calls.clear()
        val ok = manager.applyReconcile(preview, SyncReconcile.Mode.MERGE)

        assertTrue(ok)
        // 合并期间只有 MKCOL + PUT，没有第二次 GET
        assertEquals(0, calls.count { it.first == "GET" })
        assertEquals(1, calls.count { it.first == "PUT" })
        // PUT 的前置条件就是那次 GET 拿到的 ETag
        assertEquals(etag, putIfMatch)
        // 云端独有那条已经进了本机
        assertTrue(
            dao.getRange("1970-01-01", "2999-12-31").any { it.reoilDate == "2026-02-02" },
        )
    }

    @Test
    fun `以云端为准_删本机独有并用云端覆盖冲突_同时清撤销标记`() = runBlocking {
        dao.insert(entity("2026-03-01", 1000.0, amount = 30.0))   // 冲突：云端同键 40L
        dao.insert(entity("2026-03-02", 1100.0))                  // 本机独有
        cloudBody = cloudJson(
            entity("2026-03-01", 1000.0, amount = 40.0),
            entity("2026-03-03", 1200.0),                         // 云端独有
        )
        settings.setSyncRevivedKeys(setOf(RecordMerger.keyOf("2026-01-01", 500.0)))

        val preview = manager.previewReconcile()!!
        val ok = manager.applyReconcile(preview, SyncReconcile.Mode.CLOUD_WINS)

        assertTrue(ok)
        val rows = dao.getRange("1970-01-01", "2999-12-31")
        // 本机独有被删掉
        assertTrue(rows.none { it.reoilDate == "2026-03-02" })
        // 冲突项变成云端版本
        assertEquals(40.0, rows.first { it.reoilDate == "2026-03-01" }.oilAmount, 0.001)
        // 云端独有补进来了
        assertTrue(rows.any { it.reoilDate == "2026-03-03" })
        // 用户刚同意服从云端：撤销标记必须清零，否则下次推送会把云端删除又摘回来
        assertEquals(emptySet<String>(), settings.syncRevivedKeys.first())
        // 以云端为准不推送：不该有 PUT
        assertEquals(0, calls.count { it.first == "PUT" })
    }

    @Test
    fun `覆盖前的云端副本只保留最近三份`() = runBlocking {
        repeat(4) { i ->
            manager.backupBeforeOverwrite("""{"marker":$i}""")
            Thread.sleep(6)
        }
        val dir = File(context.filesDir, "sync-backups")
        val kept = dir.listFiles { f: File -> f.name.startsWith("koil_backup.before-") }
            ?.sortedBy { it.name }
            .orEmpty()
        assertEquals(3, kept.size)
        // 留下的是最新三份（最早那份已被删）
        assertTrue(kept.none { it.readText() == """{"marker":0}""" })
        assertEquals("""{"marker":3}""", kept.last().readText())
    }

    @Test
    fun `云端没有快照时预览返回null_由调用方按首次上传处理`() = runBlocking {
        dao.insert(entity("2026-04-01", 1000.0))
        // GET 404 = 云端还没备份：MockEngine 用空体 + 200 仍会被当作「内容为空」
        cloudBody = ""
        val preview = manager.previewReconcile()
        assertEquals(null, preview)
    }
}
