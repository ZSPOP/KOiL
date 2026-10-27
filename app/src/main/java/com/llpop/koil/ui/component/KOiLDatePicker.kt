package com.llpop.koil.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.llpop.koil.R
import com.llpop.koil.ui.theme.KOiLColors
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.ceil

/**
 * 自研日期选择弹窗（柔光玻璃风格，移植 Web 端 OilKCalendar 设计）。
 * - 锚定下拉弹出（AnchoredPopup）：紧贴日期字段下方，空间不足自动上翻，不被弹窗卡片遮挡
 * - 上下滑动切月（双卡轮播）：当前月跟手拖动，相邻月从对面露出，松手按位移+惯性切月，边界橡皮筋回弹
 * - 非当前月日期灰显（上月尾部 / 下月头部），点击自动跳转该月
 * - 今天：主色数字 + 底部主色小圆点；有加油记录的日期底部圆点标记
 * - 底部"今天"快捷按钮 + 取消 / 确定
 * - anchorBounds：锚点区域（字段框屏幕坐标：boundsInWindow + 窗口屏幕偏移），传 null 则锚定调用处
 */
@Composable
fun KOiLDatePickerDialog(
    initialDate: String,
    recordDates: Set<String> = emptySet(),
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    anchorBounds: IntRect? = null,
) {
    val initial = remember(initialDate) {
        runCatching { LocalDate.parse(initialDate) }.getOrDefault(LocalDate.now())
    }
    var selected by remember { mutableStateOf(initial) }
    var viewMonth by remember { mutableStateOf(YearMonth.from(initial)) }
    val today = LocalDate.now()
    val dateFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    // 三语文案
    val weekLabels = stringArrayResource(R.array.koil_cal_week)
    val monthFormat = stringResource(R.string.koil_cal_month)

    // 入场动画由 AnchoredPopup 统一驱动（miuix spring 缩放 + tween 淡入），此处不再自带动画

    // ===== 滑动切月：双卡轮播状态 =====
    val offsetY = remember { mutableFloatStateOf(0f) }       // 当前网格拖动偏移（px）
    val gridHeight = remember { mutableIntStateOf(0) }       // 网格实际高度（px）
    val switching = remember { mutableStateOf(false) }       // 是否正在做切月动画（禁止手势/点击）
    val dragActive = remember { mutableStateOf(false) }      // 是否按住拖动中
    val settleTarget = remember { mutableStateOf(0f) }       // 松手后目标偏移（px）：0=回弹，±H=切月
    val settleDelta = remember { mutableIntStateOf(0) }      // 松手后要切的月份方向
    val settleTick = remember { mutableIntStateOf(0) }       // 递增触发切月动画
    val settleVelocity = remember { mutableFloatStateOf(0f) } // 松手时速度绝对值（px/ms），用于切月动画时长
    val curAlpha = remember { mutableFloatStateOf(1f) }      // 当前层透明度（切月 1→0 / 边界橡皮筋最低 0.5）
    val prevVisible = remember { mutableStateOf(false) }     // 预览层是否渲染（拖动中或切月动画中）
    val dragDir = remember { mutableIntStateOf(0) }          // 手势锁定方向：+1=下月预览，-1=上月预览（首动定方向，避免拖动穿 0 点翻转闪烁）

    /** 构建某月网格：上月尾部 + 当月 + 下月头部（自然行数 5~6 行） */
    fun buildCells(ym: YearMonth): List<Pair<LocalDate, Boolean>> {
        val firstWeekday = ym.atDay(1).dayOfWeek.value % 7 // 周日=0
        val total = ym.lengthOfMonth()
        val rows = ceil((firstWeekday + total) / 7.0).toInt()
        val cells = ArrayList<Pair<LocalDate, Boolean>>(rows * 7)
        val prevMonth = ym.minusMonths(1)
        val prevTotal = prevMonth.lengthOfMonth()
        for (i in 0 until firstWeekday) {
            cells.add(prevMonth.atDay(prevTotal - firstWeekday + 1 + i) to true)
        }
        for (d in 1..total) cells.add(ym.atDay(d) to false)
        val tail = rows * 7 - firstWeekday - total
        val nextMonth = ym.plusMonths(1)
        for (i in 1..tail) cells.add(nextMonth.atDay(i) to true)
        return cells
    }

    val curCells = remember(viewMonth) { buildCells(viewMonth) }

    // 未来月份不可达（对齐 Web max=今天）：当前视图月 == 今天所在月时禁止再切下月
    val todayMonth = YearMonth.from(today)
    val canNextMonth = viewMonth < todayMonth
    // v1.5.16：去掉回退 120 个月（10 年）下限——补录允许任意早日期，"早于第一条记录"由业务校验拦截
    val canPrevMonth = true

    /** 切换月份（切月动画完成后的真实切换 / 箭头点击直接切换） */
    fun goMonth(delta: Int) {
        viewMonth = viewMonth.plusMonths(delta.toLong())
    }

    // 松手处理：切月走"直接切换"（与左右箭头一致：一帧完成，无滑出/滑入动画——
    // 上下滑动若保留双卡滑动动画，松手后数字会表现为"闪一下有动画效果"），
    // 拖动中的跟手与预览层保留；仅未达标/边界走回弹（320ms 过冲 + 透明度 280ms ease）
    val easeOpacity = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f) // CSS ease（回弹透明度用）
    LaunchedEffect(settleTick.intValue) {
        if (settleTick.intValue == 0) return@LaunchedEffect
        val start = offsetY.floatValue
        // 回弹：320ms 过冲弹性 + 透明度 280ms ease（仅当前层回位，预览层已在手势协程立即隐藏）
        val ease = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)
        coroutineScope {
            launch {
                animate(start, 0f, animationSpec = tween(durationMillis = 320, easing = ease)) { v, _ ->
                    offsetY.floatValue = v
                }
            }
            launch {
                animate(curAlpha.floatValue, 1f, animationSpec = tween(durationMillis = 280, easing = easeOpacity)) { v, _ ->
                    curAlpha.floatValue = v
                }
            }
        }
        offsetY.floatValue = 0f
        switching.value = false
    }

    // 弹窗根级锚定下拉：注册到 KOiLGlassDialog 的 overlay（窗口坐标定位）
    rememberAnchoredPopupRegistration(
        show = true,
        id = "koil-date-picker",
        anchorBounds = anchorBounds,
        minWidth = 300.dp,
        maxHeight = 480.dp,
        onDismiss = onDismiss,
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 320.dp),
        ) {
            // 面板背景由 AnchoredPopupHost 的 Dropdown 容器统一提供（单层），
            // 此处不再套 blur，避免"两层"玻璃叠加观感
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
                    // ===== 顶部：月份切换（箭头） =====
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .clickable(enabled = !switching.value && canPrevMonth) { goMonth(-1) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.KeyboardArrowLeft,
                                contentDescription = null,
                                tint = if (canPrevMonth) MiuixTheme.colorScheme.onBackground
                                else MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.35f),
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = String.format(monthFormat, viewMonth.year, viewMonth.monthValue),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = MiuixTheme.colorScheme.onBackground,
                        )
                        Spacer(Modifier.weight(1f))
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .clickable(enabled = !switching.value && canNextMonth) { goMonth(1) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = if (canNextMonth) MiuixTheme.colorScheme.onBackground
                                else MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.35f),
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    // ===== 星期标题 =====
                    Row(modifier = Modifier.fillMaxWidth()) {
                        weekLabels.forEach { w ->
                            Box(
                                modifier = Modifier.weight(1f),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = w,
                                    fontSize = 12.sp,
                                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    // ===== 日期网格（双卡轮播容器） =====
                    // 卡片背景用实色（非 blur）：承载 graphicsLayer translationY 的层内不可放
                    // backdrop-filter 元素（柔光玻璃纪律：transform 与 backdrop-filter 冲突）
                    // 高度策略：拖动/切月动画中容器高度直接取"目标月行数"——预览层与容器同高、
                    // 全程不被裁剪、复位时高度已就位无过渡抖动；空闲时跟随当前月行数（5~6 行）
                    val cellH = 44.dp
                    val cardBg = MiuixTheme.colorScheme.background
                    val gridRows = curCells.size / 7
                    val previewDelta = when {
                        // 预览层移除前始终跟随 settleDelta（含复位延后移除的一帧），避免预览层换边瞬移
                        prevVisible.value && settleDelta.intValue != 0 -> settleDelta.intValue
                        switching.value && settleDelta.intValue != 0 -> settleDelta.intValue
                        dragDir.intValue != 0 -> dragDir.intValue
                        else -> if (offsetY.floatValue < 0) 1 else -1
                    }
                    val previewRows = remember(viewMonth, previewDelta) {
                        if (previewDelta != 0) buildCells(viewMonth.plusMonths(previewDelta.toLong())).size / 7 else gridRows
                    }
                    val effRows = if ((dragActive.value || prevVisible.value) && previewDelta != 0) previewRows else gridRows
                    val targetGridH = cellH * effRows + 2.dp * (effRows - 1)
                    val gridH by animateDpAsState(
                        targetValue = targetGridH,
                        animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(gridH)
                            .clipToBounds() // 对齐 Web overflow:hidden：切月内容仅在网格区域（黄色框）内切换，不盖住星期标题/月份标题
                            .onSizeChanged { gridHeight.intValue = it.height }
                            .pointerInput(viewMonth, switching.value) {
                                if (switching.value) return@pointerInput
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    // 忽略点击箭头等控件（顶部区域不在网格内，无需过滤）
                                    var totalDy = 0f
                                    var velocity = 0f
                                    var lastY = down.position.y
                                    var lastT = java.lang.System.nanoTime()
                                    dragActive.value = true
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val change = event.changes.firstOrNull() ?: break
                                        if (change.positionChanged()) {
                                            val dy = change.position.y - lastY
                                            totalDy += dy
                                            // 首动锁定方向（+1=上滑→下月预览，-1=下滑→上月预览），整场手势不随 offsetY 变号翻转
                                            if (dragDir.intValue == 0 && dy != 0f) {
                                                dragDir.intValue = if (dy > 0) -1 else 1
                                            }
                                            val now = java.lang.System.nanoTime()
                                            val dt = (now - lastT) / 1_000_000f
                                            // 速度平滑 v = v×0.65 + inst×0.35
                                            if (dt > 0) velocity = velocity * 0.65f + (dy / dt) * 0.35f
                                            lastY = change.position.y
                                            lastT = now
                                            // 边界橡皮筋：方向不可达时阻尼 0.25 + 最大位移 ±70px
                                            val boundary = (totalDy < 0 && !canNextMonth) || (totalDy > 0 && !canPrevMonth)
                                            offsetY.floatValue = if (boundary) (totalDy * 0.25f).coerceIn(-70f, 70f) else totalDy
                                            // 边界时当前层最低透明度 0.5，非边界 1
                                            curAlpha.floatValue = if (boundary) {
                                                (1f - abs(offsetY.floatValue) / 70f * 0.5f).coerceIn(0.5f, 1f)
                                            } else 1f
                                        }
                                        if (change.changedToUpIgnoreConsumed()) break
                                    }
                                    dragActive.value = false
                                    settleVelocity.floatValue = abs(velocity)
                                    val H = gridHeight.intValue.coerceAtLeast(1)
                                    // fling = clamp(v×200, ±140)
                                    val fling = (velocity * 200f).coerceIn(-140f, 140f)
                                    // 方向一致性判定：只按"首动方向 dragDir"的净位移/净速度判定。
                                    // 拖动中预览层锁定 dragDir 不换边；若松手按 final 方向判 delta 而 dragDir 相反，
                                    // 动画启动瞬间预览层会跨屏瞬移（跳 2H）造成"一头闪"。
                                    // 顺 dragDir 方向时净值为负：-net ≥ 阈值才切月，反向拖动一律回弹不切。
                                    val netTotal = (totalDy + fling) * dragDir.intValue
                                    val netDy = totalDy * dragDir.intValue
                                    val ok = (-netTotal >= 60f) || (-netDy >= 55f)
                                    var delta = if (ok) dragDir.intValue else 0
                                    if (delta < 0 && !canPrevMonth) delta = 0
                                    if (delta > 0 && !canNextMonth) delta = 0
                                    if (delta != 0) {
                                        // 切月 = 直接切换（与左右箭头一致）：一帧完成，
                                        // 无滑出/滑入动画 —— 杜绝松手后数字"闪一下有动画效果"
                                        switching.value = true
                                        goMonth(delta)
                                        offsetY.floatValue = 0f
                                        curAlpha.floatValue = 1f
                                        prevVisible.value = false
                                        dragDir.intValue = 0
                                        switching.value = false
                                    } else {
                                        // 未达标/边界：回弹。立即隐藏预览层（对齐 Web：bg 立即隐藏），
                                        // 仅当前层回弹；动画在外部 LaunchedEffect 执行
                                        prevVisible.value = false
                                        dragDir.intValue = 0
                                        switching.value = true
                                        settleDelta.intValue = 0
                                        settleTarget.value = 0f
                                        settleTick.intValue++
                                    }
                                }
                            },
                        contentAlignment = Alignment.TopStart,
                    ) {
                        // 相邻月预览层：按拖动方向从对面露出（上拖=下月从下方，下拖=上月从上方）
                        // 方向：拖动中取手势锁定 dragDir（避免穿 0 点翻转闪烁）；直接切换路径
                        // 在松手帧同步移除预览层，无需 settleDelta 方向
                        // 未来月份不预览（对齐 Web max=今天 边界橡皮筋）；zIndex 0 在底层
                        // previewDelta 已在容器高度计算处定义，此处复用
                        val previewCanGo = if (previewDelta > 0) canNextMonth else canPrevMonth
                        // 拖动中预览层需可达方向；直接切换帧（prevVisible=true）预览层无条件渲染
                        // （仅回弹路径会短暂置 true？无——直接切换同步移除，此处保留防御条件）
                        val showPreview = (dragActive.value || prevVisible.value) &&
                            (prevVisible.value || (previewCanGo && abs(offsetY.floatValue) > 6f))
                        if (showPreview) {
                            val previewMonth = viewMonth.plusMonths(previewDelta.toLong())
                            val previewCells = remember(previewMonth) { buildCells(previewMonth) }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .zIndex(0f)
                                    .graphicsLayer {
                                        // 预览层全程不透明（对齐 Web：背景月无透明度动画）
                                        translationY = (gridHeight.intValue * previewDelta) + offsetY.floatValue
                                    },
                            ) {
                                CalendarCard(
                                    modifier = Modifier.fillMaxWidth(),
                                    background = cardBg,
                                ) {
                                    CalendarGrid(
                                        cells = previewCells,
                                        cellHeight = cellH,
                                        selected = selected,
                                        today = today,
                                        recordDates = recordDates,
                                        dateFmt = dateFmt,
                                        monthOfGrid = previewMonth,
                                        maxDate = today,
                                        onPick = { d -> onConfirm(d.format(dateFmt)) },
                                    )
                                }
                            }
                        }
                        // 当前月层（跟手拖动）：zIndex 1 盖住预览层，滑开才露出下方卡片
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .zIndex(1f)
                                .graphicsLayer {
                                    translationY = offsetY.floatValue
                                }
                                .alpha(curAlpha.floatValue),
                        ) {
                            CalendarCard(
                                modifier = Modifier.fillMaxWidth(),
                                background = cardBg,
                            ) {
                                CalendarGrid(
                                    cells = curCells,
                                    cellHeight = cellH,
                                    selected = selected,
                                    today = today,
                                    recordDates = recordDates,
                                    dateFmt = dateFmt,
                                    monthOfGrid = viewMonth,
                                    maxDate = today,
                                    onPick = { d -> onConfirm(d.format(dateFmt)) },
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    // 回到今天快捷按钮：切到今天所在月并选中今天
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "今天",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = MiuixTheme.colorScheme.primary,
                            modifier = Modifier.clickable {
                                viewMonth = todayMonth
                                selected = today
                            },
                        )
                    }
                }
        }
    }
}

