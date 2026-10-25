package com.llpop.koil.ui.screen.overview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.llpop.koil.data.local.dao.KOiLRecordDao
import com.llpop.koil.data.local.dao.FuelPriceDao
import com.llpop.koil.data.local.entity.KOiLRecordEntity
import com.llpop.koil.data.oil.OilPriceDbSyncer
import com.llpop.koil.data.oil.OilPriceNetwork
import com.llpop.koil.data.settings.AppSettings
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private const val MIN_DISTANCE = 100.0
private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

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

enum class ConsumptionStatus { HIGH, NORMAL, LOW, SKIPPED }

/** 9 项汇总指标（对齐 Web 版口径） */
data class SummaryStats(
    val totalCount: Int = 0,
    val totalOilAmount: Double = 0.0,
    val totalActual: Double = 0.0,
    val totalPayable: Double = 0.0,
    val totalDistance: Double = 0.0,
    val avgOilConsumption: Double? = null,
    val avgCostPerKm: Double? = null,
    val avgUnitPriceActual: Double? = null,
    val avgDistancePerReoil: Double? = null,
    val abnormalCount: Int = 0,
)

/** 趋势点 */
data class TrendPoint(
    val date: String,
    val oilConsumption: Double? = null,
    val costPerKm: Double? = null,
    val actualAmount: Double = 0.0,
)

data class OverviewUiState(
    val records: List<RecordWithDerived> = emptyList(),
    val stats: SummaryStats = SummaryStats(),
    val prevStats: SummaryStats? = null,
    val trend: List<TrendPoint> = emptyList(),
    val abnormalRecords: List<RecordWithDerived> = emptyList(),
    val avgConsumption: Double = 0.0,
    val range: String = "all",
    val editingRecord: KOiLRecordEntity? = null,
    val editDate: String = "",
    val editMileage: String = "",
    val editOilAmount: String = "",
    val editActualAmount: String = "",
    val showEditDialog: Boolean = false,
    val showBackfillDialog: Boolean = false,
    val backfillDate: String = "",
    val backfillMileage: String = "",
    val backfillOilAmount: String = "",
    val backfillActualAmount: String = "",
    val backfillGrade: String = "92",
    // 编辑/补录偏差预检（对齐首页录入：按加油日期取参考价）
    val editDeviationPct: Double? = null,
    val editDeviationExceeded: Boolean = false,
    val backfillDeviationPct: Double? = null,
    val backfillDeviationExceeded: Boolean = false,
    // 编辑/补录参考单价（按加油日期取，联动油号）与自动核算应付金额
    val editReferencePrice: Double? = null,
    val editPayableAmount: String = "",
    val editGrade: String = "92",
    val editProvince: String = "",
    val backfillReferencePrice: Double? = null,
    val backfillPayableAmount: String = "",
    val backfillProvince: String = "",
    // 日历加油日期标记（当前省份全部加油日期，供日历打点）
    val editRecordDates: Set<String> = emptySet(),
    val backfillRecordDates: Set<String> = emptySet(),
)

