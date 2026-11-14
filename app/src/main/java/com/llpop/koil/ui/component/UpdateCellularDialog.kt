package com.llpop.koil.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.llpop.koil.R
import com.llpop.koil.ui.theme.KOiLColors
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 蜂窝网络下的下载确认。
 *
 * 两种形态共用一个组件，保证文案与按钮位置不随调用处漂移：
 * - [blocked] = false：第一次在蜂窝下更新，问一次并记住选择；
 * - [blocked] = true：用户此前选了「仅 WiFi」，这次仍想下，给一个明确的强制入口。
 *
 * 挡住时要给出口：静默什么都不发生，和「点第一次没反应」是同一种缺陷。
 */
@Composable
internal fun UpdateCellularDialog(
    blocked: Boolean,
    onDownload: () -> Unit,
    onWaitWifi: () -> Unit,
    /**
     * 点弹窗外部 / 返回键。
     *
     * 必须与 [onWaitWifi] 分开：Ask 形态下「等 WiFi」会**写入持久偏好**，
     * 误触弹窗外部就变成「以后蜂窝下永远被挡」，而没有任何 UI 能把偏好改回「不问」。
     * 误触只应关掉弹窗——「只问一次」的那一次还没被消费掉，下次点更新再问。
     */
    onDismiss: () -> Unit,
) {
    KOiLGlassDialog(
        show = true,
        onDismissRequest = onDismiss,
        contentPadding = 20.dp,
        widthFraction = 0.82f,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(
                    if (blocked) R.string.koil_update_wifi_only_title
                    else R.string.koil_update_cellular_title,
                ),
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = MiuixTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(
                    if (blocked) R.string.koil_update_wifi_only_msg
                    else R.string.koil_update_cellular_msg,
                ),
                fontSize = 13.sp,
                color = MiuixTheme.colorScheme.onBackgroundVariant,
            )
            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.koil_update_cellular_wait),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .clickable { onWaitWifi() }
                        .padding(vertical = 11.dp),
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.koil_update_cellular_mobile),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(KOiLColors.Primary)
                        .clickable { onDownload() }
                        .padding(vertical = 11.dp),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
