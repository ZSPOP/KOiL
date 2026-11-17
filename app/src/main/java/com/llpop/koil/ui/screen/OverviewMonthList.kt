package com.llpop.koil.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.llpop.koil.R
import com.llpop.koil.ui.component.KOiLScreenScaffold
import com.llpop.koil.domain.logic.NumberFormat
import com.llpop.koil.domain.logic.RecordWithDerived
import com.llpop.koil.ui.theme.KOiLColors
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.llpop.koil.ui.theme.KOiLType



@Composable
internal fun monthTitle(monthKey: String): String {
    val parts = monthKey.split("-")
    val year = parts.getOrNull(0)?.toIntOrNull()
    val month = parts.getOrNull(1)?.toIntOrNull()
    return if (parts.size == 2 && year != null && month != null)
        stringResource(R.string.koil_ov_month_title, year, month)
    else monthKey
}

/** ④ 月度小结：「N条 · 共 ¥X · YL」（金额与油量为当月合计） */
@Composable
internal fun monthSummaryText(records: List<RecordWithDerived>): String {
    var totalAmount = 0.0
    var totalOil = 0.0
    for (rr in records) {
        totalAmount += rr.record.actualAmount
        totalOil += rr.record.oilAmount
    }
    return pluralStringResource(
        R.plurals.koil_ov_month_summary,
        records.size,
        records.size,
        NumberFormat.money(totalAmount),
        NumberFormat.fixed2(totalOil),
    )
}

/** 月份标题行（列表 item 与吸顶覆盖层共用，保证两处布局/字号/间距完全一致，切换无跳变） */
@Composable
internal fun MonthHeaderRow(monthKey: String, count: Int, summary: String?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = monthTitle(monthKey),
            fontSize = KOiLType.Title,
            fontWeight = FontWeight.Bold,
            color = MiuixTheme.colorScheme.onBackground,
            modifier = Modifier.padding(start = 4.dp),
        )
        Spacer(Modifier.weight(1f))
        Text(
            // ④ 有月度小结时替代纯计数
            text = summary ?: stringResource(R.string.koil_ov_month_count, count),
            fontSize = KOiLType.Body,
            fontWeight = FontWeight.Medium,
            color = MiuixTheme.colorScheme.onBackgroundVariant,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.padding(end = 4.dp),
        )
    }
}

/**
 * 计算"当前吸顶月份"（等价 Compose stickyHeader 钉住语义，回滚连续无空档）：
 * - 取所有可见月份标题中 offset ≤ 顶栏高度的**最后一个**为吸顶月份（新标题顶替旧标题）；
 * - 无标题在顶栏区时：
 *   - 无可见标题：列表已回到顶部（未进入历史区）或已滚过历史区末尾 → 取消吸顶；
 *     否则（吸顶标题滚出视口上方、下一标题未到）→ 保持吸顶；
 *   - 可见标题都在顶栏下方：吸顶标题自然位置回到顶栏（回滚松开）→ **立即接替为上一个月份标题**
 *     （此时它必已处于吸顶位，即使视口外），实现回滚时顶栏标题连续切换、无空档；
 *     吸顶标题已滚出视口上方（上滚中）→ 保持吸顶。
 * 覆盖层据此渲染顶栏同款毛玻璃标题；列表内对应标题行由各自 pinned 判定透明占位。
 *

 * 信号源与判定树：
 * 不采用 snapshotFlow { visibleItemsInfo }：每帧发射一次完整可见项快照（60Hz 滚动下每秒
 * 数十个 List + LazyListItemInfo 快照分配），collect 内还要 mapNotNull 出中间列表。
 * 当前实现：
 * - 流信号取 `firstVisibleItemIndex to firstVisibleItemScrollOffset`——snapshotFlow 按结构
 *   相等去重，静止时完全不发射；滚动时发射体只有两个装箱 int；
 * - visibleItemsInfo 在收集体内直读（它本身仍是每帧新对象，但只被消费、不进流），
 *   单趟循环同时得出 lastInTop / anyHeader / pinnedVisible，零中间列表分配；
 * - 判定分支仍是有状态的（"回滚接替"依赖上一个 pinned 值，纯 derivedStateOf 表达不了，
 *   强行无状态会改变回滚语义），所以保留 mutableStateOf + 写前比对：值不变不写入，
 *   订阅覆盖层不重组。
 */
