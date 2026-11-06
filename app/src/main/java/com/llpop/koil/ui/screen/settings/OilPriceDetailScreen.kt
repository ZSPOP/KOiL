package com.llpop.koil.ui.screen.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.llpop.koil.R
import com.llpop.koil.domain.logic.NumberFormat
import com.llpop.koil.ui.component.AnchoredDropdownColumn
import com.llpop.koil.ui.component.AnchoredDropdownItemRow
import com.llpop.koil.ui.component.AnchoredPopup
import com.llpop.koil.ui.component.KOiLBlurBackground
import com.llpop.koil.ui.component.KOiLScreenScaffold
import com.llpop.koil.ui.component.gradeLabel
import com.llpop.koil.ui.component.provinceLabel
import com.llpop.koil.ui.theme.KOiLColors
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import org.koin.androidx.compose.koinViewModel
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.util.Locale

/** 油价上调色（涨红） / 下调色（跌绿） */
private val UpColor = Color(0xFFE53935)
private val DownColor = Color(0xFF43A047)

/**
 * 油价明细页：省份（下拉）/油号切换 → 今年累计涨跌汇总 + 价格走势图 + 调价时间线。
 * 由设置页「油價數據庫」卡片入口进入；返回手势/返回按钮回到设置页。
 */
@Composable
fun OilPriceDetailScreen(
    onBack: () -> Unit,
    bottomPadding: androidx.compose.ui.unit.Dp = 0.dp,
    viewModel: OilPriceDetailViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    BackHandler(onBack = onBack)
    val surfaceColor = MiuixTheme.colorScheme.surface

    // v1.6.3：筛选上下文进顶栏副标题——滚到历史记录区后仍能看到「省 · 油号 · 年份」。
    // 用 TopAppBar 原生 subtitle：展开时在大标题下方、折叠后自动居中到小标题下方，
    // 不额外占一行、不需要浮层，也不会和上方筛选控件形成两条并列的重复横条。
    val filterSummary = buildList {
        add(provinceLabel(state.province))
        add(gradeLabel(state.grade))
        // 年份 pill 无数据时不渲染（见筛选区），副标题同步省略，保持两处一致
        if (state.years.isNotEmpty()) {
            add(stringResource(R.string.koil_detail_year, state.year))
        }
    }.joinToString(" · ")

    val scrollState = rememberScrollState()

    KOiLScreenScaffold(
        title = stringResource(R.string.koil_pdb_detail_title),
        subtitle = filterSummary,
        navigationIcon = {
            Icon(
                imageVector = MiuixIcons.Back,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.onBackground,
                modifier = Modifier
                    // indication=null：去掉点击时的方形选中色卡（仅保留响应，无背景高亮）
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onBack,
                    )
                    .padding(8.dp)
                    .size(24.dp),
            )
        },
    ) { _, topPadding, scrollBehavior, _ ->
        // —— 明细页整体滚动容器（Column + verticalScroll，对齐一级页：整页滚动、顶栏随滚动折叠）——
        // 时间线仅 20 条左右一次性渲染，避开 LazyColumn 懒加载层在纹理卡片/手势下的兼容性风险
        Column(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .verticalScroll(scrollState)
                .padding(horizontal = 12.dp),
        ) {
            Spacer(Modifier.height(topPadding))
            Spacer(Modifier.height(4.dp))

            // —— v1.6.3（UX 方案 A）筛选区重排——
            // 行 1：省份 / 年份收进下拉 pill（31 省、多年份是高基数项，原先各占一行横滑 chips
            //       + 左右箭头，筛选区顶占大半屏且 31 省里定位难）；下拉用自研 AnchoredPopup，
            //       视觉/动效对齐 miuix Dropdown，与设置页省份选择一致。
            // 行 2：油号分段控件——固定 4 项（92/95/98/0）整行等宽分段，不再横滑。
            var provinceMenuExpanded by remember { mutableStateOf(false) }
            var yearMenuExpanded by remember { mutableStateOf(false) }
            // v1.6.3fix：点击已展开的 pill 时，Popup 先因「外部点击」触发 onDismissRequest 收起，
            // 同一击又落到 pill 的 toggle 上 → 立刻重新展开，表现为「收起又弹开」闪烁。
            // 记录 dismiss 时间戳，紧随其后（350ms 内）的 pill 点击视为同一次手势、不再展开。
            var provinceDismissAt by remember { mutableStateOf(0L) }
            var yearDismissAt by remember { mutableStateOf(0L) }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                // 省份下拉 pill（含展开浮层，Popup 锚定在 pill 所在 Box）
                // map 是 inline lambda，保留组合上下文，provinceLabel（@Composable）可直接调用
                val provinceItems = state.provinces.map { provinceLabel(it) }
                Box {
                    DropdownPill(
                        label = provinceLabel(state.province),
                        expanded = provinceMenuExpanded,
                        onToggle = {
                            val justDismissed = provinceDismissAt > 0 &&
                                System.currentTimeMillis() - provinceDismissAt < 350
                            provinceDismissAt = 0L
                            if (!justDismissed) provinceMenuExpanded = !provinceMenuExpanded
                        },
                    )
                    AnchoredPopup(
                        expanded = provinceMenuExpanded,
                        onDismissRequest = {
                            provinceMenuExpanded = false
                            provinceDismissAt = System.currentTimeMillis()
                        },
                        maxHeight = 380.dp,
                    ) {
                        AnchoredDropdownColumn {
                            provinceItems.forEachIndexed { idx, name ->
                                AnchoredDropdownItemRow(
                                    text = name,
                                    selected = state.provinces[idx] == state.province,
                                    isFirst = idx == 0,
                                    isLast = idx == provinceItems.lastIndex,
                                    onClick = {
                                        provinceMenuExpanded = false
                                        // 数据键是中文省名（DB 主键），这里只换显示名
                                        viewModel.selectProvince(state.provinces[idx])
                                    },
                                )
                            }
                        }
                    }
                }
                // 年份下拉 pill（无年份数据时占位，保持省份 pill 左对齐语义）
                if (state.years.isNotEmpty()) {
                    Box {
                        DropdownPill(
                            label = stringResource(R.string.koil_detail_year, state.year),
                            expanded = yearMenuExpanded,
                            onToggle = {
                                val justDismissed = yearDismissAt > 0 &&
                                    System.currentTimeMillis() - yearDismissAt < 350
                                yearDismissAt = 0L
                                if (!justDismissed) yearMenuExpanded = !yearMenuExpanded
                            },
                        )
                        AnchoredPopup(
                            expanded = yearMenuExpanded,
                            onDismissRequest = {
                                yearMenuExpanded = false
                                yearDismissAt = System.currentTimeMillis()
                            },
                        ) {
                            AnchoredDropdownColumn {
                                state.years.forEachIndexed { idx, y ->
                                    AnchoredDropdownItemRow(
                                        text = stringResource(R.string.koil_detail_year, y),
                                        selected = y == state.year,
                                        isFirst = idx == 0,
                                        isLast = idx == state.years.lastIndex,
                                        onClick = {
                                            yearMenuExpanded = false
                                            viewModel.selectYear(y)
                                        },
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Spacer(Modifier.width(1.dp))
                }
            }
            Spacer(Modifier.height(10.dp))

            // —— 油号分段控件（v1.6.3：固定 4 项整行等宽，替代横滑 chips；选中项蓝底白字）——
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.08f))
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                state.grades.forEach { g ->
                    val selected = g == state.grade
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(9.dp))
                            .background(if (selected) KOiLColors.Primary else Color.Transparent)
                            .clickable { viewModel.selectGrade(g) }
                            .padding(vertical = 9.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = gradeLabel(g),
                            fontSize = 12.sp,
                            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                            color = if (selected) Color.White else MiuixTheme.colorScheme.onBackground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            if (state.years.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
            } else {
                Spacer(Modifier.height(2.dp))
            }

            if (state.items.isEmpty()) {
                // —— 空态 ——
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 60.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.koil_pdb_detail_empty),
                        fontSize = 14.sp,
                        color = MiuixTheme.colorScheme.onBackgroundVariant,
                    )
                }
            } else {
                // —— 涨跌汇总卡（卡片容器是 Box 布局，多个子项必须包 Column 防止重叠）——
                KOiLBlurBackground(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 20f,
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // —— 第一行：最新价格（前，大号突出）+ 今年净涨跌（后）——
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                        ) {
                            SummaryCell(
                                modifier = Modifier.weight(1f),
                                label = stringResource(R.string.koil_pdb_latest_price),
                                value = state.latestPrice?.let { "¥${NumberFormat.measure(it)}" } ?: "--",
                                valueColor = MiuixTheme.colorScheme.onBackground,
                            )
                            SummaryCell(
                                modifier = Modifier.weight(1f),
                                label = stringResource(R.string.koil_pdb_net_change),
                                value = if (state.netChange >= 0) {
                                    stringResource(R.string.koil_pdb_net_up, state.netChange)
                                } else {
                                    stringResource(R.string.koil_pdb_net_down, -state.netChange)
                                },
                                valueColor = if (state.netChange >= 0) UpColor else DownColor,
                            )
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(0.5.dp)
                                .background(MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.12f))
                        )
                        // —— 第二行：上调次数 / 下调次数 / 搁浅次数 ——
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                        ) {
                            SummaryCell(
                                modifier = Modifier.weight(1f),
                                label = stringResource(R.string.koil_pdb_up_count),
                                value = stringResource(R.string.koil_pdb_count_times, state.upCount),
                                valueColor = UpColor,
                            )
                            SummaryCell(
                                modifier = Modifier.weight(1f),
                                label = stringResource(R.string.koil_pdb_down_count),
                                value = stringResource(R.string.koil_pdb_count_times, state.downCount),
                                valueColor = DownColor,
                            )
                            SummaryCell(
                                modifier = Modifier.weight(1f),
                                label = stringResource(R.string.koil_pdb_stranded_count),
                                value = stringResource(R.string.koil_pdb_count_times, state.strandedCount),
                                valueColor = MiuixTheme.colorScheme.onBackgroundVariant,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))

                // —— 价格走势图（套圆角卡片，与汇总卡视觉统一）——
                KOiLBlurBackground(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 20f,
                ) {
                    PriceTrendCanvas(
                        items = state.items,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                    )
                }
                Spacer(Modifier.height(8.dp))

                // —— 调价时间线（最新在上）——
                // v1.6.3 二次调整：左轨道保留（涨红/跌绿/搁浅空心灰 + 淡色竖线串起节奏），
                // 右侧每行补一条贯穿全宽的幅度导轨（彩段长度 = |幅度| / 区间最大幅度），
                // 解决上一版「只有两行文字、中间一大片空」的问题。
                val timeline = state.items.asReversed()
                // 归一化基准：当前筛选区间的最大单次幅度。取不到时退化为 1 元，
                // 避免全 0 导致除零（coerceAtLeast 兜底）。
                val maxAbsDiff = timeline.maxOfOrNull { kotlin.math.abs(it.diff ?: 0.0) }
                    ?.coerceAtLeast(0.01) ?: 1.0
                timeline.forEachIndexed { idx, row ->
                    AdjustTimelineItem(
                        item = row,
                        isLatest = idx == 0,
                        isLast = idx == timeline.lastIndex,
                        maxAbsDiff = maxAbsDiff,
                    )
                }
            }
            Spacer(Modifier.height(bottomPadding + 24.dp))
        }
    }
}

