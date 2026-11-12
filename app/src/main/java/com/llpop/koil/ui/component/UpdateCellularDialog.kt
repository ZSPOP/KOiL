package com.llpop.koil.ui.component

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.llpop.koil.R

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
) {
    AlertDialog(
        // 点外部/返回键等同于「等 WiFi」：不下载就是不下载，不做隐式继续
        onDismissRequest = onWaitWifi,
        title = {
            Text(
                stringResource(
                    if (blocked) R.string.koil_update_wifi_only_title
                    else R.string.koil_update_cellular_title,
                )
            )
        },
        text = {
            Text(
                stringResource(
                    if (blocked) R.string.koil_update_wifi_only_msg
                    else R.string.koil_update_cellular_msg,
                )
            )
        },
        confirmButton = {
            TextButton(onClick = onDownload) {
                Text(stringResource(R.string.koil_update_cellular_mobile))
            }
        },
        dismissButton = {
            TextButton(onClick = onWaitWifi) {
                Text(stringResource(R.string.koil_update_cellular_wait))
            }
        },
    )
}
