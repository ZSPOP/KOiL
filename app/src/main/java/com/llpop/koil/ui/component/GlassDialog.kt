package com.llpop.koil.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.glass.GlassDialog
import top.yukonga.miuix.kmp.layout.DialogDefaults
import com.llpop.koil.ui.component.effect.rememberEffectiveGlassSpec
import com.llpop.koil.ui.component.effect.rememberKOiLGlassEdgeStrokeStatic
import com.llpop.koil.ui.theme.LocalKOiLDarkTheme

/**
 * KOiL 玻璃弹窗：直接委托官方 [GlassDialog]。
 *
 * 由 Scaffold 的弹层宿主托管（同窗口内渲染），backdrop 取页面 backdrop，
 * 因此能采到页面内容做真模糊、支持预测性返回与点外部关闭；不再自建系统 Dialog 窗口。
 *
 * 参数与历史调用点保持同一签名：contentPadding → insideMargin、maxHeight + scrollable
 * 由内容列自行消费，widthFraction 换算成 maxWidth。
 */
@Composable
fun KOiLGlassDialog(
    show: Boolean,
    onDismissRequest: () -> Unit,
    title: String? = null,
    summary: String? = null,
    contentPadding: Dp = 20.dp,
    /**
     * 面板内边距直接按四边给，优先于 [contentPadding]。
     * 横向给 0 时内容列贴到面板边，行节点的按下高亮就能横向铺满，四个角由面板自己的
     * squircle 裁切；纵向仍留出上下白。弹窗类面板按这个口径传 [DpSize]。
     */
    insideMargin: DpSize? = null,
    scrollable: Boolean = false,
    maxHeight: Dp? = null,
    widthFraction: Float? = null,
    content: @Composable () -> Unit,
) {
    // 用当前容器宽判宽，而不是 Configuration.screenWidthDp：后者在分屏/折叠屏/宽屏右栏里
    // 报的是整窗宽，会把弹窗按比实际可用空间更大的基准放大。rail 的双栏判定用的是同一口径。
    val containerWidthDp = with(LocalDensity.current) {
        LocalWindowInfo.current.containerSize.width.toDp()
    }
    val maxWidth = if (widthFraction != null) {
        containerWidthDp * widthFraction
    } else {
        DialogDefaults.MaxWidth
    }
    // 同 GlassSheet：弹窗内容在根弹层宿主里组合，backdrop 必须在此捕获后重新下发。
    // 采样源 = 底色层 + 页面层 50%：宿主页是卡片堆叠时，整页 1.0 会把每张卡的圆角轮廓
    // 透出来；压到一半后结构退成柔和层次，底色的连续感保留。
    val backdrop = rememberOverlayBackdrop(DialogContentAlpha)
    GlassDialog(
        onDismissRequest = onDismissRequest,
        show = show,
        backdrop = backdrop,
        title = title,
        summary = summary,
        maxWidth = maxWidth,
        // 材质统一从 KOiLGlassOverlayMaterials 取，与底部弹层同一档；
        // 描边同样取全站基准（静态版，避免弹窗内容随设备倾斜重组）
        material = KOiLGlassOverlayMaterials.dialog(),
        stroke = rememberKOiLGlassEdgeStrokeStatic(
            isDark = LocalKOiLDarkTheme.current,
            spec = rememberEffectiveGlassSpec(),
        ),
        insideMargin = insideMargin ?: DpSize(contentPadding, contentPadding),
    ) {
        // 弹层自身就是玻璃面：同时广播 LocalOnGlassSurface，让内部的提示条/分割线
        // 按「采样底」而不是「实色 surfaceContainer」挑颜色（否则 10% tint 的语义块
        // 会变成贴在玻璃上的一块色片）。
        CompositionLocalProvider(
            LocalPageBackdrop provides backdrop,
            LocalOverlayBackdropApplied provides (backdrop != null),
            LocalOnGlassSurface provides (backdrop != null),
        ) {
        Column(
            // 内容列吃满弹窗可用宽度：官方 demo 的弹窗内容里总有 fillMaxWidth 的元素，
            // 少了它按钮 Row 的 weight 会算不出整宽，按下高亮只能画出被内容裁掉的一截。
            modifier = Modifier
                .fillMaxWidth()
                .then(if (maxHeight != null) Modifier.heightIn(max = maxHeight) else Modifier)
                .then(if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier),
        ) {
            content()
        }
        }
    }
}
