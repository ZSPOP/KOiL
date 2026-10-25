package com.llpop.koil.ui.screen.add

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.llpop.koil.data.local.dao.FuelPriceDao
import com.llpop.koil.data.local.dao.KOiLRecordDao
import com.llpop.koil.data.local.entity.KOiLRecordEntity
import com.llpop.koil.data.oil.OilPriceDateUtil
import com.llpop.koil.data.location.LocationHelper
import com.llpop.koil.data.oil.OilPriceDbSyncer
import com.llpop.koil.data.settings.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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
    val forecastAmount: Double? = null,          // 预计调整 元/吨（正涨负跌）
    val perLiterEquivalent: Double? = null,      // 折合 元/升
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

class AddViewModel(
    private val recordDao: KOiLRecordDao,
    private val priceDao: FuelPriceDao,
    private val settings: AppSettings,
    private val locationHelper: LocationHelper,
    private val syncer: OilPriceDbSyncer,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddUiState())
    val uiState: StateFlow<AddUiState> = _uiState.asStateFlow()

    /** 预检去抖信号：输入停顿 180ms 后才实际查库，输入抖动不再反复触发 DB 查询 */
    private val precheckFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

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
            // 库行数变化（灌库完成/清库）→ 刷新涨跌趋势 + 调价窗口 + 版本号
            viewModelScope.launch {
                priceDao.observeCount().distinctUntilChanged().collect { count ->
                    if (count > 0) {
                        loadViewProvinceTrend()
                        loadPriceAdjustInfo()
                    }
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
        viewModelScope.launch {
            settings.priceDbForecast.collect { loadPriceAdjustInfo() }
        }        // 持续监听偏差阈值设置
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
                    combine(
                        priceDao.observeNewest(p, "92号汽油"),
                        priceDao.observeNewest(p, "95号汽油"),
                        priceDao.observeNewest(p, "98号汽油"),
                        priceDao.observeNewest(p, "0号柴油"),
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
                    priceDao.observeNewest(p, label)
                }
                .collect { entry ->
                    _uiState.update { it.copy(currentPrice = entry?.price?.takeIf { v -> v > 0 }) }
                    updatePayable()
                }
        }
    }

    /** 加载调价窗口信息 + 数据库版本号：仅用接口 next_adjustment + forecast（未同步时显示待检测，不做假推算） */
    private fun loadPriceAdjustInfo() {
        viewModelScope.launch {
            val province = _uiState.value.province
            val p = priceDao.getNewest(province, "92号汽油")
            if (p != null) {
                // 接口 next_adjustment 存档（未同步 → null → 不显示推算假窗口）
                val cfgNextRaw = settings.priceDbNextAdjustment.first()
                val cfgNext = OilPriceDateUtil.normalize(cfgNextRaw)
                val next: LocalDate? = if (cfgNext != null) {
                    try { LocalDate.parse(cfgNext, dateFormatter) } catch (_: Exception) { null }
                } else null
                var nextAdjustDate: String? = null
                var days = -1
                var urgent = false
                if (next != null) {
                    val today = LocalDate.now()
                    // 接口 next 已过期（days<0）时自动顺延一个周期，避免显示过去窗口
                    val effectiveNext = if (next.isBefore(today)) next.plusDays(14) else next
                    nextAdjustDate = effectiveNext.format(dateFormatter)
                    days = java.time.temporal.ChronoUnit.DAYS.between(today, effectiveNext).toInt()
                    urgent = days in 0..3
                }
                // 数据库版本号：V+最近调价窗口日（与设置页油价库标题同一口径）。
                // 取 prev 接口存档（滚动窗口链）→ 无则 next−14 推算 → 再无则 DB 最新调价日兜底（灌库即有，不再 V——）
                val prevWindow = OilPriceDateUtil.normalize(settings.priceDbPrevNextAdjustment.first())
                val recentWindow: String? = prevWindow
                    ?: next?.minusDays(14)?.toString()
                    ?: p.effectiveDate
                val versionLabel = recentWindow?.replace("-", "")?.let { "V$it" } ?: "V——"
                // 预测幅度：仅用接口 forecast（如"预计上调油价270元/吨"，正涨负跌）；
                // 解析失败/无 → 不显示（不做本地推算假值）
                val forecastRaw = settings.priceDbForecast.first()
                val forecastAmount = parseForecast(forecastRaw)
                // 折合元/升：元/吨 ÷ 1316 升/吨（92# 汽油密度约 0.76 kg/L）
                val perLiterEquivalent = forecastAmount?.let { Math.round(it / 1316.0 * 100) / 100.0 }
                _uiState.value = _uiState.value.copy(
                    versionLabel = versionLabel,
                    latestPriceDate = p.effectiveDate,
                    nextAdjustDate = nextAdjustDate,
                    daysUntilAdjust = days,
                    adjustUrgent = urgent,
                    forecastAmount = forecastAmount,
                    perLiterEquivalent = perLiterEquivalent,
                )
            }
        }
    }

    /**
     * 解析接口 forecast 文本（如"预计上调油价270元/吨"/"预计下调油价约120元/吨"/"预计搁浅"）：
     * 返回有符号元/吨（上调为正、下调为负）；无法解析或搁浅返回 null。
     */
    private fun parseForecast(raw: String): Double? {
        val text = raw.trim()
        if (text.isBlank() || text.contains("搁浅")) return null
        val m = Regex("""(\d+(?:\.\d+)?)\s*元/吨""").find(text) ?: return null
        val value = m.groupValues[1].toDoubleOrNull() ?: return null
        return if (text.contains("下调")) -value else value
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
                payableAmount = String.format("%.2f", amount * price)
            )
        }
    }

    /** 一键沿用上次加油：油号/里程/油量/实付（当前价由 observeCurrentPrice 自动刷新） */
    fun applyLastRecord() {
        val last = _uiState.value.lastRecord ?: return
        _uiState.update {
            it.copy(
                oilGrade = last.oilGrade ?: "92",
                mileage = last.mileage.toString(),
                oilAmount = last.oilAmount.toString(),
                actualAmount = last.actualAmount.toString(),
            )
        }
        updatePayable()
        runPrecheck()
    }

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
                    val discount = if (payable != null && actual != null && actual < payable) payable - actual else 0.0
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
                    payableAmount = state.payableAmount.toDoubleOrNull() ?: 0.0,
                    actualAmount = state.actualAmount.toDouble(),
                    oilPrice = state.currentPrice,
                    oilGrade = state.oilGrade,
                    province = state.province,
                    source = "entry_page",
                )
                recordDao.insert(record)
                _uiState.value = _uiState.value.copy(
                    isAdding = false,
                    addSuccess = true,
                    mileage = "",
                    oilAmount = "",
                    payableAmount = "",
                    actualAmount = "",
                    precheck = PrecheckResult(messageKey = "added"),
                )
                // 刷新上次记录
                val last = recordDao.getLatest()
                _uiState.value = _uiState.value.copy(lastRecord = last, addSuccess = false)
                runPrecheck()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isAdding = false,
                    precheck = PrecheckResult(messageKey = "add_failed", messageArgs = listOf(e.message ?: "")),
                )
            }
        }
    }
}