/** 单个月份卡片：实色圆角底，承载网格，供双卡轮播层使用（避免数字悬浮叠加） */
@Composable
private fun CalendarCard(
    modifier: Modifier = Modifier,
    background: Color,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(background),
    ) {
        content()
    }
}

/** 单个月份网格（周日起首、非当月灰显、未来禁选、今天底部主色点、记录底部圆点） */
@Composable
private fun CalendarGrid(
    cells: List<Pair<LocalDate, Boolean>>,
    cellHeight: androidx.compose.ui.unit.Dp,
    selected: LocalDate,
    today: LocalDate,
    recordDates: Set<String>,
    dateFmt: DateTimeFormatter,
    monthOfGrid: YearMonth,
    maxDate: LocalDate,
    onPick: (LocalDate) -> Unit,
) {
    val rows = cells.size / 7
    Column {
        for (r in 0 until rows) {
            Row(modifier = Modifier.fillMaxWidth()) {
                for (c in 0 until 7) {
                    val (date, isGhost) = cells[r * 7 + c]
                    val isSelected = date == selected
                    val isToday = date == today
                    val hasRecord = recordDates.contains(date.format(dateFmt))
                    val isFuture = date.isAfter(maxDate)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(cellHeight),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(if (isSelected) KOiLColors.Primary else Color.Transparent)
                                .clickable(enabled = !isFuture) { onPick(date) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = date.dayOfMonth.toString(),
                                fontSize = 14.sp,
                                fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                                color = when {
                                    isSelected -> Color.White
                                    isFuture -> MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.30f)
                                    isGhost -> MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.35f)
                                    isToday -> KOiLColors.Primary
                                    else -> MiuixTheme.colorScheme.onBackground
                                },
                            )
                        }
                        // 底部标记：今天点（主色）+ 记录点（主色），并排；选中时不显示
                        if (!isSelected && (isToday || hasRecord)) {
                            Row(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 3.dp),
                                horizontalArrangement = Arrangement.Center,
                            ) {
                                if (isToday) {
                                    Box(
                                        modifier = Modifier
                                            .size(4.dp)
                                            .clip(CircleShape)
                                            .background(KOiLColors.Primary),
                                    )
                                }
                                if (hasRecord) {
                                    if (isToday) Spacer(Modifier.width(2.dp))
                                    Box(
                                        modifier = Modifier
                                            .size(3.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (isToday) KOiLColors.Primary.copy(alpha = 0.45f)
                                                else KOiLColors.Primary
                                            ),
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(2.dp))
        }
    }
}
