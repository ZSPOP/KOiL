package com.llpop.koil.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.llpop.koil.R
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 「本次加满（跳枪）」开关行：录入页 / 编辑弹窗 / 补录弹窗三处共用。
 * 「加满」= 加油枪跳枪，与加油量多少无关——加 25L 可以是加满（剩 40L 补到跳枪），
 * 加 60L 也可能未满；判定权完全在用户，不做任何基于油量的自动推断。
 * 油耗只在「满箱 → 满箱」区间结算，未满记录待下次加满后一次结算。
 */
@Composable
fun FullTankSwitchRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.koil_add_full_tank),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MiuixTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.koil_add_full_tank_hint),
            fontSize = 11.sp,
            lineHeight = 15.sp,
            color = MiuixTheme.colorScheme.onBackgroundVariant,
        )
    }
}
