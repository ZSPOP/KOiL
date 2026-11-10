package com.llpop.koil.ui.component

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * 迷你开关（34×20dp）：设置页「自动更新」按钮内、录入页「本次加满（跳枪）」行共用。
 * 不用 Material/Miuix Switch 是因为它们自带 52×32dp 尺寸与最小触摸区，塞进按钮要么撑高行高、
 * 要么得靠 scale 缩放（缩放后点击区仍是原尺寸，位置会偏）。
 *
 * [onCheckedChange] 传 null 则纯展示（状态由外层可点击容器驱动，如设置页按钮）；
 * 传入则整个开关可点。34×20dp 小于 48dp 标准触摸区——独立成行的调用方（如
 * [FullTankSwitchRow]）应让整行 clickable 并把本组件当纯展示，不要直接传 onCheckedChange。
 */
@Composable
fun KOiLMiniSwitch(
    checked: Boolean,
    trackOn: Color,
    trackOff: Color,
    onCheckedChange: ((Boolean) -> Unit)? = null,
) {
    val knobOffset by animateDpAsState(if (checked) 15.dp else 0.dp, label = "miniSwitchKnob")
    Box(
        modifier = Modifier
            .size(width = 34.dp, height = 20.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (checked) trackOn else trackOff)
            .then(
                if (onCheckedChange != null) {
                    Modifier.clickable { onCheckedChange(!checked) }
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .padding(start = 2.dp)
                .offset { IntOffset(knobOffset.roundToPx(), 0) }
                .size(16.dp)
                .clip(CircleShape)
                .background(Color.White),
        )
    }
}
