package com.llpop.koil.ui.screen.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.llpop.koil.R
import com.llpop.koil.domain.logic.NumberFormat
import com.llpop.koil.ui.component.KOiLBlurBackground
import com.llpop.koil.ui.component.KOiLScreenScaffold
import com.llpop.koil.ui.component.gradeLabel
import com.llpop.koil.ui.component.provinceLabel
import com.llpop.koil.ui.theme.KOiLColors
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

    KOiLScreenScaffold(
        title = stringResource(R.string.koil_pdb_detail_title),
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp),
        ) {
            Spacer(Modifier.height(topPadding))
            Spacer(Modifier.height(4.dp))

            // —— 省份选择（横向滑动 chips）——
            // 用最朴素的基础 horizontalScroll（非 LazyRow）：排除懒加载/绘制层/兄弟覆盖
            // 一切可能干扰手势的因素，保证横向滑动必定生效；进入时估算滚动到当前省。
            val provinceScrollState = rememberScrollState()
            var provinceInitialScroll by remember { mutableStateOf(true) }
            val density = LocalDensity.current
            LaunchedEffect(state.province) {
                if (provinceInitialScroll) {
                    val idx = state.provinces.indexOf(state.province)
                    if (idx >= 0) {
                        // chip 宽约 52dp（2-3 字）+ 间距 8dp
                        val offsetPx = with(density) { idx * 60.dp.roundToPx() }
                        provinceScrollState.scrollTo(offsetPx)
                    }
                    provinceInitialScroll = false
                }
            }
            // 省份选择：左右箭头（提示可滑动 + 点击切换，边界变灰）+ 横向滑动 chips
            val curIdx = state.provinces.indexOf(state.province)
            val arrowOn = KOiLColors.Primary
            val arrowOff = MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.25f)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = "上一个省份",
                    tint = if (curIdx > 0) arrowOn else arrowOff,
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .clickable(enabled = curIdx > 0) { viewModel.selectPrevProvince() },
                )
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(provinceScrollState),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    state.provinces.forEach { p ->
                        ProvinceChip(
                            // 数据键是中文省名（DB 主键），这里只换显示名
                            label = provinceLabel(p),
                            selected = p == state.province,
                            onClick = { viewModel.selectProvince(p) },
                        )
                    }
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "下一个省份",
                    tint = if (curIdx < state.provinces.size - 1) arrowOn else arrowOff,
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .clickable(enabled = curIdx < state.provinces.size - 1) { viewModel.selectNextProvince() },
                )
            }
            Spacer(Modifier.height(10.dp))

            // —— 油号选择 ——
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.grades.forEach { g ->
                    GradeChip(
                        label = gradeLabel(g),
                        selected = g == state.grade,
                        onClick = { viewModel.selectGrade(g) },
                    )
                }
            }
            Spacer(Modifier.height(10.dp))

            // —— 年份选择（v1.4.191 跨年维度：默认当年，跨年后当年从零开始、可切回看往年）——
            if (state.years.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.years.forEach { y ->
                        GradeChip(
                            label = stringResource(R.string.koil_detail_year, y),
                            selected = y == state.year,
                            onClick = { viewModel.selectYear(y) },
                        )
                    }
                }
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
                // 表头：日期 价格 涨跌幅（三列左对齐，与数据行列对齐）
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.koil_pdb_col_date),
                        fontSize = 11.sp,
                        color = MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.7f),
                        modifier = Modifier.width(56.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.koil_pdb_col_price),
                        fontSize = 11.sp,
                        color = MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.7f),
                        modifier = Modifier.width(70.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.koil_pdb_col_change),
                        fontSize = 11.sp,
                        color = MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.7f),
                    )
                }
                state.items.asReversed().forEach { row ->
                    AdjustTimelineRow(row)
                }
            }
            Spacer(Modifier.height(bottomPadding + 24.dp))
        }
    }
}

/** 省份选择 chip（横向滑动） */
@Composable
private fun ProvinceChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) KOiLColors.Primary else MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.08f)
    val fg = if (selected) Color.White else MiuixTheme.colorScheme.onBackground
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(text = label, fontSize = 12.sp, color = fg, fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal)
    }
}

