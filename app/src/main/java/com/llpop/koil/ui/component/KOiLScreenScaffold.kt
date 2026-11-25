package com.llpop.koil.ui.component

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.Dp
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import com.llpop.koil.ui.component.liquid.rememberCombinedBackdrop
import androidx.compose.foundation.background
import com.llpop.koil.ui.component.effect.BgEffectBackground
import com.llpop.koil.ui.theme.LocalWallpaperEnabled
import com.llpop.koil.ui.theme.LocalWallpaperPlaying
import com.llpop.koil.ui.theme.LocalWallpaperScrollGate
import top.yukonga.miuix.kmp.theme.MiuixTheme

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
    // 二级页（关于 / 油价明细）脱离一级页 Pager：外层那份"钉在屏幕上的壁纸层"
    // 只属于三个一级页，二级页若跟着透明就会露出一片纯色（"没底"）。置 true 时
    // 本页自己建壁纸层并铺 surface 底 —— 与官方 AboutPage 的写法一致。
    ownWallpaper: Boolean = false,
    overlay: @Composable (backdrop: Backdrop?, innerPadding: PaddingValues, topPadding: Dp) -> Unit = { _, _, _ -> },
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
        // 滚动闸门上报：列表在拖拽/惯性滚动期间暂停动态底色阶的时间推进
        val scrollGate = LocalWallpaperScrollGate.current
        if (scrollGate != null) {
            LaunchedEffect(scrolledList, scrollGate) {
                snapshotFlow { scrolledList.isScrollInProgress }.collect { scrolling ->
                    scrollGate.report(scrolledList, scrolling)
                }
            }
            DisposableEffect(scrolledList, scrollGate) {
                onDispose { scrollGate.report(scrolledList, false) }
            }
        }
    }

    // 官方 AboutPage 的做法：内容与顶栏分开录进各自的 backdrop，
    // 覆盖层采样两者合并的层 —— 这样覆盖层既不会采到自己的宿主，也不会采到 scrim。
    val barBackdrop = if (blurActive) rememberLayerBackdrop() else null
    // 外层（MainScreen）已经提供了「固定壁纸」层时：本页不再自建壁纸层、也不再铺底，
    // 壁纸钉在屏幕上，翻页只移动这一页的内容；页面容器透明，让外层壁纸透上来。
    val inheritedWallpaperBackdrop = if (ownWallpaper) null else LocalWallpaperBackdrop.current
    val wallpaperBackdrop = if (blurActive && inheritedWallpaperBackdrop == null) rememberLayerBackdrop() else null
    // 本页实际可用的壁纸层：自建的那份优先，其次是外层 Pager 钉住的那份。
    val pageWallpaper = wallpaperBackdrop ?: inheritedWallpaperBackdrop
    val overlayBackdrop = if (backdrop != null && barBackdrop != null) {
        // 内容层现在不含壁纸了：覆盖层（长按浮层等）的玻璃要把壁纸垫在最下面，
        // 否则采到的只有 surface 灰。
        if (pageWallpaper != null) {
            rememberCombinedBackdrop(
                rememberCombinedBackdrop(pageWallpaper, backdrop),
                barBackdrop,
            )
        } else {
            rememberCombinedBackdrop(backdrop, barBackdrop)
        }
    } else {
        backdrop
    }
    // 顶栏的采样源 —— 与官方 GlassPage 一致，采「壁纸层 + 内容层」的整层，
    // 而不是只采壁纸：官方顶栏的渐进模糊靠"内容滚到顶栏下面"产生纹理，
    // 只给壁纸层时模糊的是平滑色阶，观感上就是"上滑没模糊了"。
    // 壁纸层在 MainScreen（固定不动），内容层是本页录制层，两层各自按当前坐标取像素。
    val topBarBackdrop: Backdrop? = when {
        pageWallpaper != null && backdrop != null -> rememberCombinedBackdrop(pageWallpaper, backdrop)
        pageWallpaper != null -> pageWallpaper
        else -> backdrop
    }

    // 弹层（GlassDialog / GlassBottomSheet / 下拉）由外层根宿主渲染，落在本页录制层
    // 之外，因此它们采样「外层整页层」（含顶栏 + 底栏 + 内容、不含弹层自身）正是
    // 官方 GlassDialog 注释要求的语义。没有外层时退回本页 backdrop。
    val inheritedPageBackdrop = LocalPageBackdrop.current
    CompositionLocalProvider(
        LocalPageBackdrop provides (inheritedPageBackdrop ?: backdrop),
        // 壁纸层 backdrop：页面内玻璃组件采「底色 + 壁纸」那层（官方 GlassPage 的
        // tabBackdrop 同一用法），不能采自己所在的录制层。
        LocalWallpaperBackdrop provides (wallpaperBackdrop ?: inheritedWallpaperBackdrop),
    ) {
    Scaffold(
        modifier = modifier,
        containerColor = Color.Transparent,
        topBar = {
            Box(modifier = if (barBackdrop != null) Modifier.layerBackdrop(barBackdrop) else Modifier) {
                BlurredBar(backdrop = topBarBackdrop, blurActive = blurActive) {
                    AdaptiveTopAppBar(
                        title = title,
                        scrollBehavior = scrollBehavior,
                        navigationIcon = navigationIcon ?: {},
                    )
                }
            }
        },
    ) { innerPadding ->
        val focusManager = LocalFocusManager.current
        val wallpaper = LocalWallpaperEnabled.current
        // 外层已有固定壁纸（三个一级页）时，这里什么都不画：直接透出 MainScreen 的
        // 壁纸层。只有脱离 Pager 的场景（没有外层壁纸）才沿用旧结构 —— 官方 GlassPage
        // 的「壁纸层 + 内容层」都录在页面自己这一层里，壁纸层垫 surface 并画流动底。
        Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier),
        ) {
            if (wallpaperBackdrop != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .layerBackdrop(wallpaperBackdrop)
                        .background(MiuixTheme.colorScheme.surface),
                ) {
                    if (wallpaper) {
                        // 只有一份动态底时由外层常驻播放；这里是"没有外层壁纸"的回退路径。
                        val wallpaperPlaying = LocalWallpaperPlaying.current
                        BgEffectBackground(
                            dynamicBackground = wallpaperPlaying,
                            modifier = Modifier.fillMaxSize(),
                            isFullSize = true,
                        ) {
                        }
                    }
                }
            }
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
        }
        // 覆盖层：必须在 backdrop 录制层**外面**。写在里面时，覆盖层内的玻璃采
        // overlayBackdrop（含这层自己）→ 绘制期重入 record，表现为"滚一下让返顶按钮
        // 出现就闪退"（Recording currently in progress）。
        overlay(overlayBackdrop, innerPadding, innerPadding.calculateTopPadding())
        }
    }
    }
}
