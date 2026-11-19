package com.llpop.koil.ui.theme

/**
 * 悬浮导航条样式。
 */
enum class FloatingBottomBarStyle(val storageValue: String) {
    /** iOS 液态玻璃悬浮导航条（IosLiquidGlassNavigationBar） */
    IosLike("ios_like"),

    /** Miuix 胶囊悬浮导航条（FloatingNavigationBar） */
    Miuix("miuix");

    companion object {
        fun fromStorage(value: String): FloatingBottomBarStyle =
            entries.firstOrNull { it.storageValue == value } ?: Miuix
    }
}

/**
 * 页面切换手势。取值与 miuix PagerInterceptionMode 一一对应：
 * - CrossAxis → PagerInterceptionMode.CrossAxis（跨轴拦截，横向翻页前先判主轴）
 * - Native → PagerInterceptionMode.Native（原生 Pager 手势）
 */
enum class PagerGestureMode(val storageValue: String) {
    CrossAxis("cross_axis"),
    Native("native");

    companion object {
        fun fromStorage(value: String): PagerGestureMode =
            entries.firstOrNull { it.storageValue == value } ?: CrossAxis
    }
}
