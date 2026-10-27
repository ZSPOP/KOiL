package com.llpop.koil.ui.screen.overview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.llpop.koil.data.local.dao.KOiLRecordDao
import com.llpop.koil.data.local.dao.KOiLPriceDao
import com.llpop.koil.data.local.entity.KOiLRecordEntity
import com.llpop.koil.data.oil.OilPriceDbSyncer
import com.llpop.koil.data.oil.OilPriceNetwork
import com.llpop.koil.data.settings.AppSettings
import com.llpop.koil.domain.logic.ConsumptionStatus
import com.llpop.koil.domain.logic.DerivedCalculator
import com.llpop.koil.domain.logic.RecordWithDerived
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter

// v1.5.20：RecordWithDerived / ConsumptionStatus / computeDerived 已抽取到
// domain.logic.DerivedCalculator（总览页与 CSV 导出共用同一口径）
private const val MIN_DISTANCE = DerivedCalculator.MIN_DISTANCE
private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

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
    // v1.5.13：编辑卡校验复刻补录卡（唯一差异：日期只读，校验排除自身 id）
    val editBeforeFirst: Boolean = false,
    val editSameDayExists: Boolean = false,
    val editMileageConflictType: String = "",
    val editMileageConflictDate: String = "",
    val editMileageConflictValue: Double = 0.0,
    val editOverPayable: Boolean = false,
    val editOverTank: Boolean = false,
    val editTankCapacity: Double = 0.0,
    // 编辑日期（只读）的里程区间（prev+1 ~ next-1，null 表示该侧无界）
    val editRangeMin: Double? = null,
    val editRangeMax: Double? = null,
    val editReady: Boolean = false,
    // v1.5.11：补录校验结果（用户确认规则：日期边界/同日重复/双向里程链 + 首页口径）
    val backfillBeforeFirst: Boolean = false,
    val backfillSameDayExists: Boolean = false,
    // 里程链冲突：type = "prev"（需大于）/ "next"（需小于），携带对端日期与里程供 UI 三语渲染
    val backfillMileageConflictType: String = "",
    val backfillMileageConflictDate: String = "",
    val backfillMileageConflictValue: Double = 0.0,
    // 首页口径：价格偏差A+B / 实付>应付 / 油箱容量
    val backfillDeviationPct: Double? = null,
    val backfillDeviationExceeded: Boolean = false,
    val backfillOverPayable: Boolean = false,
    val backfillOverTank: Boolean = false,
    // 选日期后该区间可输入里程范围（prev+1 ~ next-1，null 表示该侧无界）
    val backfillRangeMin: Double? = null,
    val backfillRangeMax: Double? = null,
    // 油箱容量（设置页实时值，供 overTank 提示）
    val backfillTankCapacity: Double = 0.0,
    val backfillReady: Boolean = false,
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
    // v1.5.16：补录/编辑保存防重入（对齐首页 isAdding）+ 失败提示（不再静默吞异常）
    val isSavingBackfill: Boolean = false,
    val isSavingEdit: Boolean = false,
    val saveError: Boolean = false,
)

