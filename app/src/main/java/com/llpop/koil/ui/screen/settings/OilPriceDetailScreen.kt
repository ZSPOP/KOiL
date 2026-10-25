package com.llpop.koil.ui.screen.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.llpop.koil.R
import com.llpop.koil.ui.component.KOiLBlurBackground
import com.llpop.koil.ui.component.KOiLScreenScaffold
import com.llpop.koil.ui.theme.KOiLColors
import kotlin.math.roundToInt
import org.koin.androidx.compose.koinViewModel
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.theme.MiuixTheme

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
    val state by viewModel.uiState.collectAsState()
    BackHandler(onBack = onBack)
    val surfaceColor = MiuixTheme.colorScheme.surface

    KOiLScreenScaffold(
        title = stringResource(R.string.koil_pdb_detail_title),
        navigationIcon = {
            Icon(
                imageVector = MiuixIcons.ChevronBackward,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.onBackgroundVariant,
                modifier = Modifier.clickable(onClick = onBack).padding(8.dp).size(18.dp),
            )
        },
    ) { _, topPadding, scrollBehavior, _ ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .padding(horizontal = 12.dp),
        ) {
            Spacer(Modifier.height(topPadding))
            Spacer(Modifier.height(4.dp))

            // —— 省份选择（横向滑动 chips + 右侧渐隐/箭头提示）——
            // 提示画在 LazyRow 自身的 drawWithContent 绘制层：只画不拦截触摸，
            // 滑动/点击手势始终落在 chips 上（此前用兄弟节点覆盖会挡触摸）。
            val provinceListState = rememberLazyListState()
            var provinceInitialScroll by remember { mutableStateOf(true) }
            LaunchedEffect(state.province) {
                if (provinceInitialScroll) {
                    val idx = state.provinces.indexOf(state.province)
                    if (idx >= 0) provinceListState.animateScrollToItem(idx)
                    provinceInitialScroll = false
                }
            }
            val provinceHintColor = MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.55f)
            LazyRow(
                state = provinceListState,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(end = 44.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .drawWithContent {
                        drawContent()
                        // 右侧渐隐遮罩
                        val gradW = size.width * 0.28f
                        drawRect(
                            brush = Brush.horizontalGradient(
                                0f to Color.Transparent,
                                1f to surfaceColor,
                                startX = size.width - gradW,
                                endX = size.width,
                            ),
                        )
                        // 半透明右箭头提示
                        val cx = size.width - 12.dp.toPx()
                        val cy = size.height / 2f
                        val s = 4.5.dp.toPx()
                        val arrowPath = Path().apply {
                            moveTo(cx - s, cy - s)
                            lineTo(cx + s * 0.6f, cy)
                            lineTo(cx - s, cy + s)
                            close()
                        }
                        drawPath(
                            path = arrowPath,
                            color = provinceHintColor,
                        )
                    },
            ) {
                items(state.provinces, key = { it }) { p ->
                    ProvinceChip(
                        label = p,
                        selected = p == state.province,
                        onClick = { viewModel.selectProvince(p) },
                    )
                }
            }
            Spacer(Modifier.height(10.dp))

            // —— 油号选择 ——
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.grades.forEach { g ->
                    GradeChip(
                        label = g,
                        selected = g == state.grade,
                        onClick = { viewModel.selectGrade(g) },
                    )
                }
            }
            Spacer(Modifier.height(12.dp))

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
                return@Column
            }

            // —— 涨跌汇总卡（卡片容器是 Box 布局，多个子项必须包 Column 防止重叠）——
            KOiLBlurBackground(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 20f,
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // —— 第一行：最新价格（前）+ 今年净涨跌（后）——
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        SummaryCell(
                            modifier = Modifier.weight(1f),
                            label = stringResource(R.string.koil_pdb_latest_price),
                            value = state.latestPrice?.let { "¥%.2f".format(it) } ?: "--",
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

            // —— 价格走势图 ——
            PriceTrendCanvas(items = state.items)
            Spacer(Modifier.height(8.dp))

            // —— 调价时间线（最新在上；底部留足底栏高度避免遮挡）——
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    bottom = (bottomPadding + 64.dp).coerceAtLeast(88.dp),
                ),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(state.items.asReversed(), key = { it.date }) { item ->
                    AdjustTimelineRow(item)
                }
            }
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
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = valueColor,
        )
    }
}

