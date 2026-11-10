package com.llpop.koil

import com.llpop.koil.data.local.entity.KOiLRecordEntity
import com.llpop.koil.data.sync.BackupCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * BackupCodec 使用 Android org.json，普通 JVM 单测里的 android.jar 只是 stub。
 * Robolectric 提供真实的 org.json 实现，才能验证 NaN/Infinity 与显式 null 的行为。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class BackupCodecRobolectricTest {

    private fun rec(actual: Double, payable: Double, oilPrice: Double?) = KOiLRecordEntity(
        reoilDate = "2026-03-01",
        mileage = 1000.0,
        oilAmount = 40.0,
        payableAmount = payable,
        actualAmount = actual,
        oilPrice = oilPrice,
        oilGrade = "92",
        province = "广东",
        source = "entry_page",
    )

    @Test
    fun `非有限值不会让整包备份序列化失败`() {
        val bad = rec(
            actual = Double.POSITIVE_INFINITY,
            payable = Double.NaN,
            oilPrice = Double.NEGATIVE_INFINITY,
        )
        val json = BackupCodec.buildBackup(listOf(bad), "test")
        assertTrue(json.contains("\"actualAmount\":null"))
        // 恢复端按统一校验跳过该行，而不是把 Infinity 再次写回本地
        val parsed = BackupCodec.parse(json)
        assertEquals(0, parsed.records.size)
    }

    @Test
    fun `显式null的可选字段恢复后仍是null而不是字符串null`() {
        val json = """
            {"records":[{
              "date":"2026-03-01","mileage":1000,"oilAmount":40,
              "payableAmount":300,"actualAmount":300,
              "oilPrice":null,"oilGrade":null,"province":null,"source":null
            }]}
        """.trimIndent()
        val parsed = BackupCodec.parse(json)
        assertEquals(1, parsed.records.size)
        val r = parsed.records.single()
        assertNull(r.oilPrice)
        assertNull(r.oilGrade)
        assertNull(r.province)
        assertNull(r.source)
    }

    @Test
    fun `缺失source字段仍使用云同步默认值`() {
        val json = """
            {"records":[{
              "date":"2026-03-01","mileage":1000,"oilAmount":40,
              "payableAmount":300,"actualAmount":300
            }]}
        """.trimIndent()
        val parsed = BackupCodec.parse(json)
        assertEquals("cloud_sync", parsed.records.single().source)
    }

    /** oilPrice 解析统一 optNullableDouble 纪律——缺失/显式 null/0/负数一律 null，有效值保留 */
    @Test
    fun `oilPrice解析缺失null与非正值一律返回null`() {
        val rec = """
            {"date":"%s","mileage":1000,"oilAmount":40,
             "payableAmount":300,"actualAmount":300%s}
        """.trimIndent()
        val json = """
            {"records":[
              ${rec.format("2026-03-01", ", \"oilPrice\":3.50")},
              ${rec.format("2026-03-02", "")},
              ${rec.format("2026-03-03", ", \"oilPrice\":null")},
              ${rec.format("2026-03-04", ", \"oilPrice\":0")},
              ${rec.format("2026-03-05", ", \"oilPrice\":-2.5")},
              ${rec.format("2026-03-06", ", \"oilPrice\":\"null\"")}
            ]}
        """.trimIndent()
        val parsed = BackupCodec.parse(json)
        assertEquals(6, parsed.records.size)
        assertEquals(
            listOf(3.50, null, null, null, null, null),
            parsed.records.map { it.oilPrice },
        )
    }
}
