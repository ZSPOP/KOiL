package com.llpop.koil.ui.component

import com.llpop.koil.data.update.blockedByWifiOnly
import com.llpop.koil.data.update.needsCellularPrompt

/** 蜂窝下载的两个门控形态：提示确认 / 已被「仅 WiFi」拦下 */
enum class CellularGate { Ask, Blocked }

/**
 * 下载前的网络门控判定（纯函数，无副作用）。
 *
 * 首页更新横幅与设置页更新卡共用这一份：两处各写一遍的话，
 * 将来只改一处就会分叉成「横幅弹确认、设置页直接下」这种最难排查的不一致。
 *
 * @return null 表示可以立即下载；非 null 表示要先弹对应的弹窗
 */
internal fun evaluateCellularGate(
    cellular: Boolean,
    decided: Boolean,
    wifiOnly: Boolean,
): CellularGate? = when {
    needsCellularPrompt(cellular, decided) -> CellularGate.Ask
    blockedByWifiOnly(cellular, decided, wifiOnly) -> CellularGate.Blocked
    else -> null
}

/**
 * 缺安装授权时，这一下是去授权 / 重试安装，不是下载：
 * 先弹「蜂窝下要下 6.7MB 吗」等于问一个不会发生的事。
 *
 * 只按 failure 短路不够 —— 包被系统清掉后 failure 仍停在 NO_PERMISSION，
 * 那样就会绕开蜂窝确认直接下几 MB，必须同时确认盘上真有包。
 */
internal fun skipNetworkGateForGrant(
    failedNoPermission: Boolean,
    apkReady: Boolean,
): Boolean = failedNoPermission && apkReady
