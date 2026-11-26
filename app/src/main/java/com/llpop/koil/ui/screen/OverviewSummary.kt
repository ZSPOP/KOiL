package com.llpop.koil.ui.screen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Money
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.llpop.koil.R
import com.llpop.koil.ui.component.KOiLHorizontalDivider
import com.llpop.koil.domain.logic.NumberFormat
import com.llpop.koil.ui.screen.overview.OverviewUiState
import com.llpop.koil.ui.theme.KOiLColors
import kotlin.math.abs
import top.yukonga.miuix.kmp.basic.Icon
import com.llpop.koil.ui.component.LocalWallpaperBackdrop
import com.llpop.koil.ui.component.blur.LocalBlurEnabled
import com.llpop.koil.ui.component.blur.rememberEmptyBackdrop
import com.llpop.koil.ui.component.effect.KOiLGlassEdgeTier
import com.llpop.koil.ui.component.effect.rememberEffectiveGlassSpec
import com.llpop.koil.ui.component.effect.rememberKOiLGlassEdgeStroke
import com.llpop.koil.ui.theme.LocalKOiLDarkTheme
import top.yukonga.miuix.kmp.glass.GlassSegmentedTabRow
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.llpop.koil.ui.theme.KOiLType
import com.llpop.koil.ui.component.KOiLSegmentedTabRow



