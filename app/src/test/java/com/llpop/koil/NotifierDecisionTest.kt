package com.llpop.koil.data.oil

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 通知去重的三个判定（纯函数）。
 *
 * 这几条判错的表现都是「只有真机跑一整天才会发现」：要么同一个窗口反复弹，
 * 要么那一次提醒永远不弹。放在 JVM 单测里把组合跑满，不依赖通知通道与 DataStore。
 */
class NotifierDecisionTest {

    // ---- 调价生效通知 ----

    @Test
    fun `首次启用只锚定，不发通知`() {
        // lastNotified 为空 = 从没通知过，第一轮只记下当前生效日
        assertFalse(shouldNotifyPriceChange(lastNotified = "", newestDate = "2026-11-17", priceChanged = true))
    }

    @Test
    fun `生效日没变不重复通知`() {
        assertFalse(
            shouldNotifyPriceChange(
                lastNotified = "2026-11-17",
                newestDate = "2026-11-17",
                priceChanged = true,
            )
        )
    }

    @Test
    fun `生效日变了且价格真变了才通知`() {
        assertTrue(
            shouldNotifyPriceChange(
                lastNotified = "2026-11-10",
                newestDate = "2026-11-17",
                priceChanged = true,
            )
        )
    }

    @Test
    fun `搁浅窗口（维持价同日入库）不误发`() {
        // 窗口日到了但价格没变：生效日刷新了，价格还是 8.00，不能发「调价至 ¥8.00」
        assertFalse(
            shouldNotifyPriceChange(
                lastNotified = "2026-11-10",
                newestDate = "2026-11-17",
                priceChanged = false,
            )
        )
    }

    @Test
    fun `首次启用即使价格变了也不发`() {
        // 回归：曾把「首次」和「有新调价」写成两个独立判断，装完就弹一条"油价调整"
        assertFalse(shouldNotifyPriceChange("", "2026-11-17", priceChanged = true))
    }

    // ---- 窗口日前一天 ----

    @Test
    fun `下一个窗口正好是明天才提醒`() {
        assertTrue(shouldRemindWindowTomorrow("2026-11-18", "2026-11-18", reminded = ""))
    }

    @Test
    fun `窗口在更远的将来不提前提醒`() {
        assertFalse(shouldRemindWindowTomorrow("2026-11-25", "2026-11-18", reminded = ""))
    }

    @Test
    fun `同一个窗口只提醒一次`() {
        assertFalse(shouldRemindWindowTomorrow("2026-11-18", "2026-11-18", reminded = "2026-11-18"))
    }

    @Test
    fun `没有下一个窗口时不提醒`() {
        assertFalse(shouldRemindWindowTomorrow("", "2026-11-18", reminded = ""))
    }

    @Test
    fun `过期窗口不会被当成明天`() {
        // 日历里只剩已过去的日期时，firstOrNull { it >= today } 可能给出今天以后的旧值，
        // 但绝不该等于明天以外的日子
        assertFalse(shouldRemindWindowTomorrow("2026-11-10", "2026-11-18", reminded = ""))
    }

    // ---- 窗口日当天 ----

    @Test
    fun `今天是窗口日且没提醒过才提醒`() {
        assertTrue(shouldRemindWindowToday(listOf("2026-11-17", "2026-12-01"), "2026-11-17", ""))
    }

    @Test
    fun `当天已经提醒过不重复`() {
        assertFalse(shouldRemindWindowToday(listOf("2026-11-17"), "2026-11-17", "2026-11-17"))
    }

    @Test
    fun `今天不是窗口日不提醒`() {
        assertFalse(shouldRemindWindowToday(listOf("2026-11-18"), "2026-11-17", ""))
    }

    @Test
    fun `日历为空不提醒`() {
        assertFalse(shouldRemindWindowToday(emptyList(), "2026-11-17", ""))
    }
}
