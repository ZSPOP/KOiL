package com.llpop.koil.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.glass.GlassBottomSheet
import top.yukonga.miuix.kmp.glass.GlassIconButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.glass.Close
import top.yukonga.miuix.kmp.icon.glass.Ok
import top.yukonga.miuix.kmp.theme.MiuixTheme
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
    startAction: (@Composable () -> Unit)? = null,
    endAction: (@Composable () -> Unit)? = null,
    onConfirm: (() -> Unit)? = null,
    confirmEnabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    // 在函数作用域（= 调用点）先取到页面 backdrop：弹层内容在根弹层宿主里组合，
    // 那里读 LocalPageBackdrop 是 null，必须在这里捕获后重新下发，否则里面的
    // 下拉面板/日历只能回落成纯色
    val backdrop = rememberSheetBackdrop()
    // 键盘/状态栏的 inset 同样必须在调用点（页面树）取：弹层正文的 lambda 在根宿主里组合，
    // 那一层读 WindowInsets.ime / statusBars 常拿到 0，封顶就会算成整屏、等于没封
    val imeVisible = WindowInsets.isImeVisible
    val density = LocalDensity.current
    val windowHeight = with(density) { LocalWindowInfo.current.containerSize.height.toDp() }
    val topInset = with(density) { WindowInsets.statusBars.getTop(density).toDp() }
    val imeBottom = with(density) { WindowInsets.ime.getBottom(density).toDp() }
    // 官方 GlassBottomSheet 只按「窗口高 − 状态栏」给面板封顶（BottomSheetContentLayout 里的
    // windowHeight - topInset），没扣键盘：键盘一弹把面板整体抬到键盘上沿，长表单的顶就会
    // 直接冲出屏幕，官方 demo 的结构也一样。这里把内容高度压回「键盘以上、状态栏以下」的可视区，
    // 多出来的部分交给内部滚动 —— 这是唯一偏离官方的补丁，其余结构全部照官方
    val bottomGap = if (imeVisible) ImeGap else 16.dp
    val contentMaxHeight = if (imeVisible) {
        // 少数 OEM 在 adjustResize + edge-to-edge 下把 ime inset 报成 0（isImeVisible 仍为 true）：
        // 此时按屏高比例估一个键盘高度兜底，宁可面板矮一点也不让它再冲顶
        val imeForCap = if (imeBottom > 0.dp) imeBottom else windowHeight * ImeFallbackRatio
        (windowHeight - topInset - imeForCap - SheetTopGap - bottomGap).coerceAtLeast(200.dp)
    } else {
        Dp.Unspecified
    }
    // header 右槽的勾：调用点只给「保存这件事」，按钮形态/采样源与左槽的叉保持同款
    val confirmAction: (@Composable () -> Unit)? = if (onConfirm == null) {
        null
    } else {
        val onClick = onConfirm
        { SheetConfirmAction(onClick, confirmEnabled) }
    }
    GlassBottomSheet(
        show = show,
        onDismissRequest = onDismissRequest,
        backdrop = backdrop,
        modifier = modifier,
        // 官方 demo 同款：header（把手 + 标题 + 两侧 action）浮在正文之上，滚动时正文从标题下穿过，
        // 渐变模糊带压在穿过的那截上。不传（默认 false）时 header 独占一行，正文永远在标题下方，
        // 看不到内容穿过标题的效果
        progressiveBlur = true,
        title = title,
        // header 主行两侧交给官方排布；左槽默认给关闭按钮（官方 demo 同款圆形玻璃图标按钮），
        // 右槽留给业务（保存/确认），不传就不显示
        startAction = startAction ?: { SheetCloseAction(onDismissRequest) },
        endAction = endAction ?: confirmAction,
        // 描边是全站基准（静态版：弹层开着时不随倾斜逐帧重组）；其余材质/内距/宿主开关
        // 全部走官方默认，与 demo 一致
        stroke = rememberKOiLGlassEdgeStrokeStatic(
            isDark = LocalKOiLDarkTheme.current,
            spec = rememberEffectiveGlassSpec(),
        ),
        insideMargin = DpSize(0.dp, 0.dp),
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
                    // 底部让位放在滚动区外面：键盘可见时只在键盘上方留 ImeGap，
                    // 收起时才让出系统导航栏 + 16dp（同官方 demo 正文尾部写法）
                    .then(
                        if (imeVisible) {
                            Modifier.padding(bottom = ImeGap)
                        } else {
                            Modifier.navigationBarsPadding().padding(bottom = 16.dp)
                        },
                    ),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        // 键盘可见时封顶，抬升后正好落在状态栏下；收起时不限
                        .then(
                            if (contentMaxHeight != Dp.Unspecified) {
                                Modifier.heightIn(max = contentMaxHeight)
                            } else {
                                Modifier
                            },
                        )
                        // 越界修饰符必须挂在滚动容器外层（与页面列表同一写法）：
                        // 放内层时它看不到滚动容器之外的手势，越界与交接都不会触发
                        .overScrollVertical()
                        .verticalScroll(rememberScrollState())
                        // 官方写法：innerPadding 给在滚动容器内侧，header 高度算进滚动内容，
                        // 正文才是从标题下「穿过」而不是被裁在标题下方
                        .padding(innerPadding),
                    content = content,
                )
            }
        }
    }
}

