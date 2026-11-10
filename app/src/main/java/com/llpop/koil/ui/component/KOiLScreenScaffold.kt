package com.llpop.koil.ui.component

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.Dp
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 页面级 Scaffold —— 完全对齐 Mishka 各 Screen 的顶栏模式（HomeScreen / SettingsScreen）：
 * - 页内 [Scaffold]（大标题 [TopAppBar]，随滚动折叠），外层 MainScreen Scaffold 只提供底栏
 * - 每页独立 backdrop（等价 Mishka rememberBlurBackdrop：drawRect(surface) + drawContent()）
 * - backdrop 挂在 content 槽位的全屏 [Box] 上（与 Mishka 挂 LazyColumn 同位置）：
 *   topBar 是兄弟节点不在录制子树，避免 textureBlur 采样到自己导致递归
 * - topBar = [BlurredBar] { [TopAppBar] }，blur 可用时栏色透明、否则 surface
 *
 * content 回调给出：
 * - topPadding：TopAppBar 当前高度（含状态栏，折叠时变化），内容开头应放等高 Spacer 避让
 * - scrollBehavior：内容滚动容器需 .nestedScroll(scrollBehavior.nestedScrollConnection) 联动折叠
 *
 * overlay 插槽：可选覆盖层，渲染在 backdrop 录制树**之外**（与 content 容器同层兄弟），
 * 因此可安全地 textureBlur(backdrop) 而不自采样递归——用于"吸顶标题"等需要顶栏同款毛玻璃、
 * 却又位于列表（backdrop 子树）内的元素。不传则无覆盖层（各页行为不变）。
 */
@Suppress("SENSELESS_COMPARISON") // backdrop 由 rememberLayerBackdrop 构造恒非空，保留可空签名给 content 调用方
@Composable
fun KOiLScreenScaffold(
    title: String,
    modifier: Modifier = Modifier,
    // 可选副标题，走 TopAppBar 原生 subtitle 槽位——
    // 展开态显示在大标题下方，折叠后自动居中到小标题下方，无需额外占一行。
    subtitle: String = "",
    navigationIcon: (@Composable () -> Unit)? = null,
    overlay: @Composable (backdrop: LayerBackdrop?, innerPadding: PaddingValues, topPadding: Dp) -> Unit = { _, _, _ -> },
    content: @Composable (
        innerPadding: PaddingValues,
        topPadding: Dp,
        scrollBehavior: ScrollBehavior,
        backdrop: LayerBackdrop?,
    ) -> Unit,
) {
    val scrollBehavior = MiuixScrollBehavior()
    val surfaceColor = MiuixTheme.colorScheme.surface
    val backdrop = rememberLayerBackdrop {
        drawRect(surfaceColor)
        drawContent()
    }
    val blurActive = backdrop != null
    val barColor = if (blurActive) Color.Transparent else surfaceColor

    Scaffold(
        modifier = modifier,
        topBar = {
            BlurredBar(backdrop = backdrop, blurActive = blurActive) {
                TopAppBar(
                    title = title,
                    subtitle = subtitle,
                    navigationIcon = navigationIcon ?: {},
                    color = barColor,
                    scrollBehavior = scrollBehavior,
                )
            }
        },
    ) { innerPadding ->
        val focusManager = LocalFocusManager.current
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier)
                    // 点击输入框外任意位置取消选中/失焦（子输入框消费点击，不误触；录入卡/总览/设置统一生效）
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = { focusManager.clearFocus() })
                    },
            ) {
                content(
                    innerPadding,
                    innerPadding.calculateTopPadding(),
                    scrollBehavior,
                    backdrop,
                )
            }
            // 覆盖层（backdrop 录制树外，可安全 textureBlur(backdrop) 而不自采样递归）
            overlay(backdrop, innerPadding, innerPadding.calculateTopPadding())
        }
    }
}
