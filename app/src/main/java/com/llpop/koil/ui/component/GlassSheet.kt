package com.llpop.koil.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.WindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.llpop.koil.ui.component.effect.rememberEffectiveGlassSpec
import com.llpop.koil.ui.component.effect.rememberKOiLGlassEdgeStrokeStatic
import com.llpop.koil.ui.theme.LocalKOiLDarkTheme
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.glass.GlassBottomSheet
import top.yukonga.miuix.kmp.glass.GlassIconButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.glass.Close
import top.yukonga.miuix.kmp.icon.glass.Ok
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical

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
    // 键盘只让一个「开/关」布尔进组合（它一次动画里只翻转两次），ime 高度本身放到测量阶段读：
    // 键盘动画期间系统每帧都推新的 ime 值，在组合阶段读会每帧重组整棵子树，面板跟不上键盘，
    // 表现就是弹起比键盘慢半拍。放进 layout 后每帧只重新测量，不重新组合
    val imeVisible = WindowInsets.isImeVisible
    // 这里只取 insets 对象（对象本身稳定），具体像素值在测量阶段读
    val imeInsets = WindowInsets.ime
    val navInsets = WindowInsets.navigationBars
    val density = LocalDensity.current
    val windowInfo = LocalWindowInfo.current
    val topInset = with(density) { WindowInsets.statusBars.getTop(density).toDp() }
    // 封顶与底部留白都按 ime 的真实进度算，键盘动画期间逐帧连续变化，不再有「某一帧突然
    // 判定成键盘收起 → 封顶解除 / 底部留白跳一档」的阶跃，面板不会先下收再回弹
    val imeMemory = remember { ImeMemory() }
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
                    // 横向导航栏让位常驻（横屏侧栏时不贴边）；竖向留白随键盘进度在下面算
                    .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Horizontal)),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        // 键盘态封顶 + 底部留白，都在测量阶段按 ime 进度连续计算
                        .imeAwareContent(
                            imeInsets = imeInsets,
                            navInsets = navInsets,
                            imeVisible = imeVisible,
                            memory = imeMemory,
                            windowInfo = windowInfo,
                            topInset = topInset,
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

/** 键盘把屏幕占满时的最矮内容区，低于这个高度表单就没法看了 */
private val MinContentHeight = 200.dp

/**
 * 键盘 inset 读不出来时的兜底比例（按屏高估键盘高）。
 *
 * 正常机型走 `WindowInsets.ime` 真实值；这条只在「键盘可见但整机从未报过非零 ime」的异常路径生效。
 * 取 0.45 是常见全键盘占比（QWERTY 约屏高 40–45%），估小一点只是面板矮一截，估大才会压扁内容。
 */
private const val ImeFallbackRatio = 0.45f

/**
 * 跨帧记住 ime 峰值（px），作为键盘进度的分母。只在测量阶段读写，不进快照状态。
 *
 * 分母必须在一次键盘动画里保持不变：每帧都把「本帧 ime」当分母会让进度恒等于 1，
 * 底部留白会在一帧之内从 40dp 跳到 12dp，反而制造出新的突跳。所以本轮峰值先记进 [peakImePx]，
 * 等键盘完全收起（ime 归零）才提交到 [maxImePx]，下一轮用它做分母。
 */
private class ImeMemory {
    /** 上一轮键盘实测高度，本轮键盘动画期间作为分母 */
    var maxImePx: Int = 0
    /** 本轮键盘正在进行的峰值，收起时提交 */
    var peakImePx: Int = 0
}

/**
 * 键盘态内容封顶 + 底部留白，全部在**测量阶段**按 ime 的真实进度连续计算。
 *
 * 两件事都要连续，否则收键盘时会「先往下收、再往上弹一点点」：
 * - 封顶：官方 `BottomSheetContentLayout` 只按 `windowHeight - topInset` 封顶、不含键盘，
 *   长表单弹起键盘会冲顶。这里按「窗口高 − 状态栏 − 键盘 − 顶部让位 − 底部留白」限制高度，
 *   键盘高度是逐帧变化的真实值，封顶也跟着逐帧收放，不做「可见/不可见」二值切换。
 * - 底部留白：键盘全出时只要 [ImeGap]，键盘全收时要让出导航栏 + 16dp。按 ime/最大 ime 的比例
 *   线性插值，留白跟着键盘一起长/收；不插值的话收起末尾会一次性多出约 40dp，面板顶上一下。
 */
private fun Modifier.imeAwareContent(
    imeInsets: WindowInsets,
    navInsets: WindowInsets,
    imeVisible: Boolean,
    memory: ImeMemory,
    windowInfo: WindowInfo,
    topInset: Dp,
): Modifier = layout { measurable, constraints ->
    val imePx = imeInsets.getBottom(this)
    if (imePx > memory.peakImePx) memory.peakImePx = imePx
    if (imePx == 0 && memory.peakImePx > 0) {
        memory.maxImePx = maxOf(memory.maxImePx, memory.peakImePx)
        memory.peakImePx = 0
    }
    val windowPx = windowInfo.containerSize.height
    // 键盘进度：0 = 完全收起，1 = 完全弹出。第一次弹键盘时还没有实测最大值，先按屏高估一个
    // 分母，等测到真实值就换成真实值；兜底估值只在这条异常路径上起作用
    val denominator = if (memory.maxImePx > 0) memory.maxImePx else (windowPx * ImeFallbackRatio).toInt()
    // ime 归零就是键盘已经不在（不管 isImeVisible 那一帧还挂着什么），否则会先按收起的留白
    // 算一帧、下一帧再跳回键盘留白，正是那个「往下收再上弹一点点」。只有整机从未报过非零 ime
    // 的异常机型、且 isImeVisible 为 true 时，才当键盘还在
    val progress = when {
        imePx > 0 -> (imePx.toFloat() / denominator).coerceIn(0f, 1f)
        imeVisible && memory.maxImePx == 0 -> 1f
        else -> 0f
    }
    val idleBottomPx = navInsets.getBottom(this) + 16.dp.roundToPx()
    val keyboardBottomPx = ImeGap.roundToPx()
    val bottomSpacePx = (idleBottomPx + (keyboardBottomPx - idleBottomPx) * progress).roundToInt()
    val capPx = if (imePx > 0 || (imeVisible && memory.maxImePx == 0)) {
        val imeForCap = if (imePx > 0) imePx else (windowPx * ImeFallbackRatio).toInt()
        (windowPx - imeForCap - topInset.roundToPx() - SheetTopGap.roundToPx() - bottomSpacePx)
            .coerceAtLeast(MinContentHeight.roundToPx())
            .coerceAtMost(constraints.maxHeight)
    } else {
        constraints.maxHeight
    }
    val placeable = measurable.measure(constraints.copy(minHeight = 0, maxHeight = capPx))
    layout(placeable.width, placeable.height + bottomSpacePx) {
        placeable.placeRelative(0, 0)
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
