package com.llpop.koil.ui.screen

import androidx.compose.foundation.background
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.llpop.koil.R
import com.llpop.koil.domain.logic.NumberFormat
import com.llpop.koil.ui.screen.settings.SettingsViewModel
import com.llpop.koil.ui.component.LocalOnGlassSurface
import com.llpop.koil.ui.component.KOiLGlassDialog
import com.llpop.koil.ui.theme.KOiLColors
import com.llpop.koil.ui.theme.KOiLType
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.glass.GlassDialogButton
import top.yukonga.miuix.kmp.glass.GlassDialogButtonRole
import top.yukonga.miuix.kmp.glass.GlassMotion
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * CSV 导入预览弹窗（M-07 拆分）：valid/duplicate/conflict/error 四页签，不入库。
 * 从 SettingsScreen 主函数整体移出，行为不变。
 *
 * 页签与内容双向绑定：点页签滚到对应一页，在内容区左右拖动则回写当前页签。
 * 每页内部仍然是纵向滚动列表，横向手势交给 pager、纵向交给列表，两者不抢方向。
 */
@Composable
internal fun ImportPreviewDialog(viewModel: SettingsViewModel) {
    val importPreview by viewModel.importPreview.collectAsStateWithLifecycle()
    importPreview?.let { preview ->
        val tabs = listOf(
            stringResource(R.string.koil_import_tab_valid, preview.valid.size) to preview.valid,
            stringResource(R.string.koil_import_tab_dup, preview.duplicates.size) to preview.duplicates,
            stringResource(R.string.koil_import_tab_conflict, preview.mileageConflicts.size) to preview.mileageConflicts,
            stringResource(R.string.koil_import_tab_error, preview.errors.size) to preview.errors,
        )
        val pagerState = rememberPagerState(initialPage = 0) { tabs.size }
        val scope = rememberCoroutineScope()
        // pagerState.currentPage 只在越过中线的瞬间跳一档，滑动过程中读它拿不到连续位置；
        // 叠上 currentPageOffsetFraction 再取整，页签高亮就能跟着手指连续切换，不用等松手。
        val tabIndex by remember(pagerState) {
            derivedStateOf {
                (pagerState.currentPage + pagerState.currentPageOffsetFraction)
                    .roundToInt()
                    .coerceIn(0, tabs.lastIndex)
            }
        }
        KOiLGlassDialog(
            show = true,
            onDismissRequest = viewModel::dismissImportPreview,
            title = stringResource(R.string.koil_import_title),
        ) {
            Column {
                // 页签条：与数据总览页的范围轨道同一套外形与手感——胶囊轨道 + 轨道内胶囊指示块，
                // 指示块在条上拖动时跟手、松手吸附，点击时滑过去；四格等分放得下带计数的长文案。
                CapsuleTabRow(
                    labels = tabs.map { it.first },
                    selectedIndex = tabIndex,
                    onSelect = { idx -> scope.launch { pagerState.animateScrollToPage(idx) } },
                )
                Spacer(Modifier.height(8.dp))
                HorizontalPager(
                    state = pagerState,
                    beyondViewportPageCount = 1,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                ) { page ->
                    val rows = tabs[page].second
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                    ) {
                        rows.forEach { r ->
                            // 四列等分（weight）而不是固定 dp：导入预览是用来逐条核对的行，
                            // 列宽一旦固定，系统字体放大或里程到 6 位时数字会换行、四列错位，
                            // 用户核对时容易看串行。改用权重后所有行列宽一致（行内不再有
                            // 随内容伸缩的第五列），文字单行显示；错误/冲突提示另起一行。
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = r.date,
                                        fontSize = KOiLType.BodySmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = MiuixTheme.colorScheme.onBackground,
                                        modifier = Modifier.weight(1.5f),
                                    )
                                    Text(
                                        text = "${r.mileage.toInt()}km",
                                        fontSize = KOiLType.CaptionLarge,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = MiuixTheme.colorScheme.onBackgroundVariant,
                                        modifier = Modifier.weight(1.3f),
                                    )
                                    Text(
                                        text = "${NumberFormat.measure(r.oilAmount)}L",
                                        fontSize = KOiLType.CaptionLarge,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = MiuixTheme.colorScheme.onBackgroundVariant,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Text(
                                        text = "¥${NumberFormat.money(r.actualAmount)}",
                                        fontSize = KOiLType.CaptionLarge,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = MiuixTheme.colorScheme.onBackgroundVariant,
                                        modifier = Modifier.weight(1.2f),
                                    )
                                }
                                r.errorKey?.let { key ->
                                    Text(
                                        text = when (key) {
                                            "format_error" -> stringResource(R.string.koil_msg_format_error)
                                            "duplicate" -> stringResource(R.string.koil_msg_duplicate)
                                            else -> key
                                        },
                                        fontSize = KOiLType.Caption,
                                        color = KOiLColors.Alert,
                                        modifier = Modifier.padding(top = 2.dp),
                                    )
                                }
                                r.conflictKey?.let { key ->
                                    Text(
                                        text = when (key) {
                                            "lt_prev" -> stringResource(R.string.koil_msg_mileage_lt_prev, r.conflictArg)
                                            "gt_next" -> stringResource(R.string.koil_msg_mileage_gt_next, r.conflictArg)
                                            else -> key
                                        },
                                        fontSize = KOiLType.Caption,
                                        color = KOiLColors.AttentionDay,
                                        modifier = Modifier.padding(top = 2.dp),
                                    )
                                }
                            }
                        }
                        if (rows.isEmpty()) {
                            Text(
                                text = stringResource(R.string.koil_import_no_data),
                                fontSize = KOiLType.BodySmall,
                                color = MiuixTheme.colorScheme.onBackgroundVariant,
                                modifier = Modifier.padding(vertical = 20.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    GlassDialogButton(
                        onClick = viewModel::dismissImportPreview,
                        modifier = Modifier.weight(1f),
                        role = GlassDialogButtonRole.Normal,
                    ) {
                        Text(
                            text = stringResource(R.string.koil_common_cancel),
                            style = MiuixTheme.textStyles.button,
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    GlassDialogButton(
                        onClick = viewModel::confirmImport,
                        modifier = Modifier.weight(1f),
                        role = GlassDialogButtonRole.Normal,
                    ) {
                        Text(
                            text = stringResource(R.string.koil_import_confirm, preview.valid.size),
                            style = MiuixTheme.textStyles.button,
                        )
                    }
                }
            }
        }
    }
}

/**
 * 导入预览页签条：复刻范围轨道的形状与手感。
 *
 * 与官方玻璃分段轨道的差别只有材质（这里用低透明底 + 细描边代替玻璃采样），
 * 形状、格位换算、指示块跟手与吸附的行为按同一套走：
 * - 点击某一格：指示块用弹簧动画滑到该格；
 * - 在条上左右拖：指示块中心跟着手指走，跨过半格就切页；
 * - 内容区被拖到别页时（selectedIndex 变化），指示块同样平滑跟过去。
 */
@Composable
private fun CapsuleTabRow(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    val capsule = RoundedCornerShape(percent = 50)
    val colors = MiuixTheme.colorScheme
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    var trackWidth by remember { mutableIntStateOf(0) }
    // 这条轨道没有格间距，一格的宽高比就是轨道内宽等分
    val slot = if (trackWidth <= 0) 0f else trackWidth.toFloat() / labels.size.coerceAtLeast(1)
    val indicatorLeft = remember { Animatable(0f) }
    val indicatorRight = remember { Animatable(0f) }
    var pressedIndex by remember { mutableIntStateOf(-1) }
    var dragging by remember { mutableStateOf(false) }
    var settleGeneration by remember { mutableIntStateOf(0) }
    val onSelectState = rememberUpdatedState(onSelect)
    val scope = rememberCoroutineScope()

    // 左右两条边各跑一条 spring：前缘与尾缘用不同曲线，所以拖动时胶囊会先伸后收
    fun animateIndicator(
        newLeft: Float,
        newRight: Float,
        leftSpring: SpringSpec<Float> = GlassMotion.edgeSpring(newLeft <= indicatorLeft.value),
        rightSpring: SpringSpec<Float> = GlassMotion.edgeSpring(newLeft > indicatorLeft.value),
    ) {
        if (indicatorLeft.targetValue == newLeft && indicatorRight.targetValue == newRight) return
        scope.launch { indicatorLeft.animateTo(newLeft, leftSpring) }
        scope.launch { indicatorRight.animateTo(newRight, rightSpring) }
    }

    LaunchedEffect(selectedIndex, settleGeneration, slot) {
        if (dragging || slot <= 0f) return@LaunchedEffect
        val targetLeft = selectedIndex * slot
        if (indicatorRight.value <= indicatorLeft.value) {
            indicatorLeft.snapTo(targetLeft)
            indicatorRight.snapTo(targetLeft + slot)
        } else {
            animateIndicator(targetLeft, targetLeft + slot)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(capsule)
            // 轨道：实色卡上 35% surfaceVariant 是一层浅灰，铺到采样玻璃上（浅色主题
            // 下 surfaceVariant 接近纯白）会变成一条白杠；玻璃面换成 onSurface 的极低 alpha。
            .background(
                if (LocalOnGlassSurface.current) {
                    colors.onSurface.copy(alpha = 0.05f)
                } else {
                    colors.surfaceVariant.copy(alpha = 0.35f)
                },
            )
            .border(1.dp, colors.onSurface.copy(alpha = 0.08f), capsule)
            .padding(3.dp)
            .height(IntrinsicSize.Min)
            .onSizeChanged { trackWidth = it.width }
            // key 只留真正影响手势语义的两项：格数与本地方向。轨道宽度不进 key——
            // 字体缩放等导致宽度变化时，拖动逻辑每帧自己读 size.width，不该重启手势、
            // 把正在拖的那一下丢掉。
            .pointerInput(labels.size, layoutDirection) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val width = size.width.toFloat()
                    val currentSlot = width / labels.size.coerceAtLeast(1)
                    if (width <= 0f || currentSlot <= 0f) return@awaitEachGesture
                    fun logicalX(x: Float): Float = if (layoutDirection == LayoutDirection.Rtl) width - x else x
                    fun indexAt(x: Float): Int = (x / currentSlot).toInt().coerceIn(0, labels.lastIndex)

                    val downX = logicalX(down.position.x)
                    var selectionX = downX
                    var current = indexAt(downX)
                    // 按下即把胶囊收到按下那一格，再开始跟手
                    pressedIndex = current
                    dragging = true
                    val pressedLeft = current * currentSlot
                    animateIndicator(pressedLeft, pressedLeft + currentSlot)
                    var grabOffset = Float.NaN
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (change.isConsumed) break
                            if (!change.pressed) {
                                if (change.changedToUp()) {
                                    val releaseDelta = change.position.x - change.previousPosition.x
                                    selectionX += if (layoutDirection == LayoutDirection.Rtl) {
                                        -releaseDelta
                                    } else {
                                        releaseDelta
                                    }
                                    onSelectState.value(indexAt(selectionX))
                                    change.consume()
                                }
                                break
                            }
                            val activeWidth = size.width.toFloat()
                            val activeSlot = activeWidth / labels.size.coerceAtLeast(1)
                            val maxLeft = (activeWidth - activeSlot).coerceAtLeast(0f)
                            val x = logicalX(change.position.x)
                            val pointerDelta = change.position.x - change.previousPosition.x
                            val delta = if (layoutDirection == LayoutDirection.Rtl) -pointerDelta else pointerDelta
                            selectionX += delta
                            if (grabOffset.isNaN()) {
                                if (abs(selectionX - downX) < viewConfiguration.touchSlop) {
                                    pressedIndex = indexAt(selectionX)
                                    continue
                                }
                                grabOffset = downX - pressedLeft
                            }
                            val chipLeft = (x - grabOffset).coerceIn(0f, maxLeft)
                            val next = indexAt(selectionX)
                            val target = dragTarget(
                                left = chipLeft,
                                width = activeSlot,
                                minLeft = 0f,
                                maxRight = activeWidth,
                                delta = delta,
                                changedItem = next != current,
                            )
                            animateIndicator(
                                target.left,
                                target.right,
                                target.leftSpring,
                                target.rightSpring,
                            )
                            current = next
                            pressedIndex = next
                            change.consume()
                        }
                    } finally {
                        pressedIndex = -1
                        dragging = false
                        settleGeneration++
                    }
                }
            },
    ) {
        // 胶囊本体：位置与宽度都由左右两条 Animatable 决定
        if (slot > 0f) {
            val leftPx = indicatorLeft.value.coerceIn(0f, (trackWidth - slot).coerceAtLeast(0f))
            val rightPx = indicatorRight.value.coerceIn(leftPx, trackWidth.toFloat())
            val pressed = pressedIndex >= 0
            Box(
                modifier = Modifier
                    .offset { IntOffset(leftPx.roundToInt(), 0) }
                    .width(with(density) { (rightPx - leftPx).toDp() })
                    .fillMaxHeight()
                    .clip(capsule)
                    .background(
                        colors.onSurface.copy(alpha = if (pressed) 0.22f else 0.14f)
                    ),
            )
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            labels.forEachIndexed { idx, label ->
                val selected = idx == selectedIndex
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(capsule)
                        .clickable { onSelectState.value(idx) }
                        .padding(horizontal = 4.dp, vertical = 7.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        fontSize = KOiLType.Caption,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        color = if (selected) {
                            colors.onSurface
                        } else {
                            colors.onBackgroundVariant
                        },
                    )
                }
            }
        }
    }
}