/**
 * v1.6.3：紧凑下拉 pill（筛选区行 1：省份 / 年份）。
 * 视觉对齐分段控件（surface 底 8% + 圆角 10dp），点击后由调用处的 AnchoredPopup 展开选择列表。
 */
@Composable
private fun DropdownPill(
    label: String,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.08f))
            .clickable(onClick = onToggle)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = MiuixTheme.colorScheme.onBackground,
            maxLines = 1,
            softWrap = false,
        )
        Spacer(Modifier.width(4.dp))
        Icon(
            imageVector = Icons.Filled.KeyboardArrowDown,
            contentDescription = null,
            tint = MiuixTheme.colorScheme.onBackgroundVariant,
            // 展开时箭头轻微上转，给出状态反馈
            modifier = Modifier
                .size(18.dp)
                .graphicsLayer { rotationZ = if (expanded) 180f else 0f },
        )
    }
}

/** 汇总单元格 */
@Composable
private fun SummaryCell(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    valueColor: Color,
    valueFontSize: TextUnit = 16.sp,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            fontSize = 11.sp,
            color = MiuixTheme.colorScheme.onBackgroundVariant,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = value,
            fontSize = valueFontSize,
            fontWeight = FontWeight.Bold,
            color = valueColor,
        )
    }
}

