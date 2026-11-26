package com.llpop.koil.ui.component

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp

/**
 * 顶栏中央可用的起始边界：宽屏侧边栏收起时，胶囊横在顶栏中央，
 * 顶栏据此把中央标题/按钮让开；`Dp.Unspecified` 表示不限制。
 *
 * 对应 官方参考实现 `LocalTopAppBarCenterObstructionStart`，值来源于
 * `GlassNavigationRailState.collapsedStart`。
 */
val LocalTopAppBarCenterObstructionStart = compositionLocalOf<Dp> { Dp.Unspecified }