/** 拖动目标的左右边与各自弹簧：跨格时直接覆盖目标格，未跨格时按位移拉出拖尾。 */
private class TabDragTarget(
    val left: Float,
    val right: Float,
    val following: Boolean,
    val movingRight: Boolean,
) {
    val leftSpring: SpringSpec<Float>
        get() = if (following) GlassMotion.navDragFollow() else GlassMotion.edgeSpring(!movingRight)
    val rightSpring: SpringSpec<Float>
        get() = if (following) GlassMotion.navDragFollow() else GlassMotion.edgeSpring(movingRight)
}

private fun dragTarget(
    left: Float,
    width: Float,
    minLeft: Float,
    maxRight: Float,
    delta: Float,
    changedItem: Boolean,
): TabDragTarget {
    val start = left.coerceIn(minLeft, (maxRight - width).coerceAtLeast(minLeft))
    val end = start + width
    // 未跨格时尾缘最多拖出 60px 的拖尾；跨格时直接覆盖到目标格，不做拉伸
    val trail = if (changedItem) 0f else (abs(delta) * 4f).coerceAtMost(60f)
    return TabDragTarget(
        left = if (delta > 0f && end < maxRight) start - trail else start,
        right = if (delta < 0f && start > minLeft) end + trail else end,
        following = !changedItem,
        movingRight = delta > 0f,
    )
}
