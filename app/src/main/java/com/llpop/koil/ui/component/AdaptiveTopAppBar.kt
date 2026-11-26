package com.llpop.koil.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.computedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.offset
import com.llpop.koil.R
import com.llpop.koil.ui.component.blur.LocalBlurEnabled
import com.llpop.koil.ui.component.blur.LocalTopBarExtraTopInset
import com.llpop.koil.ui.component.blur.LocalTopBarStartInset
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.glass.GlassIconButton
import top.yukonga.miuix.kmp.glass.GlassTopAppBar
import top.yukonga.miuix.kmp.glass.GlassTopAppBarDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.glass.ChevronBackward
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.llpop.koil.util.rememberIsWideScreen

/** 起始侧内缩：宽屏/侧栏时把顶栏内容整体右移，不改测量约束的超界判定 */
private fun Modifier.topBarStartInset(startInset: () -> Dp): Modifier = layout { measurable, constraints ->
    val insetPx = startInset().roundToPx().coerceIn(0, constraints.maxWidth)
    if (insetPx == 0) {
        val placeable = measurable.measure(constraints)
        layout(placeable.width, placeable.height) { placeable.placeRelative(0, 0) }
    } else {
        val placeable = measurable.measure(constraints.offset(horizontal = -insetPx))
        layout(placeable.width + insetPx, placeable.height) {
            placeable.placeRelative(insetPx, 0)
        }
    }
}

/** 额外顶部内缩：内容整体下移，测量高度同步增加（官方 topBarExtraTopInset 同构） */
private fun Modifier.topBarExtraTopInset(extraTopInset: () -> Dp): Modifier = layout { measurable, constraints ->
    val extraPx = extraTopInset().roundToPx().coerceAtLeast(0)
    val placeable = measurable.measure(constraints)
    layout(placeable.width, placeable.height + extraPx) {
        placeable.placeRelative(0, extraPx)
    }
}

/**
 * 顶栏的统一入口：把 backdrop / 模糊开关 / 渐变带这几个"库级默认"收在一处，
 * 页面只传业务参数。宽度与折叠规则仍由调用方决定。
 */
@Composable
fun AdaptiveTopAppBar(
    title: String,
    modifier: Modifier = Modifier,
    contentModifier: Modifier = Modifier,
    expandable: Boolean = !rememberIsWideScreen(),
    subtitle: String = "",
    titleColor: Color = MiuixTheme.colorScheme.onSurface,
    titleAlpha: () -> Float = { titleColor.alpha },
    backgroundAlpha: () -> Float = { 1f },
    defaultWindowInsetsPadding: Boolean = true,
    scrollBehavior: ScrollBehavior? = null,
    isContentScrolled: Boolean? = null,
    // 宽屏侧边栏收起时胶囊横在顶栏中央：把这条边界交给 GlassTopAppBar，
    // 中央标题与文字不会被胶囊压住（Mishka 同名参数同义）
    centerObstructionStart: Dp = LocalTopAppBarCenterObstructionStart.current,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    bottomContent: @Composable () -> Unit = {},
) {
    val backdrop = LocalTopAppBarBackdrop.current
    val barTitleAlpha = LocalTopAppBarTitleAlpha.current
    val startInset = LocalTopBarStartInset.current
    val extraTopInset = LocalTopBarExtraTopInset.current
    val surfaceColor = MiuixTheme.colorScheme.surface
    val bandBrush = remember(surfaceColor) { GlassTopAppBarDefaults.bandBrush(surfaceColor) }
    // 口径照官方 utils/PageUtils.kt 的 AdaptiveTopAppBar：
    // contentOffset < -1f 或弹性位移 overscrollOffset < -1f（阈值 1px 而不是 0，
    // 并计入手势下拉产生的 overscroll，否则"刚离开顶点"那一帧顶栏带不跟手）
    val resolvedContentScrolled by remember(scrollBehavior, isContentScrolled) {
        computedStateOf {
            isContentScrolled ?: (scrollBehavior?.state?.let {
                it.contentOffset < -1f || it.overscrollOffset < -1f
            } ?: false)
        }
    }
    // 官方原句（PageUtils.kt 的 AdaptiveTopAppBar）：
    //   val contentInsetModifier = Modifier.appPageTopBarInsets()
    // 顶栏自己消费内容区横向内距与 Top 安全区，Scaffold 不再替它垫。
    val contentInsetModifier = Modifier.appPageTopBarInsets()
    GlassTopAppBar(
        title = title,
        isContentScrolled = resolvedContentScrolled,
        centerObstructionStart = centerObstructionStart,
        modifier = modifier,
        contentModifier = contentModifier
            .topBarStartInset(startInset)
            .topBarExtraTopInset(extraTopInset)
            .then(contentInsetModifier),
        expandable = expandable,
        subtitle = subtitle,
        progressiveBlur = LocalBlurEnabled.current,
        backdrop = backdrop,
        scrollBehavior = scrollBehavior,
        bandBrush = bandBrush,
        bandOverhang = GlassTopAppBarDefaults.BandOverhang,
        defaultWindowInsetsPadding = false,
        titleAlpha = { titleAlpha() * barTitleAlpha() },
        backgroundAlpha = backgroundAlpha,
        navigationIcon = navigationIcon,
        actions = { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { actions() } },
        bottomContent = bottomContent,
    )
}

@Composable
fun BackNavigationIcon(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val layoutDirection = LocalLayoutDirection.current
    val mirrorModifier = Modifier.graphicsLayer {
        scaleX = if (layoutDirection == LayoutDirection.Rtl) -1f else 1f
    }
    GlassIconButton(
        onClick = onClick,
        modifier = modifier,
    ) {
        Icon(
            imageVector = MiuixIcons.Glass.ChevronBackward,
            contentDescription = stringResource(R.string.koil_back),
            tint = MiuixTheme.colorScheme.onSurface,
            modifier = mirrorModifier.size(28.dp),
        )
    }
}
