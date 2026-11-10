package com.llpop.koil.domain.logic

import com.llpop.koil.data.local.entity.KOiLRecordEntity

/**
 * 同日多条的里程链邻居。
 *
 * 排序口径是 `inChronologicalOrder()` = `compareBy(reoilDate).thenBy(id)`，
 * 所以同一天的记录必须按 id 顺序入链，不能按里程排序：
 * - 补录：新记录 id 最大，必然排在所有同日记录之后，前邻居=同日最后一条；
 * - 编辑：id 固定，前邻居=同日 id 更小中最大者，后邻居=同日 id 更大中最小者。
 *
 * [sameDayById] 必须是同日记录按 id 升序（DAO 查询已保证）。
 */
object MileageChain {

    data class Neighbors(
        val prev: KOiLRecordEntity?,
        val next: KOiLRecordEntity?,
    )

    /** 里程链冲突：前一条要求 `mileage > prev.mileage`，后一条要求 `mileage < next.mileage`。 */
    data class Conflict(
        val type: String,
        val date: String,
        val value: Double,
    )

    fun conflict(mileage: Double, neighbors: Neighbors): Conflict? {
        val prev = neighbors.prev
        val next = neighbors.next
        return when {
            prev != null && mileage <= prev.mileage ->
                Conflict("prev", prev.reoilDate, prev.mileage)
            next != null && mileage >= next.mileage ->
                Conflict("next", next.reoilDate, next.mileage)
            else -> null
        }
    }

    /**
     * 日期是否早于第一条。编辑最早记录时必须跳过：它自己就是第一条，
     * 排除自身后拿到的“第二条”不能当前一条，否则改最早记录永远被拦。
     */
    fun isBeforeFirst(
        date: String,
        mileage: Double?,
        earliest: KOiLRecordEntity?,
        editingId: Long? = null,
    ): Boolean {
        if (earliest == null) return false
        if (editingId != null && earliest.id == editingId) return false
        return date < earliest.reoilDate ||
            (date == earliest.reoilDate && mileage != null && mileage < earliest.mileage)
    }

    fun forBackfill(
        sameDayById: List<KOiLRecordEntity>,
        beforeDate: KOiLRecordEntity?,
        afterDate: KOiLRecordEntity?,
    ): Neighbors = Neighbors(
        prev = sameDayById.lastOrNull() ?: beforeDate,
        next = afterDate,
    )

    fun forEdit(
        sameDayById: List<KOiLRecordEntity>,
        editingId: Long,
        beforeDate: KOiLRecordEntity?,
        afterDate: KOiLRecordEntity?,
    ): Neighbors {
        val prevSameDay = sameDayById.lastOrNull { it.id < editingId }
        val nextSameDay = sameDayById.firstOrNull { it.id > editingId }
        return Neighbors(
            prev = prevSameDay ?: beforeDate,
            next = nextSameDay ?: afterDate,
        )
    }
}