@Composable
internal fun rememberTopPinnedMonth(
    listState: LazyListState,
    topPadding: Dp,
    monthKeys: List<String>,
): String? {
    val density = LocalDensity.current
    val topBarPx = with(density) { topPadding.toPx() }
    var pinned by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(listState, topBarPx, monthKeys) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collect {
                val visibleItems = listState.layoutInfo.visibleItemsInfo
                var lastInTopKey: String? = null
                var pinnedVisible = false
                var anyHeader = false
                for (info in visibleItems) {
                    val key = info.key
                    if (key is String && key.startsWith("header-")) {
                        anyHeader = true
                        val month = key.removePrefix("header-")
                        if (month == pinned) pinnedVisible = true
                        if (info.offset.toFloat() <= topBarPx + 2f) lastInTopKey = month
                    }
                }
                when {
                    // ① 顶栏区内有标题：最后一个顶替（正向滚动换月）
                    lastInTopKey != null -> if (pinned != lastInTopKey) pinned = lastInTopKey
                    // ② 视口无任何标题：列表顶部（未进入历史区）或历史区已滚完 → 取消；
                    //    否则保持吸顶（吸顶标题滚出视口上方、下一标题未到）
                    !anyHeader -> {
                        val atTop = listState.firstVisibleItemIndex == 0 &&
                            listState.firstVisibleItemScrollOffset < topBarPx.toInt()
                        if ((atTop || !listState.canScrollForward) && pinned != null) pinned = null
                    }
                    // ③ 标题都在顶栏下方且 pinned 可见：回滚松开 → 上一个月份标题立即接替
                    pinnedVisible -> {
                        val idx = pinned?.let { monthKeys.indexOf(it) } ?: -1
                        val next = if (idx > 0) monthKeys[idx - 1] else null
                        if (pinned != next) pinned = next
                    }
                    // ④ 其余（pinned 已滚出视口上方，上滚中）→ 保持吸顶
                }
            }
    }
    return pinned
}

/**
 * 吸顶月份标题覆盖层——位于 backdrop 录制树外（KOiLScreenScaffold overlay 插槽），
 * 可安全 textureBlur(backdrop) 采样整页内容（不含自身）→ 与顶栏同款真毛玻璃：
 * blurRadius 25f + surface 0.8 混合（对齐 BlurredBar）+ 底部 0.5dp 细线（同 SettingDivider 色）。
 * 模糊不可用（SDK<33 / 无 RuntimeShader）时退化为纯色 surface 0.8。
 */
@Composable
internal fun StickyMonthOverlay(
    backdrop: LayerBackdrop?,
    pinnedMonth: String,
    summary: String?,
    topPadding: Dp,
) {
    val bd = backdrop
    val canBlur = bd != null && isRuntimeShaderSupported()
    // 分隔线颜色在 Composable 上下文预取（drawBehind 内不可读主题色）
    val stickyLineColor = MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.12f)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            // 横向 12dp 与卡片外边距同值（contentPadding 已不含横向），标题文字与列表内标题行位置一致（切换无错位）
            .padding(start = 12.dp, end = 12.dp, top = topPadding)
            .then(
                if (canBlur) {
                    Modifier.textureBlur(
                        backdrop = bd,
                        shape = RectangleShape,
                        blurRadius = 25f,
                        colors = BlurColors(
                            blendColors = listOf(
                                BlendColorEntry(color = MiuixTheme.colorScheme.surface.copy(alpha = 0.8f)),
                            ),
                        ),
                    )
                } else {
                    Modifier.background(MiuixTheme.colorScheme.surface.copy(alpha = 0.8f))
                },
            )
            .drawBehind {
                val y = size.height - 0.5.dp.toPx()
                drawLine(
                    color = stickyLineColor,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 0.5.dp.toPx(),
                )
            },
    ) {
        MonthHeaderRow(monthKey = pinnedMonth, count = 0, summary = summary)
    }
}

/** 构建正序前一条记录映射（用于环比与区间） */
internal fun buildPrevMap(records: List<RecordWithDerived>): Map<Long, RecordWithDerived> {
    val map = mutableMapOf<Long, RecordWithDerived>()
    val asc = records.asReversed()
    for (i in 1 until asc.size) {
        map[asc[i].record.id] = asc[i - 1]
    }
    return map
}

/** 加油历史空态：无记录时的引导提示 */
@Composable
internal fun EmptyHistoryBox(onAddClick: (() -> Unit)? = null) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Filled.WaterDrop,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.4f),
                modifier = Modifier.size(36.dp),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.koil_ov_empty_title),
                fontSize = KOiLType.Subtitle,
                fontWeight = FontWeight.SemiBold,
                color = MiuixTheme.colorScheme.onBackgroundVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.koil_ov_empty_hint),
                fontSize = KOiLType.BodySmall,
                color = MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.7f),
            )
            // 空状态引导——可一键前往录入页（仅当宿主传入导航回调时显示）
            if (onAddClick != null) {
                Spacer(Modifier.height(16.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .background(KOiLColors.Primary)
                        .clickable(onClick = onAddClick)
                        .padding(horizontal = 22.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.koil_ov_empty_action),
                        fontSize = KOiLType.Body,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                    )
                }
            }
        }
    }
}
