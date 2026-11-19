package com.llpop.koil.ui.component

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.Dp
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.blur.LayerBackdrop

/**
 * 页面级 Scaffold —— 统一各 Screen 的顶栏模式（HomeScreen / SettingsScreen）：
 * - 页内 [Scaffold]，顶栏由 [BlurredBar] + [AdaptiveTopAppBar] 组成：底材负责纹理模糊，
 *   标题的渐进模糊与按钮材质由组件内部按模糊开关与滚动位置决定
 * - 每页独立 backdrop（rememberBlurBackdrop()），挂在列表修饰符上，交给顶栏做底色采样
 * - backdrop 挂在 content 槽位的全屏 [Box] 上（与 LazyColumn 同位置）：
 *   topBar 是兄弟节点不在录制子树，避免采样到自己导致递归
 *
 * content 回调给出：
 * - topPadding：TopAppBar 当前高度（含状态栏，折叠时变化），内容开头应放等高 Spacer 避让
 * - scrollBehavior：内容滚动容器需 .nestedScroll(scrollBehavior.nestedScrollConnection) 联动折叠
 *
 * overlay 插槽：可选覆盖层，渲染在 backdrop 录制树**之外**（与 content 容器同层兄弟），
 * 因此可安全地采样 backdrop 而不自采样递归——用于需要顶栏同款玻璃、
 * 却又位于列表（backdrop 子树）内的元素。不传则无覆盖层（各页行为不变）。
 */
@Suppress("SENSELESS_COMPARISON") // backdrop 由 rememberLayerBackdrop 构造恒非空，保留可空签名给 content 调用方
@Composable
fun KOiLScreenScaffold(
    title: String,
    modifier: Modifier = Modifier,
    navigationIcon: (@Composable () -> Unit)? = null,
    backdrop: LayerBackdrop? = null,
    listState: LazyListState? = null,
    overlay: @Composable (backdrop: LayerBackdrop?, innerPadding: PaddingValues, topPadding: Dp) -> Unit = { _, _, _ -> },
    content: @Composable (
        innerPadding: PaddingValues,
        topPadding: Dp,
        scrollBehavior: ScrollBehavior,
        backdrop: LayerBackdrop?,
    ) -> Unit,
) {
    val scrollBehavior = MiuixScrollBehavior()

    // backdrop 一律由页面自建并传入（与参考实现一致）；这里不再兜底录制，
    // 否则同一页会出现两套录制树，顶栏与列表采样的不是同一层。
    val blurActive = backdrop != null

    // 顶栏累计偏移的状态一致性：列表物理回到顶点（首项索引与偏移都为 0）时，
    // 顶栏的 contentOffset 必须归零。滚动容器只在「顶部下拉回弹仍有向下余量」时清这个值，
    // 拖回顶部松手（没有向下 fling）会把它留在负值，表现为标题已展开但顶栏渐变带仍压着首卡。
    if (listState != null) {
        val scrolledList = listState
        LaunchedEffect(scrolledList, scrollBehavior) {
            snapshotFlow {
                scrolledList.firstVisibleItemIndex == 0 &&
                    scrolledList.firstVisibleItemScrollOffset == 0 &&
                    scrollBehavior.state.contentOffset < 0f
            }.collect { residualAtTop ->
                if (residualAtTop) scrollBehavior.state.contentOffset = 0f
            }
        }
    }

    val popupLayer = remember { GlassPopupLayerState() }
    CompositionLocalProvider(
        LocalGlassPopupLayer provides popupLayer,
        LocalPageBackdrop provides backdrop,
    ) {
    Scaffold(
        modifier = modifier,
        topBar = {
            BlurredBar(backdrop = backdrop, blurActive = blurActive) {
                AdaptiveTopAppBar(
                    title = title,
                    scrollBehavior = scrollBehavior,
                    navigationIcon = navigationIcon ?: {},
                )
            }
        },
    ) { innerPadding ->
        val focusManager = LocalFocusManager.current
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
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
            // 覆盖层（backdrop 录制树外，可安全采样 backdrop 而不自采样递归）
            overlay(backdrop, innerPadding, innerPadding.calculateTopPadding())
            // 玻璃下拉宿主：页面根 Box，尺寸有界、位于内容与覆盖层之上
            GlassPopupLayerHost(popupLayer)
        }
    }
    }
}
