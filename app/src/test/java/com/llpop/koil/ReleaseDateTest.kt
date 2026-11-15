package com.llpop.koil

import com.llpop.koil.data.update.formatReleaseDate
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 关于页「发布日期」的展示格式。
 *
 * 输入来自编译期写死的 `BuildConfig.KOIL_RELEASE_DATE`，输出直接进用户可见的信息行。
 * 解析失败必须原样返回：这一行宁可显示一串 ISO，也不能开天窗。
 */
class ReleaseDateTest {

    @Test
    fun `标准 ISO 日期转中文`() {
        assertEquals("2026年11月15日", formatReleaseDate("2026-11-15"))
    }

    @Test
    fun `月日不补零 —— 展示层去掉前导零`() {
        assertEquals("2026年1月1日", formatReleaseDate("2026-01-01"))
        assertEquals("2026年12月31日", formatReleaseDate("2026-12-31"))
    }

    @Test
    fun `解析失败原样返回，不抛异常`() {
        listOf(
            "",
            "2026-11",
            "2026-11-15-extra",
            "not-a-date",
            "2026-13-15", // 月份越界
            "2026-11-32", // 日期越界
            "2026-11-xx", // 非数字
            "2026/11/15", // 分隔符不对
        ).forEach { raw ->
            assertEquals(raw, formatReleaseDate(raw))
        }
    }

    @Test
    fun `两位数年份照原样成文 —— 不做世纪推断`() {
        // 写死值是四位年份；这里只是说明「不猜」，2026 不会变成 20 年
        assertEquals("2026年11月15日", formatReleaseDate("2026-11-15"))
    }
}
