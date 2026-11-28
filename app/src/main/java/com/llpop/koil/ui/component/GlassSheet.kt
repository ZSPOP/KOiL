package com.llpop.koil.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.statusBars
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.glass.GlassBottomSheet
import top.yukonga.miuix.kmp.utils.overScrollVertical
import com.llpop.koil.ui.component.effect.rememberEffectiveGlassSpec
import com.llpop.koil.ui.component.effect.rememberKOiLGlassEdgeStrokeStatic
import com.llpop.koil.ui.theme.LocalKOiLDarkTheme

/**
 * 弹层的采样源：底色层全量 + 页面层按 [SheetContentAlpha] 合成.
 *
 * 结构照官方 `GlassPage.kt` 的 `secondaryBackdrop`（两层同层绘制、坐标残差透传首层），
 * 唯一偏离是页面层权重 —— 官方等权 1.0，这里压到一半：宿主页的卡片分组与实心主色块
 * 在 60dp 高斯下会摊成整片色块，1.0 挡不住；压下来之后底色连续、结构只剩一层。
 */
@Composable
private fun rememberSheetBackdrop(): Backdrop? = rememberOverlayBackdrop(SheetContentAlpha)

/**
 * KOiL 玻璃底部弹层：直接委托官方 [GlassBottomSheet]。
 *
 * 与 [KOiLGlassDialog] 的分工：确认类、短内容用小弹窗；补录/编辑这类长表单用底部弹层，
 * 形态上更贴近手机原生（整宽拉起、顶部圆角、带拖拽把手，可下拉关闭）。
 *
 * 布局契约：`insideMargin` 传 0 —— 标题行由官方自己居中，正文的横向内距由各内容块自己给，
 * 这样 [FullTankSwitchRow] 才能落在零横向内距的容器里（它内部固定 16dp，外层再加会双重缩进）。
 *
 * 滚动：内容必须同时挂 `overScrollVertical` —— 面板的越界拉动/回弹靠 `LocalBottomSheetNestedScrollConnection`
 * 交接，普通 `verticalScroll` 只滚自己、不会把手势交给面板，表现为「内容拉不动」。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun KOiLFormSheet(
    show: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    // 在函数作用域（= 调用点）先取到页面 backdrop：弹层内容在根弹层宿主里组合，
    // 那里读 LocalPageBackdrop 是 null，必须在这里捕获后重新下发，否则里面的
    // 下拉面板/日历只能回落成纯色
    val backdrop = rememberSheetBackdrop()
    GlassBottomSheet(
        show = show,
        onDismissRequest = onDismissRequest,
        backdrop = backdrop,
        modifier = modifier,
        title = title,
        // 材质统一从 KOiLGlassOverlayMaterials 取，与确认弹窗同一档；
        // 描边取全站基准（静态版：弹层开着时不随倾斜逐帧重组）
        material = KOiLGlassOverlayMaterials.sheet(),
        stroke = rememberKOiLGlassEdgeStrokeStatic(
            isDark = LocalKOiLDarkTheme.current,
            spec = rememberEffectiveGlassSpec(),
        ),
        insideMargin = DpSize(0.dp, 0.dp),
        renderInRootScaffold = true,
        enableNestedScroll = true,
    ) { innerPadding ->
        // 面板本身由库抬到键盘顶；键盘可见时底部只留 ImeGap，
        // 收起时才让出系统导航栏 + 16dp（那时它是真实占位）
        val imeVisible = WindowInsets.isImeVisible
        // 面板高度封顶：库在键盘可见时给面板整体加 imePadding（defaultWindowInsetsPadding），
        // 把面板底顶到键盘上沿；内容比可视区高时，面板会跟着抬出屏幕顶部。
        // 这里把内容高度限制到「窗口高 − 键盘 − 状态栏 − 余量」，抬升后正好完整落在屏幕内，
        // 超出的部分交给内部滚动；键盘收起时不限（Dp.Unspecified）。
        val density = LocalDensity.current
        val headerHeight = innerPadding.calculateTopPadding()
        val sheetContentMaxHeight = if (imeVisible) {
            val windowPx = LocalWindowInfo.current.containerSize.height
            val imePx = WindowInsets.ime.getBottom(density)
            val topPx = WindowInsets.statusBars.getTop(density)
            val available = with(density) {
                (windowPx - imePx - topPx - 16.dp.roundToPx()).toDp()
            }
            (available - headerHeight).coerceAtLeast(200.dp)
        } else {
            Dp.Unspecified
        }
        // 弹层自身是玻璃面：连同 LocalOnGlassSurface 一起广播，内部提示条/分割线
        // 才知道该按采样底取色（实色 surfaceContainer 的 tint 在玻璃上会显成色片）
        CompositionLocalProvider(
            LocalPageBackdrop provides backdrop,
            LocalOverlayBackdropApplied provides (backdrop != null),
            LocalOnGlassSurface provides (backdrop != null),
        ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // rc01 起 header（拖拽把手 + 标题）浮在正文之上，库把 header 高度
                // 作为 PaddingValues 传下来（insideMargin 传 0 时由正文自己消费）：
                // 不接这一趟，正文首行会被标题压住
                .padding(top = headerHeight)
                // 键盘可见时封顶：面板被库抬到键盘顶后不再冲出屏幕顶部（见上方推导）
                .then(
                    if (sheetContentMaxHeight != Dp.Unspecified) {
                        Modifier.heightIn(max = sheetContentMaxHeight)
                    } else {
                        Modifier
                    },
                )
                // 越界修饰符必须挂在滚动容器外层（与页面列表同一写法）：
                // 放内层时它看不到滚动容器之外的手势，越界与交接都不会触发
                .overScrollVertical()
                .verticalScroll(rememberScrollState())
                .then(
                    if (imeVisible) {
                        Modifier.padding(bottom = ImeGap)
                    } else {
                        Modifier.navigationBarsPadding().padding(bottom = 16.dp)
                    },
                ),
            content = content,
        )
        }
    }
}

/**
 * KOiL 玻璃底部列表弹层：标题 + 可滚动内容 + 钉在底部的按钮区。
 *
 * 与 [KOiLFormSheet] 的分工：表单页整页滚动、按钮跟着内容走，靠「读完再点」；
 * 纯展示的明细只要「列表能滚、按钮常在」，用这个。内容区高度封顶 [maxContentHeight]，
 * 行数超出后只在内容区内部滚动，面板不再继续长高，底部按钮始终露在外面。
 *
 * 布局契约同 [KOiLFormSheet]：`insideMargin` 传 0，横向内距交给内容块自己给；
 * 底部导航栏让位由按钮区承担，滚到列表末尾时不会被系统手势条压住。
 */
