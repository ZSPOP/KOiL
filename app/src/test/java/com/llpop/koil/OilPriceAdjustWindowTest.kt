package com.llpop.koil

import com.llpop.koil.data.oil.calculateAdjustWindowDates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * 调价窗口日历算法的回归测试。
 *
 * 这是全项目唯一一块「算错了会静默污染数据」的纯逻辑：
 * 窗口日既决定设置页「今年调价次数」与「距下轮调价日」，又作为
 * `purgeIllegalEffectiveDates` 的合法生效日白名单——算多了会误删真实记录，
 * 算少了会把真实生效日当假日期。之前零测试覆盖。
 *
 * 全部用显式年份 + 显式节假日集合，不读系统时钟，所以在任何一天跑结果都一样。
 */
class OilPriceAdjustWindowTest {

    /** 与算法同口径：(from, to] 区间内的工作日数 */
    private fun workdaysBetween(
        from: LocalDate,
        to: LocalDate,
        holidaySet: Set<String> = emptySet(),
        workdaySet: Set<String> = emptySet(),
    ): Int {
        var n = 0
        var d = from.plusDays(1)
        while (!d.isAfter(to)) {
            val s = d.toString()
            val work = when {
                s in workdaySet -> true
                d.dayOfWeek.value >= 6 -> false
                else -> s !in holidaySet
            }
            if (work) n++
            d = d.plusDays(1)
        }
        return n
    }

    @Test
    fun `无节假日时相邻窗口正好相隔十个工作日`() {
        val dates = calculateAdjustWindowDates("2026-01-05", 2026, emptySet(), emptySet())
            .map(LocalDate::parse)
        assertTrue("应当推算出多个窗口，实际 ${dates.size}", dates.size >= 10)
        dates.zipWithNext().forEach { (a, b) ->
            assertEquals("$a → $b 应相隔 10 个工作日", 10, workdaysBetween(a, b))
        }
    }

    @Test
    fun `窗口日不会落在周末`() {
        val dates = calculateAdjustWindowDates("2026-01-05", 2026, emptySet(), emptySet())
            .map(LocalDate::parse)
        dates.forEach {
            assertFalse("$it 是周六/周日，不应作为窗口日", it.dayOfWeek.value >= 6)
        }
    }

    @Test
    fun `节假日顺延一个工作日`() {
        // 2026-01-05（周一）起数 10 个工作日 → 01-19；把 01-12 标成假日则该工作日不算
        val withHoliday = calculateAdjustWindowDates(
            "2026-01-05", 2026, holidaySet = setOf("2026-01-12"), workdaySet = emptySet(),
        ).map(LocalDate::parse)
        assertEquals(LocalDate.parse("2026-01-20"), withHoliday[1])
    }

    @Test
    fun `调休补班算工作日因此窗口提前`() {
        // 2026-01-10 是周六，补班后计入工作日 → 第一个窗口从 01-19 提前到 01-16
        val withMakeup = calculateAdjustWindowDates(
            "2026-01-05", 2026, holidaySet = emptySet(), workdaySet = setOf("2026-01-10"),
        ).map(LocalDate::parse)
        assertEquals(LocalDate.parse("2026-01-16"), withMakeup[1])
    }

    @Test
    fun `推算结果被限制在目标年份内`() {
        val dates = calculateAdjustWindowDates("2026-06-15", 2026, emptySet(), emptySet())
            .map(LocalDate::parse)
        assertTrue(dates.isNotEmpty())
        dates.forEach {
            assertEquals("$it 越出了 2026 年", 2026, it.year)
        }
    }

    @Test
    fun `锚点不在目标年份也能推算出该年日历`() {
        // 跨年场景：缓存是去年的锚点，仍要能推出今年（向前倒推链要能跨过去年边界）
        val dates = calculateAdjustWindowDates("2025-12-25", 2026, emptySet(), emptySet())
            .map(LocalDate::parse)
        assertTrue("跨年推算不应为空", dates.isNotEmpty())
        dates.forEach { assertEquals(2026, it.year) }
    }

    @Test
    fun `锚点非法返回空而不是抛异常`() {
        // "2026-02-30" 是语法合法但日期不存在；RecordValidator 之外也要拦住
        assertEquals(emptyList<String>(), calculateAdjustWindowDates("2026-02-30", 2026, emptySet(), emptySet()))
        assertEquals(emptyList<String>(), calculateAdjustWindowDates("", 2026, emptySet(), emptySet()))
        assertEquals(emptyList<String>(), calculateAdjustWindowDates("not-a-date", 2026, emptySet(), emptySet()))
    }

    @Test
    fun `节假日集合不可信时整份日历作废而不是伪造窗口日`() {
        // 上游把整年标成假期（或 workdaySet 为空且 holidaySet 覆盖全年）：
        // 推进上界用尽 → 必须返回空让上层降级，绝不能返回 ±14 天之类的兜底值。
        // 曾经这里返回 from.plusDays(14)，于是日历退化成"每 14 天一个窗口"并写进缓存。
        val wholeYear = (1..365)
            .map { LocalDate.of(2026, 1, 1).plusDays((it - 1).toLong()).toString() }
            .toSet()
        assertEquals(
            emptyList<String>(),
            calculateAdjustWindowDates("2026-06-15", 2026, wholeYear, emptySet()),
        )
    }

    @Test
    fun `同一年内结果升序且无重复`() {
        val dates = calculateAdjustWindowDates("2026-06-15", 2026, emptySet(), emptySet())
        assertEquals("窗口日不应重复", dates.size, dates.distinct().size)
        assertEquals("窗口日必须升序", dates, dates.sorted())
    }
}
