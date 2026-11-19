package com.llpop.koil.ui.screen

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.llpop.koil.R
import com.llpop.koil.domain.logic.NumberFormat
import com.llpop.koil.domain.logic.RecordWithDerived
import com.llpop.koil.ui.theme.KOiLColors
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
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

/** 月份标题行（列表 item；布局/字号/间距与旧覆盖层一致，滚出即随内容消失） */
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
