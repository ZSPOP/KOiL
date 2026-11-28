package com.llpop.koil.ui.component

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import kotlinx.coroutines.delay

/**
 * 键盘落位后再发滚动请求的等待时长：键盘动画期间 inset 连续变化，
 * 太早请求会按动画中间态定位。全站统一这一个值。
 */
private const val ImeScrollDelayMillis = 240L

/**
 * 输入框随键盘滚动（全站唯一口径）。
 *
 * 键盘可见且 [focused] 为真时，等键盘落位 240ms 后把调用方节点滚进最近的可滚动祖先的
 * 可见区，滚动量取「刚好露出」。刻意只请求**调用方节点**，不请求整个卡片或弹层面板：
 * 矮视口（分屏 / 横屏）下卡片与弹层面板都比可视区高，整块请求会把整块对齐到可视区顶部，
 * 点第一个字段也会跟着跳；字段级请求只动需要的距离。焦点离开或键盘收起直接不请求，
 * 不留下残留位移。
 *
 * 用法：`val req = rememberImeFieldScrollRequester(fieldFocused)`，再把 `req` 挂到
 * 字段的容器上（`.bringIntoViewRequester(req)`）；`fieldFocused` 仍由各字段自己的
 * `onFocusChanged` 维护，业务逻辑不受影响。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun rememberImeFieldScrollRequester(focused: Boolean): BringIntoViewRequester {
    val requester = remember { BringIntoViewRequester() }
    val imeVisible = WindowInsets.isImeVisible
    LaunchedEffect(imeVisible, focused) {
        if (!imeVisible || !focused) return@LaunchedEffect
        delay(ImeScrollDelayMillis)
        requester.bringIntoView()
    }
    return requester
}