/** 价格走势折线图（横轴=调价次序，纵轴=价格）；点击数据点显示日期+价格 */
@Composable
private fun PriceTrendCanvas(items: List<PriceAdjustItem>) {
    val gridColor = MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.1f)
    val labelColor = MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.4f)
    val lineColor = KOiLColors.Primary
    val bubbleBg = MiuixTheme.colorScheme.surfaceContainer
    val bubbleBorder = MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.25f)
    val dotInnerColor = MiuixTheme.colorScheme.surface
    val onBgColor = MiuixTheme.colorScheme.onBackground
    val prices = items.map { it.price }
    var selectedIndex by remember { mutableStateOf<Int?>(null) }
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp)
            .pointerInput(prices.size) {
                fun nearestIndex(px: Float): Int? {
                    if (prices.size < 2) return if (prices.size == 1) 0 else null
                    val padLpx = 38.dp.toPx()
                    val wPx = size.width - padLpx - 12.dp.toPx()
                    val step = wPx / (prices.size - 1)
                    return ((px - padLpx) / step).roundToInt().coerceIn(0, prices.size - 1)
                }
                // 滑动跟随：手指在曲线上拖动时实时显示该点日期+价格；松手保留最后查看的点
                detectDragGestures(
                    onDragStart = { pos -> selectedIndex = nearestIndex(pos.x) },
                    onDragCancel = { selectedIndex = null },
                ) { change, _ ->
                    change.consume()
                    selectedIndex = nearestIndex(change.position.x)
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
                    textSize = 9.dp.toPx()
                }
                drawText("%.2f".format(pv), 2.dp.toPx(), y + 3.dp.toPx(), paint)
            }
        }

        fun xAt(i: Int): Float = padL + (i.toFloat() / (prices.size - 1).coerceAtLeast(1)) * w
        fun yAt(v: Double): Float = padT + h - (((v - lo) / range).toFloat() * h)

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
            val bubbleText = "${items[sel].label}  ¥%.2f".format(items[sel].price)
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
                textSize = 9.dp.toPx()
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

/** 调价时间线单行：日期（左灰）· 动作+幅度（中）· 价格（右大号），底部细分隔线 */
@Composable
private fun AdjustTimelineRow(item: PriceAdjustItem) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = item.label,
                fontSize = 13.sp,
                color = MiuixTheme.colorScheme.onBackgroundVariant,
                modifier = Modifier.width(56.dp),
            )
            Spacer(Modifier.weight(1f))
            // 中间：动作 + 幅度
            val diff = item.diff
            val actionText = when {
                diff == null -> stringResource(R.string.koil_pdb_first_base)
                diff > 0.001 -> stringResource(R.string.koil_pdb_up, diff)
                diff < -0.001 -> stringResource(R.string.koil_pdb_down, -diff)
                else -> stringResource(R.string.koil_pdb_flat)
            }
            val actionColor = when {
                diff == null -> MiuixTheme.colorScheme.onBackgroundVariant
                diff > 0.001 -> UpColor
                diff < -0.001 -> DownColor
                else -> MiuixTheme.colorScheme.onBackgroundVariant
            }
            Text(
                text = actionText,
                fontSize = 12.sp,
                fontWeight = if (diff != null) FontWeight.SemiBold else FontWeight.Normal,
                color = actionColor,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "¥%.2f".format(item.price),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = MiuixTheme.colorScheme.onBackground,
            )
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
