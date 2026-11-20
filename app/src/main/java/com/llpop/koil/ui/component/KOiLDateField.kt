package com.llpop.koil.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
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
import com.llpop.koil.ui.component.fieldSurfaceColor
import com.llpop.koil.R
import top.yukonga.miuix.kmp.basic.DropdownArrowEndAction
import top.yukonga.miuix.kmp.glass.GlassTextFieldDefaults
import top.yukonga.miuix.kmp.glass.glassPopupAnchorValue
import top.yukonga.miuix.kmp.glass.rememberGlassPopupAnchor
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.llpop.koil.ui.theme.KOiLType

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
    // 日期值文本作为锚点：日历面板从值处展开、值在展开期间淡出（与省份/油号同一条路径）
    val popupAnchor = rememberGlassPopupAnchor()
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
                fontSize = KOiLType.BodySmall,
                color = MiuixTheme.colorScheme.onBackgroundVariant,
            )
        }
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                // 不再写死高度：与 GlassTextField 同构（同一内边距 + 同一行文字高度），
                // 高度自然相等，也不受系统字体缩放影响
                .clip(RoundedCornerShape(GlassTextFieldDefaults.CornerRadius))
                .background(fieldSurfaceColor())
                .onGloballyPositioned {
                    val b = it.boundsInWindow()
                    fieldBounds = IntRect(b.left.toInt(), b.top.toInt(), b.right.toInt(), b.bottom.toInt())
                }
                .clickable(enabled = onDateSelected != null) { showPicker = true }
                .padding(
                    horizontal = GlassTextFieldDefaults.InsideMargin.width,
                    vertical = GlassTextFieldDefaults.InsideMargin.height,
                ),
            contentAlignment = Alignment.CenterStart,
        ) {
            // 日期可选择（onDateSelected != null）时右侧显示下拉箭头（对齐油号/省份字段），只读不显示
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = date,
                    style = MiuixTheme.textStyles.main,
                    fontWeight = FontWeight.Bold,
                    color = MiuixTheme.colorScheme.onBackground,
                    maxLines = 1,
                    // 锚点直接挂在日期值上：面板左边缘=值左缘、纵向=值顶
                    modifier = Modifier
                        .then(if (onDateSelected != null) Modifier.weight(1f) else Modifier)
                        .glassPopupAnchorValue(popupAnchor),
                )
                if (onDateSelected != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        DropdownArrowEndAction(actionColor = MiuixTheme.colorScheme.onBackgroundVariant)
                    }
                }
            }
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
            anchor = popupAnchor,
        )
    }
}
