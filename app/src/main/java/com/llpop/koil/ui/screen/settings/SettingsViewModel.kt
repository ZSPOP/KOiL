package com.llpop.koil.ui.screen.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.llpop.koil.data.local.dao.KOiLPriceDao
import com.llpop.koil.data.local.dao.KOiLPricePendingDao
import com.llpop.koil.data.local.dao.KOiLRecordDao
import com.llpop.koil.data.local.entity.KOiLPricePendingEntity
import com.llpop.koil.data.local.entity.KOiLRecordEntity
import com.llpop.koil.domain.logic.NumberFormat
import com.llpop.koil.data.location.LocationHelper
import com.llpop.koil.data.oil.OilPriceDbSyncer
import com.llpop.koil.data.oil.OilPriceDateUtil
import com.llpop.koil.data.settings.AppSettings
import com.llpop.koil.domain.logic.ConsumptionStatus
import com.llpop.koil.domain.logic.DerivedCalculator
import com.llpop.koil.util.ExportHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import java.util.TreeSet

data class SettingsUiState(
    val themeMode: AppSettings.ThemeMode = AppSettings.ThemeMode.SYSTEM,
    val language: AppSettings.Language = AppSettings.Language.SYSTEM,
    val defaultProvince: String = "广东",
    val defaultOilGrade: String = "92",
    val tankCapacity: Float = AppSettings.DEFAULT_TANK_CAPACITY,
    val keepLastGrade: Boolean = false,
    val abnormalThreshold: Double = AppSettings.DEFAULT_ABNORMAL_THRESHOLD,
    val priceAlert: Boolean = false,
    val pricePush: Boolean = true,
    val locationEnabled: Boolean = false,
    val priceDbAutoUpdate: Boolean = false,
    val deviationThreshold: Double = AppSettings.DEFAULT_DEVIATION_THRESHOLD,
    val priceDbCache: String = "",
    val exportMessage: String = "",
    val priceDbLatestDate: String = "",
    val priceDbTotal: Int = 0,
    val priceDbProvinceCount: Int = 0,
    val priceDbPendingCount: Int = 0,
    val pendingItems: List<KOiLPricePendingEntity> = emptyList(),
    val showPendingPanel: Boolean = false,
    val importPreview: ImportPreview? = null,
    // 导航栏（参考 Mishka 悬浮导航体系）
    val floatingBottomBar: Boolean = true,
    val floatingBottomBarStyle: String = "miuix",
    val bottomBarMode: String = "icon_and_text",
    // v1.5.47：设置异步加载完成标志（DataStore 首值到达前不渲染开关，杜绝"关→开"闪烁）
    val loaded: Boolean = false,
)

/** CSV 导入预览（四分组） */
data class ImportPreview(
    val valid: List<ParsedRecord> = emptyList(),
    val duplicates: List<ParsedRecord> = emptyList(),
    val mileageConflicts: List<ParsedRecord> = emptyList(),
    val errors: List<ParsedRecord> = emptyList(),
)

/** 本地化导出/结果通知：key 对应 strings.xml，args 为占位符参数（UI 层 stringResource 翻译） */
data class ExportNotice(
    val key: String? = null,
    val args: List<Any> = emptyList(),
)

/** 解析后的单条记录（错误/冲突以 key 表示，由 UI 层翻译） */
data class ParsedRecord(
    val date: String = "",
    val mileage: Double = 0.0,
    val oilAmount: Double = 0.0,
    val actualAmount: Double = 0.0,
    val payableAmount: Double = 0.0,
    val oilGrade: String? = null,
    val oilPrice: Double? = null,
    val province: String? = null,
    val errorKey: String? = null,
    val conflictKey: String? = null,
    val conflictArg: Int = 0,
)

