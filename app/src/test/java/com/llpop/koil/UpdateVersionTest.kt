package com.llpop.koil

import com.llpop.koil.data.update.compareVersionNames
import com.llpop.koil.data.update.isNewerRelease
import com.llpop.koil.data.update.normalizeVersion
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
    fun `compareVersionNames 两侧都接受 V 前缀`() {
        // versionName 一旦改成带 V（与 GitHub tag 同形），不 normalize 的一侧首段会被静默丢弃，
        // 结果从「1.1.4 更新」翻转成「不更新」，且没有任何报错
        assertEquals(1, compareVersionNames("V1.1.4", "V1.1.3"))
        // 任一侧带/不带 V 都按数字段比，方向不能翻转
        assertEquals(1, compareVersionNames("1.1.4", "V1.1.3"))
        assertEquals(-1, compareVersionNames("V1.1.2", "1.1.3"))
        assertEquals(0, compareVersionNames("V1.1.3", "1.1.3"))
        // 只规范化一侧的情形（isNewerRelease 的实际调用形态）
        assertTrue(isNewerRelease("V1.1.4", "V1.1.3"))
        assertFalse(isNewerRelease("V1.1.3", "V1.1.3"))
    }

    @Test
    fun `compareVersionNames 忽略非数字后缀`() {
        // "-beta" 这类后缀不参与比较（GitHub 上偶尔会出现预发布标签）
        assertEquals(0, compareVersionNames("2.0.0-beta", "2.0.0"))
        assertTrue(compareVersionNames("2.0.1-beta", "2.0.0") > 0)
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
    fun `shouldBadgeUpdate 只看有没有新版本，不受横幅被收起影响`() {
        val current = "1.1.3"

        assertFalse(shouldBadgeUpdate("", current))
        assertFalse(shouldBadgeUpdate("V1.1.2", current))
        assertFalse(shouldBadgeUpdate("V1.1.3", current))

        // 有新版本就亮 —— 与任何「已读/已收起」状态无关
        assertTrue(shouldBadgeUpdate("V1.1.4", current))

        // 装完最新版 : 不再比本机新，红点自然灭
        assertFalse(shouldBadgeUpdate("V1.1.4", "1.1.4"))
    }
}
