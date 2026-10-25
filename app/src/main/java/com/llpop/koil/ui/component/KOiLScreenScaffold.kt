package com.llpop.koil.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
 */
@Composable
fun KOiLScreenScaffold(
    title: String,
    modifier: Modifier = Modifier,
    navigationIcon: (@Composable () -> Unit)? = null,
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
                    navigationIcon = navigationIcon ?: {},
                    color = barColor,
                    scrollBehavior = scrollBehavior,
                )
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier),
        ) {
            content(
                innerPadding,
                innerPadding.calculateTopPadding(),
                scrollBehavior,
                backdrop,
            )
        }
    }
}