// ===== 六指标 2×2 网格（对齐 Web） =====
@Composable
internal fun SummaryGrid(
    uiState: OverviewUiState,
    rangeLabels: List<Pair<String, String>>,
    onRangeChange: (String) -> Unit,
    // 无异常时不单占一整张卡，收成卡片底部一行小字（null = 有异常，不显示）
    stableNote: String? = null,
) {
    val stats = uiState.stats
    val prev = uiState.prevStats
    fun pct(cur: Double, prevVal: Double?): Double? =
        if (prevVal != null && prevVal > 0) (cur - prevVal) / prevVal * 100 else null

    // 分三段：范围筛选器 / 指标网格 / 走势与汇总（段间不留分割线，靠内距区分）
        Column(modifier = Modifier.padding(16.dp)) {
            // 范围筛选器用官方 GlassSegmentedTabRow（一条轨道 + 轨道内指示块滑动）
            val rangeIndex = rangeLabels.indexOfFirst { it.first == uiState.range }.coerceAtLeast(0)
            val onRangeSelect: (Int) -> Unit = { idx -> onRangeChange(rangeLabels[idx].first) }
            // 全 App 唯一分段控件（KOiLSegmentedTabRow）：材质、描边、薄纱兜底与
            // 指示块的滑动/跟手/按压动画都收在组件里，三处调用必然同一套观感
            KOiLSegmentedTabRow(
                tabs = rangeLabels.map { it.second },
                selectedIndex = rangeIndex,
                onSelect = onRangeSelect,
            )
            Spacer(Modifier.height(14.dp))
        }
        Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp)) {
            // —— 六指标 3×2（字号统一 21sp；里程/油量涨幅不判好坏用中性灰）——
            Row(modifier = Modifier.fillMaxWidth()) {
                StatCell(
                    Modifier.weight(1f),
                    icon = Icons.Filled.WaterDrop,
                    // 无满箱锚点时退化为「长期平均」，标签必须带标识，不能冒充区间平均
                    label = if (stats.longTermAvg) stringResource(R.string.koil_ov_long_term_avg)
                    else stringResource(R.string.koil_stat_avg_consumption),
                    value = NumberFormat.measure(stats.avgOilConsumption ?: 0.0),
                    unit = "L/100km",
                    comparePct = pct(stats.avgOilConsumption ?: 0.0, prev?.avgOilConsumption),
                )
                Spacer(Modifier.width(12.dp))
                StatCell(
                    Modifier.weight(1f),
                    icon = Icons.Filled.Money,
                    // 无满箱锚点时该值同样是长期平均兜底（总油/总里程），标签必须带标识，不能冒充区间值
                    label = if (stats.longTermAvg) stringResource(R.string.koil_ov_long_term_avg)
                    else stringResource(R.string.koil_stat_cost_per_km),
                    value = NumberFormat.money(stats.avgCostPerKm ?: 0.0),
                    unit = stringResource(R.string.koil_stat_cost_unit),
                    comparePct = pct(stats.avgCostPerKm ?: 0.0, prev?.avgCostPerKm),
                )
                Spacer(Modifier.width(12.dp))
                StatCell(
                    Modifier.weight(1f),
                    icon = Icons.Filled.Speed,
                    label = stringResource(R.string.koil_stat_total_mileage),
                    value = NumberFormat.grouped(stats.totalDistance),
                    unit = "km",
                    comparePct = pct(stats.totalDistance, prev?.totalDistance),
                    // 里程增长不代表变差：中性灰，只显示变化幅度
                    neutral = true,
                )
            }
            Spacer(Modifier.height(14.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                StatCell(
                    Modifier.weight(1f),
                    icon = Icons.Filled.LocalGasStation,
                    label = stringResource(R.string.koil_stat_total_oil),
                    value = NumberFormat.grouped(stats.totalOilAmount),
                    unit = "L",
                    comparePct = pct(stats.totalOilAmount, prev?.totalOilAmount),
                    neutral = true,
                )
                Spacer(Modifier.width(12.dp))
                StatCell(
                    Modifier.weight(1f),
                    icon = Icons.Filled.Payments,
                    label = stringResource(R.string.koil_stat_total_cost),
                    value = "¥" + NumberFormat.grouped(stats.totalActual),
                    unit = "",
                    comparePct = pct(stats.totalActual, prev?.totalActual),
                )
                Spacer(Modifier.width(12.dp))
                // 总应付信息量低（与总实付几乎同值），用平均油价（含环比，可看油价走势）
                StatCell(
                    Modifier.weight(1f),
                    icon = Icons.Filled.Receipt,
                    label = stringResource(R.string.koil_stat_avg_price),
                    value = NumberFormat.measure(stats.avgUnitPriceActual ?: 0.0),
                    unit = stringResource(R.string.koil_stat_price_unit_long),
                    comparePct = pct(stats.avgUnitPriceActual ?: 0.0, prev?.avgUnitPriceActual),
                )
            }
        }
        Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
            // 指标区与走势区用淡分割线分组
            KOiLHorizontalDivider(Modifier.padding(top = 10.dp, bottom = 10.dp))
            // 不设「實付 vs 應付 对比条」——按用户要求不显示节省提示
            // 油耗走势 sparkline（不设独立趋势卡，无坐标轴无图例，只表达形状）
            val sparkValues = remember(uiState.trend) {
                // 短途闭合区间（<100km）单条失真风险大：趋势中隐藏（总账仍计入，见定稿）
                uiState.trend.mapNotNull { if (it.lowConfidence) null else it.oilConsumption }.takeLast(40)
            }
            if (sparkValues.size >= 2) {
                // 补参照——只给末值看不出当前算高还是低；给出样本数与区间
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.koil_ov_spark_label),
                        fontSize = KOiLType.CaptionLarge,
                        color = MiuixTheme.colorScheme.onBackgroundVariant,
                        maxLines = 1,
                        softWrap = false,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(
                            R.string.koil_ov_spark_range,
                            sparkValues.size,
                            NumberFormat.fixed2(sparkValues.min()),
                            NumberFormat.fixed2(sparkValues.max()),
                        ),
                        fontSize = KOiLType.Micro,
                        color = MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.7f),
                        maxLines = 1,
                        softWrap = false,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = NumberFormat.fixed2(sparkValues.last()),
                        fontSize = KOiLType.BodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MiuixTheme.colorScheme.onBackground,
                        maxLines = 1,
                        softWrap = false,
                    )
                    Spacer(Modifier.width(2.dp))
                    Text(
                        text = "L/100km",
                        fontSize = KOiLType.Micro,
                        color = MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.7f),
                        maxLines = 1,
                        softWrap = false,
                    )
                }
                Spacer(Modifier.height(6.dp))
                ConsumptionSparkline(
                    values = sparkValues,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(34.dp),
                )
                Spacer(Modifier.height(10.dp))
            }
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = pluralStringResource(R.plurals.koil_ov_total_count, stats.totalCount, stats.totalCount),
                    fontSize = KOiLType.BodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = stringResource(
                        R.string.koil_ov_avg_distance_line,
                        (stats.avgDistancePerReoil ?: 0.0).toInt(),
                    ),
                    fontSize = KOiLType.CaptionLarge,
                    color = MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.7f),
                )
            }
            // 无异常 → 「暂无异常」不单占整卡，收成这一行（省约 80dp）
            if (stableNote != null) {
                KOiLHorizontalDivider(Modifier.padding(vertical = 8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(KOiLColors.Primary),
                    )
                    Spacer(Modifier.width(7.dp))
                    Text(
                        text = stringResource(R.string.koil_ov_stable_inline),
                        fontSize = KOiLType.CaptionLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MiuixTheme.colorScheme.onBackground,
                        maxLines = 1,
                        softWrap = false,
                    )
                    Text(
                        text = " · $stableNote",
                        fontSize = KOiLType.CaptionLarge,
                        color = MiuixTheme.colorScheme.onBackgroundVariant,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
        }
}

@Composable
internal fun StatCell(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    value: String,
    unit: String,
    comparePct: Double? = null,
    // 里程/油量这类「涨了不代表变差」的指标不判好坏，只显示幅度（中性灰）
    neutral: Boolean = false,
) {
    // 六个指标统一 21sp（避免字号差异误导重要性）；深色主题用亮色变体
    val darkBg = MiuixTheme.colorScheme.background.luminance() < 0.5f
    val upColor = if (darkBg) KOiLColors.AttentionNight else KOiLColors.AttentionDay
    val downColor = if (darkBg) KOiLColors.GoodNight else KOiLColors.GoodDay
    // 单元格内容水平居中——start 对齐会让三列内容都挤在格子左侧、
    // 右侧留白约 20% 屏宽，视觉整体偏左；居中后三列视觉完全平衡
    Column(modifier = modifier.padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = KOiLColors.Primary.copy(alpha = 0.85f),
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = label,
                fontSize = KOiLType.CaptionLarge,
                color = MiuixTheme.colorScheme.onBackgroundVariant,
            )
        }
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = value,
                // 六个指标统一 21sp（不用 24/19sp 混排）
                fontSize = KOiLType.DisplayLarge,
                fontWeight = FontWeight.Bold,
                color = MiuixTheme.colorScheme.onBackground,
                maxLines = 1,
                softWrap = false,
            )
            if (unit.isNotBlank()) {
                Spacer(Modifier.width(2.dp))
                Text(
                    text = unit,
                    fontSize = KOiLType.Caption,
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                    modifier = Modifier.padding(bottom = 3.dp),
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
        comparePct?.let { pct ->
            Spacer(Modifier.height(2.dp))
            if (abs(pct) >= 0.5) {
                val up = pct > 0
                Text(
                    text = "${if (up) "↑" else "↓"}${NumberFormat.pct(abs(pct))}%",
                    fontSize = KOiLType.Caption,
                    fontWeight = FontWeight.Medium,
                    color = when {
                        neutral -> MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.75f)
                        up -> upColor
                        else -> downColor
                    },
                )
            } else {
                // 「较上期」→「持平」，变化不足 0.5% 时说清是没变化，而不是让人猜在跟谁比
                Text(
                    text = stringResource(R.string.koil_ov_flat),
                    fontSize = KOiLType.Caption,
                    color = MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.5f),
                )
            }
        }
    }
}

