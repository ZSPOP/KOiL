package com.llpop.koil.util

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

/** 宽屏阈值：达到即固定小标题（大标题只在手机上展开） */
private val WideScreenMinWidth = 600.dp

/**
 * 根部容器尺寸（px）。与官方 `App.kt` 的注入一一对应：根部
 * `BoxWithConstraints(Modifier.fillMaxSize())` 取 `constraints.maxWidth/maxHeight` 注入，
 * 宽屏与视口判断优先读它。
 *
 * 不直接用 `LocalWindowInfo.containerSize` 的原因：那是**窗口**尺寸，
 * 分屏/多窗口、以及宿主给 ComposeView 垫了外部 padding 时与实际容器不是一回事，
 * 宽屏阈值会在这类场景误判。
 */
val LocalContainerSize = staticCompositionLocalOf<IntSize?> { null }

/**
 * 在页面根注入 [LocalContainerSize]，同时承担原根容器的职责（背景等修饰由 [modifier] 传入）。
 * 官方是把根部 Box 直接换成 `BoxWithConstraints`，这里收成一个 wrapper，调用点括号层级不变。
 */
@Composable
fun ContainerSizeProvider(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    BoxWithConstraints(modifier = modifier) {
        val containerSize = IntSize(constraints.maxWidth, constraints.maxHeight)
        CompositionLocalProvider(LocalContainerSize provides containerSize, content = content)
    }
}

/** 当前容器是否宽屏（折叠屏展开 / 平板 / 横屏大窗） */
@Composable
fun rememberIsWideScreen(): Boolean {
    // 根部未注入时回退窗口尺寸，保证独立预览/测试环境也能算
    val containerSize = LocalContainerSize.current ?: LocalWindowInfo.current.containerSize
    val density = LocalDensity.current
    return with(density) { containerSize.width.toDp() >= WideScreenMinWidth }
}
