package com.llpop.koil.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.DropdownDefaults

/**
 * 锚定下拉条目（弹窗根级注册）：
 * - anchorBounds 为**窗口坐标**（boundsInWindow，弹窗卡片内坐标），与渲染层同一坐标系，零屏幕坐标转换
 * - content 为面板内容（下拉列表 / 日历）
 */
class AnchoredPopupEntry(
    val id: Any,
    val anchorBounds: IntRect,
    val endAligned: Boolean = true,
    val minWidth: Dp = 200.dp,
    val maxHeight: Dp? = null,
    val onDismiss: () -> Unit,
    val content: @Composable () -> Unit,
)

/** 弹窗根级下拉宿主状态：字段组件注册条目，宿主在弹窗内容根统一渲染 */
class AnchoredPopupHostState {
    val entries = mutableStateListOf<AnchoredPopupEntry>()

    /** 卡片在弹窗窗口内的边界（窗口坐标），用于遮罩区分"点击卡片内/外" */
    var cardBounds: IntRect? = null

    fun register(entry: AnchoredPopupEntry) {
        // v1.6.6：同一弹窗内同时只允许一个下拉展开——新展开的先收起其它条目。
        // 原来允许多个同时展开，点一次卡片外 dismissAll() 会把它们一起收掉，
        // 观感就是「没有逐级关闭、一下全关」（真机反馈）。
        if (entries.any { it.id != entry.id }) {
            entries.filter { it.id != entry.id }.forEach { it.onDismiss() }
        }
        if (entries.none { it.id == entry.id }) entries.add(entry)
    }

    fun unregister(id: Any) {
        entries.removeAll { it.id == id }
    }

    fun dismissAll() {
        // 立即清空列表：若只调 onDismiss，条目要等 recomposition 注销，导致下一次点击
        // 判断 entries 仍非空而再次 dismissAll（空转），卡片就关不掉了（逐层关闭失效）
        entries.toList().forEach { it.onDismiss() }
        entries.clear()
    }
}

val LocalAnchoredPopupHost = staticCompositionLocalOf<AnchoredPopupHostState> {
    error("AnchoredPopupHostState not provided：请在弹窗（KOiLGlassDialog）根提供")
}

/**
 * 锚定下拉宿主渲染层：只渲染下拉面板，放在**卡片上层**（窗口坐标绝对定位，不被卡片遮挡）。
 * 遮罩（AnchoredPopupOverlayMask）单独放在卡片下层，避免覆盖卡片挡住字段点击。
 */
@Composable
fun AnchoredPopupHostOverlay(
    modifier: Modifier = Modifier,
    state: AnchoredPopupHostState,
) {
    val entries = state.entries
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val density = androidx.compose.ui.platform.LocalDensity.current
        val winW = with(density) { maxWidth.roundToPx() }
        val winH = with(density) { maxHeight.roundToPx() }
        entries.forEach { entry ->
            AnchoredPopupPanel(
                entry = entry,
                winW = winW,
                winH = winH,
            )
        }
    }
}

/** 卡片外点击遮罩：放在**卡片下层**（卡片上层盖住自身区域，遮罩只收到卡片外点击），
 * 点击即触发 onTap（由外部决定收起下拉或关闭卡片）。 */
@Composable
fun AnchoredPopupOverlayMask(onTap: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(onTap) {
                detectTapGestures(onTap = { onTap() })
            },
    ) { }
}

@Composable
private fun AnchoredPopupPanel(
    entry: AnchoredPopupEntry,
    winW: Int,
    winH: Int,
) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val marginV = with(density) { 8.dp.roundToPx() }
    val fraction = remember { Animatable(0f) }
    val alpha = remember { Animatable(0f) }
    val fractionSpec = remember {
        spring<Float>(
            dampingRatio = 0.82f,
            stiffness = 362.5f,
            visibilityThreshold = 0.0001f,
        )
    }
    LaunchedEffect(Unit) {
        fraction.animateTo(1f, fractionSpec)
    }
    LaunchedEffect(Unit) {
        alpha.animateTo(1f, tween(durationMillis = 200))
    }
    val direction = remember { mutableIntStateOf(1) }
    val xDir = remember { mutableIntStateOf(1) }
    val shape = RoundedCornerShape(16.dp)
    val minWidth = entry.minWidth
    val maxHeight = entry.maxHeight
    Box(
        modifier = Modifier
            .width(IntrinsicSize.Max)
            .widthIn(min = minWidth)
            .then(if (maxHeight != null) Modifier.heightIn(max = maxHeight) else Modifier)
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                val w = placeable.width
                val h = placeable.height
                val anchor = entry.anchorBounds
                // 每个下拉对齐它对应的文本框，按空间翻边（不依赖中线硬切）：
                // 优先右对齐（面板右缘 = 文本框右缘）；若右对齐会把面板左缘顶出弹窗左边界
                // （anchor.right - w < 0，右侧放不下）→ 自动翻左对齐（面板左缘 = 文本框左缘）
                val endX = anchor.right - w
                val canEnd = endX >= 0
                xDir.intValue = if (canEnd) 1 else -1
                val xRaw = if (canEnd) endX else anchor.left
                val x = xRaw.coerceIn(0, (winW - w).coerceAtLeast(0))
                val y = when {
                    h <= winH - anchor.bottom - marginV -> {
                        direction.intValue = 1
                        anchor.bottom + marginV
                    }
                    h <= anchor.top - marginV -> {
                        direction.intValue = -1
                        anchor.top - h - marginV
                    }
                    else -> {
                        direction.intValue = 0
                        anchor.top + anchor.height / 2 - h / 2
                    }
                }.coerceIn(0, (winH - h).coerceAtLeast(0))
                layout(w, h) {
                    placeable.place(x, y)
                }
            }
            .graphicsLayer {
                val f = fraction.value
                val s = 0.15f + 0.85f * f
                scaleX = s
                scaleY = s
                this.alpha = alpha.value
                transformOrigin = TransformOrigin(
                    pivotFractionX = when (xDir.intValue) {
                        -1 -> 0f
                        1 -> 1f
                        else -> 0.5f
                    },
                    pivotFractionY = when (direction.intValue) {
                        -1 -> 1f
                        1 -> 0f
                        else -> 0.5f
                    },
                )
            }
            .drawWithContent {
                // clipReveal 展开（对齐 miuix popupClipReveal）：按展开方向从上/下/中裁剪
                val f = fraction.value
                val h = size.height
                if (f < 0.999f) {
                    val (t, b) = when (direction.intValue) {
                        -1 -> (h * (1f - f)) to h      // 从下往上展开
                        1 -> 0f to (h * f)             // 从上往下展开
                        else -> (h / 2f * (1f - f)) to (h / 2f * (1f + f)) // 居中展开
                    }
                    clipRect(top = t, bottom = b) { this@drawWithContent.drawContent() }
                } else {
                    drawContent()
                }
            }
            .shadow(10.dp, shape)
            .clip(shape)
            .background(DropdownDefaults.dropdownColors().containerColor),
    ) {
        entry.content()
    }
}

