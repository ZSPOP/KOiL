package com.llpop.koil

import com.llpop.koil.data.local.entity.KOiLPriceEntity
import com.llpop.koil.ui.screen.settings.computePriceAdjustItems
import com.llpop.koil.ui.screen.settings.normalizeGradeForImport
import com.llpop.koil.ui.screen.settings.normalizeThresholdPct
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 纯函数归一与涨跌计算回归：
 * - computePriceAdjustItems：跨年首条 prev、同日记录排除、搁浅不计涨跌
 * - normalizeThresholdPct：取整 + 区间口径（异常 0..50 / 偏差 1..50）
 * - normalizeGradeForImport：全名/简写/后缀剥离
 */
class NormalizationRegressionTest {

    private fun price(date: String, value: Double, stranded: Boolean = false) =
        KOiLPriceEntity("广东", "92号汽油", date, value, "test", false, stranded)

    /** ===== 涨跌计算 ===== */
    @Test
    fun `跨年首条调价的prev取上年末记录`() {
        val records = listOf(
            price("2025-12-24", 8.00),
            price("2026-01-23", 8.30),
        )
        val agg = computePriceAdjustItems(records, records.filter { it.effectiveDate.startsWith("2026") })
        val first = agg.items.single()
        // 年过滤后 i==0 的记录，prev 仍来自 2025 年记录（跨年涨跌不丢失）
        assertEquals(0.30, first.diff!!, 1e-9)
        assertEquals(0.30, agg.rise, 1e-9)
        assertEquals(1, agg.upCount)
        assertEquals(true, first.isUp)
    }

    @Test
    fun `同日多条记录的prev不含同日兄弟`() {
        val records = listOf(
            price("2026-01-01", 8.00),
            price("2026-01-15", 8.20),
            price("2026-01-15", 8.25), // 同日第二条（链校验保留口径）
            price("2026-02-01", 8.50),
        )
        val agg = computePriceAdjustItems(records, records)
        // 2026-02-01 的 prev 是「生效日 < 02-01 的最后一条」= 01-15 的最后一条 8.25 → diff 0.25
        val last = agg.items.last()
        assertEquals(0.25, last.diff!!, 1e-9)
        // 而 01-15 两条的 prev 都是 01-01 的 8.00（同日兄弟被 takeWhile/< 排除）：
        // 8.20−8.00=0.20、8.25−8.00=0.25
        assertEquals(0.20, agg.items[1].diff!!, 1e-9)
        assertEquals(0.25, agg.items[2].diff!!, 1e-9)
    }

    @Test
    fun `搁浅行diff归零且不计涨跌统计`() {
        val records = listOf(
            price("2026-01-23", 8.30),
            price("2026-02-10", 8.30, stranded = true), // 维持价
            price("2026-03-05", 8.10),
        )
        val agg = computePriceAdjustItems(records, records)
        val stranded = agg.items[1]
        assertEquals(0.0, stranded.diff!!, 1e-9)
        assertNull(stranded.isUp)
        assertEquals("01-23", stranded.keptFrom)
        assertEquals(0.0, agg.rise, 1e-9)
        // 只统计非搁浅调价：03-05 下调 0.20
        assertEquals(0.20, agg.fall, 1e-9)
        assertEquals(1, agg.downCount)
        assertEquals(0, agg.upCount)
    }

    @Test
    fun `库内首条diff与天数均为null`() {
        val agg = computePriceAdjustItems(listOf(price("2026-01-23", 8.30)), listOf(price("2026-01-23", 8.30)))
        val first = agg.items.single()
        assertNull(first.diff)
        assertNull(first.isUp)
        assertNull(first.pctChange)
        assertNull(first.daysSincePrev)
    }

    @Test
    fun `daysSincePrev按日历天数计算`() {
        val records = listOf(price("2026-01-31", 8.00), price("2026-02-01", 8.20))
        val agg = computePriceAdjustItems(records, records)
        assertEquals(1, agg.items.last().daysSincePrev)
    }

    /** ===== 阈值归一 ===== */
    @Test
    fun `阈值小数四舍五入取整`() {
        assertEquals(21.0, normalizeThresholdPct(20.5, 1.0, 50.0)!!, 1e-9)
        assertEquals(20.0, normalizeThresholdPct(20.4, 1.0, 50.0)!!, 1e-9)
        assertEquals(1.0, normalizeThresholdPct(0.5, 1.0, 50.0)!!, 1e-9) // round(0.5)=1
        assertEquals(0.0, normalizeThresholdPct(0.4, 0.0, 50.0)!!, 1e-9) // 异常阈值下限 0
    }

    @Test
    fun `阈值越界与非有限值返回null`() {
        assertNull(normalizeThresholdPct(0.4, 1.0, 50.0))   // round 后 0 < 偏差下限
        assertNull(normalizeThresholdPct(50.6, 1.0, 50.0))  // round 后 51 > 上限
        assertEquals(50.0, normalizeThresholdPct(50.4, 1.0, 50.0)!!, 1e-9) // round 后 50，未越界
        assertNull(normalizeThresholdPct(-3.0, 0.0, 50.0))
        assertNull(normalizeThresholdPct(Double.NaN, 0.0, 50.0))
        assertNull(normalizeThresholdPct(Double.POSITIVE_INFINITY, 0.0, 50.0))
        assertEquals(50.0, normalizeThresholdPct(50.0, 1.0, 50.0)!!, 1e-9)
    }

    /** ===== 油号归一 ===== */
    @Test
    fun `CSV油号全名与简写归一`() {
        assertEquals("92", normalizeGradeForImport("92号汽油"))
        assertEquals("95", normalizeGradeForImport("95号汽油"))
        assertEquals("98", normalizeGradeForImport("98号汽油"))
        assertEquals("0", normalizeGradeForImport("0号柴油"))
        assertEquals("0", normalizeGradeForImport("柴油"))
        assertEquals("92", normalizeGradeForImport("92号"))
        assertEquals("92", normalizeGradeForImport("92#"))
        assertEquals("92", normalizeGradeForImport("92"))
        assertEquals("95", normalizeGradeForImport(" 95号汽油 "))
        // 无数字的"汽油"无法判定油号 → null，交给逐行错误提示
        assertNull(normalizeGradeForImport("汽油"))
        assertNull(normalizeGradeForImport(""))
        assertNull(normalizeGradeForImport(null))
    }
}
