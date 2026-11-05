package com.llpop.koil

import com.llpop.koil.data.local.entity.KOiLRecordEntity
import com.llpop.koil.domain.logic.ConsumptionStatus
import com.llpop.koil.domain.logic.DerivedCalculator
import com.llpop.koil.domain.logic.NumberFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 严格口径回归测试：把「显示/派生计算」的边界行为固定下来。
 * 每条断言都是用户可见的正确行为，不是对现状的复述。
 */
class DomainRobustnessTest {

    // ===== NumberFormat：金额/度量/百分比 =====

    @Test
    fun `金额不出现负零`() {
        // -0.0001 四舍五入到分是 0.00，显示成 "-0.00" 会让用户以为扣了钱
        assertEquals("0.00", NumberFormat.money(-0.0001))
        assertEquals("0.00", NumberFormat.money(-0.0))
    }

    @Test
    fun `度量按十进制四舍五入而非二进制近似`() {
        // 7.855 的 double 实际是 7.85499999...，用 BigDecimal(double) 会错舍成 7.85
        assertEquals("7.86", NumberFormat.measure(7.855))
        assertEquals("2.68", NumberFormat.measure(2.675))
        assertEquals("1.01", NumberFormat.measure(1.005))
    }

    @Test
    fun `百分比按十进制四舍五入`() {
        assertEquals("20.6", NumberFormat.pct(20.55))
        assertEquals("20", NumberFormat.pct(20.0))
        assertEquals("-3.5", NumberFormat.pct(-3.45))
    }

    @Test
    fun `非有限值不得抛异常`() {
        // 任何 NaN/Infinity 走到这里都不能崩：崩溃点在最不起眼的文本格式化里
        NumberFormat.measure(Double.NaN)
        NumberFormat.measure(Double.POSITIVE_INFINITY)
        NumberFormat.money(Double.NEGATIVE_INFINITY)
        NumberFormat.pct(Double.NaN)
    }

    // ===== DerivedCalculator：里程/油耗派生 =====

    private fun rec(
        id: Long,
        date: String,
        mileage: Double,
        oil: Double = 40.0,
        payable: Double = 300.0,
        actual: Double = 300.0,
    ) = KOiLRecordEntity(
        id = id,
        reoilDate = date,
        mileage = mileage,
        oilAmount = oil,
        payableAmount = payable,
        actualAmount = actual,
        oilPrice = 7.5,
        oilGrade = "92",
        province = "广东",
        source = "entry_page",
    )

    @Test
    fun `首条记录无里程段且不参与油耗`() {
        val out = DerivedCalculator.compute(listOf(rec(1, "2026-01-01", 10_000.0)), 20.0)
        assertNull(out[0].distance)
        assertNull(out[0].oilConsumption)
        assertTrue(out[0].shortDistanceSkipped.not())
    }

    @Test
    fun `里程倒退是异常不是短途`() {
        // 里程表被重置/填错：distance 为负。显示成「短距」会掩盖数据错误
        val out = DerivedCalculator.compute(
            listOf(rec(1, "2026-01-01", 10_000.0), rec(2, "2026-01-05", 9_000.0)), 20.0
        )
        assertEquals(-1_000.0, out[1].distance!!, 1e-9)
        assertNull(out[1].oilConsumption)
        assertFalse("里程倒退不应被归为短途", out[1].shortDistanceSkipped)
        assertNull("负里程不得产出每公里成本", out[1].costPerKm)
    }

    @Test
    fun `短途恰好100公里边界应计入油耗`() {
        val out = DerivedCalculator.compute(
            listOf(rec(1, "2026-01-01", 10_000.0), rec(2, "2026-01-02", 10_100.0)), 20.0
        )
        assertEquals(40.0, out[1].oilConsumption!!, 1e-9) // 40L / 100km * 100
        assertFalse(out[1].shortDistanceSkipped)
    }

    @Test
    fun `油量为零不产生无穷单价`() {
        val out = DerivedCalculator.compute(
            listOf(
                rec(1, "2026-01-01", 10_000.0),
                rec(2, "2026-01-02", 10_500.0, oil = 0.0, payable = 0.0, actual = 0.0),
            ), 20.0
        )
        assertNull("油量 0 时单价应为空而不是 Infinity", out[1].unitPricePayable)
        assertNull(out[1].unitPriceActual)
        assertTrue(out[1].oilConsumption!!.isFinite())
    }

    @Test
    fun `油耗异常判定用均值上下浮动`() {
        // 三段 500km：8、8、12 L/100km → 均值 9.33，阈值 20% → 上界 11.2，第三条判偏高
        val out = DerivedCalculator.compute(
            listOf(
                rec(1, "2026-01-01", 10_000.0, oil = 40.0),
                rec(2, "2026-01-08", 10_500.0, oil = 40.0),
                rec(3, "2026-01-15", 11_000.0, oil = 40.0),
                rec(4, "2026-01-22", 11_500.0, oil = 60.0),
            ), 20.0
        )
        assertEquals(ConsumptionStatus.NORMAL, out[1].consumptionStatus)
        assertEquals(ConsumptionStatus.NORMAL, out[2].consumptionStatus)
        assertEquals(ConsumptionStatus.HIGH, out[3].consumptionStatus)
        assertTrue(out[3].deviationPct > 0)
    }

    // ===== 省份白名单（定位/导入的合法性闸门） =====

    @Test
    fun `省份白名单完整且无重复`() {
        val all = com.llpop.koil.data.oil.Provinces.ALL
        assertEquals("应为 31 个省级行政区", 31, all.size)
        assertEquals("不得重复", all.size, all.toSet().size)
        assertTrue(all.all { it.isNotBlank() })
    }

    @Test
    fun `省份校验拒绝空值与未知地区`() {
        assertTrue(com.llpop.koil.data.oil.Provinces.isValid("广东"))
        assertTrue(com.llpop.koil.data.oil.Provinces.isValid("内蒙古"))
        assertFalse(com.llpop.koil.data.oil.Provinces.isValid(null))
        assertFalse(com.llpop.koil.data.oil.Provinces.isValid(""))
        // 定位/接口可能返回英文或带后缀的全称，未归一化前必须拒绝，否则四宫格查不到价
        assertFalse(com.llpop.koil.data.oil.Provinces.isValid("Guangdong"))
        assertFalse(com.llpop.koil.data.oil.Provinces.isValid("广东省"))
    }

    // ===== 天数差（导出/明细用） =====

    @Test
    fun `天数差计算与非法日期容错`() {
        assertEquals(7L, DerivedCalculator.daysBetween("2026-01-01", "2026-01-08"))
        assertEquals(0L, DerivedCalculator.daysBetween("2026-02-28", "2026-02-28"))
        // 跨闰年 2 月
        assertEquals(1L, DerivedCalculator.daysBetween("2024-02-28", "2024-02-29"))
        assertNull(DerivedCalculator.daysBetween("2026-02-30", "2026-03-01"))
        assertNull(DerivedCalculator.daysBetween("bad", "2026-03-01"))
    }
}