/** 价格走势折线图（横轴=调价次序，纵轴=价格）；点击数据点显示日期+价格 */
@Composable
private fun PriceTrendCanvas(items: List<PriceAdjustItem>, modifier: Modifier = Modifier) {
    val gridColor = MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.1f)
    val labelColor = MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.6f)
    val lineColor = KOiLColors.Primary
    val bubbleBg = MiuixTheme.colorScheme.surfaceContainer
    val bubbleBorder = MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.25f)
    val dotInnerColor = MiuixTheme.colorScheme.surface
    val onBgColor = MiuixTheme.colorScheme.onBackground
    val prices = items.map { it.price }
    var selectedIndex by remember { mutableStateOf<Int?>(null) }
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(170.dp)
            .pointerInput(prices.size) {
                fun nearestIndex(px: Float): Int? {
                    if (prices.size < 2) return if (prices.size == 1) 0 else null
                    val padLpx = 38.dp.toPx()
                    val wPx = size.width - padLpx - 12.dp.toPx()
                    val step = wPx / (prices.size - 1)
                    return ((px - padLpx) / step).roundToInt().coerceIn(0, prices.size - 1)
                }
                // 点击 + 横向拖动跟随：按住/横向拖动实时显示该点日期+价格；
                // 纵向拖动不消费，让给外层滚动容器正常滚页
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var dragging = false
                    val slop = viewConfiguration.touchSlop
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (change.isConsumed) break // 父级已消费（纵向滚动中）
                        val pos = change.position
                        if (!dragging) {
                            val dx = pos.x - down.position.x
                            val dy = pos.y - down.position.y
                            // 横向意图（|dx| >= |dy|）且越过 slop 才进入跟随
                            if (dx * dx + dy * dy >= slop * slop && kotlin.math.abs(dx) >= kotlin.math.abs(dy)) {
                                dragging = true
                                change.consume()
                                selectedIndex = nearestIndex(pos.x)
                            }
                        } else {
                            change.consume()
                            selectedIndex = nearestIndex(pos.x)
                        }
                        if (change.changedToUp()) {
                            if (!dragging) {
                                // 纯点击：显示该点
                                selectedIndex = nearestIndex(down.position.x)
                            }
                            break
                        }
                    }
                }
            },
    ) {
        if (prices.isEmpty()) return@Canvas
        val padL = 38.dp.toPx()
        val padR = 12.dp.toPx()
        val padT = 10.dp.toPx()
        val padB = 24.dp.toPx()
        val w = size.width - padL - padR
        val h = size.height - padT - padB

        val minP = prices.minOrNull() ?: 0.0
        val maxP = prices.maxOrNull() ?: 0.0
        val span = (maxP - minP).coerceAtLeast(0.1)
        // 上下留 15% 边距
        val lo = minP - span * 0.15
        val hi = maxP + span * 0.15
        val range = (hi - lo).coerceAtLeast(0.1)

        // 横向网格 + 价格刻度
        val gridLines = 4
        for (i in 0..gridLines) {
            val frac = i.toFloat() / gridLines
            val y = padT + h * frac
            drawLine(
                color = gridColor,
                start = Offset(padL, y),
                end = Offset(padL + w, y),
                strokeWidth = 1.dp.toPx(),
            )
            val pv = hi - range * frac
            drawContext.canvas.nativeCanvas.apply {
                val paint = android.graphics.Paint().apply {
                    color = android.graphics.Color.argb(
                        153,
                        (labelColor.red * 255).toInt(),
                        (labelColor.green * 255).toInt(),
                        (labelColor.blue * 255).toInt(),
                    )
                    textSize = 10.dp.toPx()
                }
                drawText(NumberFormat.measure(pv), 2.dp.toPx(), y + 3.dp.toPx(), paint)
            }
        }

        fun xAt(i: Int): Float = padL + (i.toFloat() / (prices.size - 1).coerceAtLeast(1)) * w
        fun yAt(v: Double): Float = padT + h - (((v - lo) / range).toFloat() * h)

        // 曲线下方面积渐变填充（提升图表可读性与专业观感）
        val fillPath = Path().apply {
            prices.forEachIndexed { i, v ->
                val x = xAt(i)
                val y = yAt(v)
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
            lineTo(xAt(prices.size - 1), padT + h)
            lineTo(xAt(0), padT + h)
            close()
        }
        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(lineColor.copy(alpha = 0.20f), lineColor.copy(alpha = 0.02f)),
                startY = padT,
                endY = padT + h,
            ),
        )

        // 折线
        val path = Path()
        prices.forEachIndexed { i, v ->
            val x = xAt(i)
            val y = yAt(v)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(
            path = path,
            color = lineColor,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()),
        )
        // 端点圆点
        drawCircle(color = lineColor, radius = 3.dp.toPx(), center = Offset(xAt(0), yAt(prices.first())))
        drawCircle(color = lineColor, radius = 4.dp.toPx(), center = Offset(xAt(prices.size - 1), yAt(prices.last())))

        // 选中点：竖虚线 + 放大圆点 + 气泡（日期 + 价格）
        val sel = selectedIndex
        if (sel != null && sel in prices.indices) {
            val sx = xAt(sel)
            val sy = yAt(prices[sel])
            drawLine(
                color = labelColor.copy(alpha = 0.8f),
                start = Offset(sx, padT),
                end = Offset(sx, size.height - padB),
                strokeWidth = 1.dp.toPx(),
            )
            drawCircle(color = lineColor, radius = 5.dp.toPx(), center = Offset(sx, sy))
            drawCircle(
                color = dotInnerColor,
                radius = 2.dp.toPx(),
                center = Offset(sx, sy),
            )
            // 气泡文字
            val bubbleText = "${items[sel].label}  ¥${NumberFormat.measure(items[sel].price)}"
            drawContext.canvas.nativeCanvas.apply {
                val paint = android.graphics.Paint().apply {
                    color = android.graphics.Color.argb(
                        255,
                        (onBgColor.red * 255).toInt(),
                        (onBgColor.green * 255).toInt(),
                        (onBgColor.blue * 255).toInt(),
                    )
                    textSize = 10.dp.toPx()
                    isAntiAlias = true
                }
                val textW = paint.measureText(bubbleText)
                val bubbleW = textW + 12.dp.toPx()
                val bubbleH = 20.dp.toPx()
                // 气泡优先放在点上方；上方空间不足则放下方（且不压日期刻度）
                val above = sy - bubbleH - 8.dp.toPx() > padT
                val bubbleY = if (above) {
                    sy - bubbleH - 8.dp.toPx()
                } else {
                    (sy + 10.dp.toPx()).coerceAtMost(size.height - padB - bubbleH)
                }
                val bubbleX = (sx - bubbleW / 2).coerceIn(padL, size.width - padR - bubbleW)
                // 画圆角矩形气泡
                drawRoundRect(
                    color = bubbleBg,
                    topLeft = Offset(bubbleX, bubbleY),
                    size = androidx.compose.ui.geometry.Size(bubbleW, bubbleH),
                    cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx()),
                )
                drawRoundRect(
                    color = bubbleBorder,
                    topLeft = Offset(bubbleX, bubbleY),
                    size = androidx.compose.ui.geometry.Size(bubbleW, bubbleH),
                    cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx()),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx()),
                )
                drawText(
                    bubbleText,
                    bubbleX + 6.dp.toPx(),
                    bubbleY + 14.dp.toPx(),
                    paint,
                )
            }
        }

        // 日期标签：首、尾 + 中间等距 2 个（点少时只画首尾，避免重叠）
        drawContext.canvas.nativeCanvas.apply {
            val paint = android.graphics.Paint().apply {
                color = android.graphics.Color.argb(
                    153,
                    (labelColor.red * 255).toInt(),
                    (labelColor.green * 255).toInt(),
                    (labelColor.blue * 255).toInt(),
                )
                textSize = 10.dp.toPx()
            }
            val n = prices.size
            val labelIdx = if (n >= 8) listOf(0, n / 4, n / 2, n * 3 / 4, n - 1).distinct() else listOf(0, n - 1)
            labelIdx.forEach { i ->
                val label = items[i].label
                val x = xAt(i)
                val textW = paint.measureText(label)
                // 首标签左对齐、尾标签右对齐、中间居中对齐，避免越界
                val drawX = when (i) {
                    0 -> padL
                    n - 1 -> (x - textW).coerceAtLeast(padL)
                    else -> (x - textW / 2).coerceIn(padL, padL + w - textW)
                }
                drawText(label, drawX, size.height - 6.dp.toPx(), paint)
            }
        }
    }
}

