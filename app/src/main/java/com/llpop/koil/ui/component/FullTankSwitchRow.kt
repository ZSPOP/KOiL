package com.llpop.koil.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.llpop.koil.ui.theme.KOiLColors
import com.llpop.koil.R
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 「本次加满（跳枪）」开关行：录入页 / 编辑弹窗 / 补录弹窗三处共用。
 * 「加满」= 加油枪跳枪，与加油量多少无关——加 25L 可以是加满（剩 40L 补到跳枪），
 * 加 60L 也可能未满；判定权完全在用户，不做任何基于油量的自动推断。
 * 油耗只在「满箱 → 满箱」区间结算，未满记录待下次加满后一次结算。
 *
 * 开关用 [KOiLMiniSwitch]（34×20dp 视觉，与设置页「自动更新」一致），不用默认 Miuix Switch
 * （52×32dp 会把这一行撑得比其他表单行高出一截）。
 *
 * 可点区是**整行**（带 [Role.Switch] 语义）：34×20dp 远小于 48dp 触摸区标准，若只有
 * 开关本身可点，录入页高频操作会很难点中。故 [KOiLMiniSwitch] 传 `onCheckedChange = null`
 * 纯展示，点击响应统一由本行的 toggleable 上报。
 */
@Composable
fun FullTankSwitchRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                // 34×20 的开关本身远小于 48dp 触摸区标准，宽度补齐不够——纵向也必须补：
                // 行高下限 48dp，触摸目标才是完整可用尺寸
                .heightIn(min = 48.dp)
                // toggleable 而非 clickable：无障碍服务能读到开关状态（「已开启/已关闭」），
                // 不只是「双击以激活」
                .toggleable(
                    value = checked,
                    role = Role.Switch,
                    onValueChange = onCheckedChange,
                ),
        ) {
            Text(
                text = stringResource(R.string.koil_add_full_tank),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MiuixTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            KOiLMiniSwitch(
                checked = checked,
                onCheckedChange = null, // 展示态；点击由整行 toggleable 接管
                trackOn = KOiLColors.Primary,
                trackOff = MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.35f),
            )
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
