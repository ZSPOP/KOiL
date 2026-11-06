package com.llpop.koil.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.DropdownDefaults
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Check

/**
 * 自研锚定下拉浮层定位器，**完整对齐 miuix DropdownPositionProvider**（miuix-ui 0.9.4 源码逐项核对）：
 *
 * 坐标系（Compose Android Popup 实测反编译确认）：
 * - Popup 组件的 anchorBounds = 锚点元素 `positionInWindow()`（**相对当前 window 的坐标**，非屏幕坐标）
 * - positionProvider 返回的 offset 被直接写进 WindowManager.LayoutParams.x/y（**屏幕坐标**）
 * - 因此系统 Dialog 内（window 居中于屏幕）必须把计算结果再 +windowOffset（Dialog 窗口内容根在屏幕的位置），
 *   否则面板整体偏移，表现为"不贴文本框"
 * - 翻转/限位判断用 **window 尺寸**（弹窗卡片区域），与 miuix windowBounds 语义一致
 *
 * 定位规则：
 * - 垂直：优先贴锚点下方弹出（+8dp verticalMargin）；下方空间不足翻转到上方；两边不足锚点垂直居中
 * - 水平：endAligned=true 面板右缘贴锚点右缘；false 左缘贴锚点左缘；**null=自动判向**——锚点在窗口
 *   右半 → End 对齐、左半 → Start 对齐（v1.6.3fix：明细页省份 pill 贴屏幕左侧，固定 End 会把面板
 *   推出屏幕左缘再被钳到 0，表现为面板贴死屏幕左边）
 */
class FlipBelowPositionProvider(
    private val anchorOverride: IntRect? = null,
    private val endAligned: Boolean? = null,
    density: Density,
    winW: Int,
    winH: Int,
    private val windowOffset: IntOffset = IntOffset.Zero,
) : PopupPositionProvider {
    private val winW = winW
    private val winH = winH
    private val marginV = with(density) { 8.dp.roundToPx() }
    val direction = mutableIntStateOf(1)

    /** 自动判向后的实际水平对齐（true=End/右对齐锚点右缘），供动画 TransformOrigin 读取 */
    val endResolved = mutableStateOf(true)

    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val anchor = anchorOverride ?: anchorBounds
        val end = endAligned ?: ((anchor.left + anchor.right) / 2 >= winW / 2)
        endResolved.value = end
        val x = (if (end) {
            anchor.right - popupContentSize.width
        } else {
            anchor.left
        }).coerceIn(0, (winW - popupContentSize.width).coerceAtLeast(0))

        val belowSpace = winH - anchor.bottom
        val aboveSpace = anchor.top
        val y = when {
            popupContentSize.height <= belowSpace - marginV -> {
                direction.intValue = 1
                anchor.bottom + marginV
            }
            popupContentSize.height <= aboveSpace - marginV -> {
                direction.intValue = -1
                anchor.top - popupContentSize.height - marginV
            }
            else -> {
                direction.intValue = 0
                anchor.top + anchor.height / 2 - popupContentSize.height / 2
            }
        }.coerceIn(0, (winH - popupContentSize.height).coerceAtLeast(0))

        // 窗口坐标 → 屏幕坐标（WindowManager 放置）
        return IntOffset(x + windowOffset.x, y + windowOffset.y)
    }
}

/**
 * 锚定下拉浮层（自研，完整对齐 miuix Dropdown 视觉与动效）：
 * - 独立 Popup 窗口渲染，浮于系统 Dialog 内容之上，不会被弹窗卡片遮挡
 * - 色彩直接取 miuix DropdownDefaults.dropdownColors()（容器/文字/选中行/勾色，自动适配深浅色）
 * - 定位：水平自动判向（锚点在窗口左半 → 左对齐展开、右半 → 右对齐展开，endAligned 可强制）、
 *   下方弹出+8dp、不足上翻、居中；自动补偿 Dialog 窗口屏幕偏移（坐标系修正）
 * - 面板宽度 = 内容自适应（IntrinsicSize.Max，最宽行），最小 minWidth（默认 200dp，对齐 miuix ListPopupColumn）
 * - 动画：缩放 spring(0.15→1) + 淡入 tween(200)，transformOrigin 从锚点方向展开
 * - anchorBounds 传锚点区域（**window 坐标：boundsInWindow()**）则以其为准；否则用 Popup 默认锚点（调用处）
 */
