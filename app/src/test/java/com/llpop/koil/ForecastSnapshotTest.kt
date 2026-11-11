package com.llpop.koil

import com.llpop.koil.data.oil.CrudeOilEstimate
import com.llpop.koil.data.oil.ForecastSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 预测快照的编解码。
 *
 * 这段代码存在的唯一理由是冷启动那 2–3 秒：进程重启后内存里的预测已经没了，如果快照解析
 * 出错（多一段、少一段、日期空串处理错），界面上要么退回「等待网络」，要么显示一条字段错位的
 * 假记录 —— 后者更糟，因为用户看不出它是错的。
 */
class ForecastSnapshotTest {

    private fun estimate(
        amount: Double = -934.0,
        source: String = "EIA",
        dataDate: String? = "2026-11-09",
        releaseDate: String? = "2026-11-10",
        sampleDays: Int? = 7,
    ) = CrudeOilEstimate(
        amountPerTon = amount,
        sourceLabel = source,
        dataDate = dataDate,
        sampleDays = sampleDays,
        sourceReleaseDate = releaseDate,
    )

    @Test
    fun `编码后解码可完整还原`() {
        val encoded = ForecastSnapshot.encode(estimate())
        val decoded = ForecastSnapshot.decode(encoded, at = 1_000L, now = 2_000L)
        assertEquals(-934.0, decoded!!.amountPerTon, 0.0)
        assertEquals("EIA", decoded.sourceLabel)
        assertEquals("2026-11-09", decoded.dataDate)
        assertEquals("2026-11-10", decoded.sourceReleaseDate)
        assertEquals(7, decoded.sampleDays)
    }

    @Test
    fun `可空字段编码为空串解码回 null`() {
        val decoded = ForecastSnapshot.decode(
            ForecastSnapshot.encode(estimate(dataDate = null, releaseDate = null, sampleDays = null)),
            at = 1_000L,
            now = 2_000L,
        )
        assertEquals(-934.0, decoded!!.amountPerTon, 0.0)
        assertNull(decoded.dataDate)
        assertNull(decoded.sourceReleaseDate)
        assertNull(decoded.sampleDays)
    }

    @Test
    fun `超过二十四小时的快照丢弃`() {
        val encoded = ForecastSnapshot.encode(estimate())
        val at = 1_000L
        assertNull(ForecastSnapshot.decode(encoded, at = at, now = at + ForecastSnapshot.MAX_AGE_MS + 1))
    }

    @Test
    fun `刚好卡在二十四小时仍可用`() {
        val encoded = ForecastSnapshot.encode(estimate())
        val at = 1_000L
        assertEquals(
            -934.0,
            ForecastSnapshot.decode(encoded, at = at, now = at + ForecastSnapshot.MAX_AGE_MS)!!.amountPerTon,
            0.0,
        )
    }

    @Test
    fun `没写过或写时间非法时视为无快照`() {
        val encoded = ForecastSnapshot.encode(estimate())
        assertNull(ForecastSnapshot.decode("", at = 1_000L, now = 2_000L))
        assertNull(ForecastSnapshot.decode(encoded, at = 0L, now = 2_000L))
        assertNull(ForecastSnapshot.decode(encoded, at = -1L, now = 2_000L))
    }

    @Test
    fun `段数不足或金额非数字时视为无快照`() {
        // 少一段：旧版本或写入被截断，宁可退回等待网络，也不要把错位字段当日期显示
        assertNull(ForecastSnapshot.decode("-934.0|EIA|2026-11-09|2026-11-10", at = 1_000L, now = 2_000L))
        assertNull(ForecastSnapshot.decode("abc|EIA|2026-11-09|2026-11-10|7", at = 1_000L, now = 2_000L))
        // 来源为空：UI 靠它区分「估算」/「单日参考」，空标签会显示成一句无来源的结论
        assertNull(ForecastSnapshot.decode("-934.0||2026-11-09|2026-11-10|7", at = 1_000L, now = 2_000L))
    }
}
