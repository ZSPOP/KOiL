package com.llpop.koil.util

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp

/** 宽屏阈值：达到即固定小标题（大标题只在手机上展开） */
private val WideScreenMinWidth = 600.dp

/** 当前容器是否宽屏（折叠屏展开 / 平板 / 横屏大窗） */
@Composable
fun rememberIsWideScreen(): Boolean {
    val containerSize = LocalWindowInfo.current.containerSize
    val density = LocalDensity.current
    return with(density) { containerSize.width.toDp() >= WideScreenMinWidth }
}
