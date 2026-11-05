package com.llpop.koil.ui.screen.add

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.llpop.koil.data.local.dao.KOiLPriceDao
import com.llpop.koil.data.local.dao.KOiLRecordDao
import com.llpop.koil.data.local.entity.KOiLRecordEntity
import com.llpop.koil.data.oil.CrudeOilEstimator
import com.llpop.koil.data.oil.OilPriceDateUtil
import com.llpop.koil.data.location.LocationHelper
import com.llpop.koil.data.oil.OilPriceDbSyncer
import com.llpop.koil.data.settings.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** 录入页 UI 状态 */
data class AddUiState(
    val date: String = "",
    val oilGrade: String = "92",
    val mileage: String = "",
    val oilAmount: String = "",
    val payableAmount: String = "",
    val actualAmount: String = "",
    val currentPrice: Double? = null,
    val lastRecord: KOiLRecordEntity? = null,
    val province: String = "广东",          // 记录省份（定位/默认省份决定，供表单价/调价窗口/偏差预检/入库）
    val viewProvince: String = "广东",      // 查看省份（四宫格专属，省份按钮只切它，不影响其他）
    val versionLabel: String = "V——",       // 油价数据库版本号（V+最新调价窗口日）
    val priceMap: Map<String, Double> = emptyMap(),
    val precheck: PrecheckResult = PrecheckResult(),
    val isAdding: Boolean = false,
    val addSuccess: Boolean = false,
    val showProvincePicker: Boolean = false,
    val latestPriceDate: String? = null,
    val nextAdjustDate: String? = null,
    val daysUntilAdjust: Int = -1,
    val adjustUrgent: Boolean = false,
    val forecastAmount: Double? = null,          // 预计调整 元/吨（正涨负跌，恒来自 EIA/腾讯本地估算）
    val forecastSource: String? = null,          // 估算来源："EIA"（窗口幅度估算）/ "腾讯"（单日方向参考）
    val forecastDataDate: String? = null,        // v1.5.23：EIA 数据截至日期（T+1 滞后，窗口日当天缺末段），腾讯=null
    val perLiterEquivalent: Double? = null,      // 折合 元/升（按当前油号密度折算）
    val showPriceAdjust: Boolean = false,        // 油价调整窗口提醒板块（由设置页开关控制）
    val tankCapacity: Float = 65f,
    val priceTrend: Map<String, Double> = emptyMap(), // 油号 -> 涨跌百分比(正涨负跌，调价窗口建议用)
    val locateMessage: String? = null,                 // 定位成功一次性提示（"已切换到X省"），UI 显示后清空
)

/** 实时预检结果 */
data class PrecheckResult(
    val isDuplicate: Boolean = false,
    val mileageDecreased: Boolean = false,
    val distanceKm: Double? = null,
    val priceDeviationPct: Double? = null,
    val priceDeviationExceeded: Boolean = false,
    val amountOverPayable: Boolean = false,
    val oilOverTank: Boolean = false,
    val isReady: Boolean = false,
    // 本地化消息：key 对应 strings.xml，args 为占位符参数（由 UI 层 stringResource 翻译）
    val messageKey: String = "default",
    val messageArgs: List<Any> = emptyList(),
)

private val GRADE_LABELS = mapOf(
    "92" to "92号汽油",
    "95" to "95号汽油",
    "98" to "98号汽油",
    "0" to "0号柴油",
)

