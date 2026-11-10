package com.llpop.koil

import com.llpop.koil.data.oil.UsdCnyMonthly
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 汇率静态表的取值契约：**任何输入都要拿到一个可用值**。
 *
 * 这条契约比「取到精确月均值」更重要——如果漏更月份时返回 0 或抛异常，
 * 金额会被静默算成 0（用户看到「预计不调价」）或整块预测消失，
 * 而这两种坏法都不会有人报 bug。所以回退链必须显式并有测试。
 */
class UsdCnyMonthlyTest {

    @Test
    fun `月内任意一天取当月均值`() {
        assertEquals(6.6850, UsdCnyMonthly.rateFor("2026-10-01"), 1e-9)
        assertEquals(6.6850, UsdCnyMonthly.rateFor("2026-10-31"), 1e-9)
        // 只给月份也认
        assertEquals(6.6676, UsdCnyMonthly.rateFor("2026-11"), 1e-9)
    }

    @Test
    fun `表未更新时回退到最近一个更早月份`() {
        // 表止于 2026-11：之后的月份取 2026-11，不是 0、不是抛异常
        assertEquals(6.6676, UsdCnyMonthly.rateFor("2027-03-15"), 1e-9)
        assertEquals(6.6676, UsdCnyMonthly.rateFor("2026-12-01"), 1e-9)
    }

    @Test
    fun `早于全表时回退到最早月份`() {
        assertEquals(7.1049, UsdCnyMonthly.rateFor("2019-06-01"), 1e-9)
    }

    @Test
    fun `任何日期都返回有限正数`() {
        val samples = listOf("2010-01-01", "2023-12-01", "2024-06-15", "2026-11-09", "2099-12-31")
        samples.forEach { d ->
            val fx = UsdCnyMonthly.rateFor(d)
            assertTrue("$d 取到 $fx，必须是有限正数", fx.isFinite() && fx > 0.0)
            // 6.0~8.0 是人民币汇率三十年的实际区间，越界说明表被写坏了
            assertTrue("$d 取到 $fx，越出合理区间", fx in 6.0..8.0)
        }
    }
}
