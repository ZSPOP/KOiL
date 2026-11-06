package com.llpop.koil

import com.llpop.koil.data.update.compareVersionNames
import com.llpop.koil.data.update.normalizeVersion
import com.llpop.koil.data.update.releasePageUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 版本号比较与 tag 归一化（v1.6.5 从设置页私有函数提到 data.update 包）。
 *
 * 这套逻辑决定"要不要提示更新"和"要不要挂红点"：判错的后果是用户永远看不到新版本，
 * 或每次都被提示同一个版本。所以把边界固定成断言。
 */
class UpdateVersionTest {

    @Test
    fun `normalizeVersion 去掉 V 前缀与空白`() {
        assertEquals("1.6.4", normalizeVersion("V1.6.4"))
        assertEquals("1.6.4", normalizeVersion("v1.6.4"))
        assertEquals("1.6.4", normalizeVersion("  V1.6.4  "))
        // 没有前缀时原样返回（不能把 "16" 开头的数字吃掉）
        assertEquals("1.6.4", normalizeVersion("1.6.4"))
        assertEquals("", normalizeVersion("V"))
    }

    @Test
    fun `compareVersionNames 按数字段比较而非字符串`() {
        // 字符串比较会把 "1.6.10" 判成小于 "1.6.9"
        assertTrue(compareVersionNames("1.6.10", "1.6.9") > 0)
        assertTrue(compareVersionNames("1.7.0", "1.6.99") > 0)
        assertTrue(compareVersionNames("2.0", "1.99.99") > 0)
        assertTrue(compareVersionNames("1.6.3", "1.6.4") < 0)
        assertEquals(0, compareVersionNames("1.6.4", "1.6.4"))
    }

    @Test
    fun `compareVersionNames 段数不等时缺位补零`() {
        assertEquals(0, compareVersionNames("1.6", "1.6.0"))
        assertTrue(compareVersionNames("1.6.1", "1.6") > 0)
        assertTrue(compareVersionNames("1.6", "1.6.1") < 0)
    }

    @Test
    fun `compareVersionNames 忽略非数字后缀`() {
        // "-beta" 这类后缀不参与比较（GitHub 上偶尔会出现预发布标签）
        assertEquals(0, compareVersionNames("1.6.4-beta", "1.6.4"))
        assertTrue(compareVersionNames("1.6.5-beta", "1.6.4") > 0)
    }

    @Test
    fun `releasePageUrl 拼出 tag 页地址`() {
        assertEquals(
            "https://github.com/ZSPOP/KOiL/releases/tag/V1.6.4",
            releasePageUrl("V1.6.4"),
        )
    }
}
