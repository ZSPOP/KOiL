package com.llpop.koil.domain.logic

import com.llpop.koil.data.local.entity.KOiLRecordEntity
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * 派生油耗状态（不落库，查询时计算）。
 * - HIGH/NORMAL/LOW：已闭合满箱区间，按区间均值上下浮动判定；
 * - PENDING：已有满箱锚点、待下次加满后结算（半箱记录 / 区间里程非正的满箱记录）；
 * - NO_BASE：尚无满箱锚点（锚点之前的半箱记录 / 首个满箱记录本身）。
 */
enum class ConsumptionStatus { HIGH, NORMAL, LOW, PENDING, NO_BASE }

/** 单条记录的派生字段（不落库，查询时计算） */
data class RecordWithDerived(
    val record: KOiLRecordEntity,
    val distance: Double? = null,
    val oilDays: Long? = null,
    val unitPricePayable: Double? = null,
    val unitPriceActual: Double? = null,
    val oilConsumption: Double? = null,
    val costPerKm: Double? = null,
    val consumptionStatus: ConsumptionStatus = ConsumptionStatus.NORMAL,
    val deviationPct: Double = 0.0,
    /** 里程段为负（里程表回拨/录错）——这是数据异常，不等于"短途不走油耗" */
    val distanceAnomaly: Boolean = false,
    /** 本条满箱结算的满箱区间里程（锚点里程 → 本条里程）；与 [spanOil]/[spanActual] 成对出现 */
    val spanDistance: Double? = null,
    /** 满箱区间内累计加油量（含本条，L）；供总账加权与展开明细 */
    val spanOil: Double? = null,
    /** 满箱区间内累计实付金额（含本条，元）；供总账加权 */
    val spanActual: Double? = null,
    /** 满箱区间 < [MIN_DISTANCE]（短途）：单条数字失真风险大，标"短途参考"并隐藏出趋势；总账仍计入 */
    val shortInterval: Boolean = false,
    /** 待结算累计：自锚点以来记录条数（含本条）；与 [pendingOil]/[pendingActual] 成对出现（无锚点时为 0/null） */
    val pendingCount: Int = 0,
    /** 待结算累计加油量（L）；无锚点的半箱记录不累计（上次满油时间未知），保持 null */
    val pendingOil: Double? = null,
    /** 待结算累计实付金额（元） */
    val pendingActual: Double? = null,
)

/**
 * 加油记录派生数据统一计算器：
 * 总览页 / CSV 导出 / 备份派生共用同一口径，避免三处实现漂移。
 * 入参 records 允许任意顺序：内部统一归一到 (reoilDate, id) 升序后再派生，
 * 避免调用方漏排序时把同日多条算成负里程（见 [inChronologicalOrder]）。
 *
 * —— 满箱锚点算法（isFullTank）——
 * 油耗只在「满箱 → 满箱」区间上结算：
 * - 锚点 = 最近一次「加满」（加油枪跳枪，与加油量多少无关）的记录；
 *   锚点自身的油量属于上一个区间，不参与本区间累计；
 * - 本条加满时闭合区间：区间里程 = 本条里程 − 锚点里程，
 *   区间油量 = 锚点之后所有记录的加油量之和（含本条）。
 *   两端都满箱 ⇒ 区间内加入的所有油必然被烧掉，与中间各次半箱比例无关，数学上严格成立；
 * - 半箱记录：不产出油耗（PENDING），只累计油量/金额，等下次加满一次结算；
 * - 满箱且区间 > 0：闭合结算——短途（< [MIN_DISTANCE]）同样产出数字并计入总账
 *   （总账 Σ油/Σ里程 因此严格等于 总油/总里程，无偏），单条标 [RecordWithDerived.shortInterval]
 *   由 UI 标"短途参考"、隐藏出趋势；
 * - 满箱且区间 ≤ 0（同点加油/里程表回拨）：不产出、锚点不动、累计保留，
 *   等下一条正常里程的满箱记录对整个区间给出正确油耗——保留累计是唯一保持总账精确的选项；
 * - 历史数据（v1 库/旧 CSV/旧备份）isFullTank 缺省为 true ⇒ 全部记录都是锚点 ⇒
 *   算法退化为「逐段结算」，与升级前口径一致（短途段从"丢弃"改为"计入"，总账更准，见上）。
 *
 * 实现要点：
 * ① 负里程段（里程表回拨）单独标记 [RecordWithDerived.distanceAnomaly]，与待结算/短途区分；
 * ② 同日多条记录的日期解析做记忆化，交叉排序的大表避免重复 parse 同一日期。
 */
object DerivedCalculator {
    /** 满箱区间里程低于该值（km）时，单条数字标"短途参考"（总账仍计入，见 shortInterval） */
    const val MIN_DISTANCE = 100.0

