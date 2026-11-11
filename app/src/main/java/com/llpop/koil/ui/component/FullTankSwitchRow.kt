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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
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
 * 布局契约：[insideMargin] 横向固定 16dp —— 调用方必须把它放在**零横向内距**容器里，外层
 * 再加 `padding(horizontal)` 会双重缩进、高亮跟着缩窄。纵向只给 8dp，**上方的留白由调用方在
 * 高亮之外补**（录入卡 13dp、两个弹窗 8dp）：按压指示覆盖的是行自身边界，纵向留白若塞进
 * `insideMargin`，高亮矩形就会一路铺到上一字段下方，视觉上「贴住」上一行。
 * 底部留白分两种：录入卡里本行是卡片最后一行，**底部不再留高亮外空白**，高亮一直铺到卡片
 * 底边（用户要求按压时底部不留没覆盖的空隙）；两个弹窗里行下方还有按钮区，空白按「下面的块
 * 是否真的渲染」给 8dp（见 `OverviewScreen` 的 `hasPaymentSummary` / `hasPrecheckHint`），
 * 无数据时不留空档。
 *
 * 无障碍：开关状态用行级 `semantics { toggleableState }` 声明；内层 `Switch` 不再单独清理
 * 语义 —— 它与行 `clickable` 的 `mergeDescendants = true` 合并成同一个节点，TalkBack 读一行
 * 即可拿到「标题 + 副标题 + 开关 + 已开启/已关闭」。
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
            modifier = modifier
                .semantics {
                    // 双保险：内层 Switch 在 onCheckedChange = null 时自身已挂
                    // semantics { role = Switch; toggleableState }（Miuix 0.9.4 Switch.kt 的 else 分支），
                    // 经 clickable 的 mergeDescendants 合并进行节点，状态本已可读。这里再挂一份是同值覆盖，
                    // 只为防御 Miuix 内部实现变化；不要据此认为「状态可读性由本行负责」。
                    toggleableState = if (checked) ToggleableState.On else ToggleableState.Off
                }
                .indication(
                    interactionSource = interactionSource,
                    indication = MiuixIndication(MiuixTheme.colorScheme.onBackground),
                ),
            // 纵向只留 8dp：内容约 37dp + 16dp 仍低于 BasicComponent 的 56dp 最小行高，
            // 上下留白由调用方在高亮之外补 13dp（见 KDoc）。
            insideMargin = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
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
