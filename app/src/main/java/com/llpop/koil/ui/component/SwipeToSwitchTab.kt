package com.llpop.koil.ui.component

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 拖动选中块：手指按在标签行上左右拖动，**灰块（选中态）落到哪一格就切到哪一格**。
 *
 * 行内每一项等宽（miuix `TabRow` 的排布规则：`itemSpacing` 固定、项宽均分），
 * 所以用行宽反推每格宽度，把手指的 x 坐标换算成下标即可，拖动过程中连续生效。
 *
 * 不 consume 任何事件，点击与纵向滚动都不受影响。
 */
@Composable
fun Modifier.swipeToSwitchTab(
    tabCount: Int,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    itemSpacing: Dp = 9.dp,
): Modifier {
    val indexState = rememberUpdatedState(selectedIndex)
    val selectState = rememberUpdatedState(onSelect)
    val countState = rememberUpdatedState(tabCount)
    var rowWidthPx by remember { mutableFloatStateOf(0f) }
    val layoutDirection = LocalLayoutDirection.current
    return this
        .onSizeChanged { rowWidthPx = it.width.toFloat() }
        .pointerInput(layoutDirection) {
            val spacingPx = itemSpacing.toPx()
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                var index = indexState.value
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) break
                    val count = countState.value
                    val width = rowWidthPx
                    if (count <= 1 || width <= 0f) continue
                    val tabWidth = (width - spacingPx * (count - 1)) / count
                    if (tabWidth <= 0f) continue
                    // RTL 下手势 x 从右往左递增，先镜像成 LTR 坐标再算下标，
                    // 否则 Arabic/Urdu 等从右往左语言里拖动方向与落点会反。
                    val posX = if (layoutDirection == LayoutDirection.Rtl) {
                        width - change.position.x
                    } else {
                        change.position.x
                    }
                    val target = (posX / (tabWidth + spacingPx))
                        .toInt()
                        .coerceIn(0, count - 1)
                    if (target != index) {
                        index = target
                        selectState.value(target)
                    }
                }
            }
        }
}
