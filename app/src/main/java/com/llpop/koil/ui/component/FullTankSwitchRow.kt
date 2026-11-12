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
import androidx.compose.ui.unit.Dp
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
 * 布局契约（块模型，以「预测模块底部圆角矩形」为基准，跳枪行块照抄）：
 * ① 上 12dp = 上方内容 → 块上缘，由调用方在**高亮之外**补（录入卡是表单 Column 的
 * bottom 12dp，弹窗是行上方的 Spacer 12dp）——这段留白不进高亮，按压时高亮顶边与上一字段
 * 之间保持 12dp 卡片底色；② 块内 8dp = 高亮顶边 → 标题文字（insideMargin top）；③ 下 16dp
 * 由 [bottomInset] 决定语义归属：录入卡传 16dp——本行是卡片最后一项，高亮从块上缘一直铺到
 * 卡片底边，这 16dp 在高亮**之内**（顶角直角、底角随卡片圆角收边）；两个弹窗维持默认 8dp
 * ——高亮停在块下缘，块下缘 → 按钮行/信息块的 16dp 由调用方在高亮**之外**补（见
 * `OverviewScreen` 下方 Column 的 top 16dp）。
 * [insideMargin] 横向固定 16dp —— 调用方必须把它放在**零横向内距**容器里，外层再加
 * `padding(horizontal)` 会双重缩进、高亮跟着缩窄。高亮只是覆盖在布局上的颜色，不参与布局：
 * 所有间距都按上面 ①②③ 定死，不再出现「往高亮里塞间距」的补丁。
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
    bottomInset: Dp = 8.dp,
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
            // 块内上距固定 8dp（基准块口径）；块内下距由 [bottomInset] 决定：
            // 录入卡 16dp（高亮铺到卡底，间距在高亮内）、弹窗 8dp（高亮停在块下缘，见 KDoc）。
            // BasicComponent 的 56dp 最小行高只是下限，录入卡 8+37+16≈65dp 自然超过它。
            insideMargin = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = bottomInset),
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
