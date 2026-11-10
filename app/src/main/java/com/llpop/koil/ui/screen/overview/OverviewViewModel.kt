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
import com.llpop.koil.domain.logic.MileageChain
import com.llpop.koil.domain.logic.RecordValidator
import com.llpop.koil.domain.logic.RecordWithDerived
import com.llpop.koil.domain.logic.SummaryMath
import com.llpop.koil.domain.logic.TrendSampler
import com.llpop.koil.domain.logic.inChronologicalOrder
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

// RecordWithDerived / ConsumptionStatus / computeDerived 统一走
// domain.logic.DerivedCalculator（总览页与 CSV 导出共用同一口径）
private const val MIN_DISTANCE = DerivedCalculator.MIN_DISTANCE
/** 趋势点抽样上限——记录极多时均匀抽样，避免全量点进 UI */
private const val MAX_TREND_POINTS = 60
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
    /** avgOilConsumption/avgCostPerKm 来自"长期平均"兜底（无满箱锚点：总油/总里程），须带标识展示 */
    val longTermAvg: Boolean = false,
)

/** 趋势点 */
data class TrendPoint(
    val date: String,
    val oilConsumption: Double? = null,
    val costPerKm: Double? = null,
    val actualAmount: Double = 0.0,
    /** 短途闭合区间（<100km）：单条数字失真风险大，趋势中隐藏（总账仍计入） */
    val lowConfidence: Boolean = false,
)

data class OverviewUiState(
    val records: List<RecordWithDerived> = emptyList(),
    val stats: SummaryStats = SummaryStats(),
    val prevStats: SummaryStats? = null,
    val trend: List<TrendPoint> = emptyList(),
    val abnormalRecords: List<RecordWithDerived> = emptyList(),
    val avgConsumption: Double = 0.0,
    val range: String = "all",
    /** 趋势图油号筛选："all"=全部油号合并，否则为 92/95/98/0（仅作用于趋势图） */
    val trendGrade: String = "all",
)

/**
 * 编辑/补录弹窗的全部输入态、预检结果与保存标志独立成第二条状态流 DialogUiState。
 * 不塞进 OverviewUiState：否则弹窗每敲一个字符 → copy 出全新 state 实例 →
 * 其中 List/Set 是 Compose 不稳定类型 → "值未变即跳过重组"全失效 → 读取任意字段的
 * 可见内容（记录列表可见项、四宫格、趋势图）整页进重组判定。
 * 弹窗输入只使 DialogUiState 变化，页面级状态纹丝不动，重组范围缩到弹窗自身。
 */
