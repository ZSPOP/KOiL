package com.llpop.koil.ui.component

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.llpop.koil.R
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.MiuixIndication

/**
 * 「本次加满（跳枪）」开关行：录入页 / 编辑弹窗 / 补录弹窗三处共用。
 * 「加满」= 加油枪跳枪，与加油量多少无关——加 25L 可以是加满（剩 40L 补到跳枪），
 * 加 60L 也可能未满；判定权完全在用户，不做任何基于油量的自动推断。
 * 油耗只在「满箱 → 满箱」区间结算，未满记录待下次加满后一次结算。
 *
 * 按压反馈对齐 Miuix `BasicComponent` / `SwitchPreference`：整行可点，开关本身不消费
 * 指针；行上显式挂 Miuix 按压指示，并屏蔽 `BasicComponent` 内层默认指示，避免叠两层。
 *
 * 前置条件：[insideMargin] 在本组件内部固定为「横向 16dp / 纵向 13dp」（与设置页
 * `SettingSwitchRow`、EIA 行同值），调用方必须把它放在**零横向内距**的容器里——外层再加
 * `padding(horizontal)` 会双重缩进，按压高亮也会跟着缩窄；纵向同理，上距/底距只由行内
 * 13dp 承担，调用方不要再补 `Spacer` 或 `padding(bottom)`。
 */
@Composable
fun FullTankSwitchRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    CompositionLocalProvider(LocalIndication provides MiuixIndication(Color.Transparent)) {
        BasicComponent(
            modifier = modifier.indication(
                interactionSource = interactionSource,
                indication = MiuixIndication(MiuixTheme.colorScheme.onBackground),
            ),
            // 上下 13dp 与设置页 SettingSwitchRow / EIA 行同值：行内距之外不再由外层补间距，
            // 避免 56dp 最小行高把「标题 + 小字」居中悬空、看起来离卡片底边太远。
            insideMargin = PaddingValues(horizontal = 16.dp, vertical = 13.dp),
            onClick = { onCheckedChange(!checked) },
            role = Role.Switch,
            interactionSource = interactionSource,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.koil_add_full_tank),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = MiuixTheme.colorScheme.onBackground,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.koil_add_full_tank_hint),
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        color = MiuixTheme.colorScheme.onBackgroundVariant,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Switch(
                    checked = checked,
                    onCheckedChange = null,
                )
            }
        }
    }
}
