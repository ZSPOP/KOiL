package com.llpop.koil

import com.llpop.koil.data.update.blockedByWifiOnly
import com.llpop.koil.data.update.needsCellularPrompt
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 蜂窝下载策略的两个判据。
 *
 * 这两个布尔决定「点更新之后会不会弹窗、会不会被挡住」，是两页共用的同一套逻辑：
 * 写错一边，用户会看到「首页能下、设置页点了没反应」这种最难解释的差异。
 */
class UpdateNetworkPolicyTest {

    @Test
    fun `蜂窝且未表态 —— 先问一次`() {
        assertTrue(needsCellularPrompt(isCellular = true, decided = false))
    }

    @Test
    fun `WiFi 下永不问`() {
        assertFalse(needsCellularPrompt(isCellular = false, decided = false))
        assertFalse(needsCellularPrompt(isCellular = false, decided = true))
    }

    @Test
    fun `已表态过就不再重复问`() {
        assertFalse(needsCellularPrompt(isCellular = true, decided = true))
    }

    @Test
    fun `蜂窝且选了仅 WiFi —— 挡住，但要有强制入口`() {
        assertTrue(blockedByWifiOnly(isCellular = true, decided = true, wifiOnly = true))
    }

    @Test
    fun `选了任意网络或已在 WiFi 上 —— 不挡`() {
        assertFalse(blockedByWifiOnly(isCellular = true, decided = true, wifiOnly = false))
        assertFalse(blockedByWifiOnly(isCellular = false, decided = true, wifiOnly = true))
        assertFalse(blockedByWifiOnly(isCellular = false, decided = false, wifiOnly = false))
    }
}
