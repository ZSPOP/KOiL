package com.llpop.koil

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.llpop.koil.data.local.AppDatabase
import com.llpop.koil.data.local.RecordStore
import com.llpop.koil.data.local.dao.KOiLRecordDao
import com.llpop.koil.data.local.entity.KOiLRecordEntity
import com.llpop.koil.data.settings.AppSettings
import com.llpop.koil.data.sync.BackupCodec
import com.llpop.koil.data.sync.CloudSyncManager
import com.llpop.koil.data.sync.RecordMerger
import com.llpop.koil.data.sync.SyncConfig
import com.llpop.koil.data.sync.WebDavClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 删除扩散 + 快照 v2（PR ③ 评审必查 1/2/3/4/5/6/7/9/10/11/12 条）。
 *
 * 每条测试名对应一条必查项，评审可以按名字找证据。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class SyncTombstoneV2Test {

    private lateinit var context: Context
    private lateinit var settings: AppSettings
    private lateinit var db: AppDatabase
    private lateinit var dao: KOiLRecordDao
    private lateinit var store: RecordStore
    private lateinit var manager: CloudSyncManager

    private var cloudBody = ""
    private var lastPutBody = ""
    private var putStatus = HttpStatusCode.NoContent
    private val calls = mutableListOf<String>()
    private val etag = "\"tomb-etag\""

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        settings = AppSettings(context)
        settings.clearSyncSecretForTest()
        // DataStore 是进程级单例：墓碑/撤销/缓存会从上个用例漏过来
        settings.clearSyncTombstonesForTest()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.KOiLRecordDao()
        store = RecordStore(dao, settings)

        val engine = MockEngine { request ->
            calls.add(request.method.value)
            when (request.method.value) {
                "GET" -> respond(
                    cloudBody,
                    if (cloudBody.isEmpty()) HttpStatusCode.NotFound else HttpStatusCode.OK,
                    headersOf(
                        HttpHeaders.ETag to listOf(etag),
                        HttpHeaders.ContentType to listOf("application/json"),
                    ),
                )
                "PUT" -> {
                    lastPutBody = String(request.body.toByteArray())
                    respond("", putStatus)
                }
                "MKCOL" -> respond("", HttpStatusCode.MethodNotAllowed)
                else -> respond("", HttpStatusCode.OK)
            }
        }
        manager = CloudSyncManager(settings, dao, WebDavClient(engine), db, context).apply {
            // 重试间隔置 0：412 那条测试否则要真睡 15s+60s
            retryDelaysMs = longArrayOf(0L, 0L)
        }

        settings.setSyncEnabled(true)
        settings.setSyncUrl(SyncConfig.DEFAULT_URL)
        settings.setSyncUsername(SyncConfig.DEFAULT_USERNAME)
        settings.setSyncAppPassword(SyncConfig.DEFAULT_APP_PASSWORD)
        settings.setSyncPasswordEverSet(true)
        settings.setSyncEverConfigured()
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

    private fun key(date: String, km: Double) = RecordMerger.keyOf(date, km)

    /** 1 · 撤销是物理摘除：重录后推送，云端 deleted 里不得再有该键 */
    @Test
    fun `撤销后推送_云端deleted里不再有该键`() = runBlocking {
        // 先删一条（墓碑）
        dao.insert(entity("2026-11-01", 1000.0))
        val row = dao.getRange("1970-01-01", "2999-12-31").single()
        store.delete(row)
        assertEquals(1, settings.syncDeletedKeys.first().size)

        // 用户重录同一个键（删错了）
        store.insert(entity("2026-11-01", 1000.0))
        assertEquals(
            setOf(key("2026-11-01", 1000.0)),
            settings.syncRevivedKeys.first(),
        )

        cloudBody = ""
        assertTrue(manager.pushNow(force = true))

        val pushed = BackupCodec.parse(lastPutBody)
        assertEquals(2, pushed.version)
        // 物理摘除：云端 JSON 里不能有这个键
        assertFalse(
            "撤销的键必须从 deleted 里摘掉，不能只在本机过滤",
            pushed.deleted.any { it.key == key("2026-11-01", 1000.0) },
        )
        // 推送成功后才清标记
        assertTrue(settings.syncRevivedKeys.first().isEmpty())
    }

    /** 2 · 摘除失败不清标记：412 之后 revivedKeys 必须还在 */
    @Test
    fun `412时撤销标记必须保留`() = runBlocking {
        dao.insert(entity("2026-11-02", 1010.0))
        val row = dao.getRange("1970-01-01", "2999-12-31").single()
        store.delete(row)
        store.insert(entity("2026-11-02", 1010.0))
        val revivedBefore = settings.syncRevivedKeys.first()
        assertTrue(revivedBefore.isNotEmpty())

        putStatus = HttpStatusCode.PreconditionFailed
        val ok = manager.pushNow(force = true)

        assertFalse("412 不能当成成功", ok)
        assertEquals("摘除没成功，标记不能清", revivedBefore, settings.syncRevivedKeys.first())
    }

    /** 4 · 撤销高于墓碑：先删、再重录，记录必须在 */
    @Test
    fun `重录的记录不会被墓碑当场删掉`() = runBlocking {
        dao.insert(entity("2026-11-03", 1020.0))
        val row = dao.getRange("1970-01-01", "2999-12-31").single()
        store.delete(row)
        assertTrue(dao.getRange("1970-01-01", "2999-12-31").isEmpty())

        store.insert(entity("2026-11-03", 1020.0))

        val rows = dao.getRange("1970-01-01", "2999-12-31")
        assertEquals(1, rows.size)
        assertTrue(
            "撤销标记必须存在，否则下次推送会把这条再删一次",
            settings.syncRevivedKeys.first().contains(key("2026-11-03", 1020.0)),
        )
    }

    /** 5 · 删除时清撤销标记：重录 → 再删，标记为空且墓碑保留 */
    @Test
    fun `再删一次会清掉撤销标记但保留墓碑`() = runBlocking {
        dao.insert(entity("2026-11-04", 1030.0))
        val row = dao.getRange("1970-01-01", "2999-12-31").single()
        store.delete(row)
        store.insert(entity("2026-11-04", 1030.0))
        assertTrue(settings.syncRevivedKeys.first().isNotEmpty())

        val again = dao.getRange("1970-01-01", "2999-12-31").single()
        store.delete(again)

        assertTrue("删了就是删了，撤销标记不能留着", settings.syncRevivedKeys.first().isEmpty())
        assertTrue(
            "墓碑必须还在，否则云端会把它拉回来",
            settings.syncDeletedKeys.first().contains(key("2026-11-04", 1030.0)),
        )
    }

    /** 6 · 同步侧删除不写墓碑：以云端为准删本地，syncDeletedKeys 不得新增 */
    @Test
    fun `以云端为准时删本地不写墓碑`() = runBlocking {
        dao.insert(entity("2026-11-05", 1040.0))          // 本机独有 → 会被云端为准删掉
        dao.insert(entity("2026-11-06", 1050.0))
        cloudBody = BackupCodec.buildBackup(listOf(entity("2026-11-06", 1050.0)), "test")

        val preview = (manager.probeReconcile() as CloudSyncManager.ReconcileProbe.Ready).preview
        assertTrue(manager.applyReconcile(preview, com.llpop.koil.data.sync.SyncReconcile.Mode.CLOUD_WINS))

        assertEquals(1, dao.getRange("1970-01-01", "2999-12-31").size)
        assertTrue(
            "同步侧删除若写墓碑，下次推送又会把它推回云端",
            settings.syncDeletedKeys.first().isEmpty(),
        )
    }

    /** 7 · 同步插入不打标记：合并云端记录不产生 revivedKeys */
    @Test
    fun `合并云端插入的记录不打撤销标记`() = runBlocking {
        dao.insert(entity("2026-11-07", 1060.0))
        val now = System.currentTimeMillis()
        cloudBody = BackupCodec.buildBackup(
            listOf(entity("2026-11-07", 1060.0), entity("2026-11-08", 1070.0)),
            "test",
            listOf(BackupCodec.DeletedEntry(key("2026-11-09", 1080.0), now)),
        )

        val preview = (manager.probeReconcile() as CloudSyncManager.ReconcileProbe.Ready).preview
        assertTrue(manager.applyReconcile(preview, com.llpop.koil.data.sync.SyncReconcile.Mode.MERGE))

        assertEquals(2, dao.getRange("1970-01-01", "2999-12-31").size)
        assertTrue(settings.syncRevivedKeys.first().isEmpty())
    }

    /** 9 · 只拉不推也触发：云端 v1 快照 + 本地缓存里有删除 → 已删记录不得复活 */
    @Test
    fun `只拉不推的设备也不会复活已删记录`() = runBlocking {
        // 云端是 v1（没有 deleted），但本机缓存知道这条被删过
        cloudBody = BackupCodec.buildBackup(listOf(entity("2026-11-10", 1090.0)), "test")
            .replace("\"version\":2", "\"version\":1")
            .replace(",\"deleted\":[]", "")
        settings.setSyncDeletedCache(
            mapOf(key("2026-11-10", 1090.0) to System.currentTimeMillis() - 1000),
        )

        // 走合并路径（只读云端，不写云端）
        val preview = (manager.probeReconcile() as CloudSyncManager.ReconcileProbe.Ready).preview
        assertTrue(manager.applyReconcile(preview, com.llpop.koil.data.sync.SyncReconcile.Mode.MERGE))

        assertTrue(
            "缓存里的删除必须挡住这条记录，否则删过的东西又回来了",
            dao.getRange("1970-01-01", "2999-12-31").isEmpty(),
        )
    }

    /** 10 · at 钳制：设备时间 +200 天的删除标记，写出去被压到 now+1d 以内 */
    @Test
    fun `未来时间的删除标记写出去时被钳制`() = runBlocking {
        val now = System.currentTimeMillis()
        val day = 24L * 60 * 60 * 1000
        cloudBody = BackupCodec.buildBackup(
            emptyList(), "test",
            listOf(BackupCodec.DeletedEntry(key("2026-11-11", 1100.0), now + 200 * day)),
        )

        assertTrue(manager.pushNow(force = true))

        val pushed = BackupCodec.parse(lastPutBody)
        val at = pushed.deleted.single().at
        assertTrue("at=$at 必须 <= now+1d", at <= now + day + 2000)
    }

    /** 11 · deleted 去重取 max：同键两条（at=100、at=200）合并后只剩一条且 at=200 */
    @Test
    fun `同键多次删除合并后只剩最晚一条`() = runBlocking {
        val merged = RecordMerger.mergeTombstones(
            local = emptySet(),
            cloud = listOf(
                BackupCodec.DeletedEntry(key("2026-11-12", 1110.0), 100L),
                BackupCodec.DeletedEntry(key("2026-11-12", 1110.0), 200L),
            ),
            cache = emptyMap(),
            now = 300L,
        )
        assertEquals(1, merged.size)
        assertEquals(200L, merged[key("2026-11-12", 1110.0)])
    }

    /** 12 · 向后兼容：v1 快照能读（deleted 空集）；v2 被抹掉后靠缓存补回 */
    @Test
    fun `v1快照能读且缓存能把删除补回云端`() = runBlocking {
        val v1 = """{"version":1,"records":[{"date":"2026-11-13","mileage":1120,
            "oilAmount":40,"payableAmount":320,"actualAmount":320}]}""".trimIndent()
        val parsed = BackupCodec.parse(v1)
        assertEquals(1, parsed.version)
        assertTrue(parsed.deleted.isEmpty())

        // 模拟「v2 快照被 v1 客户端抹掉」：云端没有 deleted，但本机缓存有
        cloudBody = BackupCodec.buildBackup(listOf(entity("2026-11-13", 1120.0)), "test")
            .replace(",\"deleted\":[]", "")
            .replace("\"version\":2", "\"version\":1")
        // at 取「刚刚」：超过 180 天窗口的删除标记会被裁掉（设计如此）
        settings.setSyncDeletedCache(
            mapOf(key("2026-11-14", 1130.0) to System.currentTimeMillis() - 1000),
        )

        assertTrue(manager.pushNow(force = true))

        val pushed = BackupCodec.parse(lastPutBody)
        assertTrue(
            "缓存里的删除必须补回云端，否则它被抹掉一次就永远消失",
            pushed.deleted.any { it.key == key("2026-11-14", 1130.0) },
        )
    }

    /** 数据赢过删除：云端说删了、本机这条还在 → 保留记录并摘掉云端删除 */
    @Test
    fun `本地还有记录时云端删除让位`() = runBlocking {
        dao.insert(entity("2026-11-15", 1140.0))
        val now = System.currentTimeMillis()
        cloudBody = BackupCodec.buildBackup(
            listOf(entity("2026-11-15", 1140.0)), "test",
            listOf(BackupCodec.DeletedEntry(key("2026-11-15", 1140.0), now)),
        )

        assertTrue(manager.pushNow(force = true))

        // 记录还在
        assertEquals(1, dao.getRange("1970-01-01", "2999-12-31").size)
        // 云端那条删除被摘掉
        val pushed = BackupCodec.parse(lastPutBody)
        assertFalse(pushed.deleted.any { it.key == key("2026-11-15", 1140.0) })
    }

    /** P1-1：云端读不懂 → 「立即备份」必须报失败，且一个字节都没上传 */
    @Test
    fun `云端读不懂时立即备份必须报失败`() = runBlocking {
        cloudBody = """{"version":3,"records":[{"date":"2026-11-16","mileage":1150,
            "oilAmount":40,"payableAmount":320}],"deleted":[]}"""
        calls.clear()

        val ok = manager.pushNow(force = true)

        assertFalse("不能让上层以为推成功了", ok)
        assertEquals("fail:corrupt", settings.syncLastStatus.first())
        assertEquals("读不懂的云端文件一个字节都不许动", 0, calls.count { it == "PUT" })
    }

    /** P1-2：正常推送不带历史缓存——缓存只在「云端可能被旧版抹掉」时参与 */
    @Test
    fun `正常推送不带上历史缓存`() = runBlocking {
        val old = key("2026-10-01", 900.0)
        val day = 24L * 60 * 60 * 1000
        settings.setSyncDeletedCache(mapOf(old to System.currentTimeMillis() - day))

        // 云端已是 v2，本机也写过 v2 → 缓存不参与
        cloudBody = BackupCodec.buildBackup(listOf(entity("2026-11-17", 1160.0)), "test")
        settings.setLastPushedBackupVersion(2)
        assertTrue(manager.pushNow(force = true))

        val pushed = BackupCodec.parse(lastPutBody)
        assertFalse(
            "缓存是修复用的，不该变成常驻第四源：正常同步只写墓碑 ∪ 云端",
            pushed.deleted.any { it.key == old },
        )

        // 同一个键，换成「本机还没写过 v2」→ 缓存必须参与，否则一次故障就永久丢删除
        settings.setLastPushedBackupVersion(0)
        assertTrue(manager.pushNow(force = true))
        assertTrue(
            BackupCodec.parse(lastPutBody).deleted.any { it.key == old },
        )
    }

    /** P2-3：缓存与写出去的 JSON 同一口径，不随历史无限增长 */
    @Test
    fun `缓存超过上限会被裁剪`() = runBlocking {
        val now = System.currentTimeMillis()
        val day = 24L * 60 * 60 * 1000
        val huge = LinkedHashMap<String, Long>()
        repeat(1005) { huge["2026-00-00|$it"] = now - day }
        // 两条超窗口的：它们永远写不出去，也不能永远占着缓存
        huge["2025-01-01|1"] = now - 400 * day
        huge["2025-01-02|2"] = now - 500 * day
        settings.setSyncDeletedCache(huge)
        settings.setLastPushedBackupVersion(0)
        cloudBody = ""

        assertTrue(manager.pushNow(force = true))

        val cache = settings.syncDeletedCache.first()
        assertTrue("缓存不得超上限，实际 ${cache.size}", cache.size <= 1000)
        assertFalse("超窗口的删除不该留在缓存里", cache.containsKey("2025-01-01|1"))
        // 写出去的 JSON 与缓存同源
        assertEquals(cache.keys, BackupCodec.parse(lastPutBody).deleted.map { it.key }.toSet())
    }

    /** 以云端为准之后，本地那批墓碑已作废（否则永远不再生效、白占窗口） */
    @Test
    fun `以云端为准会清掉作废的本地墓碑`() = runBlocking {
        dao.insert(entity("2026-11-19", 1180.0))
        val row = dao.getRange("1970-01-01", "2999-12-31").single()
        store.delete(row)
        assertTrue(settings.syncDeletedKeys.first().isNotEmpty())

        cloudBody = BackupCodec.buildBackup(listOf(entity("2026-11-20", 1190.0)), "test")
        val preview = (manager.probeReconcile() as CloudSyncManager.ReconcileProbe.Ready).preview
        assertTrue(manager.applyReconcile(preview, com.llpop.koil.data.sync.SyncReconcile.Mode.CLOUD_WINS))

        assertTrue(
            "以云端为准之后本地墓碑没有生效对象，留着只是占窗口",
            settings.syncDeletedKeys.first().isEmpty(),
        )
    }

    /** P2：恢复也是「读别人的文件」，读不懂就不许动本地库 */
    @Test
    fun `恢复预览遇到读不懂的快照时一条都不恢复`() = runBlocking {
        dao.insert(entity("2026-11-21", 1200.0))
        // v3 只增字段的典型形态：records 读得出、deleted 一个都读不出
        cloudBody = """{"version":3,"records":[{"date":"2026-11-22","mileage":1210,
            "oilAmount":40,"payableAmount":320}],"deleted":[]}"""

        val result = manager.restorePreview()

        assertTrue("读不懂必须明说，不能当成「云端是空的」", result!!.broken)
        assertEquals(0, result.valid.size)
        assertEquals(1, dao.getRange("1970-01-01", "2999-12-31").size)
    }
}
