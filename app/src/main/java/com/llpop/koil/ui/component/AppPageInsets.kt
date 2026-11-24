package com.llpop.koil.ui.component

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.captionBar
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/**
 * 从 miuix v0.9.5-beta03 `example/shared/src/commonMain/kotlin/utils/PageUtils.kt` 整段移植。
 *
 * 页面的整窗安全区与页面窗格内剩余的间距。侧栏（rail）如果已经预留了起始空间，
 * 它就不重复计算；以浮层形式存在的侧栏会算进 [contentInsets]。背景仍然保持 edge-to-edge。
 * 带动画的 provider 由 layout 阶段读取，不在发布这个契约时读取。
 */
@Stable
class AppPageInsets(
    val windowInsets: WindowInsets,
    val safeInsets: WindowInsets,
    private val overlayStart: () -> Dp,
    val searchTop: () -> Dp,
) {
    val contentInsets: WindowInsets = object : WindowInsets {
        override fun getLeft(density: Density, layoutDirection: LayoutDirection): Int =
            safeInsets.getLeft(density, layoutDirection) +
                if (layoutDirection == LayoutDirection.Ltr) with(density) { overlayStart().roundToPx() } else 0

        override fun getRight(density: Density, layoutDirection: LayoutDirection): Int =
            safeInsets.getRight(density, layoutDirection) +
                if (layoutDirection == LayoutDirection.Rtl) with(density) { overlayStart().roundToPx() } else 0

        override fun getTop(density: Density): Int = safeInsets.getTop(density)

        override fun getBottom(density: Density): Int = safeInsets.getBottom(density)
    }

    /** Keeps the last in-view clearance while [hidden], instead of following the overlay. */
    fun whenHidden(hidden: () -> Boolean): AppPageInsets = AppPageInsets(
        windowInsets = windowInsets,
        safeInsets = safeInsets,
        overlayStart = overlayStart.frozenWhile(hidden),
        searchTop = searchTop.frozenWhile(hidden),
    )
}

/** Reads [this] while [frozen] is false; while it is true, returns the last value read instead. */
private fun (() -> Dp).frozenWhile(frozen: () -> Boolean): () -> Dp {
    val live = this
    var last = Dp.Unspecified
    return {
        val held = last
        if (frozen() && held != Dp.Unspecified) held else live().also { last = it }
    }
}

val LocalAppPageInsets = staticCompositionLocalOf {
    AppPageInsets(WindowInsets(0), WindowInsets(0), { 0.dp }, { 0.dp })
}

/** Only the app host combines platform safe areas, external host padding and navigation chrome. */
@Composable
fun rememberAppPageInsets(
    hostPadding: PaddingValues,
    startAlreadyReserved: Boolean,
    overlayStart: () -> Dp,
    searchTop: () -> Dp,
): AppPageInsets {
    val systemInsets = WindowInsets.systemBars.union(WindowInsets.captionBar).union(WindowInsets.displayCutout)
    return remember(systemInsets, hostPadding, startAlreadyReserved, overlayStart, searchTop) {
        val hostInsets = object : WindowInsets {
            override fun getLeft(density: Density, layoutDirection: LayoutDirection): Int = with(density) {
                hostPadding.calculateLeftPadding(layoutDirection).roundToPx()
            }

            override fun getRight(density: Density, layoutDirection: LayoutDirection): Int = with(density) {
                hostPadding.calculateRightPadding(layoutDirection).roundToPx()
            }

            override fun getTop(density: Density): Int = with(density) { hostPadding.calculateTopPadding().roundToPx() }

            override fun getBottom(density: Density): Int = with(density) { hostPadding.calculateBottomPadding().roundToPx() }
        }
        val windowInsets = systemInsets.union(hostInsets)
        val safeInsets = if (startAlreadyReserved) {
            windowInsets.only(WindowInsetsSides.End + WindowInsetsSides.Vertical)
        } else {
            windowInsets
        }
        AppPageInsets(windowInsets, safeInsets, overlayStart, searchTop)
    }
}

/** Apply and consume horizontal page space without moving its backdrop or vertical content. */
@Composable
fun Modifier.appPageHorizontalInsets(): Modifier = windowInsetsPadding(
    LocalAppPageInsets.current.contentInsets.only(WindowInsetsSides.Horizontal),
)

/** Top bars own the remaining top safe area; internal inset modifiers see it consumed. */
@Composable
fun Modifier.appPageTopBarInsets(): Modifier = appPageHorizontalInsets().windowInsetsPadding(
    LocalAppPageInsets.current.safeInsets.only(WindowInsetsSides.Top),
)

/** System/host safe areas only, independent of the sidebar and collapsed search capsule. */
@Composable
fun rememberAppSafePadding(): PaddingValues = LocalAppPageInsets.current.safeInsets.asPaddingValues()

/**
 * 列表底部的留白：只取外层槽位与整窗安全区底边的较大值（横向/顶部一律 0）。
 *
 * 与 [pageContentPadding] 的区别：那个是"四边取 max"给整页骨架用；这个专门给
 * 需要自己拼 contentPadding 的场景（列表底只想留底栏槽高、不想把顶部安全区也带进来）。
 * 原样对齐官方 `utils/PageUtils.kt` 的 `pageBottomPadding`。
 */
@Composable
fun pageBottomPadding(
    outerPadding: PaddingValues = PaddingValues(0.dp),
    extraBottom: Dp = 0.dp,
): PaddingValues {
    val safePadding = rememberAppSafePadding()
    return remember(outerPadding, safePadding, extraBottom) {
        object : PaddingValues {
            override fun calculateLeftPadding(layoutDirection: LayoutDirection): Dp = 0.dp

            override fun calculateRightPadding(layoutDirection: LayoutDirection): Dp = 0.dp

            override fun calculateTopPadding(): Dp = 0.dp

            override fun calculateBottomPadding(): Dp = maxOf(
                outerPadding.calculateBottomPadding(),
                safePadding.calculateBottomPadding(),
            ) + extraBottom
        }
    }
}

/**
 * 滚动容器的标准修饰符组合，原样对齐官方 `utils/PageUtils.kt` 的 `Modifier.pageScrollModifiers`：
 * 滚动到底的触感反馈、overscroll 回弹偏移回写给顶栏、与顶栏折叠联动，最后铺满高度。
 *
 * 三页与两个二级页以前是手写同一条链（`.scrollEndHaptic().overScrollVertical(...).nestedScroll(...)`），
 * 现在统一走这里，任何一页想关掉顶栏折叠（[showTopAppBar] = false）也不会漏掉另一段接线。
 */
@Composable
fun Modifier.pageScrollModifiers(
    enableScrollEndHaptic: Boolean,
    showTopAppBar: Boolean,
    topAppBarScrollBehavior: ScrollBehavior,
): Modifier {
    val state = topAppBarScrollBehavior.state
    val onOffsetChanged: (Float) -> Unit = remember(state) {
        { state.overscrollOffset = it }
    }
    return this
        .then(if (enableScrollEndHaptic) Modifier.scrollEndHaptic() else Modifier)
        .overScrollVertical(onOffsetChanged = if (showTopAppBar) onOffsetChanged else null)
        .then(if (showTopAppBar) Modifier.nestedScroll(topAppBarScrollBehavior.nestedScrollConnection) else Modifier)
        .fillMaxHeight()
}