/**
 * 摘要卡内的油耗 sparkline。
 * 只画折线 + 末点，不画坐标轴与网格——趋势的形状用一条线就能表达，
 * 不用双轴折线图（油耗 + 每公里成本同画布）：两套量纲不可比、轴标签 9sp 也读不清。
 */
@Composable
internal fun ConsumptionSparkline(values: List<Double>, modifier: Modifier = Modifier) {
    val lineColor = KOiLColors.Primary
    Canvas(modifier = modifier) {
        if (values.size < 2) return@Canvas
        val minV = values.min()
        val maxV = values.max()
        val span = (maxV - minV).takeIf { it > 0.0001 } ?: 1.0
        val inset = 3.dp.toPx()
        val usableH = (size.height - inset * 2).coerceAtLeast(1f)
        val stepX = size.width / (values.size - 1)
        val pts = values.mapIndexed { i, v ->
            Offset(stepX * i, inset + usableH - ((v - minV) / span).toFloat() * usableH)
        }
        val path = Path().apply {
            moveTo(pts.first().x, pts.first().y)
            for (i in 1 until pts.size) lineTo(pts[i].x, pts[i].y)
        }
        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(
                width = 1.6.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )
        drawCircle(color = lineColor, radius = 2.4.dp.toPx(), center = pts.last())
    }
}

@Composable
internal fun AbnormalAlertCard(
    count: Int,
    peakConsumption: Double?,
    latestConsumption: Double?,
    onClick: () -> Unit,
) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = null,
                tint = KOiLColors.AttentionDay,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = pluralStringResource(R.plurals.koil_ov_abnormal_count, count, count),
                    fontSize = KOiLType.BodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = MiuixTheme.colorScheme.onBackground,
                )
                // 这里用异常里最高/最近两次而非「平均油耗」（后者跟摘要卡同一个数），才是新增信息
                if (peakConsumption != null && latestConsumption != null) {
                    Text(
                        text = stringResource(
                            R.string.koil_ov_abnormal_peak,
                            NumberFormat.fixed2(peakConsumption),
                            NumberFormat.fixed2(latestConsumption),
                        ),
                        fontSize = KOiLType.CaptionLarge,
                        color = MiuixTheme.colorScheme.onBackgroundVariant,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            // 右侧不用「范围 + 条数」两行（跟顶部筛选器、摘要卡全重复），用明确的点击入口
            Text(
                text = stringResource(R.string.koil_ov_detail_go) + " ›",
                fontSize = KOiLType.CaptionXLarge,
                fontWeight = FontWeight.Medium,
                color = KOiLColors.Primary,
                maxLines = 1,
                softWrap = false,
            )
        }
}