@Composable
fun KOiLListSheet(
    show: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    maxContentHeight: Dp = 480.dp,
    footer: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    // 同 KOiLFormSheet：底色层 + 内容层按权重合成
    val backdrop = rememberSheetBackdrop()
    GlassBottomSheet(
        show = show,
        onDismissRequest = onDismissRequest,
        backdrop = backdrop,
        modifier = modifier,
        title = title,
        // 同 KOiLFormSheet：材质统一入口
        material = KOiLGlassOverlayMaterials.sheet(),
        stroke = rememberKOiLGlassEdgeStrokeStatic(
            isDark = LocalKOiLDarkTheme.current,
            spec = rememberEffectiveGlassSpec(),
        ),
        insideMargin = DpSize(0.dp, 0.dp),
        renderInRootScaffold = true,
        enableNestedScroll = true,
    ) { innerPadding ->
        // 弹层自身是玻璃面：连同 LocalOnGlassSurface 一起广播，内部提示条/分割线
        // 才知道该按采样底取色（实色 surfaceContainer 的 tint 在玻璃上会显成色片）
        CompositionLocalProvider(
            LocalPageBackdrop provides backdrop,
            LocalOverlayBackdropApplied provides (backdrop != null),
            LocalOnGlassSurface provides (backdrop != null),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(bottom = 16.dp),
            ) {
                // 越界修饰符同样挂在滚动容器外层：内层挂它看不到容器外的手势，
                // 下拉回弹与「内容拉到头再交给面板」的交接都会失效
                // rc01 起 header 浮在正文上，这里只在滚动区顶部让出 header 高度，
                // 底部按钮区不受影响（钉在面板底部的前提不变）
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = maxContentHeight)
                        .padding(top = innerPadding.calculateTopPadding())
                        .overScrollVertical()
                        .verticalScroll(rememberScrollState()),
                    content = content,
                )
                if (footer != null) {
                    KOiLHorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, top = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        content = footer,
                    )
                }
            }
        }
    }
}
