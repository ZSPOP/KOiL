package com.llpop.koil

import com.llpop.koil.util.AppClock
import com.llpop.koil.util.TimeFormats
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/**
 * 统一时间入口的两个契约：
 * ① [TimeFormats] 的 UTC 文件名可被自己解析回来（同步副本靠文件名排序与展示）；
 * ② [AppClock] 注入后，格式化结果跟着注入的时刻走（否则"可注入"只是摆设）。
 */
class TimeFormatsTest {

    @After
    fun tearDown() = AppClock.setTestClock(null)

    @Test
    fun `UTC 文件名戳可以原样解析回来`() {
        val instant = Instant.parse("2026-11-17T02:03:04.567Z")
        val stamp = TimeFormats.fileStampUtc(instant)

        assertEquals("20261117-020304-567", stamp)
        assertEquals(instant, TimeFormats.parseFileStampUtc(stamp))
    }

    @Test
    fun `解析失败返回 null 而不是抛异常`() {
        assertNull(TimeFormats.parseFileStampUtc("not-a-stamp"))
        assertNull(TimeFormats.parseFileStampUtc(""))
    }

    @Test
    fun `展示时间用本地时区，文件名用 UTC`() {
        AppClock.setTestClock(Clock.fixed(Instant.parse("2026-11-17T02:03:04Z"), ZoneId.of("Asia/Shanghai")))

        // 10:03 本地 == 02:03 UTC
        assertEquals("11-17 10:03", TimeFormats.minute())
        assertEquals("2026-11-17 10:03:04", TimeFormats.second())
        assertEquals("20261117-020304", TimeFormats.fileStampUtc().substring(0, 15))
    }

    @Test
    fun `日期格式固定 US 数字，不跟随系统语言`() {
        AppClock.setTestClock(Clock.fixed(Instant.parse("2026-01-05T00:00:00Z"), ZoneId.of("UTC")))
        // 阿拉伯语/泰语 locale 下若是数字形态跟系统走，这里会变成其它数字体系
        assertEquals("01-05 00:00", TimeFormats.minute())
        assertEquals("2026-01-05 00:00:00", TimeFormats.second())
    }
}
