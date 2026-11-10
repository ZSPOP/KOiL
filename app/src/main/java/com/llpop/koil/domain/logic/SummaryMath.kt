package com.llpop.koil.domain.logic

/**
 * 总览统计口径（纯函数，便于单测）。
 *
 * 「累计里程」与「油耗/每公里成本」是两套口径：
 * - 累计里程 = 全部非负有效里程段（短途段也真实行驶过，必须计入）；
 * - 油耗/每公里成本 = 满箱闭合区间加权（Σ油/Σ里程，含短途区间——总账无偏），
 *   区间聚合在 OverviewViewModel.computeSummary，短途单条由 UI 标低置信（见 DerivedCalculator）。
 */
object SummaryMath {

    /** 参与累计里程的里程段（排除 null、负数、非有限值） */
    fun distanceSegments(rows: List<RecordWithDerived>): List<Double> =
        rows.mapNotNull { row ->
            row.distance?.takeIf { it.isFinite() && it >= 0 }
        }

    /** 累计里程：含 <100km 短途段 */
    fun totalDistance(rows: List<RecordWithDerived>): Double =
        distanceSegments(rows).sum()

    /** 平均单次里程：按「每次加油」语义，用全部有效里程段 */
    fun avgDistancePerReoil(rows: List<RecordWithDerived>): Double? {
        val segments = distanceSegments(rows)
        return if (segments.isEmpty()) null else segments.sum() / segments.size
    }
}
