package com.llpop.koil.ui.navigation

import top.yukonga.miuix.kmp.nav.core.NavKey

/**
 * 应用路由。Main 常驻栈底（三 tab 在它内部由 Pager 承载），
 * 二级页以 entry 压栈，转场与交互式侧滑返回交给导航层。
 */
sealed interface Route : NavKey {
    data object Main : Route
    data object OilPriceDetail : Route
    data object About : Route
}