/** 油号选择 chip */
@Composable
private fun GradeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) KOiLColors.Primary else MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.08f)
    val fg = if (selected) Color.White else MiuixTheme.colorScheme.onBackground
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
    ) {
        Text(text = label, fontSize = 12.sp, color = fg, fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal)
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

/** 调价时间线单行（三列紧凑 + 第4列幅度条）：
 *  日期(左) · 价格(左) · 涨跌幅(chip,左) · 幅度条(长度=调价幅度/1元基准，涨红跌绿)；
 *  搁浅行：日期/价格/标签全灰，chip 显示「擱淺」，尾部显示「維持 X 價格」（无幅度条） */
@Composable
private fun AdjustTimelineRow(item: PriceAdjustItem) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 列1：日期（固定宽，左对齐；搁浅行加深灰）
            Text(
                text = item.label,
                fontSize = 12.sp,
                color = if (item.isStranded) {
                    MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.55f)
                } else {
                    MiuixTheme.colorScheme.onBackgroundVariant
                },
                modifier = Modifier.width(56.dp),
            )
            Spacer(Modifier.width(8.dp))
            // 列2：价格（固定宽，左对齐；搁浅行灰色显示维持价）
            Text(
                text = "¥${NumberFormat.measure(item.price)}",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (item.isStranded) {
                    MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.7f)
                } else {
                    MiuixTheme.colorScheme.onBackground
                },
                modifier = Modifier.width(70.dp),
            )
            Spacer(Modifier.width(8.dp))
            // 列3：涨跌幅 chip（箭头 + 文字）；搁浅 = 灰色「擱淺」chip
            if (item.isStranded) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.08f))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.koil_detail_stranded),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MiuixTheme.colorScheme.onBackgroundVariant,
                    )
                }
            } else {
                ActionChip(diff = item.diff)
            }
            Spacer(Modifier.width(8.dp))
            // 列4：调价 → 幅度条（长度正比于调价幅度，1 元为满条，最短 6% 保证可见，涨红跌绿）；
            //       搁浅 → 「維持 09-25 價格」说明（无幅度条）
            if (item.isStranded) {
                Text(
                    text = stringResource(R.string.koil_detail_held_price, item.keptFrom ?: stringResource(R.string.koil_detail_last_year)),
                    fontSize = 11.sp,
                    color = MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.7f),
                    modifier = Modifier.weight(1f),
                )
            } else {
                val diff = item.diff
                val barColor = when {
                    diff == null -> null
                    diff > 0.001 -> UpColor
                    diff < -0.001 -> DownColor
                    else -> null
                }
                if (barColor != null) {
                    val frac = (kotlin.math.abs(diff!!).toFloat() / 1f).coerceIn(0.06f, 1f)
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(frac)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(barColor.copy(alpha = 0.8f)),
                        )
                    }
                } else {
                    // 基準價/持平：无幅度条，占位列保持对齐
                    Spacer(Modifier.weight(1f))
                }
            }
        }
        // 行间细分隔线
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(0.5.dp)
                .background(MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.06f)),
        )
    }
}

/** 動作 chip：淡红/淡绿圆角底 + 方向箭头（↑红/↓绿）+ 文字；基準價/持平为灰色无箭头 */
@Composable
private fun ActionChip(diff: Double?) {
    val neutralBg = MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.08f)
    val neutralFg = MiuixTheme.colorScheme.onBackgroundVariant
    val (bgColor, fgColor, arrow) = when {
        diff == null -> Triple(neutralBg, neutralFg, null)
        diff > 0.001 -> Triple(UpColor.copy(alpha = 0.12f), UpColor, "↑")
        diff < -0.001 -> Triple(DownColor.copy(alpha = 0.12f), DownColor, "↓")
        else -> Triple(neutralBg, neutralFg, null)
    }
    val actionText = when {
        diff == null -> stringResource(R.string.koil_pdb_first_base)
        diff > 0.001 -> stringResource(R.string.koil_pdb_up, diff)
        diff < -0.001 -> stringResource(R.string.koil_pdb_down, -diff)
        else -> stringResource(R.string.koil_pdb_flat)
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (arrow != null) {
            Text(
                text = arrow,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = fgColor,
            )
            Spacer(Modifier.width(3.dp))
        }
        Text(
            text = actionText,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = fgColor,
        )
    }
}
