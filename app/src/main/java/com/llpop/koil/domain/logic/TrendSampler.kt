package com.llpop.koil.domain.logic

/**
 * 趋势图抽样（纯函数，便于单测）。
 *
 * 记录数超过 [maxPoints] 时按等间隔取点，并**显式保留最后一条**：
 * 按 `derived[(i * step).toInt()]` 取点永远取不到末尾，趋势图会停在倒数第二条，
 * 与「保留首尾形态」的口径不一致。
 */
object TrendSampler {

    /**
     * @param items 已按时间升序排列的数据
     * @param maxPoints 最多保留的点数（至少 2，保证首尾都能取到）
     */
    fun <T> sample(items: List<T>, maxPoints: Int): List<T> {
        require(maxPoints >= 2) { "maxPoints must be >= 2" }
        if (items.size <= maxPoints) return items

        val step = (items.size - 1).toDouble() / (maxPoints - 1)
        // LinkedHashSet 去重且保持顺序：极端参数下截断可能产生重复下标
        val indices = LinkedHashSet<Int>(maxPoints)
        for (i in 0 until maxPoints - 1) {
            indices.add((i * step).toInt().coerceIn(0, items.size - 1))
        }
        indices.add(items.size - 1)
        return indices.map { items[it] }
    }
}