class SettingsViewModel(
    private val settings: AppSettings,
    private val recordDao: KOiLRecordDao,
    private val priceDao: KOiLPriceDao,
    private val pendingDao: KOiLPricePendingDao,
    private val syncer: OilPriceDbSyncer,
    private val locationHelper: LocationHelper,
    private val context: Context,
) : ViewModel() {

    init {
        // v1.5.24：库行数变化（后台补全国/立即更新写库）时自动刷新缺口统计，
        // 解决"重装后自动补灌完成但设置页缺口停留旧快照"的问题；
        // loadPriceDbStatus 只读不写库，无循环风险
        viewModelScope.launch {
            priceDao.observeCount()
                .distinctUntilChanged()
                .collect { loadPriceDbStatus() }
        }
    }

    private val _exportMessage = MutableStateFlow(ExportNotice())
    val exportMessage: StateFlow<ExportNotice> = _exportMessage.asStateFlow()



    // ===== 油价库状态 =====
    data class PriceDbStatus(
        val latestDate: String = "",
        val total: Int = 0,
        val provinceCount: Int = 0,
        val gradeCount: Int = 0,
        val adjustmentCount: Int = 0,
        val pendingCount: Int = 0,
        // 真实状态字段（对齐 Web OilPriceDbStatusResponse）
        val initialized: Boolean = false,
        val lastPriceChangeDate: String? = null,
        val lastWindowChecked: String? = null,
        val versionDate: String? = null,
        val lastCheckedAt: Long = 0,
        val lastUpdatedAt: String? = null,
        val initializedAt: Long = 0,
        val autoUpdateEnabled: Boolean = false,
        val checkIntervalHours: Int = 8,
        val lastUpdateMessage: String? = null,
        val isStranded: Boolean = false,
        // 调价窗口（方案 B：10 个工作日推算）
        val adjustWindowDate: String? = null,       // 最近调价窗口日（从最近实际调价日推 10 工作日）
        val nextAdjustWindowDate: String? = null,   // 下一个调价窗口日
        val windowStranded: Boolean = false,        // 最近窗口搁浅（库内无该窗口调价记录）
        val adjustWindowPrice: String? = null,      // 最近窗口有调价时的价格文本（元/升）
        val strandedKeptDate: String? = null,       // 搁浅时维持的上一条调价生效日（如 09-25）
        val strandedKeptPrice: String? = null,      // 搁浅时维持的价格（元/升）
        val incompleteProvinceCount: Int = 0,       // 窗口日覆盖不足的省份数（完整性提示）
        val versionLabel: String = "V——",           // 版本号（V+最新调价窗口日，不受搁浅影响）
        // v1.5.27：多源一致性（最近一次同步来源快照，如 "锚点:xxapi · 今日价:abapi"）
        val lastSyncSources: String = "",
    )
    private val _priceDbStatus = MutableStateFlow(PriceDbStatus())
    val priceDbStatus: StateFlow<PriceDbStatus> = _priceDbStatus.asStateFlow()

    private val _pendingItems = MutableStateFlow<List<KOiLPricePendingEntity>>(emptyList())
    val pendingItems: StateFlow<List<KOiLPricePendingEntity>> = _pendingItems.asStateFlow()

    private val _showPendingPanel = MutableStateFlow(false)
    val showPendingPanel: StateFlow<Boolean> = _showPendingPanel.asStateFlow()

    private val _priceDbUpdating = MutableStateFlow(false)
    val priceDbUpdating: StateFlow<Boolean> = _priceDbUpdating.asStateFlow()

    // EIA API Key（原油估算主源配置，默认内置；用户可改，留空即禁用 EIA 估算）
    private val _eiaApiKey = MutableStateFlow("")
    val eiaApiKey: StateFlow<String> = _eiaApiKey.asStateFlow()

    // EIA 原油估算源启用开关（v1.5.1：开=用 EIA；关=直接用腾讯，输入框显示供修改）
    private val _eiaEnabled = MutableStateFlow(true)
    val eiaEnabled: StateFlow<Boolean> = _eiaEnabled.asStateFlow()

    // v1.5.47：EIA 配置加载完成标志（首值到达前不渲染 EIA 开关，杜绝"开→关/关→开"闪烁）
    private val _eiaLoaded = MutableStateFlow(false)
    val eiaLoaded: StateFlow<Boolean> = _eiaLoaded.asStateFlow()

    private val _importPreview = MutableStateFlow<ImportPreview?>(null)
    val importPreview: StateFlow<ImportPreview?> = _importPreview.asStateFlow()

    fun togglePendingPanel() { _showPendingPanel.value = !_showPendingPanel.value }

    /** 读取 EIA 配置初始值（Key + 启用开关，默认内置 Key / 默认开） */
    fun loadEiaApiKey() {
        viewModelScope.launch {
            _eiaApiKey.value = settings.eiaApiKey.first()
            _eiaEnabled.value = settings.eiaEnabled.first()
            _eiaLoaded.value = true
        }
    }

    /** 保存 EIA API Key（留空 = 禁用 EIA 估算，仅腾讯降级） */
    fun setEiaApiKey(key: String) {
        _eiaApiKey.value = key
        viewModelScope.launch { settings.setEiaApiKey(key) }
    }

    /** 切换 EIA 原油估算源启用状态（开=EIA，关=腾讯） */
    fun setEiaEnabled(enabled: Boolean) {
        _eiaEnabled.value = enabled
        viewModelScope.launch { settings.setEiaEnabled(enabled) }
    }

    /** 刷新油价库状态：统计 + 配置真实字段 + pending 列表 */
    fun loadPriceDbStatus() {
        viewModelScope.launch {
            // 先按已缓存的权威窗口日历幂等清理孤立假生效日（如降级灌入的 09-27），
            // 再统计，保证"价格维持日 / 调价次数 / 记录数"不被假日期污染
            syncer.purgeIllegalEffectiveDatesIfReady()
            // SQL 聚合统计（替代全表 getAll 内存统计，数据量大时不再卡顿）
            val total = priceDao.count()
            val latest = priceDao.maxDate() ?: ""
            val provinceCount = priceDao.countProvinces()
            val gradeCount = priceDao.countGrades()
            // 调价次数：优先取"权威调价窗口日历"中今年已发生的窗口日数（含搁浅窗口），
            // 窗口日历为空时才回退库内去重生效日期数
            //（DISTINCT effectiveDate 会被降级假生效日污染，不可作为调价次数口径）
            // v1.5.21 B4：窗口日 24 点前不算已发生 → 严格 < today（今天窗口日不计入，修复"29 号当天调价 21 次"）
            val windowDates = settings.priceDbAdjustDates.first()
                .split(",").map { it.trim() }.filter { it.isNotBlank() }
            val todayStr = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
            val adjustmentCount = if (windowDates.isNotEmpty()) {
                windowDates.count { it < todayStr }
            } else {
                priceDao.countAdjustments()
            }
            val pendingCount = pendingDao.countPending()

            // 完整性缺口：阈值 = "已调价窗口数"（排除搁浅窗口——搁浅不写记录，
            // 计入会让所有省都差几个而误报），再容忍 2 个窗口的数据拉取缺口（各省数据源偶发缺窗口）
            // 窗口是否调价过：窗口日 +1 在库内有记录（生效日 = 窗口日 + 1 天）
            // v1.5.21 B4：同改严格 < today（今天窗口日不计入缺口阈值）
            val adjustedWindowCount = windowDates.count { w ->
                w < todayStr && runCatching {
                    priceDao.getByDate(LocalDate.parse(w).plusDays(1).toString()) != null
                }.getOrDefault(false)
            }
            val incompleteProvinceCount = if (adjustedWindowCount > 0) {
                val threshold = adjustedWindowCount - 2
                OilPriceDbSyncer.PROVINCES.count { province ->
                    runCatching { priceDao.countDatesForProvince(province) }.getOrDefault(0) < threshold
                }
            } else {
                0
            }

            val lastCheckTs = settings.priceDbLastCheckAt.first()
            val lastUpdatedAt = settings.priceDbLastUpdateDate.first()
            val initAt = settings.priceDbInitAt.first()
            val lastMsg = settings.priceDbCache.first()
            val lastWindowChecked = settings.priceDbLastWindowChecked.first()
            val autoEnabled = settings.priceDbAutoUpdate.first()

            // 对齐 Web getStatus：versionDate/isStranded 判定
            var versionDate: String? = null
            var isStranded = false
            when {
                latest.isNotBlank() && lastWindowChecked.isNotBlank() && lastWindowChecked > latest -> {
                    versionDate = lastWindowChecked
                    isStranded = true
                }
                latest.isNotBlank() -> versionDate = latest
                lastWindowChecked.isNotBlank() -> versionDate = lastWindowChecked
            }

            // 调价窗口（接口官方调价日历滚动存档）：W1=上次接口预告的 next（已过窗口），W2=当前接口预告
            // 日期规范化：兼容接口返回的 "2026-10-29" / "2026/10/29" / "10月29日" 等多种格式
            val prevWindow = OilPriceDateUtil.normalize(settings.priceDbPrevNextAdjustment.first())
            val nextWindow = OilPriceDateUtil.normalize(settings.priceDbNextAdjustment.first())
            var adjustWindowDate: String? = prevWindow
            var nextAdjustWindowDate: String? = nextWindow
            // v1.5.36 P1：接口 next 过期/缺失时优先权威窗口日历（含节假日推算）取 ≥今天 第一个窗口；
            // 权威日历为空才回退 +14 天顺延（极端兜底，原语义）
            if (nextAdjustWindowDate != null) {
                val nextDate = runCatching { LocalDate.parse(nextAdjustWindowDate, DateTimeFormatter.ofPattern("yyyy-MM-dd")) }.getOrNull()
                if (nextDate == null || nextDate.isBefore(LocalDate.now())) {
                    nextAdjustWindowDate = windowDates.firstOrNull { it >= LocalDate.now().toString() }
                        ?: nextDate?.plusDays(14)?.toString()
                }
            }
            // W1（最近已过调价窗口）优先级：① 接口 prev 存档（滚动窗口链）→ ② v1.5.36 P1：
            // 权威日历最后一个 <今天（含搁浅窗口日，替代 next − 14 天推算，节假日不错位）→
            // ③ next − 14 天推算（首次同步 prev 未形成时，10-29 → 10-15，真实窗口）→ ④ 最后兜底 lastWindowChecked
            //（仅当接口无任何窗口信息时；注意核对当天可能不是调价窗口日，故不能作为首选）
            if (adjustWindowDate == null) {
                val todayStr = LocalDate.now().toString()
                adjustWindowDate = windowDates.filter { it < todayStr }.lastOrNull()
                    ?: runCatching {
                        LocalDate.parse(nextAdjustWindowDate ?: "", DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                    }.getOrNull()?.minusDays(14)?.toString()
                    ?: OilPriceDateUtil.normalize(lastWindowChecked)
            }
            var windowStranded = false
            var adjustWindowPrice: String? = null
            var strandedKeptDate: String? = null
            var strandedKeptPrice: String? = null
            if (adjustWindowDate != null) {
                // W1 窗口调价判定（v1.4.188 方案C：跟随 App 默认省 + 92号汽油——不同省份/油号价格
                // 各不相同，不能用"任意省任一油号"的记录，否则会显示其他省的价格，如 8.57 vs 广东 8.63）。
                // 库内记录生效日 = 窗口日 + 1（窗口 24 时生效），故查「窗口日+1」。
                val home = settings.defaultProvince.first().takeIf { it in OilPriceDbSyncer.PROVINCES } ?: "广东"
                val w1Eff = runCatching { LocalDate.parse(adjustWindowDate).plusDays(1).toString() }.getOrNull() ?: adjustWindowDate
                val w1Row = priceDao.getPrice(home, "92号汽油", w1Eff)
                if (w1Row != null && !w1Row.isStranded) {
                    windowStranded = false
                    adjustWindowPrice = NumberFormat.measure(w1Row.price)
                } else {
                    // 搁浅：维持「默认省 92# 上一条调价记录」的价格（如 10-15 窗口维持 09-25 的 ¥8.63），
                    // 而非生效日 10-16——上一版本误显示生效日导致歧义。
                    windowStranded = true
                    val kept = priceDao.getLatestNonStranded(home, "92号汽油", w1Eff)
                    if (kept != null) {
                        strandedKeptDate = kept.effectiveDate.takeLast(5)
                        strandedKeptPrice = NumberFormat.measure(kept.price)
                    }
                }
            }

            // 版本号：V + 最新调价窗口日（不受搁浅影响）；无存档显示 V——
            val versionLabel = adjustWindowDate?.replace("-", "")?.let { "V$it" } ?: "V——"

            _priceDbStatus.value = PriceDbStatus(
                latestDate = latest,
                total = total,
                provinceCount = provinceCount,
                gradeCount = gradeCount,
                adjustmentCount = adjustmentCount,
                pendingCount = pendingCount,
                initialized = total > 0,
                lastPriceChangeDate = latest.ifBlank { null },
                lastWindowChecked = lastWindowChecked.ifBlank { null },
                versionDate = versionDate,
                lastCheckedAt = lastCheckTs,
                lastUpdatedAt = lastUpdatedAt.ifBlank { null },
                initializedAt = initAt,
                autoUpdateEnabled = autoEnabled,
                checkIntervalHours = (OilPriceDbSyncer.CHECK_INTERVAL_MS / (60 * 60 * 1000)).toInt(),
                lastUpdateMessage = lastMsg.ifBlank { null },
                isStranded = isStranded,
                adjustWindowDate = adjustWindowDate,
                nextAdjustWindowDate = nextAdjustWindowDate,
                windowStranded = windowStranded,
                adjustWindowPrice = adjustWindowPrice,
                strandedKeptDate = strandedKeptDate,
                strandedKeptPrice = strandedKeptPrice,
                incompleteProvinceCount = incompleteProvinceCount,
                versionLabel = versionLabel,
                lastSyncSources = settings.priceDbLastSources.first(),
            )
            _pendingItems.value = pendingDao.getPending()
        }
    }

    /** 立即更新：真联网检查（force=true，绕过开关与 8h 节流） */
    fun checkPriceDbNow() {
        if (_priceDbUpdating.value) return
        _priceDbUpdating.value = true
        viewModelScope.launch {
            try {
                syncer.checkAndUpdate(force = true)
                // v1.5.22 B：手动「立即更新」成功后也触发调价/窗口提醒（与周期 Worker 共用同一入口）
                syncer.notifyPriceChanges(context)
            } catch (e: Exception) {
                _exportMessage.value = ExportNotice("pdb_update_failed", listOf(e.message ?: ""))
            } finally {
                _priceDbUpdating.value = false
                loadPriceDbStatus()
            }
        }
    }

    fun acceptPending(id: Long, usePrimary: Boolean) {
        viewModelScope.launch {
            val ok = syncer.acceptPending(id, usePrimary)
            _pendingItems.value = pendingDao.getPending()
            _priceDbStatus.value = _priceDbStatus.value.copy(pendingCount = _pendingItems.value.size)
            _exportMessage.value = ExportNotice(if (ok) "adopted" else "adopt_failed")
            loadPriceDbStatus()
        }
    }

    fun ignorePending(id: Long) {
        viewModelScope.launch {
            syncer.ignorePending(id)
            _pendingItems.value = pendingDao.getPending()
            _priceDbStatus.value = _priceDbStatus.value.copy(pendingCount = _pendingItems.value.size)
            _exportMessage.value = ExportNotice("ignored")
            loadPriceDbStatus()
        }
    }

    /** 导出油价历史库为 CSV */
    fun exportOilPriceHistory() {
        viewModelScope.launch {
            try {
                val all = priceDao.getAll()
                if (all.isEmpty()) {
                    _exportMessage.value = ExportNotice("price_db_empty")
                    return@launch
                }
                val header = "省份,油号,生效日期,价格(元/升),来源"
                val lines = mutableListOf(header)
                for (p in all) {
                    lines.add(
                        listOf(
                            p.province,
                            p.grade,
                            p.effectiveDate,
                            NumberFormat.measure(p.price),
                            p.source ?: "内置",
                        ).joinToString(",")
                    )
                }
                val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
                val fileName = "KOiL-oil-price-history-$timestamp.csv"
                // 输出到用户可浏览的公共位置（Download/KOiL/，不再写入私有 files 目录）
                val saved = ExportHelper.saveToDownloads(context, fileName, "\uFEFF" + lines.joinToString("\n"))
                if (saved == null) {
                    _exportMessage.value = ExportNotice("export_failed", listOf("保存失败"))
                    return@launch
                }
                _exportMessage.value = ExportNotice("price_exported", listOf(all.size, saved))
            } catch (e: Exception) {
                _exportMessage.value = ExportNotice("export_failed", listOf(e.message ?: ""))
            }
        }
    }

    val uiState: StateFlow<SettingsUiState> = combine<Any, SettingsUiState>(
        settings.themeMode,
        settings.language,
        settings.defaultProvince,
        settings.defaultOilGrade,
        settings.tankCapacity,
        settings.keepLastGrade,
        settings.abnormalThreshold,
        settings.priceAlert,
        settings.pricePush,
        settings.locationEnabled,
        settings.priceDbAutoUpdate,
        settings.deviationThreshold,
        settings.priceDbCache,
        settings.floatingBottomBar,
        settings.floatingBottomBarStyle,
        settings.bottomBarMode,
    ) { args ->
        SettingsUiState(
            themeMode = args[0] as AppSettings.ThemeMode,
            language = args[1] as AppSettings.Language,
            defaultProvince = args[2] as String,
            defaultOilGrade = args[3] as String,
            tankCapacity = args[4] as Float,
            keepLastGrade = args[5] as Boolean,
            abnormalThreshold = args[6] as Double,
            priceAlert = args[7] as Boolean,
            pricePush = args[8] as Boolean,
            locationEnabled = args[9] as Boolean,
            priceDbAutoUpdate = args[10] as Boolean,
            deviationThreshold = args[11] as Double,
            priceDbCache = args[12] as String,
            floatingBottomBar = args[13] as Boolean,
            floatingBottomBarStyle = args[14] as String,
            bottomBarMode = args[15] as String,
            loaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, SettingsUiState())

    fun setThemeMode(mode: AppSettings.ThemeMode) =
        viewModelScope.launch { settings.setThemeMode(mode) }

    fun setLanguage(lang: AppSettings.Language) =
        viewModelScope.launch { settings.setLanguage(lang) }

    fun setFloatingBottomBar(on: Boolean) =
        viewModelScope.launch { settings.setFloatingBottomBar(on) }

    fun setFloatingBottomBarStyle(style: String) =
        viewModelScope.launch { settings.setFloatingBottomBarStyle(style) }

    fun setBottomBarMode(mode: String) =
        viewModelScope.launch { settings.setBottomBarMode(mode) }

    fun setDefaultProvince(province: String) =
        viewModelScope.launch { settings.setDefaultProvince(province) }

    fun setDefaultOilGrade(grade: String) =
        viewModelScope.launch { settings.setDefaultOilGrade(grade) }

    fun setTankCapacity(capacity: Float) =
        viewModelScope.launch { settings.setTankCapacity(capacity) }

    fun setKeepLastGrade(keep: Boolean) =
        viewModelScope.launch { settings.setKeepLastGrade(keep) }

    fun setAbnormalThreshold(pct: Double) =
        viewModelScope.launch { settings.setAbnormalThreshold(pct) }

    fun setPriceAlert(on: Boolean) =
        viewModelScope.launch { settings.setPriceAlert(on) }

    fun setPricePush(on: Boolean) =
        viewModelScope.launch { settings.setPricePush(on) }

    fun setLocationEnabled(on: Boolean) =
        viewModelScope.launch { settings.setLocationEnabled(on) }

    /** 定位并写入默认省份；有权限且定位成功返回 true，无权限返回 false（调用方应请求权限） */
    fun locateAndSetProvince(onSuccess: (String) -> Unit = {}) {
        viewModelScope.launch {
            if (!locationHelper.hasPermission()) return@launch
            val province = locationHelper.getCurrentProvince()
            if (!province.isNullOrBlank()) {
                settings.setDefaultProvince(province)
                onSuccess(province)
            }
        }
    }

    fun hasLocationPermission(): Boolean = locationHelper.hasPermission()

    fun setPriceDbAutoUpdate(on: Boolean) =
        viewModelScope.launch { settings.setPriceDbAutoUpdate(on) }

    fun setDeviationThreshold(pct: Double) =
        viewModelScope.launch {
            // §7.5 偏差阈值范围 0-50%
            val clamped = pct.coerceIn(0.0, 50.0)
            settings.setDeviationThreshold(clamped)
        }

    /**
     * 导出加油记录为 CSV（§8.7 格式，20 列），写入应用 files 目录。
     * 导出后 _exportMessage 显示文件路径或错误信息。
     */
    fun exportCsv() {
        viewModelScope.launch {
            try {
                val records = recordDao.getRange("1970-01-01", "2999-12-31")
                    .sortedBy { it.reoilDate }
                if (records.isEmpty()) {
                    _exportMessage.value = ExportNotice("no_records")
                    return@launch
                }

                val header = "加油日期,上次加油日期,油号,加油量(L),油价(元/L),应付金额(元),实付金额(元)," +
                    "本次里程(km),上次里程(km),行驶里程(km),上次加油量(L),上次实付金额(元)," +
                    "应付单价(元/L),实付单价(元/L),用油天数,平均油耗(L/100km),每公里成本(元/km)," +
                    "油耗状态,省份,来源"

                val lines = mutableListOf(header)
                // v1.5.20：与总览共用 DerivedCalculator，导出真实油耗状态（不再写死"正常"），里程保留小数
                val abnormalPct = settings.abnormalThreshold.first()
                val derived = DerivedCalculator.compute(records, abnormalPct)
                for ((index, d) in derived.withIndex()) {
                    val r = d.record
                    val prev = derived.getOrNull(index - 1)?.record
                    val distance = d.distance
                    val unitPayable = d.unitPricePayable?.let { NumberFormat.measure(it) } ?: "-"
                    val unitActual = d.unitPriceActual?.let { NumberFormat.measure(it) } ?: "-"
                    val consumption = d.oilConsumption?.let { NumberFormat.measure(it) } ?: "-"
                    val costPerKm = d.costPerKm?.let { NumberFormat.money(it) } ?: "-"
                    // 里程保留原始小数（原 %.0f 会把 89050.5 截成 89050，导入回环失真）
                    val mileageStr = r.mileage.fmtMileage()
                    val prevMileageStr = prev?.mileage?.fmtMileage() ?: "-"
                    val distanceStr = distance?.fmtMileage() ?: "-"
                    // 油耗状态：第一条无里程段→-；短途(<100km)→短距；否则按阈值判定 正常/偏高/偏低
                    val status = when {
                        distance == null -> "-"
                        distance < DerivedCalculator.MIN_DISTANCE -> "短距"
                        d.consumptionStatus == ConsumptionStatus.HIGH -> "偏高"
                        d.consumptionStatus == ConsumptionStatus.LOW -> "偏低"
                        else -> "正常"
                    }

                    lines.add(
                        listOf(
                            r.reoilDate,
                            prev?.reoilDate ?: "-",
                            "${r.oilGrade ?: "-"}号",
                            NumberFormat.measure(r.oilAmount),
                            r.oilPrice?.let { NumberFormat.measure(it) } ?: "-",
                            NumberFormat.money(r.payableAmount),
                            NumberFormat.money(r.actualAmount),
                            mileageStr,
                            prevMileageStr,
                            distanceStr,
                            prev?.let { NumberFormat.measure(it.oilAmount) } ?: "-",
                            prev?.let { NumberFormat.money(it.actualAmount) } ?: "-",
                            unitPayable,
                            unitActual,
                            d.oilDays?.toString() ?: "-",
                            consumption,
                            costPerKm,
                            status,
                            r.province ?: "-",
                            r.source ?: "-",
                        ).joinToString(",")
                    )
                }

                val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
                val fileName = "KOiL-records-$timestamp.csv"
                // 输出到用户可浏览的公共位置（Download/KOiL/，不再写入私有 files 目录）
                val saved = ExportHelper.saveToDownloads(context, fileName, "\uFEFF" + lines.joinToString("\n"))
                if (saved == null) {
                    _exportMessage.value = ExportNotice("export_failed", listOf("保存失败"))
                    return@launch
                }
                _exportMessage.value = ExportNotice("records_exported", listOf(records.size, saved))
            } catch (e: Exception) {
                _exportMessage.value = ExportNotice("export_failed", listOf(e.message ?: ""))
            }
        }
    }

    fun clearExportMessage() { _exportMessage.value = ExportNotice() }

    /**
     * 预览 CSV 导入（§7.8）：解析文件，分为 valid/duplicate/mileage_conflict/errors 四组，不入库。
     * v1.5.20：①文件内自身重复/里程链也检测（库内 + 已接受文件行合并视图）；②TreeSet 二分（O(n log n)，原 O(n²)）；
     * ③复合键用 Double 精确比较（不再字符串拼接）；④解析/判定切 Dispatchers.Default，大文件不卡主线程。
     */
    fun previewCsv(uri: Uri) {
        viewModelScope.launch {
            try {
                val content = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                } ?: run { _exportMessage.value = ExportNotice("cannot_read"); return@launch }

                val lines = content.lines().filter { it.isNotBlank() }
                if (lines.size < 2) {
                    _exportMessage.value = ExportNotice("file_empty")
                    return@launch
                }

                val headers = lines[0].split(",").map { it.trim().removeBOM() }
                val dateIdx = findColumn(headers, listOf("加油日期", "日期", "reoilDate", "date"))
                val mileageIdx = findColumn(headers, listOf("本次里程", "里程", "里程表", "mileage"))
                val oilAmountIdx = findColumn(headers, listOf("加油量", "油量", "oilAmount", "fuel"))
                val actualIdx = findColumn(headers, listOf("实付金额", "实付", "actualAmount", "actual"))
                val payableIdx = findColumn(headers, listOf("应付金额", "应付", "payableAmount", "payable"))
                val gradeIdx = findColumn(headers, listOf("油号", "油品", "oilGrade", "grade"))
                val priceIdx = findColumn(headers, listOf("油价", "单价", "oilPrice", "price"))
                val provinceIdx = findColumn(headers, listOf("省份", "省市", "province"))

                if (dateIdx < 0 || mileageIdx < 0 || oilAmountIdx < 0 || actualIdx < 0) {
                    _exportMessage.value = ExportNotice("missing_columns")
                    return@launch
                }

                // 解析 + 判定均为 CPU 密集，切后台线程（大文件不再卡 UI）
                val preview = withContext(Dispatchers.Default) {
                    val existing = recordDao.getRange("1970-01-01", "2999-12-31")
                    // 合并视图有序集合：库内记录 + 已接受的文件行。compareBy(date,mileage) 后
                    // lower/higher 即"日期/里程的前一条/后一条"，contains 即同日同里程重复（Double 精确比较）
                    val tree = TreeSet<MileageKey>(compareBy({ it.date }, { it.mileage }))
                    existing.forEach { tree.add(MileageKey(it.reoilDate, it.mileage)) }

                    val errors = mutableListOf<ParsedRecord>()
                    // 第一遍：按文件原顺序解析；格式错误直接入 errors，合法行保序暂存
                    val parsed = mutableListOf<ParsedRecord>()
                    for (line in lines.drop(1)) {
                        // v1.5.23 C1：最小 CSV 解析（支持引号包裹字段与 "" 转义）；KOiL 自导出无引号，行为与 split 一致
                        val cols = parseCsvLine(line)
                        val date = cols.getOrNull(dateIdx)?.trim()?.normalizeDate() ?: ""
                        val mileage = cols.getOrNull(mileageIdx)?.toDoubleOrNull()
                        val oilAmount = cols.getOrNull(oilAmountIdx)?.toDoubleOrNull()
                        val actual = cols.getOrNull(actualIdx)?.toDoubleOrNull()

                        if (date.isBlank() || !date.matches(Regex("""\d{4}-\d{2}-\d{2}""")) ||
                            mileage == null || oilAmount == null || actual == null ||
                            mileage <= 0 || oilAmount <= 0 || actual < 0
                        ) {
                            errors.add(ParsedRecord(date = date, errorKey = "format_error"))
                            continue
                        }

                        val payable = cols.getOrNull(payableIdx)?.toDoubleOrNull() ?: actual
                        val grade = cols.getOrNull(gradeIdx)?.trim()?.removeSuffix("号")?.removeSuffix("#")
                        val price = cols.getOrNull(priceIdx)?.toDoubleOrNull()
                        val province = cols.getOrNull(provinceIdx)?.trim()
                        parsed.add(
                            ParsedRecord(
                                date = date, mileage = mileage, oilAmount = oilAmount,
                                actualAmount = actual, payableAmount = payable,
                                oilGrade = grade, oilPrice = price, province = province,
                            )
                        )
                    }

                    // 第二遍：按 (date,mileage) 升序，在合并视图上二分判重 + 严格递增里程链；
                    // 已接受的文件行即时入树，故文件内自身重复/冲突也能被发现
                    val verdicts = HashMap<Int, CsvVerdict>()
                    parsed.withIndex()
                        .sortedWith(compareBy({ it.value.date }, { it.value.mileage }))
                        .forEach { (idx, p) ->
                            val key = MileageKey(p.date, p.mileage)
                            val prev = tree.lower(key)
                            val next = tree.higher(key)
                            when {
                                tree.contains(key) ->
                                    verdicts[idx] = CsvVerdict(CSV_DUP)
                                prev != null && p.mileage <= prev.mileage ->
                                    verdicts[idx] = CsvVerdict(CSV_CONFLICT, "lt_prev", prev.mileage.toInt())
                                next != null && p.mileage >= next.mileage ->
                                    verdicts[idx] = CsvVerdict(CSV_CONFLICT, "gt_next", next.mileage.toInt())
                                else -> {
                                    tree.add(key)
                                    verdicts[idx] = CsvVerdict(CSV_VALID)
                                }
                            }
                        }

                    // 第三遍：按文件原顺序归类（展示顺序与文件一致）
                    val valid = mutableListOf<ParsedRecord>()
                    val duplicates = mutableListOf<ParsedRecord>()
                    val conflicts = mutableListOf<ParsedRecord>()
                    parsed.forEachIndexed { idx, p ->
                        when (val v = verdicts[idx]) {
                            null -> errors.add(p.copy(errorKey = "format_error"))
                            else -> when (v.group) {
                                CSV_VALID -> valid.add(p)
                                CSV_DUP -> duplicates.add(p.copy(errorKey = "duplicate"))
                                else -> conflicts.add(p.copy(conflictKey = v.conflictKey, conflictArg = v.conflictArg))
                            }
                        }
                    }

                    ImportPreview(
                        valid = valid, duplicates = duplicates,
                        mileageConflicts = conflicts, errors = errors,
                    )
                }

                _importPreview.value = preview
                _exportMessage.value = ExportNotice()
            } catch (e: Exception) {
                _exportMessage.value = ExportNotice("parse_failed", listOf(e.message ?: ""))
            }
        }
    }

    /** 确认导入：将 valid 组批量写入数据库（v1.5.20 单事务 + IGNORE，原逐条 insert + 吞异常） */
    fun confirmImport() {
        val preview = _importPreview.value ?: return
        viewModelScope.launch {
            val entities = preview.valid.map { r ->
                KOiLRecordEntity(
                    reoilDate = r.date, mileage = r.mileage, oilAmount = r.oilAmount,
                    payableAmount = r.payableAmount, actualAmount = r.actualAmount,
                    oilPrice = r.oilPrice, oilGrade = r.oilGrade, province = r.province,
                    source = "csv_import",
                )
            }
            // 单事务批量插入；IGNORE 兜底唯一索引冲突（预览后数据被改动），rowId=-1 即跳过
            val imported = if (entities.isEmpty()) 0
            else withContext(Dispatchers.Default) {
                recordDao.insertAllIgnore(entities).count { it != -1L }
            }
            _importPreview.value = null
            val skipped = preview.duplicates.size + preview.mileageConflicts.size + preview.errors.size
            _exportMessage.value = ExportNotice("import_done", listOf(imported, skipped))
        }
    }

    fun dismissImportPreview() { _importPreview.value = null }

    /**
     * 从完整备份 JSON 恢复数据（§7.10 反向操作）。
     * 解析 records + settings，写入数据库和 DataStore。
     */
    fun importBackup(uri: Uri) {
        viewModelScope.launch {
            try {
                val content = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    ?: run { _exportMessage.value = ExportNotice("cannot_read"); return@launch }

                val json = org.json.JSONObject(content)
                val recordsArr = json.optJSONArray("records")
                if (recordsArr == null || recordsArr.length() == 0) {
                    _exportMessage.value = ExportNotice("backup_no_records")
                    return@launch
                }

                var imported = 0
                var skipped = 0
                for (i in 0 until recordsArr.length()) {
                    val r = recordsArr.getJSONObject(i)
                    try {
                        recordDao.insert(
                            KOiLRecordEntity(
                                reoilDate = r.getString("date"),
                                mileage = r.getDouble("mileage"),
                                oilAmount = r.getDouble("oilAmount"),
                                payableAmount = r.optDouble("payableAmount", 0.0),
                                actualAmount = r.getDouble("actualAmount"),
                                oilPrice = if (r.isNull("oilPrice")) null else r.optDouble("oilPrice"),
                                oilGrade = r.optString("oilGrade").takeIf { it.isNotEmpty() },
                                province = r.optString("province").takeIf { it.isNotEmpty() },
                                source = r.optString("source", "backup_restore"),
                            )
                        )
                        imported++
                    } catch (_: Exception) { skipped++ }
                }

                // 恢复设置
                val settingsObj = json.optJSONObject("settings")
                if (settingsObj != null) {
                    settingsObj.optString("themeMode").takeIf { it.isNotEmpty() }?.let {
                        runCatching { settings.setThemeMode(AppSettings.ThemeMode.valueOf(it)) }
                    }
                    settingsObj.optString("language").takeIf { it.isNotEmpty() }?.let {
                        runCatching { settings.setLanguage(AppSettings.Language.valueOf(it)) }
                    }
                    settingsObj.optString("defaultProvince").takeIf { it.isNotEmpty() }?.let { settings.setDefaultProvince(it) }
                    settingsObj.optString("defaultGrade").takeIf { it.isNotEmpty() }?.let { settings.setDefaultOilGrade(it) }
                    if (!settingsObj.isNull("tankCapacity")) settings.setTankCapacity(settingsObj.getDouble("tankCapacity").toFloat())
                    if (!settingsObj.isNull("keepLastGrade")) settings.setKeepLastGrade(settingsObj.getBoolean("keepLastGrade"))
                    if (!settingsObj.isNull("abnormalThreshold")) settings.setAbnormalThreshold(settingsObj.getDouble("abnormalThreshold"))
                    if (!settingsObj.isNull("deviationThreshold")) settings.setDeviationThreshold(settingsObj.getDouble("deviationThreshold"))
                    if (!settingsObj.isNull("priceAlert")) settings.setPriceAlert(settingsObj.getBoolean("priceAlert"))
                    if (!settingsObj.isNull("locationEnabled")) settings.setLocationEnabled(settingsObj.getBoolean("locationEnabled"))
                    if (!settingsObj.isNull("priceDbAutoUpdate")) settings.setPriceDbAutoUpdate(settingsObj.getBoolean("priceDbAutoUpdate"))
                }

                _exportMessage.value = ExportNotice("restore_done", listOf(imported, skipped))
            } catch (e: Exception) {
                _exportMessage.value = ExportNotice("restore_failed", listOf(e.message ?: ""))
            }
        }
    }

    private fun findColumn(headers: List<String>, candidates: List<String>): Int {
        for ((i, h) in headers.withIndex()) {
            if (candidates.any { h.startsWith(it, ignoreCase = true) }) return i
        }
        return -1
    }

    private fun String.removeBOM(): String = if (startsWith("\uFEFF")) substring(1) else this

    private fun String.normalizeDate(): String = replace("/", "-").let {
        val parts = it.split("-")
        if (parts.size == 3) {
            // v1.5.36 P3：非法数字日期（如 abc-01-01）单行容错——返回原串，由调用方正则判 format_error，
            // 不再抛 NumberFormatException 拖垮整文件解析
            runCatching {
                "%04d-%02d-%02d".format(Locale.ROOT, parts[0].toInt(), parts[1].toInt(), parts[2].toInt())
            }.getOrDefault(it)
        } else it
    }

    /** 里程/距离导出：整数不带小数点，小数保留原值（避免 %.0f 截断小数，保证导入回环不失真） */
    private fun Double.fmtMileage(): String =
        if (this % 1.0 == 0.0) this.toLong().toString() else this.toString()

    /**
     * 完整备份（§7.10）：导出 JSON，包含全部记录（含派生字段）+ 设置项。
     * 文件名：KOiL_完整备份_YYYYMMDD.json，version 1。
     */
    fun exportBackup() {
        viewModelScope.launch {
            try {
                val records = recordDao.getRange("1970-01-01", "2999-12-31")
                    .sortedBy { it.reoilDate }
                if (records.isEmpty()) {
                    _exportMessage.value = ExportNotice("no_records_backup")
                    return@launch
                }

                // 计算派生字段
                val dateFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd")
                val recordList = mutableListOf<Map<String, Any?>>()
                var prev: KOiLRecordEntity? = null
                for (r in records) {
                    val distance = prev?.let { r.mileage - it.mileage }
                    val oilDays = prev?.let {
                        val d1 = LocalDate.parse(r.reoilDate, dateFmt)
                        val d0 = LocalDate.parse(it.reoilDate, dateFmt)
                        java.time.temporal.ChronoUnit.DAYS.between(d0, d1)
                    }
                    val consumption = if (distance != null && distance >= 100)
                        Math.round(r.oilAmount / distance * 100 * 100) / 100.0 else null
                    val costPerKm = if (distance != null && distance >= 100)
                        Math.round(r.actualAmount / distance * 100) / 100.0 else null

                    recordList.add(
                        mapOf(
                            "date" to r.reoilDate,
                            "mileage" to r.mileage,
                            "oilAmount" to r.oilAmount,
                            "payableAmount" to r.payableAmount,
                            "actualAmount" to r.actualAmount,
                            "oilPrice" to r.oilPrice,
                            "oilGrade" to r.oilGrade,
                            "province" to r.province,
                            "source" to r.source,
                            "distance" to distance,
                            "oilDays" to oilDays,
                            "oilConsumption" to consumption,
                            "costPerKm" to costPerKm,
                        )
                    )
                    prev = r
                }

                // 设置项
                val settingsMap = mapOf(
                    "themeMode" to settings.themeMode.first().name,
                    "language" to settings.language.first().name,
                    "defaultProvince" to settings.defaultProvince.first(),
                    "defaultGrade" to settings.defaultOilGrade.first(),
                    "tankCapacity" to settings.tankCapacity.first(),
                    "keepLastGrade" to settings.keepLastGrade.first(),
                    "abnormalThreshold" to settings.abnormalThreshold.first(),
                    "deviationThreshold" to settings.deviationThreshold.first(),
                    "priceAlert" to settings.priceAlert.first(),
                    "locationEnabled" to settings.locationEnabled.first(),
                    "priceDbAutoUpdate" to settings.priceDbAutoUpdate.first(),
                )

                val backup = mapOf(
                    "version" to 1,
                    "exportTime" to SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()),
                    "recordCount" to records.size,
                    "records" to recordList,
                    "settings" to settingsMap,
                )

                // 手动构建 JSON（避免额外依赖）
                val json = buildJsonString(backup)
                val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
                val fileName = "KOiL_完整备份_$timestamp.json"
                // 输出到用户可浏览的公共位置（Download/KOiL/，不再写入私有 files 目录）
                val saved = ExportHelper.saveToDownloads(context, fileName, json)
                if (saved == null) {
                    _exportMessage.value = ExportNotice("backup_failed", listOf("保存失败"))
                    return@launch
                }
                _exportMessage.value = ExportNotice("backup_done", listOf(records.size, saved))
            } catch (e: Exception) {
                _exportMessage.value = ExportNotice("backup_failed", listOf(e.message ?: ""))
            }
        }
    }

    private fun buildJsonString(obj: Any?): String = when (obj) {
        null -> "null"
        is Map<*, *> -> obj.entries.joinToString(",", "{", "}") { (k, v) ->
            "\"$k\":${buildJsonString(v)}"
        }
        is List<*> -> obj.joinToString(",", "[", "]") { buildJsonString(it) }
        is String -> "\"${obj.replace("\\", "\\\\").replace("\"", "\\\"")}\""
        // v1.5.23 C3：NaN/Infinity 输出非法 JSON——防御性写 null（当前数据源不会产生，防外部异常值）
        is Double -> if (obj.isFinite()) obj.toString() else "null"
        is Number -> obj.toString()
        is Boolean -> obj.toString()
        else -> "\"$obj\""
    }

    /** v1.5.23 C1：最小 CSV 行解析——双引号包裹字段 + "" 转义；未包裹字段按逗号切分（兼容 KOiL 导出格式，回环等价） */
    private fun parseCsvLine(line: String): List<String> {
        val out = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                inQuotes -> {
                    if (c == '"') {
                        if (i + 1 < line.length && line[i + 1] == '"') { sb.append('"'); i++ }
                        else inQuotes = false
                    } else sb.append(c)
                }
                c == '"' -> inQuotes = true
                c == ',' -> { out.add(sb.toString()); sb.setLength(0) }
                else -> sb.append(c)
            }
            i++
        }
        out.add(sb.toString())
        return out
    }
}

// ===== v1.5.20 CSV 导入二分判定辅助（O(n log n)，库内 + 文件内合并视图）=====
/** (日期, 里程) 复合键；ISO 日期字符串字典序即时间序，配合 TreeSet 做 lower/higher 二分定位 */
private data class MileageKey(val date: String, val mileage: Double)

/** CSV 行判定结果；group 取 CSV_VALID / CSV_DUP / CSV_CONFLICT，冲突时带 conflictKey/Arg */
private data class CsvVerdict(val group: Int, val conflictKey: String? = null, val conflictArg: Int = 0)

private const val CSV_VALID = 0
private const val CSV_DUP = 1
private const val CSV_CONFLICT = 2
