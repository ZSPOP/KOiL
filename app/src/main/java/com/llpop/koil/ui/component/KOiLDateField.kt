package com.llpop.koil.ui.component

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.llpop.koil.R
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 日期选择字段（补录/编辑/首页录入统一使用）。
 * 点击弹锚定下拉日历（KOiLDatePickerDialog，紧贴字段下方、空间不足自动上翻、独立 Popup 不被遮挡）。
 * - onDateSelected != null：可点击选择（补录/编辑用）
 * - onDateSelected == null：只读展示（首页录入用，保持结构统一，后续开启可编辑直接传回调即可）
 */
@Composable
fun KOiLDateField(
    modifier: Modifier = Modifier,
    date: String,
    recordDates: Set<String> = emptySet(),
    onDateSelected: ((String) -> Unit)? = null,
) {
    var showPicker by remember { mutableStateOf(false) }
    var fieldBounds by remember { mutableStateOf<IntRect?>(null) }
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.CalendarMonth,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.onBackgroundVariant,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = stringResource(R.string.koil_add_date),
                fontSize = 12.sp,
                color = MiuixTheme.colorScheme.onBackgroundVariant,
            )
        }
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MiuixTheme.colorScheme.secondaryContainer)
                .onGloballyPositioned {
                    val b = it.boundsInWindow()
                    fieldBounds = IntRect(b.left.toInt(), b.top.toInt(), b.right.toInt(), b.bottom.toInt())
                }
                .clickable(enabled = onDateSelected != null) { showPicker = true }
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = date,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MiuixTheme.colorScheme.onBackground,
                maxLines = 1,
            )
        }
    }
    if (showPicker && onDateSelected != null) {
        KOiLDatePickerDialog(
            initialDate = date,
            recordDates = recordDates,
            onDismiss = { showPicker = false },
            onConfirm = { selected ->
                onDateSelected(selected)
                showPicker = false
            },
            anchorBounds = fieldBounds,
        )
    }
}