@OptIn(kotlinx.coroutines.FlowPreview::class)
class OverviewViewModel(
    private val recordDao: KOiLRecordDao,
    private val settings: AppSettings,
    private val syncer: OilPriceDbSyncer,
    private val priceDao: KOiLPriceDao,
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
            // v1.5.20：debounce 合并连续写入（批量导入/快速增删），120ms 停顿后只重算一次；
            // 排序 + 派生计算（O(n)）切 Dispatchers.Default，不再阻塞主线程；状态更新仍回主线程
            recordDao.observeAll().debounce(120).collect { raw ->
                val pair = withContext(Dispatchers.Default) {
                    val sorted = raw.sortedBy { it.reoilDate }
                    sorted to DerivedCalculator.compute(sorted, abnormalThresholdPct)
                }
                lastRawRecords = pair.first
                allDerived = pair.second
                recompute()
            }
        }
        // 监听异常油耗阈值变化，设置页修改后总览页立即重算
        viewModelScope.launch {
            settings.abnormalThreshold.collect { pct ->
                abnormalThresholdPct = pct
                if (lastRawRecords.isNotEmpty()) {
                    allDerived = withContext(Dispatchers.Default) {
                        DerivedCalculator.compute(lastRawRecords, pct)
                    }
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
            // v1.5.14：应付由参考价自动算，打开瞬间未算出 → 空（显示"待计算"），precheck 完成后即时刷新
            editPayableAmount = "",
            editGrade = record.oilGrade ?: "92",
            editProvince = province,
            editRecordDates = reoilDatesFor(province),
            // v1.5.13：打开即重置编辑卡校验态（对齐补录卡）
            editBeforeFirst = false,
            editSameDayExists = false,
            editMileageConflictType = "",
            editMileageConflictDate = "",
            editMileageConflictValue = 0.0,
            editOverPayable = false,
            editOverTank = false,
            editRangeMin = null,
            editRangeMax = null,
            editReady = false,
            isSavingEdit = false,
            saveError = false,
            showEditDialog = true,
        )
        // 打开即跑预检：自动带出参考价（油号单价）与应付自动算，避免 ¥--
        runEditPrecheck()
    }

    fun setEditDate(v: String) { _uiState.value = _uiState.value.copy(editDate = v); runEditPrecheck() }
    fun setEditMileage(v: String) { _uiState.value = _uiState.value.copy(editMileage = v); runEditPrecheck() }
    fun setEditOilAmount(v: String) {
        _uiState.value = _uiState.value.copy(editOilAmount = v)
        // v1.5.13：应付自动计算（对齐补录卡/首页 updatePayable：油量 × 参考单价）
        updateEditPayable()
        runEditPrecheck()
    }
    fun setEditActualAmount(v: String) { _uiState.value = _uiState.value.copy(editActualAmount = v); runEditPrecheck() }
    fun setEditGrade(v: String) { _uiState.value = _uiState.value.copy(editGrade = v); updateEditPayable(); runEditPrecheck() }
    fun setEditProvince(v: String) {
        _uiState.value = _uiState.value.copy(editProvince = v, editRecordDates = reoilDatesFor(v))
        runEditPrecheck()
    }
    /** v1.5.13：编辑卡应付只读自动计算（对齐补录卡），setter 保留兼容，UI 不再可编辑 */
    fun setEditPayableAmount(v: String) { _uiState.value = _uiState.value.copy(editPayableAmount = v) }

    /** v1.5.13：编辑卡应付金额自动计算（对齐补录卡 updateBackfillPayable），油量/油号/参考价变化时即时刷新；v1.5.14：无法计算时清空显示待计算 */
    private fun updateEditPayable() {
        val s = _uiState.value
        val amount = s.editOilAmount.toDoubleOrNull()
        val price = s.editReferencePrice
        val payable = if (amount != null && price != null && amount > 0 && price > 0)
            String.format("%.2f", amount * price)
        else ""
        _uiState.value = s.copy(editPayableAmount = payable)
    }

    /** 编辑偏差预检入口（去抖信号，实际执行见 doRunEditPrecheck） */
    private fun runEditPrecheck() {
        editPrecheckFlow.tryEmit(Unit)
    }

    /** 编辑校验（v1.5.13 复刻补录卡：日期边界/同日重复/双向里程链 + 偏差A+B/实付>应付/油箱容量；唯一差异：日期只读、校验排除自身 id） */
    private suspend fun doRunEditPrecheck() {
        val s = _uiState.value
        val r = s.editingRecord ?: return
        val grade = s.editGrade
        val date = s.editDate
        val mileage = s.editMileage.toDoubleOrNull()
        val amount = s.editOilAmount.toDoubleOrNull()
        val actual = s.editActualAmount.toDoubleOrNull()
        val payableInput = s.editPayableAmount.toDoubleOrNull()
        val province = s.editProvince.ifBlank { settings.defaultProvince.first() }
        // 参考单价（按加油日期取 ≤ 它的最新价）
        val label = OilPriceNetwork.GRADE_LABELS[grade] ?: grade
        val refPrice = if (date.isNotBlank()) priceDao.getLatestPrice(province, label, date)?.price else null
        // 油箱容量（首页口径）
        val tankCapacity = settings.tankCapacity.first().toDouble()

        var beforeFirst = false
        var sameDayExists = false
        var mileageConflictType = ""
        var mileageConflictDate = ""
        var mileageConflictValue = 0.0
        var devPct: Double? = null
        var devExceeded = false
        var overPayable = false
        var overTank = false
        var rangeMin: Double? = null
        var rangeMax: Double? = null

        if (date.isNotBlank()) {
            // 1) 不早于第一条（排除自身：编辑对象就是最早记录时不误判）
            val earliest = recordDao.getEarliestExcluding(r.id)
            if (earliest != null) {
                beforeFirst = date < earliest.reoilDate ||
                    (date == earliest.reoilDate && mileage != null && mileage < earliest.mileage)
            }
            // 2) 同日同里程重复（排除自身）
            val sameDay = recordDao.getSameDayRecordsExcluding(date, r.id)
            if (mileage != null) {
                sameDayExists = sameDay.any { it.mileage == mileage }
            }
            // 3) 里程链（prev < date 需大于、next > date 需小于；日期只读 → prev/next 天然不含自身）
            val prev = recordDao.getBeforeDate(date)
            val next = recordDao.getAfterDate(date)
            if (mileage != null && mileage > 0) {
                when {
                    prev != null && mileage <= prev.mileage -> {
                        mileageConflictType = "prev"
                        mileageConflictDate = prev.reoilDate
                        mileageConflictValue = prev.mileage
                    }
                    next != null && mileage >= next.mileage -> {
                        mileageConflictType = "next"
                        mileageConflictDate = next.reoilDate
                        mileageConflictValue = next.mileage
                    }
                }
            }
            // 4) 该日期区间可输入里程范围（prev+1 ~ next-1，无界侧为 null）
            rangeMin = prev?.let { it.mileage + 1 }
            rangeMax = next?.let { it.mileage - 1 }
            if (rangeMin != null && rangeMax != null && rangeMin > rangeMax) {
                rangeMin = null
                rangeMax = null
            }
        }

        // 4) 价格偏差（首页口径 A+B：A 参考价偏差优先，B 无参考价回退实付vs应付，阈值=设置页）
        if (date.isNotBlank() && amount != null && amount > 0 && actual != null && actual > 0) {
            val result = syncer.checkDeviation(province, grade, date, amount, actual)
            val threshold = result.thresholdPercent
            if (result.referencePrice != null) {
                devPct = result.deviationPercent
                devExceeded = result.hasDeviation
            } else {
                val payable = if (payableInput != null && payableInput > 0) payableInput
                else if (refPrice != null && refPrice > 0) amount * refPrice
                else null
                val pct = if (payable != null && payable > 0)
                    kotlin.math.abs(actual - payable) / payable * 100 else 0.0
                devPct = pct
                devExceeded = pct > threshold
            }
            // 5) 实付>应付 一律拦截（=允许）
            val payableForCheck = if (payableInput != null && payableInput > 0) payableInput
            else if (refPrice != null && refPrice > 0) amount * refPrice
            else null
            if (payableForCheck != null && payableForCheck > 0 && actual > payableForCheck) {
                overPayable = true
            }
        }
        // 6) 油箱容量（加油量 > 油箱容量）
        if (amount != null && amount > tankCapacity && tankCapacity > 0) {
            overTank = true
        }

        val ready = date.isNotBlank() &&
            mileage != null && mileage > 0 &&
            amount != null && amount > 0 &&
            actual != null && actual > 0 &&
            !beforeFirst && !sameDayExists &&
            mileageConflictType.isEmpty() &&
            !devExceeded && !overPayable && !overTank

        _uiState.value = s.copy(
            editReferencePrice = refPrice,
            editDeviationPct = devPct,
            editDeviationExceeded = devExceeded,
            editBeforeFirst = beforeFirst,
            editSameDayExists = sameDayExists,
            editMileageConflictType = mileageConflictType,
            editMileageConflictDate = mileageConflictDate,
            editMileageConflictValue = mileageConflictValue,
            editOverPayable = overPayable,
            editOverTank = overTank,
            editTankCapacity = tankCapacity,
            editRangeMin = rangeMin,
            editRangeMax = rangeMax,
            editReady = ready,
            saveError = false,
        )
        // v1.5.14：参考价就绪后立即刷新应付自动算（避免打开弹窗停留在"待计算"）
        updateEditPayable()
    }

    fun saveEdit() {
        val s = _uiState.value
        val r = s.editingRecord ?: return
        // v1.5.13：对齐补录卡——预检全通过（日期边界/同日重复/里程链/偏差A+B/实付>应付/油箱容量）才允许保存
        // v1.5.16：增加 isSavingEdit 防重入（快速双击不再并发 update）+ 失败提示（不再静默吞异常）
        if (s.editDate.isBlank() || !s.editReady || s.isSavingEdit) return
        viewModelScope.launch {
            val cur = _uiState.value
            if (cur.isSavingEdit) return@launch
            _uiState.value = cur.copy(isSavingEdit = true, saveError = false)
            val province = cur.editProvince.ifBlank { settings.defaultProvince.first() }
            try {
                recordDao.update(
                    r.copy(
                        reoilDate = cur.editDate,
                        mileage = cur.editMileage.toDoubleOrNull() ?: r.mileage,
                        oilAmount = cur.editOilAmount.toDoubleOrNull() ?: r.oilAmount,
                        // v1.5.16：应付算不出时回退本次实付（对齐补录卡），不再保留旧应付
                        payableAmount = cur.editPayableAmount.toDoubleOrNull()
                            ?: (cur.editActualAmount.toDoubleOrNull() ?: r.payableAmount),
                        actualAmount = cur.editActualAmount.toDoubleOrNull() ?: r.actualAmount,
                        oilGrade = cur.editGrade,
                        oilPrice = cur.editReferencePrice ?: r.oilPrice,
                        province = province,
                    )
                )
                _uiState.value = _uiState.value.copy(
                    showEditDialog = false, editingRecord = null,
                    isSavingEdit = false, saveError = false,
                )
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(isSavingEdit = false, saveError = true)
            }
        }
    }

    fun cancelEdit() {
        _uiState.value = _uiState.value.copy(
            showEditDialog = false, editingRecord = null,
            isSavingEdit = false, saveError = false,
        )
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
                isSavingBackfill = false,
                saveError = false,
            )
            // 打开即跑预检：自动带出参考价（油号单价），避免 ¥--
            runBackfillPrecheck()
        }
    }
    fun setBackfillDate(v: String) { _uiState.value = _uiState.value.copy(backfillDate = v); runBackfillPrecheck() }
    fun setBackfillMileage(v: String) { _uiState.value = _uiState.value.copy(backfillMileage = v); runBackfillPrecheck() }
    fun setBackfillOilAmount(v: String) {
        _uiState.value = _uiState.value.copy(backfillOilAmount = v)
        // v1.5.12：应付自动计算（对齐首页 updatePayable：油量 × 参考单价）
        updateBackfillPayable()
        runBackfillPrecheck()
    }
    fun setBackfillActualAmount(v: String) { _uiState.value = _uiState.value.copy(backfillActualAmount = v); runBackfillPrecheck() }
    fun setBackfillGrade(v: String) { _uiState.value = _uiState.value.copy(backfillGrade = v); runBackfillPrecheck() }
    fun setBackfillProvince(v: String) {
        _uiState.value = _uiState.value.copy(backfillProvince = v, backfillRecordDates = reoilDatesFor(v))
        runBackfillPrecheck()
    }
    /** 应付只读自动计算（对齐首页）：setter 保留兼容，UI 不再可编辑 */
    fun setBackfillPayableAmount(v: String) { _uiState.value = _uiState.value.copy(backfillPayableAmount = v) }

    /** v1.5.12：补录应付金额自动计算（对齐首页 updatePayable），油量或参考价变化时即时刷新；v1.5.14：无法计算时清空显示待计算 */
    private fun updateBackfillPayable() {
        val s = _uiState.value
        val amount = s.backfillOilAmount.toDoubleOrNull()
        val price = s.backfillReferencePrice
        val payable = if (amount != null && price != null && amount > 0 && price > 0)
            String.format("%.2f", amount * price)
        else ""
        _uiState.value = s.copy(backfillPayableAmount = payable)
    }
    fun cancelBackfill() {
        _uiState.value = _uiState.value.copy(
            showBackfillDialog = false,
            isSavingBackfill = false, saveError = false,
        )
    }

    /** 补录偏差预检入口（去抖信号，实际执行见 doRunBackfillPrecheck） */
    private fun runBackfillPrecheck() {
        backfillPrecheckFlow.tryEmit(Unit)
    }

    /** 补录偏差预检（v1.5.11 用户确认规则：日期边界/同日重复/双向里程链 + 首页口径 A+B偏差/实付>应付/油箱容量） */
    private suspend fun doRunBackfillPrecheck() {
        val s = _uiState.value
        val grade = s.backfillGrade
        val date = s.backfillDate
        val mileage = s.backfillMileage.toDoubleOrNull()
        val amount = s.backfillOilAmount.toDoubleOrNull()
        val actual = s.backfillActualAmount.toDoubleOrNull()
        val payableInput = s.backfillPayableAmount.toDoubleOrNull()
        val province = s.backfillProvince.ifBlank { settings.defaultProvince.first() }
        // 参考单价（按加油日期取 ≤ 它的最新价）
        val label = OilPriceNetwork.GRADE_LABELS[grade] ?: grade
        val refPrice = if (date.isNotBlank()) priceDao.getLatestPrice(province, label, date)?.price else null
        // 油箱容量（首页口径）
        val tankCapacity = settings.tankCapacity.first().toDouble()

        var beforeFirst = false
        var sameDayExists = false
        var mileageConflictType = ""
        var mileageConflictDate = ""
        var mileageConflictValue = 0.0
        var devPct: Double? = null
        var devExceeded = false
        var overPayable = false
        var overTank = false
        var rangeMin: Double? = null
        var rangeMax: Double? = null

        if (date.isNotBlank()) {
            // 1) 不早于第一条（date < firstDate 或 同日且里程更小）
            val earliest = recordDao.getEarliest()
            if (earliest != null) {
                beforeFirst = date < earliest.reoilDate ||
                    (date == earliest.reoilDate && mileage != null && mileage < earliest.mileage)
            }
            // 2) 同日同里程重复
            val sameDay = recordDao.getSameDayRecords(date)
            if (mileage != null) {
                sameDayExists = sameDay.any { it.mileage == mileage }
            }
            // 3) 里程链（prev < date 需大于、next > date 需小于）
            val prev = recordDao.getBeforeDate(date)
            val next = recordDao.getAfterDate(date)
            if (mileage != null && mileage > 0) {
                when {
                    prev != null && mileage <= prev.mileage -> {
                        mileageConflictType = "prev"
                        mileageConflictDate = prev.reoilDate
                        mileageConflictValue = prev.mileage
                    }
                    next != null && mileage >= next.mileage -> {
                        mileageConflictType = "next"
                        mileageConflictDate = next.reoilDate
                        mileageConflictValue = next.mileage
                    }
                }
            }
            // 4) 选日期后该区间可输入里程范围（prev+1 ~ next-1，无界侧为 null）
            rangeMin = prev?.let { it.mileage + 1 }
            rangeMax = next?.let { it.mileage - 1 }
            if (rangeMin != null && rangeMax != null && rangeMin > rangeMax) {
                // 区间异常（前一条大于后一条）→ 不提示范围，仅里程链冲突会拦截
                rangeMin = null
                rangeMax = null
            }
        }

        // 4) 价格偏差（首页口径 A+B：A 参考价偏差优先，B 无参考价回退实付vs应付，阈值=设置页）
        if (date.isNotBlank() && amount != null && amount > 0 && actual != null && actual > 0) {
            val result = syncer.checkDeviation(province, grade, date, amount, actual)
            val threshold = result.thresholdPercent
            if (result.referencePrice != null) {
                devPct = result.deviationPercent
                devExceeded = result.hasDeviation
            } else {
                val payable = if (payableInput != null && payableInput > 0) payableInput
                else if (refPrice != null && refPrice > 0) amount * refPrice
                else null
                val pct = if (payable != null && payable > 0)
                    kotlin.math.abs(actual - payable) / payable * 100 else 0.0
                devPct = pct
                devExceeded = pct > threshold
            }
            // 5) 实付>应付 一律拦截（=允许）
            val payableForCheck = if (payableInput != null && payableInput > 0) payableInput
            else if (refPrice != null && refPrice > 0) amount * refPrice
            else null
            if (payableForCheck != null && payableForCheck > 0 && actual > payableForCheck) {
                overPayable = true
            }
        }
        // 6) 油箱容量（加油量 > 油箱容量）
        if (amount != null && amount > tankCapacity && tankCapacity > 0) {
            overTank = true
        }

        val ready = date.isNotBlank() &&
            mileage != null && mileage > 0 &&
            amount != null && amount > 0 &&
            actual != null && actual > 0 &&
            !beforeFirst && !sameDayExists &&
            mileageConflictType.isEmpty() &&
            !devExceeded && !overPayable && !overTank

        _uiState.value = s.copy(
            backfillReferencePrice = refPrice,
            backfillBeforeFirst = beforeFirst,
            backfillSameDayExists = sameDayExists,
            backfillMileageConflictType = mileageConflictType,
            backfillMileageConflictDate = mileageConflictDate,
            backfillMileageConflictValue = mileageConflictValue,
            backfillDeviationPct = devPct,
            backfillDeviationExceeded = devExceeded,
            backfillOverPayable = overPayable,
            backfillOverTank = overTank,
            backfillRangeMin = rangeMin,
            backfillRangeMax = rangeMax,
            backfillTankCapacity = tankCapacity,
            backfillReady = ready,
            saveError = false,
        )
        // v1.5.14：参考价就绪后立即刷新应付自动算（避免打开弹窗停留在"待计算"）
        updateBackfillPayable()
    }

    fun saveBackfill() {
        val s = _uiState.value
        val mileage = s.backfillMileage.toDoubleOrNull() ?: return
        val oilAmount = s.backfillOilAmount.toDoubleOrNull() ?: return
        val actual = s.backfillActualAmount.toDoubleOrNull() ?: return
        // v1.5.11：用户确认规则——预检全通过（日期边界/同日重复/里程链/偏差A+B/实付>应付/油箱容量）才允许保存
        // v1.5.16：增加 isSavingBackfill 防重入（快速双击不再并发 insert 撞唯一约束崩溃）+ try-catch 失败提示
        if (s.backfillDate.isBlank() || !s.backfillReady || s.isSavingBackfill) return
        viewModelScope.launch {
            val cur = _uiState.value
            if (cur.isSavingBackfill) return@launch
            _uiState.value = cur.copy(isSavingBackfill = true, saveError = false)
            val curMileage = cur.backfillMileage.toDoubleOrNull() ?: return@launch
            val curOil = cur.backfillOilAmount.toDoubleOrNull() ?: return@launch
            val curActual = cur.backfillActualAmount.toDoubleOrNull() ?: return@launch
            val payable = cur.backfillPayableAmount.toDoubleOrNull() ?: curActual
            val province = cur.backfillProvince.ifBlank { settings.defaultProvince.first() }
            try {
                recordDao.insert(
                    KOiLRecordEntity(
                        reoilDate = cur.backfillDate,
                        mileage = curMileage,
                        oilAmount = curOil,
                        payableAmount = payable,
                        actualAmount = curActual,
                        oilPrice = cur.backfillReferencePrice ?: (if (curOil > 0) curActual / curOil else null),
                        oilGrade = cur.backfillGrade,
                        province = province,
                        source = "backfill_modal",
                    )
                )
                _uiState.value = _uiState.value.copy(
                    showBackfillDialog = false,
                    isSavingBackfill = false,
                    saveError = false,
                )
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(isSavingBackfill = false, saveError = true)
            }
        }
    }

    /** 删除记录 */
    fun deleteRecord(id: Long) {
        viewModelScope.launch { recordDao.deleteById(id) }
    }

    // ===== 计算 =====
    // 派生字段（distance/油耗/每公里成本/油耗状态）统一由 domain.logic.DerivedCalculator.compute 计算

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
