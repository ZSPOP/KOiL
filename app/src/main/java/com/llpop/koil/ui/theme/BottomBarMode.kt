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
 * 底栏显示模式。
 */
enum class BottomBarMode(val storageValue: String) {
    /** 图标 + 文字 */
    IconAndText("icon_and_text"),

    /** 仅图标 */
    IconOnly("icon_only");

    companion object {
        fun fromStorage(value: String): BottomBarMode =
            entries.firstOrNull { it.storageValue == value } ?: IconAndText
    }
}
