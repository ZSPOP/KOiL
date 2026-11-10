package com.llpop.koil

import com.llpop.koil.data.oil.CrudeOilEstimator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 预测幅度回归测试：固定「本窗口只取窗口内交易日」这一口径。
 *
 * 背景：旧实现按「全序列往前取 10 个交易日」取值，调价后头几天会把上一窗口的走势
 * 混进本窗口，把幅度稀释到接近 0。实测 2026-11 那个窗口本窗口只有 2 个交易日，
 * 混入 8 天旧数据后输出 -91.5 元/吨，而仅用窗口内数据为 -647.8 元/吨。
 *
 * 数据取自 EIA 布伦特现货（RBRTE）真实日度值，不是构造出来的平滑曲线——
 * 平滑数据无法暴露「混入上一窗口」这个缺陷。
 */
class CrudeOilEstimatorTest {

    /** 2026-10-01 ~ 2026-11-03 的真实 EIA 布伦特现货日度序列（22 条，满足 MIN_EIA_POINTS=20） */
    private val eia = listOf(
        "2026-10-01" to 114.82,
        "2026-10-02" to 135.51,
        "2026-10-05" to 125.51,
        "2026-10-06" to 125.44,
        "2026-10-07" to 126.00,
        "2026-10-08" to 130.18,
        "2026-10-09" to 130.03,
        "2026-10-12" to 129.95,
        "2026-10-13" to 125.78,
        "2026-10-14" to 126.55,
        "2026-10-15" to 126.35,
        "2026-10-16" to 119.33,
        "2026-10-19" to 118.82,
        "2026-10-20" to 115.22,
        "2026-10-21" to 117.37,
        "2026-10-22" to 125.39,
        "2026-10-23" to 128.30,
        "2026-10-26" to 128.12,
        "2026-10-27" to 123.10,
        "2026-10-28" to 119.20,
        "2026-10-29" to 115.28,
        "2026-10-30" to 120.15,
        "2026-11-02" to 107.39,
        "2026-11-03" to 107.03,
    )

    /** 窗口日：10-16 与 10-30；10-31 为 10-30 窗口的生效日 */
    private val adjustDates = listOf("2026-10-16", "2026-10-30")

    // ---------- 核心回归：不得跨窗口回补 ----------

    @Test
    fun `窗口内仅两个交易日时只取窗口内数据`() {
        val r = CrudeOilEstimator.calcFromEiaSeries(
            points = eia,
            adjustDates = adjustDates,
            baseDate = "2026-10-31",
            today = "2026-11-10",
        )
        assertNotNull(r)
        r!!
        // 本窗口 = (10-30, 11-10] = 11-02、11-03 两天
        assertEquals("样本天数必须是窗口内实际交易日", 2, r.sampleDays)
        // 窗口内均价 107.21 vs 基准 10-19~10-30 均价 121.095 → -11.466% → -647.8 元/吨
        assertEquals(-647.8, r.amountPerTon, 1.0)
    }

    @Test
    fun `跨窗口回补会让幅度失真到零附近`() {
        val r = CrudeOilEstimator.calcFromEiaSeries(eia, adjustDates, "2026-10-31", "2026-11-10")!!
        // 旧实现（往前取满 10 个交易日，混入 8 天上一窗口）会得到 -91.5；
        // 该值把真实的 10.8% 跌幅稀释成 1.6%，必须挡在 -600 之外
        assertTrue(
            "幅度 ${r.amountPerTon} 不应接近旧实现的 -91.5",
            r.amountPerTon < -600.0,
        )
    }

    @Test
    fun `样本天数随窗口推进递增且不超过十`() {
        val days = listOf("2026-11-02", "2026-11-03").map { today ->
            CrudeOilEstimator.calcFromEiaSeries(eia, adjustDates, "2026-10-31", today)!!.sampleDays
        }
        assertEquals(listOf(1, 2), days)
        assertTrue(days.all { it <= 10 })
    }

    // ---------- 满窗口：与官方 10 天口径一致 ----------

    @Test
    fun `窗口满十个交易日时样本天数为十`() {
        val r = CrudeOilEstimator.calcFromEiaSeries(
            points = eia,
            adjustDates = adjustDates,
            baseDate = "2026-10-17",   // 10-16 窗口的生效日
            today = "2026-10-30",
        )
        assertNotNull(r)
        r!!
        assertEquals(10, r.sampleDays)
        // 窗口 10-19~10-30 均价 121.095 vs 基准 10-05~10-16 均价 126.512 → -4.282% → -241.9
        assertEquals(-241.9, r.amountPerTon, 1.0)
    }

    // ---------- 边界：数据不足时宁可不出值 ----------

    @Test
    fun `基准日贴近序列头部时返回 null`() {
        // 基准锚在 10-02，往前凑不满 10 个交易日 → 与完整窗口不可比，不得输出
        val r = CrudeOilEstimator.calcFromEiaSeries(eia, adjustDates, "2026-10-02", "2026-11-10")
        assertNull(r)
    }

    @Test
    fun `窗口内没有交易日时返回 null`() {
        // 10-31 是窗口日次日、11-01 周日，EIA 无数据 → 本窗口尚无交易日
        val r = CrudeOilEstimator.calcFromEiaSeries(eia, adjustDates, "2026-10-31", "2026-10-31")
        assertNull(r)
    }

    @Test
    fun `EIA 序列不足二十条时返回 null`() {
        val short = eia.take(10)
        val r = CrudeOilEstimator.calcFromEiaSeries(short, adjustDates, "2026-10-31", "2026-11-10")
        assertNull(r)
    }

    // ---------- 兜底：没有窗口日历时退回 最近10 对 前10 ----------

    @Test
    fun `无窗口日历回退最近十对前十`() {
        val r = CrudeOilEstimator.calcFromEiaSeries(
            points = eia,
            adjustDates = emptyList(),
            baseDate = null,
            today = "2026-11-10",
        )
        assertNotNull(r)
        r!!
        assertEquals(10, r.sampleDays)
        // 最近 10 天 = 10-21~11-03 均价 119.133；前 10 天 = 10-07~10-20 均价 124.821
        // (119.133 - 124.821) / 124.821 = -4.557% → -257.5
        assertEquals(-257.5, r.amountPerTon, 1.5)
    }
}