/** 弹层顶边与状态栏之间保留的余量，与外面的 16dp 视觉节奏一致 */
private val SheetTopGap = 16.dp

/**
 * 键盘 inset 读不出来时的兜底比例（按屏高估键盘高）。
 *
 * 正常机型走 `WindowInsets.ime` 真实值；这条只在「键盘可见但 inset 报 0」的异常路径生效。
 * 取 0.45 是常见全键盘占比（QWERTY 约屏高 40–45%），估小一点只是面板矮一截，估大才会压扁内容。
 */
private const val ImeFallbackRatio = 0.45f

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
        // 同 KOiLFormSheet：header 浮在正文上，列表滚过标题时走官方渐变模糊带
        progressiveBlur = true,
        title = title,
        // 同 KOiLFormSheet：左槽给关闭按钮，形态与表单弹层完全一致；右槽留空
        // （明细弹层没有"确认"这件事，底部已有钉住的按钮区）
        startAction = { SheetCloseAction(onDismissRequest) },
        // 描边同上；其余材质/内距/宿主开关全部走官方默认
        stroke = rememberKOiLGlassEdgeStrokeStatic(
            isDark = LocalKOiLDarkTheme.current,
            spec = rememberEffectiveGlassSpec(),
        ),
        insideMargin = DpSize(0.dp, 0.dp),
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
                // 官方写法：innerPadding 给在滚动容器内侧，列表从标题下穿过；底部按钮区
                // 在滚动区之外，钉在面板底部的前提不变。
                // 封顶按「列表本身的高」算：header 高度含在滚动内容里，所以要把
                // innerPadding 的 top 加回来，[maxContentHeight] 才是可见的列表高度
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = maxContentHeight + innerPadding.calculateTopPadding())
                        .overScrollVertical()
                        .verticalScroll(rememberScrollState())
                        .padding(innerPadding),
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

/**
 * header 右槽的确认动作：官方 demo 同款圆形玻璃图标按钮（勾），点击等同内容底部的储存。
 *
 * 不显式传 backdrop：`GlassIconButton` 在 header 语境里默认 `inheritTopBarMaterial = true`，
 * 会直接吃 header 下发的采样层（与左槽的叉同一份），自己再传一份反而会被忽略。
 * 置灰（[enabled] = false）时压暗且不响应点击，语义与底部储存按钮的 `enabled` 一致。
 */
@Composable
private fun SheetConfirmAction(
    onClick: () -> Unit,
    enabled: Boolean,
) {
    GlassIconButton(
        onClick = { if (enabled) onClick() },
        // 官方 demo 同款：右槽留 12dp
        modifier = Modifier
            .padding(end = 12.dp)
            .alpha(if (enabled) 1f else 0.4f),
    ) {
        Icon(
            imageVector = MiuixIcons.Glass.Ok,
            contentDescription = null,
            tint = MiuixTheme.colorScheme.onSurface,
        )
    }
}

/**
 * header 左槽的默认动作：官方 demo 同款圆形玻璃图标按钮，点击即关闭。
 *
 * 采样源同 [SheetConfirmAction]：不显式传，由 header 下发，两个圆圈天然一致。
 */
@Composable
private fun SheetCloseAction(
    onDismissRequest: () -> Unit,
) {
    GlassIconButton(
        onClick = onDismissRequest,
        // 官方 demo 同款：按钮不贴边，左槽留 12dp
        modifier = Modifier.padding(start = 12.dp),
    ) {
        Icon(
            imageVector = MiuixIcons.Glass.Close,
            contentDescription = null,
            tint = MiuixTheme.colorScheme.onSurface,
        )
    }
}
