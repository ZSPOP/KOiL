package com.llpop.koil.domain.logic

import com.llpop.koil.data.local.entity.KOiLRecordEntity
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/** 派生油耗状态（不落库，查询时计算） */
enum class ConsumptionStatus { HIGH, NORMAL, LOW, SKIPPED }

/** 单条记录的派生字段（不落库，查询时计算） */
data class RecordWithDerived(
    val record: KOiLRecordEntity,
    val distance: Double? = null,
    val oilDays: Long? = null,
    val unitPricePayable: Double? = null,
    val unitPriceActual: Double? = null,
    val oilConsumption: Double? = null,
    val costPerKm: Double? = null,
    val shortDistanceSkipped: Boolean = false,
    val consumptionStatus: ConsumptionStatus = ConsumptionStatus.NORMAL,
    val deviationPct: Double = 0.0,
)

/**
 * 加油记录派生数据统一计算器（v1.5.20 抽取）：
 * 总览页 / CSV 导出共用同一口径，避免导出油耗状态写死"正常"。
 * 入参 records 必须已按 reoilDate 升序。
 */
object DerivedCalculator {
    /** 单段行驶里程低于该值（km）时不参与油耗统计（短途，油耗失真） */
    const val MIN_DISTANCE = 100.0

    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    fun compute(sorted: List<KOiLRecordEntity>, abnormalPct: Double): List<RecordWithDerived> {
        val result = mutableListOf<RecordWithDerived>()
        var prev: KOiLRecordEntity? = null
        for (r in sorted) {
            val distance = prev?.let { r.mileage - it.mileage }
            val oilDays = prev?.let {
                val d1 = LocalDate.parse(r.reoilDate, dateFormatter)
                val d0 = LocalDate.parse(it.reoilDate, dateFormatter)
                ChronoUnit.DAYS.between(d0, d1)
            }
            val unitPricePayable = if (r.oilAmount > 0) r.payableAmount / r.oilAmount else null
            val unitPriceActual = if (r.oilAmount > 0) r.actualAmount / r.oilAmount else null
            val validDistance = distance != null && distance >= MIN_DISTANCE
            val oilConsumption = distance?.takeIf { it >= MIN_DISTANCE }?.let { d -> r.oilAmount / d * 100 }
            val costPerKm = distance?.takeIf { it >= MIN_DISTANCE }?.let { d -> r.actualAmount / d }
            result.add(
                RecordWithDerived(
                    record = r,
                    distance = distance,
                    oilDays = oilDays,
                    unitPricePayable = unitPricePayable?.let { Math.round(it * 100) / 100.0 },
                    unitPriceActual = unitPriceActual?.let { Math.round(it * 100) / 100.0 },
                    oilConsumption = oilConsumption?.let { Math.round(it * 100) / 100.0 },
                    costPerKm = costPerKm?.let { Math.round(it * 100) / 100.0 },
                    shortDistanceSkipped = !validDistance && distance != null,
                )
            )
            prev = r
        }
        val validConsumptions = result.mapNotNull { it.oilConsumption }
        if (validConsumptions.isNotEmpty()) {
            val avg = validConsumptions.average()
            val high = avg * (1 + abnormalPct / 100.0)
            val low = avg * (1 - abnormalPct / 100.0)
            for (i in result.indices) {
                val d = result[i]
                val status = when {
                    d.oilConsumption == null -> ConsumptionStatus.SKIPPED
                    d.oilConsumption > high -> ConsumptionStatus.HIGH
                    d.oilConsumption < low -> ConsumptionStatus.LOW
                    else -> ConsumptionStatus.NORMAL
                }
                val dev = if (d.oilConsumption != null && avg > 0) (d.oilConsumption - avg) / avg * 100 else 0.0
                result[i] = d.copy(consumptionStatus = status, deviationPct = dev)
            }
        }
        return result
    }
}