    // 用 ISO_LOCAL_DATE（严格解析）——ofPattern 默认 SMART 会把 2026-02-30 静默纠正成 02-28，
    // 脏日期本该被识别为异常，不该被"猜"成一个合法日期。
    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    fun compute(sorted: List<KOiLRecordEntity>, abnormalPct: Double): List<RecordWithDerived> {
        // 防御性归一：DAO 的 observeAll 是 DESC、getRange 只按日期排，导入顺序更是任意；
        // 里程段由相邻记录决定，顺序错了就是错的油耗，所以在这里兜最后一次。
        val ordered = sorted.inChronologicalOrder()
        val result = ArrayList<RecordWithDerived>(ordered.size)
        val epochDays = HashMap<String, Long>(minOf(ordered.size, 1024))
        // 非法日期用哨兵值缓存，避免同一脏日期反复走异常分支
        fun epochDay(date: String): Long = epochDays.getOrPut(date) {
            runCatching { LocalDate.parse(date, dateFormatter).toEpochDay() }.getOrDefault(Long.MIN_VALUE)
        }

        // 满箱锚点状态：anchorMileage 为 null 表示还没有可用锚点（首条之前 / 从未加满）
        var anchorMileage: Double? = null
        var spanOilAcc = 0.0
        var spanActualAcc = 0.0
        var anchorCount = 0

        var prev: KOiLRecordEntity? = null
        for (r in ordered) {
            val distance = prev?.let { r.mileage - it.mileage }
            val oilDays = prev?.let { p ->
                val d1 = epochDay(r.reoilDate)
                val d0 = epochDay(p.reoilDate)
                if (d1 == Long.MIN_VALUE || d0 == Long.MIN_VALUE) null else d1 - d0
            }
            val unitPricePayable = if (r.oilAmount > 0) r.payableAmount / r.oilAmount else null
            val unitPriceActual = if (r.oilAmount > 0) r.actualAmount / r.oilAmount else null
            val anomaly = distance != null && distance < 0

            var oilConsumption: Double? = null
            var costPerKm: Double? = null
            var spanDistance: Double? = null
            var spanOilOut: Double? = null
            var spanActualOut: Double? = null
            var shortInterval = false
            var status = ConsumptionStatus.NORMAL
            var pendingCount = 0
            var pendingOil: Double? = null
            var pendingActual: Double? = null

            if (anchorMileage == null) {
                // 尚无锚点：首个满箱记录成为锚点（UI 提示"已建立满油基准"）；
                // 半箱记录不累计——上次满箱时间未知，区间基线不存在（UI 提示"暂无满油基准"）。
                if (r.isFullTank) {
                    anchorMileage = r.mileage
                    spanOilAcc = 0.0
                    spanActualAcc = 0.0
                    anchorCount = 0
                }
                status = ConsumptionStatus.NO_BASE
            } else {
                spanOilAcc += r.oilAmount
                spanActualAcc += r.actualAmount
                anchorCount++
                if (!r.isFullTank) {
                    // 半箱：不产出，锚点不动，油量/金额继续累计到下次满箱
                    status = ConsumptionStatus.PENDING
                    pendingCount = anchorCount
                    pendingOil = spanOilAcc
                    pendingActual = spanActualAcc
                } else {
                    val span = r.mileage - anchorMileage
                    when {
                        // 满箱 + 区间有里程 → 闭合结算（短途也结算，标 shortInterval），本条成为新锚点
                        span > 0.0 -> {
                            oilConsumption = spanOilAcc / span * 100
                            costPerKm = spanActualAcc / span
                            spanDistance = span
                            spanOilOut = spanOilAcc
                            spanActualOut = spanActualAcc
                            shortInterval = span < MIN_DISTANCE
                            anchorMileage = r.mileage
                            spanOilAcc = 0.0
                            spanActualAcc = 0.0
                            anchorCount = 0
                        }
                        // 满箱 + 零/负区间（同点加油/里程表回拨）：数据异常，
                        // 锚点不动、累计保留，等下一条正常里程的满箱记录对整个区间结算
                        else -> {
                            status = ConsumptionStatus.PENDING
                            pendingCount = anchorCount
                            pendingOil = spanOilAcc
                            pendingActual = spanActualAcc
                        }
                    }
                }
            }

            result.add(
                RecordWithDerived(
                    record = r,
                    distance = distance,
                    oilDays = oilDays,
                    unitPricePayable = unitPricePayable?.round2(),
                    unitPriceActual = unitPriceActual?.round2(),
                    oilConsumption = oilConsumption?.round2(),
                    costPerKm = costPerKm?.round2(),
                    consumptionStatus = status,
                    spanDistance = spanDistance,
                    spanOil = spanOilOut,
                    spanActual = spanActualOut,
                    shortInterval = shortInterval,
                    pendingCount = pendingCount,
                    pendingOil = pendingOil?.round2(),
                    pendingActual = pendingActual?.round2(),
                    distanceAnomaly = anomaly,
                )
            )
            prev = r
        }
        // 异常判定只取正常闭合区间（!shortInterval）：短途单条数字失真大，
        // 不参与均值/HIGH/LOW/异常计数/着色，保持 NORMAL + deviationPct=0（"短途参考"仅作展示）；
        // 总账不受影响——总账由 span* 字段按 Σ油/Σ里程 加权，短途照常计入。
        val validConsumptions = result.filter { !it.shortInterval }.mapNotNull { it.oilConsumption }
        if (validConsumptions.isNotEmpty()) {
            val avg = validConsumptions.average()
            val high = avg * (1 + abnormalPct / 100.0)
            val low = avg * (1 - abnormalPct / 100.0)
            for (i in result.indices) {
                val d = result[i]
                if (d.oilConsumption == null || d.shortInterval) continue // PENDING/NO_BASE/短途保持默认
                val status = when {
                    d.oilConsumption > high -> ConsumptionStatus.HIGH
                    d.oilConsumption < low -> ConsumptionStatus.LOW
                    else -> ConsumptionStatus.NORMAL
                }
                val dev = if (avg > 0) (d.oilConsumption - avg) / avg * 100 else 0.0
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
