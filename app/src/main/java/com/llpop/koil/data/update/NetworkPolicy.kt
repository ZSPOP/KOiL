package com.llpop.koil.data.update

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/**
 * 蜂窝 + 还没表态过 → 下载前先问一次。
 * 只问一次：每次都问会让稳定流程多一步，而明确表态过的人不该被反复打扰。
 */
internal fun needsCellularPrompt(isCellular: Boolean, decided: Boolean): Boolean =
    isCellular && !decided

/**
 * 蜂窝 + 已表态「仅 WiFi」→ 挡住，给一次强制下载入口。
 * 不静默挡住：用户明确想下却什么都不发生，和 ② 那个「点第一次没反应」是同一种体验缺陷。
 */
internal fun blockedByWifiOnly(isCellular: Boolean, decided: Boolean, wifiOnly: Boolean): Boolean =
    isCellular && decided && wifiOnly

/**
 * 当前活动网络是不是蜂窝。
 * minSdk 33，`getNetworkCapabilities` 一条 API 够；WiFi / 以太网 / VPN 都返回 false
 * （VPN 下不拦：用户既然开着 VPN，流量路径已经不是他关心的那件事）。
 */
fun isCellularNetwork(context: Context): Boolean {
    val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
    val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
    return caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
}
