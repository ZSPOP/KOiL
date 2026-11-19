package com.llpop.koil.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
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
    visualContentOffset: (() -> Float)? = null,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    bottomContent: @Composable () -> Unit = {},
) {
    val backdrop = LocalTopAppBarBackdrop.current
    val barTitleAlpha = LocalTopAppBarTitleAlpha.current
    val startInset = LocalTopBarStartInset.current
    val surfaceColor = MiuixTheme.colorScheme.surface
    val bandBrush = remember(surfaceColor) { GlassTopAppBarDefaults.bandBrush(surfaceColor) }
    val resolvedContentScrolled by remember(scrollBehavior, isContentScrolled) {
        derivedStateOf {
            isContentScrolled ?: (scrollBehavior?.state?.let { it.contentOffset < 0f } ?: false)
        }
    }
    GlassTopAppBar(
        title = title,
        isContentScrolled = resolvedContentScrolled,
        modifier = modifier,
        contentModifier = contentModifier.topBarStartInset(startInset),
        expandable = expandable,
        subtitle = subtitle,
        progressiveBlur = LocalBlurEnabled.current,
        backdrop = backdrop,
        scrollBehavior = scrollBehavior,
        bandBrush = bandBrush,
        bandOverhang = GlassTopAppBarDefaults.BandOverhang,
        defaultWindowInsetsPadding = defaultWindowInsetsPadding,
        visualContentOffset = visualContentOffset,
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
