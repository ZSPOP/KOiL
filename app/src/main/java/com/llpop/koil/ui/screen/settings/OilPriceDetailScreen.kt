package com.llpop.koil.ui.screen.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.llpop.koil.R
import com.llpop.koil.ui.component.KOiLBlurBackground
import com.llpop.koil.ui.component.KOiLScreenScaffold
import com.llpop.koil.ui.theme.KOiLColors
import org.koin.androidx.compose.koinViewModel
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 油价上调色（涨红） / 下调色（跌绿） */
private val UpColor = Color(0xFFE53935)
private val DownColor = Color(0xFF43A047)

/**
 * 油价明细页：省份/油号切换 → 今年累计涨跌汇总 + 价格走势图 + 调价时间线。
 * 由设置页「油價數據庫」卡片入口进入；返回手势/返回按钮回到设置页。
 */
@Composable
fun OilPriceDetailScreen(
    onBack: () -> Unit,
    viewModel: OilPriceDetailViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    BackHandler(onBack = onBack)

    KOiLScreenScaffold(
        title = stringResource(R.string.pdb_detail_title),
    ) { _, topPadding, _, _ ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
        ) {
            Spacer(Modifier.height(topPadding))

            // —— 返回行 ——
            Row(
                modifier = Modifier
                    .clickable(onClick = onBack)
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.onBackgroundVariant,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = stringResource(R.string.pdb_back),
                    fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                )
            }
            Spacer(Modifier.height(8.dp))

            // —— 省份选择（横向滚动 chips）——
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.provinces) { p ->
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
                        text = stringResource(R.string.pdb_detail_empty),
                        fontSize = 14.sp,
                        color = MiuixTheme.colorScheme.onBackgroundVariant,
                    )
                }
                return@Column
            }

            // —— 涨跌汇总卡 ——
            KOiLBlurBackground(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 20f,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                ) {
                    SummaryCell(
                        modifier = Modifier.weight(1f),
                        label = stringResource(R.string.pdb_rise_total),
                        value = "+¥%.2f/L".format(state.riseTotal),
                        valueColor = UpColor,
                    )
                    SummaryCell(
                        modifier = Modifier.weight(1f),
                        label = stringResource(R.string.pdb_fall_total),
                        value = "-¥%.2f/L".format(state.fallTotal),
                        valueColor = DownColor,
                    )
                    SummaryCell(
                        modifier = Modifier.weight(1f),
                        label = stringResource(R.string.pdb_latest_price),
                        value = state.latestPrice?.let { "¥%.2f".format(it) } ?: "--",
                        valueColor = MiuixTheme.colorScheme.onBackground,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))

            // —— 价格走势图 ——
            PriceTrendCanvas(items = state.items)
            Spacer(Modifier.height(8.dp))

            // —— 调价时间线 ——
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(state.items, key = { it.date }) { item ->
                    AdjustTimelineRow(item)
                }
            }
        }
    }
}

/** 省份选择 chip */
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

/** 价格走势折线图（横轴=调价次序，纵轴=价格） */
@Composable
private fun PriceTrendCanvas(items: List<PriceAdjustItem>) {
    val gridColor = MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.1f)
    val labelColor = MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.4f)
    val lineColor = KOiLColors.Primary
    val prices = items.map { it.price }
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp),
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
        // 首尾日期标签
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
            drawText(items.first().label, padL, size.height - 6.dp.toPx(), paint)
            val lastLabel = items.last().label
            val lastX = xAt(prices.size - 1)
            drawText(lastLabel, lastX, size.height - 6.dp.toPx(), paint)
        }
    }
}

/** 调价时间线单行：日期 · 价格 · 涨跌 badge */
@Composable
private fun AdjustTimelineRow(item: PriceAdjustItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = item.label,
            fontSize = 14.sp,
            color = MiuixTheme.colorScheme.onBackground,
            modifier = Modifier.width(56.dp),
        )
        Text(
            text = "¥%.2f".format(item.price),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = MiuixTheme.colorScheme.onBackground,
            modifier = Modifier.width(88.dp),
        )
        val diff = item.diff
        when {
            diff == null -> {
                Text(
                    text = stringResource(R.string.pdb_first_base),
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                )
            }
            diff > 0.001 -> {
                Text(
                    text = stringResource(R.string.pdb_up, diff),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = UpColor,
                )
            }
            diff < -0.001 -> {
                Text(
                    text = stringResource(R.string.pdb_down, -diff),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = DownColor,
                )
            }
            else -> {
                Text(
                    text = stringResource(R.string.pdb_flat),
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                )
            }
        }
    }
}
