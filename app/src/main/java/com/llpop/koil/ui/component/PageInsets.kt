package com.llpop.koil.ui.component

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/**
 * 键盘弹起时，最后一行内容到键盘上沿的统一间距。
 *
 * 凡是"会被键盘顶到的可输入内容"都用它，不再各处自定一个数（8 / 16 / 20 混用过）：
 * 首页录入卡抬升后的卡底间距、表单弹层在键盘可见时的底部内距。
 * 键盘收起时的底部占位（系统导航栏、悬浮底栏）不属于这个口径，各按各的真实高度走。
 */
val ImeGap = 12.dp

/**
 * 页面内容四边内边距：对每个方向取 [innerPadding] / [outerPadding] 的较大值，再叠加 [extraXxx]。
 *
 * 为什么不用 `PaddingValues(top = ..., bottom = ...)` 手工拼：多层 Scaffold（外层底栏槽
 * + 内层顶栏）各自算出的内边距是"下限"而不是"定值"，直接赋值会互相覆盖 —— 外层槽高变大时
 * 被内层小值吃掉，或反之。取 max 让每一层的诉求都成立，新增一层（分屏/侧栏/搜素框）时
 * 也只需把它接进来，不用回头改每个页面的拼法。
 */
@Composable
fun pageContentPadding(
    innerPadding: PaddingValues,
    outerPadding: PaddingValues = PaddingValues(0.dp),
    extraTop: Dp = 0.dp,
    extraStart: Dp = 0.dp,
    extraEnd: Dp = 0.dp,
    extraBottom: Dp = 0.dp,
): PaddingValues {
    return remember(innerPadding, outerPadding, extraTop, extraStart, extraEnd, extraBottom) {
        object : PaddingValues {
            override fun calculateLeftPadding(layoutDirection: LayoutDirection): Dp = maxOf(
                innerPadding.calculateLeftPadding(layoutDirection),
                outerPadding.calculateLeftPadding(layoutDirection),
            ) + if (layoutDirection == LayoutDirection.Ltr) extraStart else extraEnd

            override fun calculateRightPadding(layoutDirection: LayoutDirection): Dp = maxOf(
                innerPadding.calculateRightPadding(layoutDirection),
                outerPadding.calculateRightPadding(layoutDirection),
            ) + if (layoutDirection == LayoutDirection.Rtl) extraStart else extraEnd

            override fun calculateTopPadding(): Dp = maxOf(
                innerPadding.calculateTopPadding(),
                outerPadding.calculateTopPadding(),
            ) + extraTop

            override fun calculateBottomPadding(): Dp = maxOf(
                innerPadding.calculateBottomPadding(),
                outerPadding.calculateBottomPadding(),
            ) + extraBottom
        }
    }
}