data class DialogUiState(
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
    // 编辑卡校验复刻补录卡（唯一差异：日期只读，校验排除自身 id）
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
    // 补录校验结果（用户确认规则：日期边界/同日重复/双向里程链 + 首页口径）
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
    // 补录/编辑保存防重入（对齐首页 isAdding）+ 失败提示（不静默吞异常）
    // 满箱标记（编辑卡初始值取 editingRecord.isFullTank，补录默认勾选；改后整链实时重算）
    val editIsFullTank: Boolean = true,
    val backfillIsFullTank: Boolean = true,
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

    // 弹窗独立状态流（见 DialogUiState 注释）
    private val _dialogState = MutableStateFlow(DialogUiState())
    val dialogState: StateFlow<DialogUiState> = _dialogState.asStateFlow()

    private var allDerived: List<RecordWithDerived> = emptyList()
    private var lastRawRecords: List<KOiLRecordEntity> = emptyList()
    private var abnormalThresholdPct: Double = AppSettings.DEFAULT_ABNORMAL_THRESHOLD

    /** 编辑/补录预检去抖信号：输入停顿 180ms 后才查库（参考价 + 偏差），输入抖动不反复打 DB */
    private val editPrecheckFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val backfillPrecheckFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /**
     * 总览重算的合并触发信号（CONFLATED = 只保留最新一次）。
     * 不在主线程跑 3 次 filter + average + sortedByDescending，
     * 万级记录时是一次实打实的卡帧（不是"以后可能会"，而是每次切范围/加记录都在做）。
     * 调用方只 trySend，单个消费协程在 Default 算完再回主线程合并状态，
     * 天然串行，不会出现两次重算交叉写状态导致的乱序。
     */
    private val recomputeSignal = Channel<Unit>(Channel.CONFLATED)

    init {
        viewModelScope.launch {
            while (true) {
                recomputeSignal.receive()
                doRecompute()
            }
        }
        viewModelScope.launch {
            editPrecheckFlow.debounce(180).collect { doRunEditPrecheck() }
        }
        viewModelScope.launch {
            backfillPrecheckFlow.debounce(180).collect { doRunBackfillPrecheck() }
        }
        viewModelScope.launch {
            // debounce 合并连续写入（批量导入/快速增删），120ms 停顿后只重算一次；
            // 排序 + 派生计算（O(n)）切 Dispatchers.Default，不阻塞主线程；状态更新仍回主线程
            recordDao.observeAll().debounce(120).collect { raw ->
                val pair = withContext(Dispatchers.Default) {
                    val sorted = raw.inChronologicalOrder()
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
        // 启动恢复上次范围选择（范围选择持久化，重启后保持）
        viewModelScope.launch {
            val saved = settings.summaryRange.first()
            if (saved in listOf("all", "3m", "6m", "year")) {
                _uiState.value = _uiState.value.copy(range = saved)
                recompute()
            }
        }
    }

    fun setRange(range: String) {
        _uiState.value = _uiState.value.copy(range = range)
        // 范围选择持久化（重启后仍保持上次选择）
        viewModelScope.launch { settings.setSummaryRange(range) }
        recompute()
    }

    /** 切换趋势图油号（仅影响趋势曲线，不落盘持久化） */
    fun setTrendGrade(grade: String) {
        _uiState.value = _uiState.value.copy(trendGrade = grade)
        recompute()
    }

    /** 触发一次重算（非阻塞，多次调用自动合并为一次） */
    private fun recompute() {
        recomputeSignal.trySend(Unit)
    }

    /** 真正的重算：筛范围 → 汇总 → 趋势 → 异常列表，全程在 Default，只在最后一步回主线程写状态 */
    private suspend fun doRecompute() {
        val snapshot = allDerived
        val s0 = _uiState.value
        val range = s0.range
        val trendGrade = s0.trendGrade
        val (start, prevStart, prevEnd) = rangeBounds(range)
        // 用字符串比较：reoilDate 固定 yyyy-MM-dd 零填充，字典序等价时间序（不逐条 LocalDate.parse，
        // 实测 2000 条 7.25ms → 1.17ms，快 6.2 倍；且消除了主线程上的对象分配与解析异常面）。
        val startStr = start.toString()
        val prevStartStr = prevStart.toString()
        val prevEndStr = prevEnd.toString()

        val computed = withContext(Dispatchers.Default) {
            // 「全部」范围直接复用原列表（reversed() 会新建，不会污染 allDerived），省一次全量过滤 + 拷贝
            val filtered = if (range == "all") snapshot
            else snapshot.filter { it.record.reoilDate >= startStr }
            // 「全部」时上一周期区间是空区间（1970-01-01 ~ 1970-01-01），无需过滤即可判定为空
            val prevFiltered = if (range == "all") emptyList()
            else snapshot.filter {
                it.record.reoilDate >= prevStartStr && it.record.reoilDate < prevEndStr
            }
            val stats = computeSummary(filtered)
            val prevStats = if (prevFiltered.isNotEmpty()) computeSummary(prevFiltered) else null
            // 趋势图按油号筛选（全部/92/95/98/0），六指标/异常卡/历史列表仍走全部记录
            val trend = computeTrend(if (trendGrade == "all") filtered else filtered.filter { it.record.oilGrade == trendGrade })
            // 展示用均值排除短途（单条失真风险大，与异常判定口径一致）；总账不受影响（走 computeSummary）
            val avgCons = filtered.filter { !it.shortInterval }.mapNotNull { it.oilConsumption }.average().let { if (it.isNaN()) 0.0 else it }
            val abnormal = filtered.filter {
                it.consumptionStatus == ConsumptionStatus.HIGH || it.consumptionStatus == ConsumptionStatus.LOW
            }.sortedByDescending { kotlin.math.abs(it.deviationPct) }
            ComputedView(
                records = filtered.reversed(),
                stats = stats,
                prevStats = prevStats,
                trend = trend,
                abnormal = abnormal,
                avgConsumption = avgCons,
            )
        }
        // 落到最新状态上（期间用户可能已切换弹窗等无关字段，不能用旧快照整体覆盖）
        _uiState.update {
            it.copy(
                records = computed.records,
                stats = computed.stats,
                prevStats = computed.prevStats,
                trend = computed.trend,
                abnormalRecords = computed.abnormal,
                avgConsumption = computed.avgConsumption,
            )
        }
    }

    private data class ComputedView(
        val records: List<RecordWithDerived>,
        val stats: SummaryStats,
        val prevStats: SummaryStats?,
        val trend: List<TrendPoint>,
        val abnormal: List<RecordWithDerived>,
        val avgConsumption: Double,
    )

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
        _dialogState.value = _dialogState.value.copy(
            editingRecord = record,
            editDate = record.reoilDate,
            // Double.toString 会带出 "12345.0"/"40.0"，编辑框里出现无意义的小数点，
            // 用户追加输入时容易变成 "12345.05"。整数按整数显示，小数保留原值。
            editMileage = record.mileage.editText(),
            editOilAmount = record.oilAmount.editText(),
            editActualAmount = record.actualAmount.editText(),
            // 应付由参考价自动算，打开瞬间未算出 → 空（显示"待计算"），precheck 完成后即时刷新
            editPayableAmount = "",
            editGrade = record.oilGrade ?: "92",
            editProvince = province,
            editIsFullTank = record.isFullTank,
            editRecordDates = reoilDatesFor(province),
            // 打开即重置编辑卡校验态（对齐补录卡）
            editBeforeFirst = false,
            editSameDayExists = false,
            editMileageConflictType = "",
            editMileageConflictDate = "",
            editMileageConflictValue = 0.0,
            editDeviationPct = null,
            editDeviationExceeded = false,
            editOverPayable = false,
            editOverTank = false,
            editTankCapacity = 0.0,
            editReferencePrice = null,
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

    fun setEditDate(v: String) { _dialogState.value = _dialogState.value.copy(editDate = v); runEditPrecheck() }
    fun setEditMileage(v: String) { _dialogState.value = _dialogState.value.copy(editMileage = v); runEditPrecheck() }
    fun setEditOilAmount(v: String) {
        _dialogState.value = _dialogState.value.copy(editOilAmount = v)
        // 应付自动计算（对齐补录卡/首页 updatePayable：油量 × 参考单价）
        updateEditPayable()
        runEditPrecheck()
    }
    fun setEditActualAmount(v: String) { _dialogState.value = _dialogState.value.copy(editActualAmount = v); runEditPrecheck() }
    fun setEditGrade(v: String) { _dialogState.value = _dialogState.value.copy(editGrade = v); updateEditPayable(); runEditPrecheck() }
    fun setEditProvince(v: String) {
        _dialogState.value = _dialogState.value.copy(editProvince = v, editRecordDates = reoilDatesFor(v))
        runEditPrecheck()
    }
    /** 编辑满箱标记：派生字段全部实时重算，无缓存需清理 */
    fun setEditFullTank(v: Boolean) { _dialogState.value = _dialogState.value.copy(editIsFullTank = v) }
    /** 编辑卡应付金额自动计算（对齐补录卡 updateBackfillPayable），油量/油号/参考价变化时即时刷新；无法计算时清空显示待计算 */
    private fun updateEditPayable() {
        val s = _dialogState.value
        val amount = s.editOilAmount.toDoubleOrNull()
        val price = s.editReferencePrice
        val payable = if (amount != null && amount.isFinite() && amount > 0 &&
            price != null && price.isFinite() && price > 0
        ) {
            (amount * price).takeIf { it.isFinite() && it >= 0 }
                ?.let { String.format(Locale.ROOT, "%.2f", it) }
        } else null
        _dialogState.update { it.copy(editPayableAmount = payable ?: "") }
    }

    /** 编辑偏差预检入口（去抖信号，实际执行见 doRunEditPrecheck） */
    private fun runEditPrecheck() {
        editPrecheckFlow.tryEmit(Unit)
    }

    /** 编辑校验（复刻补录卡：日期边界/同日重复/双向里程链 + 偏差A+B/实付>应付/油箱容量；唯一差异：日期只读、校验排除自身 id） */
    private suspend fun doRunEditPrecheck() {
        val s = _dialogState.value
        val r = s.editingRecord ?: return
        val grade = s.editGrade
        val date = s.editDate
        val mileage = s.editMileage.toDoubleOrNull()
        val amount = s.editOilAmount.toDoubleOrNull()
        val actual = s.editActualAmount.toDoubleOrNull()
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
        var effectivePayable: Double? = null
        var rangeMin: Double? = null
        var rangeMax: Double? = null

        if (date.isNotBlank()) {
            // 1) 不早于第一条（编辑对象本身是最早记录时跳过：它没有“前一条”，
            //    排除自身后拿到的第二条不能当作前一条，否则改最早一条永远 beforeFirst）
            val earliest = recordDao.getEarliest()
            beforeFirst = MileageChain.isBeforeFirst(date, mileage, earliest, editingId = r.id)
            // 2) 同日同里程重复（排除自身）
            val sameDay = recordDao.getSameDayRecordsExcluding(date, r.id)
            if (mileage != null) {
                sameDayExists = sameDay.any { it.mileage == mileage }
            }
            // 3) 里程链：同日记录按 id 顺序参与，不能只看跨日 prev/next。
            //    编辑对象 id 固定 → 前邻居=同日 id 更小中最大者，后邻居=同日 id 更大中最小者。
            val chain = MileageChain.forEdit(
                sameDayById = sameDay,
                editingId = r.id,
                beforeDate = recordDao.getBeforeDate(date),
                afterDate = recordDao.getAfterDate(date),
            )
            val prev = chain.prev
            val next = chain.next
            if (mileage != null && mileage > 0) {
                val conflict = MileageChain.conflict(mileage, chain)
                if (conflict != null) {
                    mileageConflictType = conflict.type
                    mileageConflictDate = conflict.date
                    mileageConflictValue = conflict.value
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
        if (date.isNotBlank() && amount != null && amount.isFinite() && amount > 0 &&
            actual != null && actual.isFinite() && actual > 0
        ) {
            val result = syncer.checkDeviation(province, grade, date, amount, actual)
            val threshold = result.thresholdPercent
            // 应付只能按“当前参考价 × 油量”重算；不能拿弹窗里可能滞后的 payableInput 判定，
            // 否则换油号/省份后实付 > 新应付仍会被旧应付放行。
            val referencePrice = result.referencePrice
                ?.takeIf { it.isFinite() && it > 0 }
                ?: refPrice?.takeIf { it.isFinite() && it > 0 }
            effectivePayable = referencePrice
                ?.let { amount * it }
                ?.takeIf { it.isFinite() && it >= 0 }
            if (result.referencePrice != null && result.referencePrice > 0) {
                devPct = result.deviationPercent
                devExceeded = result.hasDeviation
            } else {
                val pct = if (effectivePayable != null && effectivePayable!! > 0)
                    kotlin.math.abs(actual - effectivePayable!!) / effectivePayable!! * 100 else 0.0
                devPct = pct
                devExceeded = pct > threshold
            }
            // 5) 实付>应付 一律拦截（=允许）
            if (effectivePayable != null && effectivePayable!! > 0 && actual > effectivePayable!!) {
                overPayable = true
            }
        }
        // 6) 油箱容量（加油量 > 油箱容量）
        if (amount != null && amount > tankCapacity && tankCapacity > 0) {
            overTank = true
        }

        val fieldsValid = mileage != null && amount != null && actual != null &&
            RecordValidator.isValidRecord(
                date,
                mileage,
                amount,
                effectivePayable ?: actual,
                actual,
            )
        val ready = fieldsValid &&
            !beforeFirst && !sameDayExists &&
            mileageConflictType.isEmpty() &&
            !devExceeded && !overPayable && !overTank

        // 采用 update 合并，只写预检结果字段（避免用开头捕获的 `s.copy(...)` 整体回写，
        // 那会用旧快照覆盖预检期间用户继续输入的内容，输入被"吞"）。
        _dialogState.update {
            it.copy(
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
        }
        // 参考价就绪后立即刷新应付自动算（避免打开弹窗停留在"待计算"）
        updateEditPayable()
    }

    fun saveEdit() {
        val s = _dialogState.value
        val r = s.editingRecord ?: return
        // 对齐补录卡——预检全通过（日期边界/同日重复/里程链/偏差A+B/实付>应付/油箱容量）才允许保存
        // isSavingEdit 防重入（快速双击不并发 update）+ 失败提示（不静默吞异常）
        if (s.editDate.isBlank() || !s.editReady || s.isSavingEdit) return
        viewModelScope.launch {
            val cur = _dialogState.value
            if (cur.isSavingEdit) return@launch
            _dialogState.value = cur.copy(isSavingEdit = true, saveError = false)
            try {
                // 保存前同步重跑预检：editReady 来自 180ms 去抖结果，是旧输入快照。
                // 不重跑会在「改坏输入后立刻保存」时把旧值/非法值写回库。
                doRunEditPrecheck()
                val fresh = _dialogState.value
                val mileage = fresh.editMileage.toDoubleOrNull()
                val oilAmount = fresh.editOilAmount.toDoubleOrNull()
                val actual = fresh.editActualAmount.toDoubleOrNull()
                if (!fresh.editReady || fresh.editDate.isBlank() ||
                    mileage == null || oilAmount == null || actual == null
                ) {
                    _dialogState.update { it.copy(isSavingEdit = false, saveError = true) }
                    return@launch
                }
                val province = fresh.editProvince.ifBlank { settings.defaultProvince.first() }
                recordDao.update(
                    r.copy(
                        reoilDate = fresh.editDate,
                        mileage = mileage,
                        oilAmount = oilAmount,
                        // 应付算不出时回退本次实付（对齐补录卡），不保留旧应付
                        payableAmount = fresh.editPayableAmount.toDoubleOrNull() ?: actual,
                        actualAmount = actual,
                        oilGrade = fresh.editGrade,
                        oilPrice = fresh.editReferencePrice ?: r.oilPrice,
                        province = province,
                        isFullTank = fresh.editIsFullTank,
                    )
                )
                _dialogState.value = _dialogState.value.copy(
                    showEditDialog = false, editingRecord = null,
                    isSavingEdit = false, saveError = false,
                )
            } catch (_: Exception) {
                _dialogState.value = _dialogState.value.copy(isSavingEdit = false, saveError = true)
            }
        }
    }

    fun cancelEdit() {
        _dialogState.value = _dialogState.value.copy(
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
            val defaultGrade = settings.defaultOilGrade.first()
                .takeIf { it in OilPriceNetwork.GRADE_LABELS } ?: "92"
            _dialogState.value = _dialogState.value.copy(
                showBackfillDialog = true,
                backfillDate = LocalDate.now().format(dateFormatter),
                backfillMileage = "",
                backfillOilAmount = "",
                backfillActualAmount = "",
                backfillPayableAmount = "",
                backfillGrade = defaultGrade,
                backfillProvince = home,
                backfillIsFullTank = true,
                backfillRecordDates = reoilDatesFor(home),
                // 打开即重置全部校验态：否则上一次补录的 ready / 参考价会残留到新弹窗，
                // 在 180ms 预检完成前按钮显示为「可保存」、应付按旧价算。
                backfillBeforeFirst = false,
                backfillSameDayExists = false,
                backfillMileageConflictType = "",
                backfillMileageConflictDate = "",
                backfillMileageConflictValue = 0.0,
                backfillDeviationPct = null,
                backfillDeviationExceeded = false,
                backfillOverPayable = false,
                backfillOverTank = false,
                backfillRangeMin = null,
                backfillRangeMax = null,
                backfillTankCapacity = 0.0,
                backfillReferencePrice = null,
                backfillReady = false,
                isSavingBackfill = false,
                saveError = false,
            )
            // 打开即跑预检：自动带出参考价（油号单价），避免 ¥--
            runBackfillPrecheck()
        }
    }
    fun setBackfillDate(v: String) { _dialogState.value = _dialogState.value.copy(backfillDate = v); runBackfillPrecheck() }
    fun setBackfillMileage(v: String) { _dialogState.value = _dialogState.value.copy(backfillMileage = v); runBackfillPrecheck() }
    fun setBackfillOilAmount(v: String) {
        _dialogState.value = _dialogState.value.copy(backfillOilAmount = v)
        // 应付自动计算（对齐首页 updatePayable：油量 × 参考单价）
        updateBackfillPayable()
        runBackfillPrecheck()
    }
    fun setBackfillActualAmount(v: String) { _dialogState.value = _dialogState.value.copy(backfillActualAmount = v); runBackfillPrecheck() }
    fun setBackfillGrade(v: String) { _dialogState.value = _dialogState.value.copy(backfillGrade = v); runBackfillPrecheck() }
    /** 补录满箱标记（默认勾选；跳枪才代表加满，与加油量无关） */
    fun setBackfillFullTank(v: Boolean) { _dialogState.value = _dialogState.value.copy(backfillIsFullTank = v) }
    fun setBackfillProvince(v: String) {
        _dialogState.value = _dialogState.value.copy(backfillProvince = v, backfillRecordDates = reoilDatesFor(v))
        runBackfillPrecheck()
    }
    /** 补录应付金额自动计算（对齐首页 updatePayable），油量或参考价变化时即时刷新；无法计算时清空显示待计算 */
    private fun updateBackfillPayable() {
        val s = _dialogState.value
        val amount = s.backfillOilAmount.toDoubleOrNull()
        val price = s.backfillReferencePrice
        val payable = if (amount != null && amount.isFinite() && amount > 0 &&
            price != null && price.isFinite() && price > 0
        ) {
            (amount * price).takeIf { it.isFinite() && it >= 0 }
                ?.let { String.format(Locale.ROOT, "%.2f", it) }
        } else null
        _dialogState.update { it.copy(backfillPayableAmount = payable ?: "") }
    }
    fun cancelBackfill() {
        _dialogState.value = _dialogState.value.copy(
            showBackfillDialog = false,
            isSavingBackfill = false, saveError = false,
        )
    }

    /** 补录偏差预检入口（去抖信号，实际执行见 doRunBackfillPrecheck） */
    private fun runBackfillPrecheck() {
        backfillPrecheckFlow.tryEmit(Unit)
    }

    /** 补录偏差预检（用户确认规则：日期边界/同日重复/双向里程链 + 首页口径 A+B偏差/实付>应付/油箱容量） */
    private suspend fun doRunBackfillPrecheck() {
        val s = _dialogState.value
        val grade = s.backfillGrade
        val date = s.backfillDate
        val mileage = s.backfillMileage.toDoubleOrNull()
        val amount = s.backfillOilAmount.toDoubleOrNull()
        val actual = s.backfillActualAmount.toDoubleOrNull()
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
        var effectivePayable: Double? = null
        var rangeMin: Double? = null
        var rangeMax: Double? = null

        if (date.isNotBlank()) {
            // 1) 不早于第一条（date < firstDate 或 同日且里程更小）
            val earliest = recordDao.getEarliest()
            beforeFirst = MileageChain.isBeforeFirst(date, mileage, earliest)
            // 2) 同日同里程重复
            val sameDay = recordDao.getSameDayRecords(date)
            if (mileage != null) {
                sameDayExists = sameDay.any { it.mileage == mileage }
            }
            // 3) 里程链：补录新记录 id 必然大于所有既有记录，同日链尾就是同日最后一条；
            //    同日还有记录时，新里程必须大于同日最大里程，否则派生出一段负里程。
            val chain = MileageChain.forBackfill(
                sameDayById = sameDay,
                beforeDate = recordDao.getBeforeDate(date),
                afterDate = recordDao.getAfterDate(date),
            )
            val prev = chain.prev
            val next = chain.next
            if (mileage != null && mileage > 0) {
                val conflict = MileageChain.conflict(mileage, chain)
                if (conflict != null) {
                    mileageConflictType = conflict.type
                    mileageConflictDate = conflict.date
                    mileageConflictValue = conflict.value
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
        if (date.isNotBlank() && amount != null && amount.isFinite() && amount > 0 &&
            actual != null && actual.isFinite() && actual > 0
        ) {
            val result = syncer.checkDeviation(province, grade, date, amount, actual)
            val threshold = result.thresholdPercent
            // 应付只能按“当前参考价 × 油量”重算；不能拿弹窗里可能滞后的 payableInput 判定，
            // 否则换油号/省份后实付 > 新应付仍会被旧应付放行。
            val referencePrice = result.referencePrice
                ?.takeIf { it.isFinite() && it > 0 }
                ?: refPrice?.takeIf { it.isFinite() && it > 0 }
            effectivePayable = referencePrice
                ?.let { amount * it }
                ?.takeIf { it.isFinite() && it >= 0 }
            if (result.referencePrice != null && result.referencePrice > 0) {
                devPct = result.deviationPercent
                devExceeded = result.hasDeviation
            } else {
                val pct = if (effectivePayable != null && effectivePayable!! > 0)
                    kotlin.math.abs(actual - effectivePayable!!) / effectivePayable!! * 100 else 0.0
                devPct = pct
                devExceeded = pct > threshold
            }
            // 5) 实付>应付 一律拦截（=允许）
            if (effectivePayable != null && effectivePayable!! > 0 && actual > effectivePayable!!) {
                overPayable = true
            }
        }
        // 6) 油箱容量（加油量 > 油箱容量）
        if (amount != null && amount > tankCapacity && tankCapacity > 0) {
            overTank = true
        }

        val fieldsValid = mileage != null && amount != null && actual != null &&
            RecordValidator.isValidRecord(
                date,
                mileage,
                amount,
                effectivePayable ?: actual,
                actual,
            )
        val ready = fieldsValid &&
            !beforeFirst && !sameDayExists &&
            mileageConflictType.isEmpty() &&
            !devExceeded && !overPayable && !overTank

        // 同编辑卡——不整体回写旧快照，避免覆盖预检期间的新输入
        _dialogState.update {
            it.copy(
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
        }
        // 参考价就绪后立即刷新应付自动算（避免打开弹窗停留在"待计算"）
        updateBackfillPayable()
    }

    fun saveBackfill() {
        val s = _dialogState.value
        // 用户确认规则——预检全通过（日期边界/同日重复/里程链/偏差A+B/实付>应付/油箱容量）才允许保存
        // isSavingBackfill 防重入（快速双击不并发 insert 撞唯一约束崩溃）+ try-catch 失败提示
        if (s.backfillDate.isBlank() || !s.backfillReady || s.isSavingBackfill) return
        viewModelScope.launch {
            val cur = _dialogState.value
            if (cur.isSavingBackfill) return@launch
            _dialogState.value = cur.copy(isSavingBackfill = true, saveError = false)
            try {
                // 保存前同步重跑预检：backfillReady 是 180ms 去抖的旧快照，
                // 弹窗刚打开时还可能残留上一次的 ready（见 openBackfill 重置）。
                doRunBackfillPrecheck()
                val fresh = _dialogState.value
                val curMileage = fresh.backfillMileage.toDoubleOrNull()
                val curOil = fresh.backfillOilAmount.toDoubleOrNull()
                val curActual = fresh.backfillActualAmount.toDoubleOrNull()
                // 解析失败/校验未过必须把防重入标志复位，否则保存按钮会永久置灰
                if (!fresh.backfillReady || fresh.backfillDate.isBlank() ||
                    curMileage == null || curOil == null || curActual == null
                ) {
                    _dialogState.update { it.copy(isSavingBackfill = false, saveError = true) }
                    return@launch
                }
                val payable = fresh.backfillPayableAmount.toDoubleOrNull() ?: curActual
                val province = fresh.backfillProvince.ifBlank { settings.defaultProvince.first() }
                recordDao.insert(
                    KOiLRecordEntity(
                        reoilDate = fresh.backfillDate,
                        mileage = curMileage,
                        oilAmount = curOil,
                        payableAmount = payable,
                        actualAmount = curActual,
                        oilPrice = fresh.backfillReferencePrice ?: (if (curOil > 0) curActual / curOil else null),
                        oilGrade = fresh.backfillGrade,
                        province = province,
                        source = "backfill_modal",
                        isFullTank = fresh.backfillIsFullTank,
                    )
                )
                _dialogState.value = _dialogState.value.copy(
                    showBackfillDialog = false,
                    isSavingBackfill = false,
                    saveError = false,
                )
            } catch (_: Exception) {
                _dialogState.value = _dialogState.value.copy(isSavingBackfill = false, saveError = true)
            }
        }
    }

    /** 删除记录 */
    fun deleteRecord(id: Long) {
        viewModelScope.launch {
            val record = recordDao.getById(id) ?: return@launch
            // 成功推送过云同步（即使当前关闭）才留墓碑：关同步期间删除、之后重开同步时，
            // 云端旧快照里的同键记录不能被合并回来。未成功推送过则不写墓碑，避免无意义累积。
            // 判定读 hasEverConfiguredSync 标记——"URL 非空 + 用户名非空"会被默认 URL 架空。
            val everConfigured = settings.syncEverConfigured.first()
            if (everConfigured) {
                settings.markRecordDeleted(record.reoilDate, record.mileage)
            }
            recordDao.deleteById(id)
        }
    }

    // ===== 计算 =====
    // 派生字段（distance/油耗/每公里成本/油耗状态）统一由 domain.logic.DerivedCalculator.compute 计算

    private fun computeSummary(derived: List<RecordWithDerived>): SummaryStats {
        val totalCount = derived.size
        val totalOilAmount = derived.sumOf { it.record.oilAmount }
        val totalActual = derived.sumOf { it.record.actualAmount }
        val totalPayable = derived.sumOf { it.record.payableAmount }
        // 「累计里程」必须包含全部有效里程段（短途段也真实行驶过）；
        // 油耗/每公里成本用满箱闭合区间加权：Σ油量/Σ里程、Σ实付/Σ里程（含短途区间——
        // 全部闭合区间的油与里程都计入，总账严格等于 总油/总里程，无偏；短途单条由 UI 标低置信）。
        val totalDistance = SummaryMath.totalDistance(derived)
        val closed = derived.filter { it.spanDistance != null }
        val closedDistance = closed.sumOf { it.spanDistance ?: 0.0 }
        val closedOil = closed.sumOf { it.spanOil ?: 0.0 }
        val closedActual = closed.sumOf { it.spanActual ?: 0.0 }
        // 兜底：整库没有任何满箱锚点时退化为「长期平均」= 总加油量 / 首末里程差（端点油位差
        // 最多约一箱油，里程越长越准），必须带 longTermAvg 标识展示，不能冒充区间平均。
        var longTermAvg = false
        var avgOilConsumption: Double? = null
        var avgCostPerKm: Double? = null
        if (closedDistance > 0) {
            avgOilConsumption = closedOil / closedDistance * 100
            avgCostPerKm = closedActual / closedDistance
        } else if (derived.size >= 2) {
            val mileageSpan = derived.last().record.mileage - derived.first().record.mileage
            if (mileageSpan > 0) {
                avgOilConsumption = totalOilAmount / mileageSpan * 100
                avgCostPerKm = totalActual / mileageSpan
                longTermAvg = true
            }
        }
        val avgUnitPriceActual = if (totalOilAmount > 0) totalActual / totalOilAmount else null
        val avgDistancePerReoil = SummaryMath.avgDistancePerReoil(derived)
        val abnormalCount = derived.count { it.consumptionStatus == ConsumptionStatus.HIGH || it.consumptionStatus == ConsumptionStatus.LOW }
        return SummaryStats(
            longTermAvg = longTermAvg,            totalCount = totalCount,
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

    private fun computeTrend(derived: List<RecordWithDerived>): List<TrendPoint> {
        // 抽样逻辑抽到 TrendSampler（纯函数，单测覆盖「61/100 条保留最后一条」）
        val sampled = TrendSampler.sample(derived, MAX_TREND_POINTS)
        return sampled.map {
            TrendPoint(
                date = it.record.reoilDate.takeLast(5),
                oilConsumption = it.oilConsumption,
                costPerKm = it.costPerKm,
                actualAmount = it.record.actualAmount,
                lowConfidence = it.shortInterval,
            )
        }
    }

    /** 数值回填输入框：整数不带 ".0"，小数保留原值 */
    private fun Double.editText(): String =
        if (isFinite() && this % 1.0 == 0.0) toLong().toString() else toString()
}
