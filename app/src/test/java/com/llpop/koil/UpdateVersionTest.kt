package com.llpop.koil

import com.llpop.koil.data.update.compareVersionNames
import com.llpop.koil.data.update.isNewerRelease
import com.llpop.koil.data.update.normalizeVersion
import com.llpop.koil.data.update.releasePageUrl
import com.llpop.koil.data.update.shouldBadgeUpdate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Test
    fun `isNewerRelease 只看版本不看是否已读`() {
        // 这是红点、横幅、行内文字共用的唯一判据，不能掺入"已读"状态
        assertFalse(isNewerRelease("", "1.1.3"))
        assertFalse(isNewerRelease("V1.1.3", "1.1.3"))
        assertFalse(isNewerRelease("V1.1.2", "1.1.3"))
        assertTrue(isNewerRelease("V1.1.4", "1.1.3"))
    }

    @Test
    fun `shouldBadgeUpdate 只在有新版本且未知晓时成立`() {
        val current = "1.1.3"

        // 从未查到过 : 不亮
        assertFalse(shouldBadgeUpdate("", "", current))
        // 查到的 tag 不比本机新 : 不亮（防止把同版本或更低版本标成更新）
        assertFalse(shouldBadgeUpdate("V1.1.2", "", current))
        assertFalse(shouldBadgeUpdate("V1.1.3", "", current))

        // 有更新且未知晓 : 亮
        assertTrue(shouldBadgeUpdate("V1.1.4", "", current))
        assertTrue(shouldBadgeUpdate("V1.1.4", "V1.1.3", current))

        // 有更新但已知晓 : 不亮（点过之后同一版本不再重复提醒）
        assertFalse(shouldBadgeUpdate("V1.1.4", "V1.1.4", current))

        // 发了更新的版本 : seenTag 还停在旧版本也要重新亮
        assertTrue(shouldBadgeUpdate("V1.1.5", "V1.1.4", current))
    }
}
