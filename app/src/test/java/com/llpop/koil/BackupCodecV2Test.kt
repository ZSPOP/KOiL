package com.llpop.koil

import com.llpop.koil.data.local.entity.KOiLRecordEntity
import com.llpop.koil.data.sync.BackupCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 快照 v2（`deleted` 删除扩散）的格式契约：
 * - v2 写出去的 `deleted` 能原样读回来（云同步路径要求「能无损往返」）；
 * - v1 快照没有 `deleted` → 读作空集，不能报错；
 * - 同键多次删除取最晚 `at`，否则摘除时会摘一条漏一条；
 * - `at` 写入前钳制到 now+1d：时间设到未来的设备不能永久获胜；
 * - 窗口裁剪（180 天 / 1000 条），文件不能无限长。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class BackupCodecV2Test {

    private val day = 24L * 60 * 60 * 1000

    private fun record(date: String, mileage: Double) = KOiLRecordEntity(
        reoilDate = date,
        mileage = mileage,
        oilAmount = 40.0,
        payableAmount = 300.0,
        actualAmount = 300.0,
        oilPrice = 7.5,
        oilGrade = "92#",
        province = "广东",
        source = "entry_page",
    )

    @Test
    fun `v2 写出的 deleted 能原样读回来`() {
        val now = System.currentTimeMillis()
        val json = BackupCodec.buildBackup(
            records = listOf(record("2026-11-01", 1000.0)),
            appVersion = "test",
            deleted = listOf(
                BackupCodec.DeletedEntry("2026-10-01|900.0", now - 1000),
                BackupCodec.DeletedEntry("2026-10-02|950.0", now - 2000),
            ),
        )
        val parsed = BackupCodec.parse(json)

        assertEquals(2, parsed.version)
        assertEquals(2, parsed.deleted.size)
        assertEquals(
            setOf("2026-10-01|900.0", "2026-10-02|950.0"),
            parsed.deleted.map { it.key }.toSet(),
        )
        // records 一条不少：加 deleted 不能影响原有载荷
        assertEquals(1, parsed.records.size)
    }

    @Test
    fun `v1 快照没有 deleted 时读作空集`() {
        // 手写 v1：只有 records，没有 deleted 键
        val v1 = """
            {"version":1,"records":[{"date":"2026-10-01","mileage":900,"oilAmount":40,
            "payableAmount":300,"actualAmount":300}]}
        """.trimIndent()
        val parsed = BackupCodec.parse(v1)

        assertEquals(1, parsed.version)
        assertTrue(parsed.deleted.isEmpty())
        assertEquals(1, parsed.records.size)
    }

    @Test
    fun `同一键出现两次时只留一条且取最晚 at`() {
        val now = System.currentTimeMillis()
        val json = """
            {"version":2,"records":[],"deleted":[
              {"key":"2026-10-01|900.0","at":${now - 5000}},
              {"key":"2026-10-01|900.0","at":${now - 1000}}
            ]}
        """.trimIndent()
        val parsed = BackupCodec.parse(json)

        assertEquals(1, parsed.deleted.size)
        assertEquals(now - 1000, parsed.deleted.single().at)
    }

    @Test
    fun `at 晚于 now 加一天时被钳制`() {
        val now = System.currentTimeMillis()
        // 一台时间设到 200 天后的设备
        val json = BackupCodec.buildBackup(
            records = emptyList(),
            appVersion = "test",
            deleted = listOf(BackupCodec.DeletedEntry("2026-10-01|900.0", now + 200 * day)),
        )
        val at = BackupCodec.parse(json).deleted.single().at

        // 钳到 now+1d 以内，而不是 200 天后
        assertTrue("at=$at 必须被压到 now+1d 以内", at <= now + day + 2000)
        assertTrue("at=$at 不能退回到过去", at >= now)
    }

    @Test
    fun `超出 180 天窗口的删除标记不写入`() {
        val now = System.currentTimeMillis()
        val json = BackupCodec.buildBackup(
            records = emptyList(),
            appVersion = "test",
            deleted = listOf(
                BackupCodec.DeletedEntry("2026-01-01|100.0", now - 200 * day),
                BackupCodec.DeletedEntry("2026-10-01|900.0", now - day),
            ),
        )
        val keys = BackupCodec.parse(json).deleted.map { it.key }

        assertEquals(listOf("2026-10-01|900.0"), keys)
    }

    @Test
    fun `删除标记最多写 1000 条`() {
        val now = System.currentTimeMillis()
        val deleted = (1..1200).map {
            BackupCodec.DeletedEntry("2026-10-01|${it.toDouble()}", now - it)
        }
        val json = BackupCodec.buildBackup(emptyList(), "test", deleted)

        assertEquals(1000, BackupCodec.parse(json).deleted.size)
    }

    @Test
    fun `空 deleted 也写出数组_结构自检认得出`() {
        val json = BackupCodec.buildBackup(emptyList(), "test")
        assertTrue(json.contains("\"deleted\":[]"))
        assertTrue(BackupCodec.looksLikeBackup(json))
    }
}
