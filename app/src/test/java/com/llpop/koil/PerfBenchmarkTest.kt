package com.llpop.koil

import com.llpop.koil.data.local.entity.KOiLRecordEntity
import com.llpop.koil.domain.logic.ConsumptionStatus
import com.llpop.koil.domain.logic.DerivedCalculator
import com.llpop.koil.domain.logic.RecordWithDerived
import org.junit.Test
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 临时压测（非业务测试）：量化大记录量下「全量派生计算 + 范围过滤 + 汇总」的耗时，
 * 用于验证总览页 O(n) 路径在 1k / 10k / 50k 记录下的表现。
 */
class PerfBenchmarkTest {

    private val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    private fun gen(n: Int): List<KOiLRecordEntity> {
        val base = LocalDate.of(2016, 1, 1)
        var mileage = 0.0
        return (0 until n).map { i ->
            mileage += 450.0 + (i % 7) * 30
            KOiLRecordEntity(
                id = (i + 1).toLong(),
                reoilDate = base.plusDays(i * 3L).toString(),
                mileage = mileage,
                oilAmount = 38.0 + (i % 11),
                payableAmount = 300.0 + (i % 13),
                actualAmount = 295.0 + (i % 17),
                oilPrice = 7.8,
                oilGrade = if (i % 5 == 0) "95" else "92",
                province = "广东",
                source = "entry_page",
            )
        }
    }

    /** 复刻 OverviewViewModel.recompute() 的工作量（不含 UI 状态 copy） */
    private fun recomputeSim(derived: List<RecordWithDerived>): Int {
        val start = LocalDate.now().minusMonths(6)
        val prevStart = start.minusMonths(6)
        val filtered = derived.filter { !LocalDate.parse(it.record.reoilDate, fmt).isBefore(start) }
        val prevFiltered = derived.filter {
            val d = LocalDate.parse(it.record.reoilDate, fmt)
            !d.isBefore(prevStart) && d.isBefore(start)
        }
        var sum = 0.0
        filtered.forEach { sum += it.record.oilAmount }
        prevFiltered.forEach { sum += it.record.actualAmount }
        val avgCons = filtered.mapNotNull { it.oilConsumption }.average()
        val abnormal = filtered.filter {
            it.consumptionStatus == ConsumptionStatus.HIGH || it.consumptionStatus == ConsumptionStatus.LOW
        }.sortedByDescending { kotlin.math.abs(it.deviationPct) }
        return filtered.size + abnormal.size + sum.toInt() + avgCons.toInt()
    }

    /** 优化候选：日期字符串比较（yyyy-MM-dd 字典序 == 时间序），零解析 */
    private fun recomputeFastSim(derived: List<RecordWithDerived>): Int {
        val startStr = LocalDate.now().minusMonths(6).toString()
        val prevStartStr = LocalDate.now().minusMonths(12).toString()
        val filtered = derived.filter { it.record.reoilDate >= startStr }
        val prevFiltered = derived.filter { it.record.reoilDate >= prevStartStr && it.record.reoilDate < startStr }
        var sum = 0.0
        filtered.forEach { sum += it.record.oilAmount }
        prevFiltered.forEach { sum += it.record.actualAmount }
        val avgCons = filtered.mapNotNull { it.oilConsumption }.average()
        val abnormal = filtered.filter {
            it.consumptionStatus == ConsumptionStatus.HIGH || it.consumptionStatus == ConsumptionStatus.LOW
        }.sortedByDescending { kotlin.math.abs(it.deviationPct) }
        return filtered.size + abnormal.size + sum.toInt() + avgCons.toInt()
    }

    private fun ms(block: () -> Unit): Double {
        val t0 = System.nanoTime()
        block()
        return (System.nanoTime() - t0) / 1_000_000.0
    }

    @Test
    fun benchmark() {
        // 预热 JIT
        repeat(3) { recomputeSim(DerivedCalculator.compute(gen(500), 20.0)) }

        println("---- 派生计算（数据变更时，跑在 Dispatchers.Default）----")
        for (n in intArrayOf(200, 500, 2_000, 10_000, 50_000)) {
            val raw = gen(n)
            val runs = if (n <= 2_000) 5 else 3
            var t = 0.0
            var derived: List<RecordWithDerived> = emptyList()
            repeat(runs) { t += ms { derived = DerivedCalculator.compute(raw, 20.0) } }
            println("n=%6d  compute=%6.2f ms  (${derived.size} 条派生)".format(n, t / runs))
        }

        println("---- recompute() 现状 vs 字符串比较（当前跑在主线程）----")
        for (n in intArrayOf(200, 500, 2_000, 10_000, 50_000)) {
            val raw = gen(n)
            val derived = DerivedCalculator.compute(raw, 20.0)
            val runs = if (n <= 2_000) 5 else 3
            var tOld = 0.0
            var tNew = 0.0
            repeat(runs) {
                tOld += ms { recomputeSim(derived) }
                tNew += ms { recomputeFastSim(derived) }
            }
            println("n=%6d  现状=%6.2f ms  字符串比较=%5.2f ms  提速 %.1fx".format(n, tOld / runs, tNew / runs, (tOld / tNew)))
        }
    }
}
