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
    /** v1.6.2：里程段为负（里程表回拨/录错）——这是数据异常，不等于"短途不走油耗" */
    val distanceAnomaly: Boolean = false,
)

/**
 * 加油记录派生数据统一计算器（v1.5.20 抽取）：
 * 总览页 / CSV 导出共用同一口径，避免导出油耗状态写死"正常"。
 * 入参 records 必须已按 reoilDate 升序。
 *
 * v1.6.2 压测修正：
 * ① 负里程段（里程表回拨）单独标记 [RecordWithDerived.distanceAnomaly]，不再混入"短途跳过"；
 * ② 同日多条记录的日期解析做记忆化，交叉排序的大表不再重复 parse 同一日期。
 */
object DerivedCalculator {
    /** 单段行驶里程低于该值（km）时不参与油耗统计（短途，油耗失真） */
    const val MIN_DISTANCE = 100.0

    // v1.6.2：用 ISO_LOCAL_DATE（严格解析）——ofPattern 默认 SMART 会把 2026-02-30 静默纠正成 02-28，
    // 脏日期本该被识别为异常，不该被"猜"成一个合法日期。
    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    fun compute(sorted: List<KOiLRecordEntity>, abnormalPct: Double): List<RecordWithDerived> {
        val result = ArrayList<RecordWithDerived>(sorted.size)
        val epochDays = HashMap<String, Long>(minOf(sorted.size, 1024))
        // 非法日期用哨兵值缓存，避免同一脏日期反复走异常分支
        fun epochDay(date: String): Long = epochDays.getOrPut(date) {
            runCatching { LocalDate.parse(date, dateFormatter).toEpochDay() }.getOrDefault(Long.MIN_VALUE)
        }

        var prev: KOiLRecordEntity? = null
        for (r in sorted) {
            val distance = prev?.let { r.mileage - it.mileage }
            val oilDays = prev?.let { p ->
                val d1 = epochDay(r.reoilDate)
                val d0 = epochDay(p.reoilDate)
                if (d1 == Long.MIN_VALUE || d0 == Long.MIN_VALUE) null else d1 - d0
            }
            val unitPricePayable = if (r.oilAmount > 0) r.payableAmount / r.oilAmount else null
            val unitPriceActual = if (r.oilAmount > 0) r.actualAmount / r.oilAmount else null
            val anomaly = distance != null && distance < 0
            val validDistance = distance != null && distance >= MIN_DISTANCE
            val oilConsumption = distance?.takeIf { it >= MIN_DISTANCE }?.let { d -> r.oilAmount / d * 100 }
            val costPerKm = distance?.takeIf { it >= MIN_DISTANCE }?.let { d -> r.actualAmount / d }
            result.add(
                RecordWithDerived(
                    record = r,
                    distance = distance,
                    oilDays = oilDays,
                    unitPricePayable = unitPricePayable?.round2(),
                    unitPriceActual = unitPriceActual?.round2(),
                    oilConsumption = oilConsumption?.round2(),
                    costPerKm = costPerKm?.round2(),
                    shortDistanceSkipped = !validDistance && distance != null && !anomaly,
                    distanceAnomaly = anomaly,
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

    /** 十进制四舍五入到 2 位（非有限值原样透传，避免上游 NaN 静默变 0） */
    private fun Double.round2(): Double {
        if (!isFinite()) return this
        return Math.round(this * 100.0) / 100.0
    }

    /** 供 UI/导出复用的日期差（天）；非法日期返回 null */
    fun daysBetween(from: String, to: String): Long? = runCatching {
        ChronoUnit.DAYS.between(LocalDate.parse(from, dateFormatter), LocalDate.parse(to, dateFormatter))
    }.getOrNull()
}
