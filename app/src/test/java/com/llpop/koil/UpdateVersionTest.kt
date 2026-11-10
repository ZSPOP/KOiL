package com.llpop.koil

import com.llpop.koil.data.update.compareVersionNames
import com.llpop.koil.data.update.normalizeVersion
import com.llpop.koil.data.update.releasePageUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 版本号比较与 tag 归一化（供设置页与自动更新检查共用）。
 *
 * 这套逻辑决定"要不要提示更新"和"要不要挂红点"：判错的后果是用户永远看不到新版本，
 * 或每次都被提示同一个版本。所以把边界固定成断言。
 */
class UpdateVersionTest {

    @Test
    fun `normalizeVersion 去掉 V 前缀与空白`() {
        assertEquals("2.0.0", normalizeVersion("V2.0.0"))
        assertEquals("2.0.0", normalizeVersion("v2.0.0"))
        assertEquals("2.0.0", normalizeVersion("  V2.0.0  "))
        // 没有前缀时原样返回（不能把 "20" 开头的数字吃掉）
        assertEquals("2.0.0", normalizeVersion("2.0.0"))
        assertEquals("", normalizeVersion("V"))
    }

    @Test
    fun `compareVersionNames 按数字段比较而非字符串`() {
        // 字符串比较会把 "2.0.10" 判成小于 "2.0.9"
        assertTrue(compareVersionNames("2.0.10", "2.0.9") > 0)
        assertTrue(compareVersionNames("3.0.0", "2.0.99") > 0)
        assertTrue(compareVersionNames("3.0", "2.99.99") > 0)
        assertTrue(compareVersionNames("2.0.3", "2.0.4") < 0)
        assertEquals(0, compareVersionNames("2.0.0", "2.0.0"))
    }

    @Test
    fun `compareVersionNames 段数不等时缺位补零`() {
        assertEquals(0, compareVersionNames("2.0", "2.0.0"))
        assertTrue(compareVersionNames("2.0.1", "2.0") > 0)
        assertTrue(compareVersionNames("2.0", "2.0.1") < 0)
    }

    @Test
    fun `compareVersionNames 忽略非数字后缀`() {
        // "-beta" 这类后缀不参与比较（GitHub 上偶尔会出现预发布标签）
        assertEquals(0, compareVersionNames("2.0.0-beta", "2.0.0"))
        assertTrue(compareVersionNames("2.0.1-beta", "2.0.0") > 0)
    }

    @Test
    fun `releasePageUrl 拼出 tag 页地址`() {
        assertEquals(
            "https://github.com/ZSPOP/KOiL/releases/tag/V2.0.0",
            releasePageUrl("V2.0.0"),
        )
    }
}
