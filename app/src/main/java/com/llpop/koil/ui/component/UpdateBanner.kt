package com.llpop.koil.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.llpop.koil.R
import com.llpop.koil.ui.theme.KOiLColors
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 首页（AddScreen）摘要卡之后的更新横幅。
 *
 * 位置修正：这个组件最初写在 OverviewScreen（底部导航的「数据」页），真机验收发现横幅
 * 显示在数据总览页而不是首页。首页是 tab 0 的 AddScreen，摘要卡是其中的 OilPriceCard。
 *
 * 显示条件与设置页红点完全同源（同一个 shouldBadgeUpdate，有单测），所以不会出现
 * 「有一处亮、另一处不亮」。点「更新」跳 Release 页 —— ④ 应用内下载落地后只改 onOpen
 * 回调，判据不用动；点 × 视为已知晓，写 seenTag 后同一版本不再出现（不做「7 天后再提醒」）。
 */
@Composable
internal fun UpdateBanner(
    version: String,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
    /** 右侧动作文案：下载失败时换成「重试」，其余场景仍是「更新」 */
    actionLabel: String = stringResource(R.string.koil_ov_update_action),
    /**
     * 自定义主文案；null 时用「发现新版本 %1$s」。
     * 下载/安装失败时传具体原因（签名不一致、需要授权…），不这么做的话
     * 所有失败都会被压成一句「下载失败，请重试」，签名不一致会被误报成网络问题并诱导反复重下。
     */
    title: String? = null,
) {
    KOiLBlurBackground(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 20f,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // 整条可点（含左侧图标与留白）：用户对横幅的第一反应是点整行，
                // 只把 clickable 套在文字上会让图标区域点了没反应；× 有自己的 clickable 会先消费
                .clickable(onClick = onOpen)
                .heightIn(min = 46.dp)
                .padding(start = 16.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.SystemUpdate,
                contentDescription = null,
                tint = KOiLColors.Primary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(10.dp))
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title ?: stringResource(R.string.koil_ov_update_new, version),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MiuixTheme.colorScheme.onBackground,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = actionLabel + " ›",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = KOiLColors.Primary,
                    maxLines = 1,
                    softWrap = false,
                )
            }
            // 关闭：36dp 可点区域（图标视觉仍是 18dp），比直接点 18dp 图标好按
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = stringResource(R.string.koil_ov_update_dismiss),
                tint = MiuixTheme.colorScheme.onBackgroundVariant,
                modifier = Modifier
                    .size(36.dp)
                    .clickable(onClick = onDismiss)
                    .padding(9.dp),
            )
        }
    }
}