/**
 * 调价时间线单条（v1.6.3 二次调整：时间轴轨道 + 贯穿幅度导轨）。
 * 上一版只有「价格/日期」两行文字，轨道右侧中段完全没有别的东西，看着空；本版把原表格里
 * 那条「幅度条」请回来，改成贯穿整行宽度的导轨：
 * - 行 1：日期（左）—— 价格 · 涨跌额 · 百分比（右），两端对齐；
 * - 行 2：一条铺满整行宽度的浅灰导轨 + 彩色段（长度 = |幅度| / 区间最大幅度）+ 端点圆点。
 *   导轨始终被完整填满，行内不再有没人管的空白；搁浅/基准价只有灰轨 + 起点灰点（表达「没动」）。
 * 左轨道保留：节点按 涨红/跌绿/搁浅空心灰 着色，淡色竖线串起相邻节点，连续涨跌的节奏一眼可见。
 */
@Composable
private fun AdjustTimelineItem(
    item: PriceAdjustItem,
    isLatest: Boolean,
    isLast: Boolean,
    maxAbsDiff: Double,
) {
    val diff = item.diff
    val stranded = item.isStranded
    val accent: Color = when {
        stranded -> MiuixTheme.colorScheme.onBackgroundVariant
        diff != null && diff > 0.001 -> UpColor
        diff != null && diff < -0.001 -> DownColor
        else -> MiuixTheme.colorScheme.onBackgroundVariant
    }
    val trackColor = MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.15f)
    // 彩段占比：以当前筛选区间的最大幅度为满格 → 任何数据量级下条长都有可比性（不再固定 1 元满格
    // 那样全是很短的条）；非零幅度保底 3%，保证 0.01 元这种小调价也看得见。
    val absDiff = diff?.let { kotlin.math.abs(it) } ?: 0.0
    val hasBar = !stranded && absDiff > 0.001
    val barFrac = if (hasBar) (absDiff / maxAbsDiff).toFloat().coerceIn(0.03f, 1f) else 0f

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
    ) {
        // —— 轨道：竖线（最后一条不画）+ 节点 ——
        Box(
            modifier = Modifier
                .width(16.dp)
                .fillMaxHeight(),
        ) {
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .width(1.5.dp)
                        .fillMaxHeight()
                        .background(accent.copy(alpha = 0.2f)),
                )
            }
            val dotSize = if (isLatest) 10.dp else 8.dp
            val hollow = stranded || diff == null || kotlin.math.abs(diff) <= 0.001
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = if (isLatest) 9.dp else 6.dp)
                    .size(dotSize)
                    .then(
                        if (hollow) {
                            Modifier.border(BorderStroke(1.5.dp, accent.copy(alpha = 0.75f)), CircleShape)
                        } else {
                            Modifier.background(accent, CircleShape)
                        }
                    ),
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 10.dp, bottom = if (isLast) 0.dp else 12.dp),
        ) {
            // —— 行 1：日期（左） / 价格 · 涨跌 · 百分比（右）——
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = item.label,
                    fontSize = 11.sp,
                    color = MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.85f),
                )
                if (isLatest) {
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = stringResource(R.string.koil_detail_latest),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = KOiLColors.Primary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(5.dp))
                            .background(KOiLColors.Primary.copy(alpha = 0.12f))
                            .padding(horizontal = 4.dp, vertical = 1.dp),
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text = "¥${NumberFormat.measure(item.price)}",
                    fontSize = if (isLatest) 17.sp else 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (stranded) {
                        MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.7f)
                    } else {
                        MiuixTheme.colorScheme.onBackground
                    },
                )
                Spacer(Modifier.width(6.dp))
                // 右端：涨跌额 / 搁浅 / 基准价 / 持平
                when {
                    stranded -> Text(
                        text = stringResource(R.string.koil_detail_stranded),
                        fontSize = 11.sp,
                        color = MiuixTheme.colorScheme.onBackgroundVariant,
                    )

                    diff == null -> Text(
                        text = stringResource(R.string.koil_pdb_first_base),
                        fontSize = 11.sp,
                        color = MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.8f),
                    )

                    diff > 0.001 -> Text(
                        text = stringResource(R.string.koil_detail_delta_up, diff),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = UpColor,
                    )

                    diff < -0.001 -> Text(
                        text = stringResource(R.string.koil_detail_delta_down, -diff),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = DownColor,
                    )

                    else -> Text(
                        text = stringResource(R.string.koil_pdb_flat),
                        fontSize = 11.sp,
                        color = MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.8f),
                    )
                }
                val pct = item.pctChange
                if (pct != null) {
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = if (pct >= 0) {
                            stringResource(R.string.koil_detail_pct_up, NumberFormat.measure(pct * 100))
                        } else {
                            stringResource(R.string.koil_detail_pct_down, NumberFormat.measure(-pct * 100))
                        },
                        fontSize = 10.sp,
                        color = accent.copy(alpha = 0.9f),
                    )
                }
            }
            Spacer(Modifier.height(5.dp))
            // —— 行 2：贯穿导轨 ——
            // 灰轨铺满整行（不随幅度变化），彩段按幅度占比向左延伸，末端一个圆点收尾。
            // 搁浅/基准：只有灰轨 + 起点灰点，没有彩段——「这一轮没动」比文字更直观。
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.5.dp)
                        .clip(RoundedCornerShape(1.5.dp))
                        .background(trackColor),
                )
                if (hasBar) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(barFrac)
                            .height(8.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(2.5.dp)
                                .clip(RoundedCornerShape(1.5.dp))
                                .background(accent),
                        )
                        // 端点圆点（右缘对齐彩段末端；不裁剪，允许半个点压在轨道外沿）
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .size(7.dp)
                                .background(accent, CircleShape),
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(trackColor, CircleShape),
                    )
                }
            }
        }
    }
}