@Composable
fun AnchoredPopup(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    minWidth: Dp = 200.dp,
    maxHeight: Dp? = null,
    anchorBounds: IntRect? = null,
    endAligned: Boolean? = null,
    content: @Composable () -> Unit,
) {
    if (!expanded) return
    val density = LocalDensity.current
    // v1.6.4：窗口可用尺寸取 LocalWindowInfo（多窗口/折叠屏/分屏下 Configuration 仍是整屏）
    val windowInfo = androidx.compose.ui.platform.LocalWindowInfo.current
    val view = LocalView.current
    var windowOffset by remember { mutableStateOf(IntOffset.Zero) }
    var winSize by remember { mutableStateOf(IntSize.Zero) }
    LaunchedEffect(Unit) {
        val loc = IntArray(2)
        view.getLocationOnScreen(loc)
        windowOffset = IntOffset(loc[0], loc[1])
        winSize = IntSize(view.width, view.height)
    }
    val fallbackW = windowInfo.containerSize.width
    val fallbackH = windowInfo.containerSize.height
    val positionProvider = remember(anchorBounds, endAligned, windowOffset, winSize) {
        FlipBelowPositionProvider(
            anchorOverride = anchorBounds,
            endAligned = endAligned,
            density = density,
            winW = winSize.width.takeIf { it > 0 } ?: fallbackW,
            winH = winSize.height.takeIf { it > 0 } ?: fallbackH,
            windowOffset = windowOffset,
        )
    }
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
    val direction = positionProvider.direction.intValue
    val endResolved = positionProvider.endResolved.value
    val origin = remember(direction, endResolved) {
        TransformOrigin(
            pivotFractionX = if (endResolved) 1f else 0f,
            pivotFractionY = when (direction) {
                -1 -> 1f
                1 -> 0f
                else -> 0.5f
            },
        )
    }
    val shape = RoundedCornerShape(16.dp)
    val sizeMod = remember(minWidth, maxHeight) {
        var m = Modifier.width(IntrinsicSize.Max).widthIn(min = minWidth)
        if (maxHeight != null) m = m.heightIn(max = maxHeight)
        m
    }
    Popup(
        onDismissRequest = onDismissRequest,
        popupPositionProvider = positionProvider,
    ) {
        Box(
            modifier = modifier
                .then(sizeMod)
                .graphicsLayer {
                    val f = fraction.value
                    val s = 0.15f + 0.85f * f
                    scaleX = s
                    scaleY = s
                    this.alpha = alpha.value
                    transformOrigin = origin
                }
                .shadow(10.dp, shape)
                .clip(shape)
                .background(DropdownDefaults.dropdownColors().containerColor),
        ) { content() }
    }
}

/**
 * 下拉列表项（对齐 miuix DropdownImpl 非 dialog 模式）：
 * - 外层 Row：SpaceBetween，左侧文本块（限宽 216dp，对齐 miuix MaxItemTextWidth），右侧勾选图标（未选中=透明勾，保持列对齐）
 * - 行高内容自适应（无固定高度）：行内水平 padding 20dp；垂直 padding 首/尾行 20dp、中间行 12dp
 * - 文字 16sp（body1）恒 Medium；选中：文字/勾=primary（selectedContentColor/selectedIndicatorColor），
 *   行底色=容器色（选中行无独立高亮，与 miuix 一致）
 */
@Composable
fun AnchoredDropdownItemRow(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    isFirst: Boolean = false,
    isLast: Boolean = false,
) {
    val colors = DropdownDefaults.dropdownColors()
    val verticalPadding = if (isFirst || isLast) 20.dp else 12.dp
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(colors.containerColor)
            .padding(horizontal = 20.dp, vertical = verticalPadding),
    ) {
        Text(
            text = text,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = if (selected) colors.selectedContentColor else colors.contentColor,
            maxLines = 1,
            modifier = Modifier.widthIn(max = 216.dp),
        )
        Icon(
            imageVector = MiuixIcons.Basic.Check,
            contentDescription = null,
            tint = if (selected) colors.selectedIndicatorColor else Color.Transparent,
            modifier = Modifier
                .padding(start = 12.dp)
                .size(20.dp),
        )
    }
}

/** 下拉列表容器（超高滚动） */
@Composable
fun AnchoredDropdownColumn(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(modifier = modifier.verticalScroll(rememberScrollState())) { content() }
}
