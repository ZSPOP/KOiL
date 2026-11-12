package com.llpop.koil.ui.component

import androidx.annotation.StringRes
import com.llpop.koil.R
import com.llpop.koil.data.update.UpdateFailure

/**
 * 下载/安装失败原因到用户文案的唯一映射点。
 * 两处入口（首页横幅、设置页检查更新）都走这里：失败原因将来增减时，
 * 漏改一处就会出现「同一个错误两个页面说法不同」。
 */
@StringRes
fun UpdateFailure.messageRes(): Int = when (this) {
    UpdateFailure.DOWNLOAD -> R.string.koil_update_fail_download
    UpdateFailure.SIGNATURE -> R.string.koil_update_fail_signature
    UpdateFailure.NO_PERMISSION -> R.string.koil_update_fail_permission
    UpdateFailure.LAUNCH -> R.string.koil_update_fail_launch
}