@OptIn(kotlinx.coroutines.FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class AddViewModel(
    private val recordDao: KOiLRecordDao,
    private val priceDao: KOiLPriceDao,
    private val settings: AppSettings,
    private val locationHelper: LocationHelper,
    private val syncer: OilPriceDbSyncer,
    private val estimator: CrudeOilEstimator,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddUiState())
    val uiState: StateFlow<AddUiState> = _uiState.asStateFlow()

    /** 预检去抖信号：输入停顿 180ms 后才实际查库，输入抖动不再反复触发 DB 查询 */
    private val precheckFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    /** v1.5.22：估值周期刷新间隔（8h，与油价同步节流对齐） */
    private val OIL_ESTIMATE_REFRESH_MS = 8L * 60 * 60 * 1000

    init {
        val today = LocalDate.now().format(dateFormatter)
        _uiState.update { it.copy(date = today) }

        // 预检去抖执行（runPrecheck 只发信号）
        viewModelScope.launch {
            precheckFlow.debounce(180).collect { doRunPrecheck() }
        }

        // 初始配置并行读取：settings 4 项 + 最新记录 5 路并发（替代原 5 次串行 first/getLatest）
        viewModelScope.launch {
            coroutineScope {
                val provinceD = async { settings.defaultProvince.first() }
                val keepGradeD = async { settings.keepLastGrade.first() }
                val defaultGradeD = async { settings.defaultOilGrade.first() }
                val tankD = async { settings.tankCapacity.first() }
                val lastD = async { recordDao.getLatest() }
                val province = provinceD.await()
                val keepGrade = keepGradeD.await()
                val defaultGrade = defaultGradeD.await()
                val tank = tankD.await()
                val last = lastD.await()
                val grade = if (keepGrade && last?.oilGrade != null) last.oilGrade else defaultGrade
                _uiState.update {
                    it.copy(
                        province = province,
                        viewProvince = province,
                        lastRecord = last,
                        oilGrade = grade,
                        tankCapacity = tank,
                    )
                }
            }
            // 响应式订阅（常驻，替代一次性查询：灌库/调价更新后自动刷新，根治冷启动数据空白）
            observeViewProvincePrices()
            observeCurrentPrice()
            // v1.5.0：「上次加油」卡片常驻订阅——任何入口（本页/记录页/明细页）增删改记录即实时刷新
            viewModelScope.launch {
                recordDao.observeLatest().collect { last ->
                    _uiState.update { it.copy(lastRecord = last) }
                }
            }
            // 库行数变化（灌库完成/清库）→ 刷新涨跌趋势 + 调价窗口 + 版本号
            viewModelScope.launch {
                priceDao.observeCount().distinctUntilChanged().collect { count ->
                    if (count > 0) {
                        loadViewProvinceTrend()
                        loadPriceAdjustInfo()
                    }
                }
            }
            // v1.5.22：估值周期刷新——原油跨日变动后首页不停留旧估值（8h 粒度，与同步周期对齐；首刷由灌库触发）
            viewModelScope.launch {
                while (true) {
                    delay(OIL_ESTIMATE_REFRESH_MS)
                    refreshForecast()
                }
            }
            runPrecheck()
        }
        // 持续监听油箱容量设置（设置页修改后立即生效）
        viewModelScope.launch {
            settings.tankCapacity.collect { tank ->
                _uiState.value = _uiState.value.copy(tankCapacity = tank)
                runPrecheck()
            }
        }
        // 持续监听「沿用上次油号」开关 + 默认油号：设置页修改后录入页立即生效
        viewModelScope.launch {
            combine(settings.keepLastGrade, settings.defaultOilGrade) { keep, def -> keep to def }
                .collect { (keep, def) ->
                    val last = _uiState.value.lastRecord
                    val grade = if (keep && last?.oilGrade != null) last.oilGrade else def
                    if (_uiState.value.oilGrade != grade) {
                        _uiState.value = _uiState.value.copy(oilGrade = grade)
                        runPrecheck()
                    }
                }
        }
        // 持续监听「油价调整窗口提醒」开关：设置页切换后首页调价板块立即显示/隐藏
        viewModelScope.launch {
            settings.priceAlert.collect { on ->
                _uiState.value = _uiState.value.copy(showPriceAdjust = on)
            }
        }
        // 持续监听接口调价窗口/预测：设置页「立即更新」或自动同步写入后，首页调价板块自动刷新
        viewModelScope.launch {
            settings.priceDbNextAdjustment.collect { loadPriceAdjustInfo() }
        }
        // 持续监听偏差阈值设置
        viewModelScope.launch {
            settings.deviationThreshold.collect {
                runPrecheck()
            }
        }
    }

    /** 四宫格响应式：监听查看省份 4 油号最新价，库内写入（灌库/调价更新）后自动刷新 */
    private fun observeViewProvincePrices() {
        viewModelScope.launch {
            _uiState.map { it.viewProvince }
                .distinctUntilChanged()
                .flatMapLatest { p ->
                    // v1.5.21 B7：取"截至今天已生效"的最新价（窗口日 24 点前不显示次日新价）；today 每次重算跨天自动跟随
                    val today = LocalDate.now().format(dateFormatter)
                    combine(
                        priceDao.observeNewestUpTo(p, "92号汽油", today),
                        priceDao.observeNewestUpTo(p, "95号汽油", today),
                        priceDao.observeNewestUpTo(p, "98号汽油", today),
                        priceDao.observeNewestUpTo(p, "0号柴油", today),
                    ) { a, b, c, d -> listOfNotNull(a, b, c, d) }
                }
                .collect { prices ->
                    val map = prices.filter { it.price > 0 }.associate { entry ->
                        val key = when (entry.grade) {
                            "92号汽油" -> "92"
                            "95号汽油" -> "95"
                            "98号汽油" -> "98"
                            else -> "0"
                        }
                        key to entry.price
                    }
                    _uiState.update { it.copy(priceMap = map) }
                }
        }
    }

    /** 四宫格涨跌趋势：最新价 vs 上一调价日（价格变化/灌库完成后触发） */
    private fun loadViewProvinceTrend() {
        viewModelScope.launch {
            val province = _uiState.value.viewProvince
            val map = _uiState.value.priceMap
            val trend = mutableMapOf<String, Double>()
            for ((key, label) in GRADE_LABELS) {
                val p = map[key]
                if (p != null) {
                    val prev = priceDao.getPrevious(province, label)
                    if (prev != null && prev.price > 0) {
                        trend[key] = (p - prev.price) / prev.price * 100
                    }
                }
            }
            _uiState.update { it.copy(priceTrend = trend) }
        }
    }

    /** 当前油号价格响应式：省份/油号任一变化或库内写入后自动刷新（记录省份） */
    private fun observeCurrentPrice() {
        viewModelScope.launch {
            combine(
                _uiState.map { it.province }.distinctUntilChanged(),
                _uiState.map { it.oilGrade }.distinctUntilChanged(),
            ) { p, g -> p to g }
                .distinctUntilChanged()
                .flatMapLatest { (p, g) ->
                    val label = GRADE_LABELS[g] ?: "92号汽油"
                    // v1.5.21 B7：当前单价取"截至今天已生效"的最新价（窗口日白天不提前显示次日新价）
                    val today = LocalDate.now().format(dateFormatter)
                    priceDao.observeNewestUpTo(p, label, today)
                }
                .collect { entry ->
                    _uiState.update { it.copy(currentPrice = entry?.price?.takeIf { v -> v > 0 }) }
                    updatePayable()
                }
        }
    }

    /** 加载调价窗口信息 + 数据库版本号 + 预测幅度。
     *  v1.4.194：拆两段更新——窗口日/版本号（纯本地，立即显示，无网络等待）；
     *  预测幅度（EIA 估算含网络请求）独立异步，完成后补更新，不阻塞窗口板块冷启动渲染。 */
    private fun loadPriceAdjustInfo() {
        // 第一段：窗口日 + 版本号（纯本地，秒显）
        viewModelScope.launch {
            val province = _uiState.value.province
            val p = priceDao.getNewest(province, "92号汽油")
            if (p != null) {
                // 接口 next_adjustment 存档（未同步 → null → v1.4.192 回退本地推算窗口日历，
                // 与数据库卡「下个窗口」同一来源；两者皆无 → 不显示推算假窗口）
                val cfgNextRaw = settings.priceDbNextAdjustment.first()
                val todayStr = LocalDate.now().toString()
                val adjustDates = settings.priceDbAdjustDates.first()
                    .split(",").map { it.trim() }.filter { it.isNotBlank() }
                val cfgNext = OilPriceDateUtil.normalize(cfgNextRaw)
                // v1.5.36 P1：下个窗口优先取权威窗口日历（含节假日推算）——接口存档过期时
                // 不再 ±14 天硬编码顺延（春节等长假期会漂到非窗口日）；日历为空才回退接口存档
                val next: LocalDate? = when {
                    cfgNext != null -> {
                        val parsed = runCatching { LocalDate.parse(cfgNext, dateFormatter) }.getOrNull()
                        if (parsed != null && !parsed.isBefore(LocalDate.now())) parsed
                        else adjustDates.firstOrNull { it >= todayStr }
                            ?.let { runCatching { LocalDate.parse(it, dateFormatter) }.getOrNull() }
                    }
                    else -> adjustDates.firstOrNull { it >= todayStr }
                        ?.let { runCatching { LocalDate.parse(it, dateFormatter) }.getOrNull() }
                }
                var nextAdjustDate: String? = null
                var days = -1
                var urgent = false
                if (next != null) {
                    val today = LocalDate.now()
                    // 接口 next 已过期（days<0）时循环顺延一个周期直到 ≥ 今天（v1.5.0：
                    // 原只顺延一次，长期未开 App 且存档过期很远时仍显示过去窗口；循环覆盖
                    // 春节等长周期 + 多轮错过场景）。P1：正常场景 next 已优先来自权威日历，
                    // 此循环仅在权威日历为空（极端离线）时兜底。
                    var effectiveNext: LocalDate = next
                    while (effectiveNext.isBefore(today)) effectiveNext = effectiveNext.plusDays(14)
                    nextAdjustDate = effectiveNext.format(dateFormatter)
                    days = java.time.temporal.ChronoUnit.DAYS.between(today, effectiveNext).toInt()
                    urgent = days in 0..3
                }
                // 数据库版本号：V+最近调价窗口日（与设置页油价库标题同一口径）。
                // 取 prev 接口存档（滚动窗口链）→ P1：权威日历最后一个 <今天（含搁浅窗口日）→
                // 再无则 next−14 推算 → 最后 DB 最新调价日兜底（灌库即有，不再 V——）
                val prevWindow = OilPriceDateUtil.normalize(settings.priceDbPrevNextAdjustment.first())
                val recentWindow: String? = prevWindow
                    ?: adjustDates.filter { it < todayStr }.lastOrNull()
                    ?: next?.minusDays(14)?.toString()
                    ?: p.effectiveDate
                val versionLabel = recentWindow?.replace("-", "")?.let { "V$it" } ?: "V——"
                _uiState.value = _uiState.value.copy(
                    versionLabel = versionLabel,
                    latestPriceDate = p.effectiveDate,
                    nextAdjustDate = nextAdjustDate,
                    daysUntilAdjust = days,
                    adjustUrgent = urgent,
                )
            }
        }
        // 第二段：预测幅度（v1.5.21 起 apizero forecast 已彻底移除，恒走 EIA/腾讯原油本地估算；含网络请求，异步补更新）
        viewModelScope.launch { refreshForecast() }
    }

    /**
     * 刷新预测幅度（EIA 窗口幅度估算 / 腾讯单日方向参考）+ 折合元/升。
     * v1.5.22：折合按当前油号密度折算——92/95/98 汽油 0.76 → 1316 升/吨；0# 柴油 0.83 → 1205 升/吨；
     * 来源写入 forecastSource，UI 按来源标注「估算」/「单日参考」。
     */
    private suspend fun refreshForecast() {
        val p = priceDao.getNewest(_uiState.value.province, "92号汽油") ?: return
        val estimate = runCatching { estimator.estimate() }.getOrNull()
        val forecastAmount = estimate?.amountPerTon
        val litersPerTon = if (_uiState.value.oilGrade == "0") 1205.0 else 1316.0
        val perLiterEquivalent = forecastAmount?.let { Math.round(it / litersPerTon * 100) / 100.0 }
        _uiState.value = _uiState.value.copy(
            forecastAmount = forecastAmount,
            forecastSource = estimate?.sourceLabel,
            forecastDataDate = estimate?.dataDate,
            perLiterEquivalent = perLiterEquivalent,
        )
    }

    fun showProvincePicker() { _uiState.value = _uiState.value.copy(showProvincePicker = true) }
    fun dismissProvincePicker() { _uiState.value = _uiState.value.copy(showProvincePicker = false) }

    fun setOilGrade(grade: String) {
        _uiState.update { it.copy(oilGrade = grade) }
        runPrecheck()
    }

    /** 省份切换（四宫格右上）→ 仅改查看省份（observeViewProvincePrices 自动刷新四宫格，不影响记录省份/表单价/调价窗口/预检/入库） */
    fun setProvince(province: String) {
        _uiState.update { it.copy(viewProvince = province, showProvincePicker = false) }
    }

    /** 实时定位并更新省份：切回实际省份（记录省份写入设置 + 四宫格查看省份同步） */
    fun locateProvince() {
        if (!locationHelper.hasPermission()) return
        viewModelScope.launch(Dispatchers.IO) {
            // 方案A：任何主动定位触发即开启自动定位开关（含首次授权），不依赖本次定位是否立即成功
            settings.setLocationEnabled(true)
            val province = locationHelper.getCurrentProvince()
            if (province != null) {
                // locateMessage 只存省份简称（"广东"/"内蒙古"），文案由 UI 层 stringResource 本地化，
                // 避免硬编码中文（App 支持 简中/繁中/英文）
                _uiState.update {
                    it.copy(
                        province = province,
                        viewProvince = province,
                        locateMessage = province,
                    )
                }
                settings.setDefaultProvince(province)
                loadPriceAdjustInfo()
            }
        }
    }

    /** 清除定位成功提示（UI 显示 Toast 后调用） */
    fun clearLocateMessage() {
        _uiState.value = _uiState.value.copy(locateMessage = null)
    }

    /** 清除"已添加"一次性状态（UI 消费成功后调用，避免重复提示） */
    fun consumeAddSuccess() {
        if (_uiState.value.addSuccess) {
            _uiState.value = _uiState.value.copy(addSuccess = false)
        }
    }

    fun setMileage(mileage: String) {
        _uiState.value = _uiState.value.copy(mileage = mileage)
        runPrecheck()
    }

    fun setOilAmount(amount: String) {
        _uiState.value = _uiState.value.copy(oilAmount = amount)
        updatePayable()
        runPrecheck()
    }

    fun setActualAmount(amount: String) {
        _uiState.value = _uiState.value.copy(actualAmount = amount)
        runPrecheck()
    }

    fun setPayableAmount(amount: String) {
        _uiState.value = _uiState.value.copy(payableAmount = amount)
        runPrecheck()
    }

    /** 应付金额 = 加油量 × 当前油价（自动计算，可手动覆盖） */
    private fun updatePayable() {
        val state = _uiState.value
        val amount = state.oilAmount.toDoubleOrNull()
        val price = state.currentPrice
        if (amount != null && price != null && amount > 0) {
            _uiState.value = _uiState.value.copy(
                payableAmount = String.format(Locale.ROOT, "%.2f", amount * price)
            )
        }
    }

    /** 一键沿用上次加油：油号/油量/实付（当前价由 observeCurrentPrice 自动刷新）。
     *  v1.5.36 P6：不再沿用上次里程——沿用旧值必触发"里程递减"拦截，置空由用户输入本次新里程。 */
    fun applyLastRecord() {
        val last = _uiState.value.lastRecord ?: return
        _uiState.update {
            it.copy(
                oilGrade = last.oilGrade ?: "92",
                mileage = "",
                // v1.6.2：Double.toString 会写成 "45.0"，输入框里带个多余的小数点，改为整数/小数自适应
                oilAmount = last.oilAmount.editText(),
                actualAmount = last.actualAmount.editText(),
            )
        }
        updatePayable()
        runPrecheck()
    }

    /** 数值回填输入框：整数不带 ".0"（与编辑卡同口径） */
    private fun Double.editText(): String =
        if (isFinite() && this % 1.0 == 0.0) toLong().toString() else toString()

    /** 实时预检入口：去抖信号（实际执行见 doRunPrecheck） */
    private fun runPrecheck() {
        precheckFlow.tryEmit(Unit)
    }

    /** 实时预检：重复 / 里程递减 / 行驶里程 / 价格偏差 / 金额校验 / 日期校验 / 就绪判断 */
    private suspend fun doRunPrecheck() {
            val state = _uiState.value
            val mileage = state.mileage.toDoubleOrNull()
            val amount = state.oilAmount.toDoubleOrNull()
            val actual = state.actualAmount.toDoubleOrNull()
            val payable = state.payableAmount.toDoubleOrNull()

            var isDuplicate = false
            var mileageDecreased = false
            var distanceKm: Double? = null
            var priceDeviationPct: Double? = null
            var priceDeviationExceeded = false
            var amountOverPayable = false
            var oilOverTank = false
            var messageKey = "default"
            var messageArgs: List<Any> = emptyList()

            // 日期固定今天（录入页不可编辑）：同日+同里程重复拦截
            if (state.date.isNotBlank() && mileage != null) {
                val count = recordDao.countSameDayMileage(state.date, mileage)
                isDuplicate = count > 0
            }

            // 里程校验：本次里程必须 > 上次（等于/低于都拦截）
            val last = state.lastRecord
            if (last != null && mileage != null) {
                if (mileage <= last.mileage) {
                    mileageDecreased = true
                } else {
                    distanceKm = mileage - last.mileage
                }
            }

            // 偏差校验（A 优先 + B 兜底，阈值 = 设置页价格偏差值）：
            // A：有当日历史参考价 → 实付单价 vs 当日参考价，超阈值拦截
            // B：无参考价 → 回退 实付 vs 应付（|实付-应付|÷应付），超阈值拦截
            // 硬规则：实付 > 应付 一律拦截，实付 = 应付 允许
            if (amount != null && amount > 0 && actual != null && actual > 0) {
                val date = state.date.ifBlank { LocalDate.now().format(dateFormatter) }
                val result = syncer.checkDeviation(state.province, state.oilGrade, date, amount, actual)
                val threshold = result.thresholdPercent
                if (result.referencePrice != null) {
                    // A：有参考价，直接用参考价偏差
                    priceDeviationPct = result.deviationPercent
                    priceDeviationExceeded = result.hasDeviation
                } else {
                    // B：缺参考价，回退 实付 vs 应付
                    val pct = if (payable != null && payable > 0)
                        kotlin.math.abs(actual - payable) / payable * 100 else 0.0
                    priceDeviationPct = pct
                    priceDeviationExceeded = pct > threshold
                }
            }

            // 实付 > 应付 一律拦截（=允许）
            if (actual != null && payable != null && payable > 0 && actual > payable) {
                amountOverPayable = true
            }

            // 油箱容量校验：加油量 > 油箱容量 → 报错
            if (amount != null && amount > state.tankCapacity && state.tankCapacity > 0) {
                oilOverTank = true
            }

            val isReady = state.date.isNotBlank() &&
                mileage != null && mileage > 0 &&
                amount != null && amount > 0 &&
                actual != null && actual > 0 &&
                !isDuplicate && !mileageDecreased &&
                !amountOverPayable && !oilOverTank &&
                !priceDeviationExceeded

            when {
                isDuplicate -> messageKey = "duplicate"
                mileageDecreased -> {
                    messageKey = "mileage_decreased"
                    messageArgs = listOf(last?.mileage?.toInt() ?: 0)
                }
                amountOverPayable -> messageKey = "over_payable"
                oilOverTank -> {
                    messageKey = "over_tank"
                    messageArgs = listOf(state.tankCapacity.toInt())
                }
                priceDeviationExceeded -> {
                    messageKey = "deviation"
                    messageArgs = listOf(priceDeviationPct ?: 0.0)
                }
                isReady -> {
                    // 优惠金额 = 应付金额 - 实付金额（实付 ≥ 应付则无优惠，记 0）
                    // actual 在 isReady 分支恒非空（v1.5.36 清 warning）
                    val discount = if (payable != null && actual < payable) payable - actual else 0.0
                    messageKey = "ready"
                    messageArgs = listOf(distanceKm ?: 0.0, discount)
                }
                else -> messageKey = "default"
            }

            _uiState.value = _uiState.value.copy(
                precheck = PrecheckResult(
                    isDuplicate = isDuplicate,
                    mileageDecreased = mileageDecreased,
                    distanceKm = distanceKm,
                    priceDeviationPct = priceDeviationPct,
                    priceDeviationExceeded = priceDeviationExceeded,
                    amountOverPayable = amountOverPayable,
                    oilOverTank = oilOverTank,
                    isReady = isReady,
                    messageKey = messageKey,
                    messageArgs = messageArgs,
                )
            )
    }

    /** 添加记录到 Room */
    fun addRecord() {
        viewModelScope.launch {
            val state = _uiState.value
            if (!state.precheck.isReady || state.isAdding) return@launch
            _uiState.value = _uiState.value.copy(isAdding = true)
            try {
                val record = KOiLRecordEntity(
                    reoilDate = state.date,
                    mileage = state.mileage.toDouble(),
                    oilAmount = state.oilAmount.toDouble(),
                    // v1.5.16：无参考价且未手填应付时，应付按实付入账（对齐补录卡），避免入库 0 元导致总览异常
                    payableAmount = state.payableAmount.toDoubleOrNull() ?: state.actualAmount.toDouble(),
                    actualAmount = state.actualAmount.toDouble(),
                    oilPrice = state.currentPrice,
                    oilGrade = state.oilGrade,
                    province = state.province,
                    source = "entry_page",
                )
                recordDao.insert(record)
                // v1.6.2：原实现在同一个协程里先 addSuccess=true 再立刻 false，StateFlow 会合并成一次发射，
                // UI 永远看不到成功态；且紧接着的 runPrecheck() 把 "已添加" 横幅冲成 "default"。
                // 现在：成功态与提示都由 UI 消费后显式清除，输入区清空但不再重跑预检。
                _uiState.update {
                    it.copy(
                        isAdding = false,
                        addSuccess = true,
                        mileage = "",
                        oilAmount = "",
                        payableAmount = "",
                        actualAmount = "",
                        precheck = PrecheckResult(messageKey = "added"),
                    )
                }
                // 刷新上次记录（成功后 lastRecord 必须是最新一条，供「沿用上次」使用）
                val last = recordDao.getLatest()
                _uiState.update { it.copy(lastRecord = last) }
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(
                    isAdding = false,
                    // v1.5.36 P7：失败提示固定文案，不透出数据库内部异常文本
                    precheck = PrecheckResult(messageKey = "add_failed"),
                )
            }
        }
    }
}
