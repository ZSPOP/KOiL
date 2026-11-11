package com.llpop.koil.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 圆形勾选状态标记：选中 = 实心圆 + 白色对勾，未选中 = 空心描边圆。
 *
 * 用途是给「整块可点的容器」做**纯展示**状态指示，本身不接收点击——点击由外层容器驱动
 * （如资料库卡片里「自动更新」按钮，点整个按钮切换，标记只反映状态）。
 *
 * 与开关类控件（带滑块的 Switch / 迷你开关）的区别：这个不画滑块、不占坑位，只表达
 * 「开 / 关」，所以在窄按钮里不会引出「控件尺寸 vs 文字尺寸」的比例问题。
 */
@Composable
fun KOiLCheckMark(
    checked: Boolean,
    activeColor: Color,
    inactiveColor: Color,
    modifier: Modifier = Modifier,
    iconSize: Dp = 20.dp,
) {
    Canvas(modifier = modifier.size(iconSize)) {
        val side = size.minDimension
        val radius = side / 2f
        if (checked) {
            drawCircle(color = activeColor, radius = radius)
            // 对勾：左上 → 中下 → 右上，线宽按图标边长取比例，保证不同尺寸下观感一致
            val strokeWidth = side * 0.11f
            val start = Offset(side * 0.28f, side * 0.52f)
            val bottom = Offset(side * 0.44f, side * 0.69f)
            val end = Offset(side * 0.73f, side * 0.34f)
            drawLine(Color.White, start, bottom, strokeWidth = strokeWidth, cap = StrokeCap.Round)
            drawLine(Color.White, bottom, end, strokeWidth = strokeWidth, cap = StrokeCap.Round)
        } else {
            drawCircle(
                color = inactiveColor,
                radius = radius - side * 0.06f,
                style = Stroke(width = side * 0.075f),
            )
        }
    }
}