@OptIn(kotlinx.coroutines.FlowPreview::class)
class OverviewViewModel(
    private val recordDao: KOiLRecordDao,
    private val settings: AppSettings,
    private val syncer: OilPriceDbSyncer,
    private val priceDao: FuelPriceDao,
) : ViewModel() {

    private val _uiState = MutableStateFlow(OverviewUiState())
    val uiState: StateFlow<OverviewUiState> = _uiState.asStateFlow()

    private var allDerived: List<RecordWithDerived> = emptyList()
    private var lastRawRecords: List<KOiLRecordEntity> = emptyList()
    private var abnormalThresholdPct: Double = AppSettings.DEFAULT_ABNORMAL_THRESHOLD

    /** 编辑/补录预检去抖信号：输入停顿 180ms 后才查库（参考价 + 偏差），输入抖动不再反复打 DB */
    private val editPrecheckFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val backfillPrecheckFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    init {
        viewModelScope.launch {
            editPrecheckFlow.debounce(180).collect { doRunEditPrecheck() }
        }
        viewModelScope.launch {
            backfillPrecheckFlow.debounce(180).collect { doRunBackfillPrecheck() }
        }
        viewModelScope.launch {
            recordDao.observeAll().collect { raw ->
                lastRawRecords = raw.sortedBy { it.reoilDate }
                allDerived = computeDerived(lastRawRecords, abnormalThresholdPct)
                recompute()
            }
        }
        // 监听异常油耗阈值变化，设置页修改后总览页立即重算
        viewModelScope.launch {
            settings.abnormalThreshold.collect { pct ->
                abnormalThresholdPct = pct
                if (lastRawRecords.isNotEmpty()) {
                    allDerived = computeDerived(lastRawRecords, pct)
                    recompute()
                }
            }
        }
    }

    fun setRange(range: String) {
        _uiState.value = _uiState.value.copy(range = range)
        recompute()
    }

    private fun recompute() {
        val range = _uiState.value.range
        val (start, prevStart, prevEnd) = rangeBounds(range)
        val filtered = allDerived.filter {
            val d = LocalDate.parse(it.record.reoilDate, dateFormatter)
            !d.isBefore(start)
        }
        val prevFiltered = allDerived.filter {
            val d = LocalDate.parse(it.record.reoilDate, dateFormatter)
            !d.isBefore(prevStart) && d.isBefore(prevEnd)
        }
        val stats = computeSummary(filtered)
        val prevStats = if (prevFiltered.isNotEmpty()) computeSummary(prevFiltered) else null
        val trend = computeTrend(filtered)
        val avgCons = filtered.mapNotNull { it.oilConsumption }.average().let { if (it.isNaN()) 0.0 else it }
        val abnormal = filtered.filter {
            it.consumptionStatus == ConsumptionStatus.HIGH || it.consumptionStatus == ConsumptionStatus.LOW
        }.sortedByDescending { kotlin.math.abs(it.deviationPct) }

        _uiState.value = _uiState.value.copy(
            records = filtered.reversed(),
            stats = stats,
            prevStats = prevStats,
            trend = trend,
            abnormalRecords = abnormal,
            avgConsumption = avgCons,
        )
    }

    /** 返回 (当前范围起点, 上一周期起点, 上一周期终点) */
    private fun rangeBounds(range: String): Triple<LocalDate, LocalDate, LocalDate> {
        val now = LocalDate.now()
        return when (range) {
            "year" -> {
                val start = LocalDate.of(now.year, 1, 1)
                val prevStart = LocalDate.of(now.year - 1, 1, 1)
                Triple(start, prevStart, start)
            }
            "6m" -> {
                val start = now.minusMonths(6)
                val prevEnd = start
                val prevStart = start.minusMonths(6)
                Triple(start, prevStart, prevEnd)
            }
            "3m" -> {
                val start = now.minusMonths(3)
                val prevEnd = start
                val prevStart = start.minusMonths(3)
                Triple(start, prevStart, prevEnd)
            }
            else -> Triple(LocalDate.of(1970, 1, 1), LocalDate.of(1970, 1, 1), LocalDate.of(1970, 1, 1))
        }
    }

    // ===== 编辑 =====
    fun startEdit(record: KOiLRecordEntity) {
        val province = record.province ?: ""
        _uiState.value = _uiState.value.copy(
            editingRecord = record,
            editDate = record.reoilDate,
            editMileage = record.mileage.toString(),
            editOilAmount = record.oilAmount.toString(),
            editActualAmount = record.actualAmount.toString(),
            editPayableAmount = record.payableAmount.toString(),
            editGrade = record.oilGrade ?: "92",
            editProvince = province,
            editRecordDates = reoilDatesFor(province),
            showEditDialog = true,
        )
        // 打开即跑预检：自动带出参考价（油号单价），避免 ¥--
        runEditPrecheck()
    }

    fun setEditDate(v: String) { _uiState.value = _uiState.value.copy(editDate = v); runEditPrecheck() }
    fun setEditMileage(v: String) { _uiState.value = _uiState.value.copy(editMileage = v); runEditPrecheck() }
    fun setEditOilAmount(v: String) { _uiState.value = _uiState.value.copy(editOilAmount = v); runEditPrecheck() }
    fun setEditActualAmount(v: String) { _uiState.value = _uiState.value.copy(editActualAmount = v); runEditPrecheck() }
    fun setEditGrade(v: String) { _uiState.value = _uiState.value.copy(editGrade = v); runEditPrecheck() }
    fun setEditProvince(v: String) {
        _uiState.value = _uiState.value.copy(editProvince = v, editRecordDates = reoilDatesFor(v))
        runEditPrecheck()
    }
    /** 编辑态应付金额可手动修改（对齐 Web：编辑弹窗应付为可编辑输入框） */
    fun setEditPayableAmount(v: String) { _uiState.value = _uiState.value.copy(editPayableAmount = v) }

    /** 编辑偏差预检入口（去抖信号，实际执行见 doRunEditPrecheck） */
    private fun runEditPrecheck() {
        editPrecheckFlow.tryEmit(Unit)
    }

    /** 编辑偏差预检：按编辑后的加油日期取参考价（对齐首页录入 checkDeviation）；参考价按所选省份联动 */
    private suspend fun doRunEditPrecheck() {
        val s = _uiState.value
        val grade = s.editGrade
        val date = s.editDate
        val amount = s.editOilAmount.toDoubleOrNull()
        val actual = s.editActualAmount.toDoubleOrNull()
        val province = s.editProvince.ifBlank { settings.defaultProvince.first() }
            // 参考单价（按加油日期取 ≤ 它的最新价）
            val label = OilPriceNetwork.GRADE_LABELS[grade] ?: grade
            val refPrice = if (date.isNotBlank()) priceDao.getLatestPrice(province, label, date)?.price else null
            var devPct: Double? = null
            var devExceeded = false
            if (date.isNotBlank() && amount != null && amount > 0 && actual != null && actual > 0) {
                val r = syncer.checkDeviation(province, grade, date, amount, actual)
                devPct = r.deviationPercent
                devExceeded = r.hasDeviation
            }
            _uiState.value = s.copy(
                editReferencePrice = refPrice,
                editDeviationPct = devPct,
                editDeviationExceeded = devExceeded,
            )
    }

    fun saveEdit() {
        val s = _uiState.value
        val r = s.editingRecord ?: return
        // 偏差超阈值 → 拦截保存（对齐首页录入：预检不过不允许提交）
        if (s.editDeviationExceeded) return
        viewModelScope.launch {
            val province = s.editProvince.ifBlank { settings.defaultProvince.first() }
            try {
                recordDao.update(
                    r.copy(
                        reoilDate = _uiState.value.editDate,
                        mileage = _uiState.value.editMileage.toDoubleOrNull() ?: r.mileage,
                        oilAmount = _uiState.value.editOilAmount.toDoubleOrNull() ?: r.oilAmount,
                        payableAmount = _uiState.value.editPayableAmount.toDoubleOrNull() ?: r.payableAmount,
                        actualAmount = _uiState.value.editActualAmount.toDoubleOrNull() ?: r.actualAmount,
                        oilGrade = _uiState.value.editGrade,
                        oilPrice = _uiState.value.editReferencePrice ?: r.oilPrice,
                        province = province,
                    )
                )
                _uiState.value = _uiState.value.copy(showEditDialog = false, editingRecord = null)
            } catch (_: Exception) { }
        }
    }

    fun cancelEdit() {
        _uiState.value = _uiState.value.copy(showEditDialog = false, editingRecord = null)
    }

    /** 该省份全部加油日期（供日历打点标记） */
    private fun reoilDatesFor(province: String): Set<String> =
        lastRawRecords.filter { it.province == province }.map { it.reoilDate }.toSet()

    // ===== 补录弹窗（source=backfill_modal） =====
    fun openBackfill() {
        viewModelScope.launch {
            val home = settings.defaultProvince.first()
            _uiState.value = _uiState.value.copy(
                showBackfillDialog = true,
                backfillDate = LocalDate.now().format(dateFormatter),
                backfillMileage = "",
                backfillOilAmount = "",
                backfillActualAmount = "",
                backfillPayableAmount = "",
                backfillGrade = "92",
                backfillProvince = home,
                backfillRecordDates = reoilDatesFor(home),
            )
            // 打开即跑预检：自动带出参考价（油号单价），避免 ¥--
            runBackfillPrecheck()
        }
    }
    fun setBackfillDate(v: String) { _uiState.value = _uiState.value.copy(backfillDate = v); runBackfillPrecheck() }
    fun setBackfillMileage(v: String) { _uiState.value = _uiState.value.copy(backfillMileage = v); runBackfillPrecheck() }
    fun setBackfillOilAmount(v: String) { _uiState.value = _uiState.value.copy(backfillOilAmount = v); runBackfillPrecheck() }
    fun setBackfillActualAmount(v: String) { _uiState.value = _uiState.value.copy(backfillActualAmount = v); runBackfillPrecheck() }
    fun setBackfillGrade(v: String) { _uiState.value = _uiState.value.copy(backfillGrade = v); runBackfillPrecheck() }
    fun setBackfillProvince(v: String) {
        _uiState.value = _uiState.value.copy(backfillProvince = v, backfillRecordDates = reoilDatesFor(v))
        runBackfillPrecheck()
    }
    /** 应付可编辑（对齐编辑卡：补录卡应付不再只读自动，用户可改） */
    fun setBackfillPayableAmount(v: String) { _uiState.value = _uiState.value.copy(backfillPayableAmount = v) }
    fun cancelBackfill() { _uiState.value = _uiState.value.copy(showBackfillDialog = false) }

    /** 补录偏差预检入口（去抖信号，实际执行见 doRunBackfillPrecheck） */
    private fun runBackfillPrecheck() {
        backfillPrecheckFlow.tryEmit(Unit)
    }

    /** 补录偏差预检：按补录的加油日期取参考价（对齐首页录入 checkDeviation）；应付由用户输入（对齐编辑卡）；参考价按所选省份联动 */
    private suspend fun doRunBackfillPrecheck() {
        val s = _uiState.value
        val grade = s.backfillGrade
        val date = s.backfillDate
        val amount = s.backfillOilAmount.toDoubleOrNull()
        val actual = s.backfillActualAmount.toDoubleOrNull()
        val province = s.backfillProvince.ifBlank { settings.defaultProvince.first() }
            // 参考单价（按加油日期取 ≤ 它的最新价）
            val label = OilPriceNetwork.GRADE_LABELS[grade] ?: grade
            val refPrice = if (date.isNotBlank()) priceDao.getLatestPrice(province, label, date)?.price else null
            var devPct: Double? = null
            var devExceeded = false
            if (date.isNotBlank() && amount != null && amount > 0 && actual != null && actual > 0) {
                val r = syncer.checkDeviation(province, grade, date, amount, actual)
                devPct = r.deviationPercent
                devExceeded = r.hasDeviation
            }
            _uiState.value = s.copy(
                backfillReferencePrice = refPrice,
                backfillDeviationPct = devPct,
                backfillDeviationExceeded = devExceeded,
            )
    }

    fun saveBackfill() {
        val s = _uiState.value
        val mileage = s.backfillMileage.toDoubleOrNull() ?: return
        val oilAmount = s.backfillOilAmount.toDoubleOrNull() ?: return
        val actual = s.backfillActualAmount.toDoubleOrNull() ?: return
        if (s.backfillDate.isBlank() || mileage <= 0 || oilAmount <= 0 || actual < 0) return
        // 偏差超阈值 → 拦截保存（对齐首页录入：预检不过不允许提交）
        if (s.backfillDeviationExceeded) return
        viewModelScope.launch {
            val payable = s.backfillPayableAmount.toDoubleOrNull() ?: actual
            val province = s.backfillProvince.ifBlank { settings.defaultProvince.first() }
            recordDao.insert(
                KOiLRecordEntity(
                    reoilDate = s.backfillDate,
                    mileage = mileage,
                    oilAmount = oilAmount,
                    payableAmount = payable,
                    actualAmount = actual,
                    oilPrice = s.backfillReferencePrice ?: (if (oilAmount > 0) actual / oilAmount else null),
                    oilGrade = s.backfillGrade,
                    province = province,
                    source = "backfill_modal",
                )
            )
            _uiState.value = _uiState.value.copy(showBackfillDialog = false)
        }
    }

    /** 删除记录 */
    fun deleteRecord(id: Long) {
        viewModelScope.launch { recordDao.deleteById(id) }
    }

    // ===== 计算 =====
    private fun computeDerived(sorted: List<KOiLRecordEntity>, abnormalPct: Double): List<RecordWithDerived> {
        val result = mutableListOf<RecordWithDerived>()
        var prev: KOiLRecordEntity? = null
        for (r in sorted) {
            val distance = prev?.let { r.mileage - it.mileage }
            val oilDays = prev?.let {
                val d1 = LocalDate.parse(r.reoilDate, dateFormatter)
                val d0 = LocalDate.parse(it.reoilDate, dateFormatter)
                java.time.temporal.ChronoUnit.DAYS.between(d0, d1)
            }
            val unitPricePayable = if (r.oilAmount > 0) r.payableAmount / r.oilAmount else null
            val unitPriceActual = if (r.oilAmount > 0) r.actualAmount / r.oilAmount else null
            val validDistance = distance != null && distance >= MIN_DISTANCE
            // 用 takeIf + let 避免重复判空（清理 "Condition is always true" 编译警告）
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
                    shortDistanceSkipped = distance != null && distance < MIN_DISTANCE,
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

    private fun computeSummary(derived: List<RecordWithDerived>): SummaryStats {
        val totalCount = derived.size
        val totalOilAmount = derived.sumOf { it.record.oilAmount }
        val totalActual = derived.sumOf { it.record.actualAmount }
        val totalPayable = derived.sumOf { it.record.payableAmount }
        val valid = derived.filter { it.distance != null && it.distance >= MIN_DISTANCE }
        val totalDistance = valid.sumOf { it.distance ?: 0.0 }
        val validOil = valid.sumOf { it.record.oilAmount }
        val validActual = valid.sumOf { it.record.actualAmount }
        val avgOilConsumption = if (totalDistance > 0) validOil / totalDistance * 100 else null
        val avgCostPerKm = if (totalDistance > 0) validActual / totalDistance else null
        val avgUnitPriceActual = if (totalOilAmount > 0) totalActual / totalOilAmount else null
        val avgDistancePerReoil = if (valid.isNotEmpty()) totalDistance / valid.size else null
        val abnormalCount = derived.count { it.consumptionStatus == ConsumptionStatus.HIGH || it.consumptionStatus == ConsumptionStatus.LOW }
        return SummaryStats(
            totalCount = totalCount,
            totalOilAmount = Math.round(totalOilAmount * 100) / 100.0,
            totalActual = Math.round(totalActual * 100) / 100.0,
            totalPayable = Math.round(totalPayable * 100) / 100.0,
            totalDistance = Math.round(totalDistance * 100) / 100.0,
            avgOilConsumption = avgOilConsumption?.let { Math.round(it * 100) / 100.0 },
            avgCostPerKm = avgCostPerKm?.let { Math.round(it * 100) / 100.0 },
            avgUnitPriceActual = avgUnitPriceActual?.let { Math.round(it * 100) / 100.0 },
            avgDistancePerReoil = avgDistancePerReoil?.let { Math.round(it * 100) / 100.0 },
            abnormalCount = abnormalCount,
        )
    }

    private fun computeTrend(derived: List<RecordWithDerived>): List<TrendPoint> =
        derived.map {
            TrendPoint(
                date = it.record.reoilDate.takeLast(5),
                oilConsumption = it.oilConsumption,
                costPerKm = it.costPerKm,
                actualAmount = it.record.actualAmount,
            )
        }
}