/**
 * 字段组件便捷注册：组合内调用，show 时注册、关闭时注销（锚点变化自动重建）。
 */
@Composable
fun AnchoredPopupAnchor(
    show: Boolean,
    id: Any,
    anchorBounds: IntRect?,
    endAligned: Boolean = true,
    minWidth: Dp = 200.dp,
    maxHeight: Dp? = null,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    val host = LocalAnchoredPopupHost.current
    val entry = remember(id, anchorBounds, endAligned, minWidth, maxHeight) {
        if (anchorBounds != null) {
            AnchoredPopupEntry(
                id = id,
                anchorBounds = anchorBounds,
                endAligned = endAligned,
                minWidth = minWidth,
                maxHeight = maxHeight,
                onDismiss = onDismiss,
                content = content,
            )
        } else null
    }
    androidx.compose.runtime.DisposableEffect(show, anchorBounds) {
        if (show && entry != null) {
            host.register(entry)
            onDispose { host.unregister(entry.id) }
        } else {
            onDispose { }
        }
    }
}

/** 在弹窗根包裹：提供宿主状态 + 渲染遮罩与下拉面板（供 KOiLGlassDialog 调用）。
 * 外层 Box 用 fillMaxSize 铺满整个 Dialog 窗口（anchor 的 boundsInWindow 是相对窗口坐标，
 * overlay 必须从窗口坐标原点(0,0)铺满，两坐标系才严格一致，面板才能精确贴字段框）。
 * 分层：遮罩在**卡片下层**（卡片盖住自身区域，遮罩只收到卡片外点击，不挡卡片内字段）；
 * 卡片居中；下拉面板在**卡片上层**（窗口坐标定位，不被卡片遮挡）。
 * 逐层关闭：点卡片外 → 有下拉收起下拉（卡片保留）、无下拉走 onOutsideTap 关闭卡片。
 * hostState 由外部（KOiLGlassDialog）创建并传入。 */
@Composable
fun AnchoredPopupDialogWrapper(
    hostState: AnchoredPopupHostState,
    onOutsideTap: () -> Unit,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalAnchoredPopupHost provides hostState) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = androidx.compose.ui.Alignment.Center,
        ) {
            // 下层：卡片外点击遮罩（卡片盖住自身区域，只收到卡片外点击）
            AnchoredPopupOverlayMask(
                onTap = {
                    if (hostState.entries.isNotEmpty()) hostState.dismissAll() else onOutsideTap()
                },
            )
            // 中层：卡片（用 onGloballyPositioned 记录边界；卡片盖住下层遮罩自身区域）
            Box(
                modifier = Modifier.onGloballyPositioned {
                    val b = it.boundsInWindow()
                    hostState.cardBounds = androidx.compose.ui.unit.IntRect(
                        b.left.toInt(), b.top.toInt(), b.right.toInt(), b.bottom.toInt(),
                    )
                },
            ) {
                content()
            }
            // 卡片上遮罩：仅在**有下拉展开时**渲染，覆盖卡片+外部；
            // 点非面板区域（含卡片内部空白/其它字段）即收起下拉。无下拉时不渲染，卡片内字段正常展开。
            if (hostState.entries.isNotEmpty()) {
                AnchoredPopupOverlayMask(
                    onTap = { hostState.dismissAll() },
                )
            }
            // 最上层：下拉面板（窗口坐标定位，不被任何遮罩/卡片遮挡）
            Box(modifier = Modifier.matchParentSize()) {
                AnchoredPopupHostOverlay(state = hostState)
            }
        }
    }
}
