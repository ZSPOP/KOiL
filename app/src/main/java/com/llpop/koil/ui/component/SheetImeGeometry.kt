package com.llpop.koil.ui.component

import kotlin.math.roundToInt

/**
 * 弹层随键盘变化的两处尺寸（px）：内容区封顶、底部让位。
 *
 * 拆成纯函数是为了能在单元测试里把边界钉死 —— 这段几何一旦回归，表现出来就是
 * 「收键盘回弹」「弹起冲顶」这类只有真机才看得出的问题。
 */
internal data class SheetImeGeometry(
    val contentCapPx: Int,
    val bottomSpacePx: Int,
)

/** 键盘 inset 读不出来时按屏高估键盘高的比例，只在「整机从未报过非零 ime」时用 */
internal const val SheetImeFallbackRatio = 0.45f

/**
 * 键盘进度：0 = 完全收起，1 = 完全弹出。
 *
 * `imePx` 归零即视为键盘已收起，不看 `imeVisible` —— 收起末尾 `ime` 先归零、`imeVisible`
 * 还挂 `true` 的那一帧若按「键盘还在」算，下一帧就会翻回去，面板「先往下收、再往上弹一点点」。
 * 只有整机从未报过非零 ime 的异常机型，才靠可见布尔当键盘还在。
 */
internal fun keyboardProgressPx(
    imePx: Int,
    imeVisible: Boolean,
    maxImePx: Int,
    windowPx: Int,
): Float {
    val denominator = if (maxImePx > 0) maxImePx else (windowPx * SheetImeFallbackRatio).toInt()
    return when {
        imePx > 0 -> (imePx.toFloat() / denominator).coerceIn(0f, 1f)
        imeVisible && maxImePx == 0 -> 1f
        else -> 0f
    }
}

/**
 * 键盘态内容封顶 + 底部留白，按 ime 的真实进度连续计算（全程在测量阶段调用）。
 *
 * - 封顶按「窗口高 − 状态栏 − 键盘 − 顶部让位 − 底部留白」限制，键盘高度逐帧变化，封顶也逐帧收放。
 * - 底部留白在「键盘全出 [imeGapPx]」与「键盘全收 导航栏 + [idleExtraPx]」之间线性插值。
 * - 下限 [minContentPx] 只保证表单不至于矮到没法用，**但不能把面板顶出可视区**：键盘占掉半屏以上时
 *   「可视区」本身就低于 200dp，硬顶 200dp 会让面板高过键盘上沿、重新冲顶。所以下限收在可视区内。
 */
internal fun sheetImeGeometry(
    imePx: Int,
    imeVisible: Boolean,
    maxImePx: Int,
    windowPx: Int,
    topInsetPx: Int,
    navBottomPx: Int,
    imeGapPx: Int,
    idleExtraPx: Int,
    minContentPx: Int,
    topGapPx: Int,
    maxHeightPx: Int,
): SheetImeGeometry {
    val progress = keyboardProgressPx(
        imePx = imePx,
        imeVisible = imeVisible,
        maxImePx = maxImePx,
        windowPx = windowPx,
    )
    val idleBottomPx = navBottomPx + idleExtraPx
    // 取整沿用小数点四舍五入：向零截断会让进度非整时的留白系统性偏小 1px，
    // 和 vc662 那一版对不上，也会让「留白跟着键盘长」在末段差一像素
    val bottomSpacePx = (idleBottomPx + (imeGapPx - idleBottomPx) * progress).roundToInt()

    val keyboardOn = imePx > 0 || (imeVisible && maxImePx == 0)
    if (!keyboardOn) return SheetImeGeometry(contentCapPx = maxHeightPx, bottomSpacePx = bottomSpacePx)

    val imeForCap = if (imePx > 0) imePx else (windowPx * SheetImeFallbackRatio).toInt()
    // 键盘上沿到状态栏之间的活空间，面板最多只能长到这里
    val visiblePx = windowPx - imeForCap - topInsetPx - topGapPx - bottomSpacePx
    val floorPx = if (visiblePx > 0) minOf(minContentPx, visiblePx) else 0
    val capPx = visiblePx
        .coerceAtLeast(floorPx)
        .coerceAtMost(maxHeightPx)
    return SheetImeGeometry(contentCapPx = capPx, bottomSpacePx = bottomSpacePx)
}
