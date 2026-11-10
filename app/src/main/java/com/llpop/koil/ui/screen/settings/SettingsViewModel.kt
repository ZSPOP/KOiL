package com.llpop.koil.ui.screen.settings

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.llpop.koil.R
import com.llpop.koil.data.local.dao.KOiLPriceDao
import com.llpop.koil.data.local.dao.KOiLRecordDao
import com.llpop.koil.data.local.entity.KOiLRecordEntity
import com.llpop.koil.domain.logic.NumberFormat
import com.llpop.koil.data.location.LocationHelper
import com.llpop.koil.data.oil.OilPriceDbSyncer
import com.llpop.koil.data.sync.BackupCodec
import com.llpop.koil.data.oil.OilPriceDateUtil
import com.llpop.koil.data.oil.OilPriceNetwork
import com.llpop.koil.data.oil.Provinces
import com.llpop.koil.data.settings.AppSettings
import com.llpop.koil.domain.logic.ConsumptionStatus
import com.llpop.koil.domain.logic.DerivedCalculator
import com.llpop.koil.domain.logic.RecordValidator
import com.llpop.koil.domain.logic.inChronologicalOrder
import com.llpop.koil.util.ExportHelper
import kotlinx.coroutines.CancellationException
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
    val importPreview: ImportPreview? = null,
    // 导航栏（参考 Mishka 悬浮导航体系）
    val floatingBottomBar: Boolean = true,
    val floatingBottomBarStyle: String = AppSettings.DEFAULT_FLOATING_BAR_STYLE,
    val bottomBarMode: String = "icon_and_text",
    // 设置异步加载完成标志（DataStore 首值到达前不渲染开关，杜绝"关→开"闪烁）
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
    /** 满箱标记：旧 CSV 无该列按 true（历史口径=全部视为加满） */
    val isFullTank: Boolean = true,
    val errorKey: String? = null,
    val conflictKey: String? = null,
    val conflictArg: Int = 0,
)

class SettingsViewModel(
    private val settings: AppSettings,
    private val recordDao: KOiLRecordDao,
    private val priceDao: KOiLPriceDao,
    private val syncer: OilPriceDbSyncer,
    private val locationHelper: LocationHelper,
    // 类型为 Application。ViewModel 比 Activity 长寿，持有 Activity Context 会泄漏；
    // 这里本来拿到的就是 application（Koin androidContext），只是声明成 Context 让 lint 无从判断。
    private val context: Application,
) : ViewModel() {

    init {
        // 库行数变化（后台补全国/立即更新写库）时自动刷新缺口统计，
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
        val windowStranded: Boolean = false,        // 最近窗口搁浅（发改委确认维持，库内有该窗口记录且 isStranded=true）
        val windowRecordMissing: Boolean = false,   // 库内根本没有该窗口记录（待补灌/待核验）——与搁浅严格区分
        val adjustWindowPrice: String? = null,      // 最近窗口有调价时的价格文本（元/升）
        val strandedKeptDate: String? = null,       // 搁浅时维持的上一条调价生效日（如 09-25）
        val strandedKeptPrice: String? = null,      // 搁浅时维持的价格（元/升）
        // 本次窗口相对上一期调价记录的涨跌额（元/升，正=涨）。
        // 与 adjustWindowPrice 同口径（默认省 + 92 号）；搁浅或无上期记录时为 null，UI 不显示。
        val adjustWindowDelta: Double? = null,
        val incompleteProvinceCount: Int = 0,       // 窗口日覆盖不足的省份数（完整性提示）
        // 补灌可观测状态——区分"正在补灌 N/31"与"已放弃，需手动重试"
        val seedState: OilPriceDbSyncer.SeedState = OilPriceDbSyncer.SeedState.IDLE,
        val seedDone: Int = 0,
        val seedTotal: Int = 0,
        val seedFailedCount: Int = 0,               // 本轮补灌失败省数（弱网首装"看似补完"的缺口）
        val versionLabel: String = "V——",           // 版本号（V+最新调价窗口日，不受搁浅影响）
        // 多源一致性（最近一次同步来源快照，如 "锚点:xxapi · 今日价:abapi"）
        val lastSyncSources: String = "",
    )
    private val _priceDbStatus = MutableStateFlow(PriceDbStatus())
    val priceDbStatus: StateFlow<PriceDbStatus> = _priceDbStatus.asStateFlow()

    private val _priceDbUpdating = MutableStateFlow(false)
    val priceDbUpdating: StateFlow<Boolean> = _priceDbUpdating.asStateFlow()

    // EIA API Key（原油估算主源配置，默认内置；用户可改，留空即禁用 EIA 估算）
    private val _eiaApiKey = MutableStateFlow("")
    val eiaApiKey: StateFlow<String> = _eiaApiKey.asStateFlow()

    // EIA 原油估算源启用开关（开=用 EIA；关=直接用腾讯，输入框显示供修改）
    private val _eiaEnabled = MutableStateFlow(true)
    val eiaEnabled: StateFlow<Boolean> = _eiaEnabled.asStateFlow()

    // EIA 配置加载完成标志（首值到达前不渲染 EIA 开关，杜绝"开→关/关→开"闪烁）
    private val _eiaLoaded = MutableStateFlow(false)
    val eiaLoaded: StateFlow<Boolean> = _eiaLoaded.asStateFlow()

    private val _importPreview = MutableStateFlow<ImportPreview?>(null)
    val importPreview: StateFlow<ImportPreview?> = _importPreview.asStateFlow()

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

    /**
     * 状态刷新防重入：补灌期间页面每 3 秒触发一次，单次查询若超过间隔会堆叠并发、进一步压 DB。
     * 上一轮没跑完就直接跳过本轮（下一轮会补上）。
     */
    private val statusLoadInFlight = java.util.concurrent.atomic.AtomicBoolean(false)

    /**
     * 刷新油价库状态：统计 + 配置真实字段。
     *
     * @param runMaintenance 是否顺带做一次假生效日维护扫描。只在打开设置页时传 true——
     *        每次状态刷新都跑全表 DISTINCT（补灌期间每秒一次）是最大的无效开销；
     *        维护只删同价孤立假日期。
     */
    fun loadPriceDbStatus(runMaintenance: Boolean = false) {
        if (!statusLoadInFlight.compareAndSet(false, true)) return
        viewModelScope.launch {
            try {
                loadPriceDbStatusNow(runMaintenance)
            } finally {
                // 无论成功/取消/异常都释放，否则一次异常会让状态永久不再刷新
                statusLoadInFlight.set(false)
            }
        }
    }

    /** [loadPriceDbStatus] 的实际查询体（防重入与生命周期在调用方处理） */
    private suspend fun loadPriceDbStatusNow(runMaintenance: Boolean) {
        if (runMaintenance) {
            // 打开页面时做一次维护扫描（过渡期只记录可疑日期，不删数据）
            syncer.purgeIllegalEffectiveDatesIfReady()
        }
        // SQL 聚合统计（避免全表 getAll 内存统计在大数据量时卡顿）
        val total = priceDao.count()
        val latest = priceDao.maxDate() ?: ""
        val provinceCount = priceDao.countProvinces()
        val gradeCount = priceDao.countGrades()
        // 调价次数：优先取"权威调价窗口日历"中今年已发生的窗口日数（含搁浅窗口），
        // 窗口日历为空时才回退库内去重生效日期数
        //（DISTINCT effectiveDate 会被降级假生效日污染，不可作为调价次数口径）
        // 窗口日 24 点前不算已发生 → 严格 < today（今天窗口日不计入，避免"29 号当天调价 21 次"）
        val windowDates = settings.priceDbAdjustDates.first()
            .split(",").map { it.trim() }.filter { it.isNotBlank() }
        val todayStr = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        val adjustmentCount = if (windowDates.isNotEmpty()) {
            windowDates.count { it < todayStr }
        } else {
            priceDao.countAdjustments()
        }

        // 完整性缺口：阈值 = "已调价窗口数"（排除搁浅窗口——搁浅不写记录，
        // 计入会让所有省都差几个而误报），再容忍 2 个窗口的数据拉取缺口（各省数据源偶发缺窗口）
        // 窗口是否调价过：窗口日 +1 在库内有记录（生效日 = 窗口日 + 1 天）
        // 缺口阈值同样用严格 < today（今天窗口日不计入）
        val adjustedWindowCount = windowDates.count { w ->
            w < todayStr && runCatching {
                priceDao.getByDate(LocalDate.parse(w).plusDays(1).toString()) != null
            }.getOrDefault(false)
        }
        // 日历未就绪时不能恒返回 0 —— "首装/弱网卡在取日历"恰恰是最需要提示的场景，
        // 结果设置页一片正常，用户只能干等或杀进程。此时退化为「一条记录都没有的省份」：
        // 不依赖窗口日历也能看出全国没铺开（只灌了本省 → 30 省为 0）。
        suspend fun coverageOf(province: String): Int =
            runCatching { priceDao.countDatesForProvince(province) }.getOrDefault(0)
        // 日历就绪 → 阈值 = 已调价窗口数 − 2（容忍数据源偶发缺窗口），至少为 1；
        // 日历未就绪 → 阈值 1，即"零记录才算缺口"
        val threshold = if (adjustedWindowCount > 0) (adjustedWindowCount - 2).coerceAtLeast(1) else 1
        var gapCount = 0
        for (province in OilPriceDbSyncer.PROVINCES) {
            if (coverageOf(province) < threshold) gapCount++
        }
        val incompleteProvinceCount = gapCount

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
        // 接口 next 过期/缺失时优先权威窗口日历（含节假日推算）取 ≥今天 第一个窗口；
        // 接口与日历都可用时取更早的窗口；权威日历为空才回退 +14 天顺延（极端兜底）。
        val todayDate = runCatching { LocalDate.parse(todayStr) }.getOrNull()
        val nextDate = nextAdjustWindowDate?.let {
            runCatching { LocalDate.parse(it, DateTimeFormatter.ofPattern("yyyy-MM-dd")) }.getOrNull()
        }
        val calendarNext = windowDates.firstOrNull { todayDate != null && it >= todayStr }
        if (nextDate == null || (todayDate != null && nextDate.isBefore(todayDate))) {
            nextAdjustWindowDate = calendarNext
                ?: nextDate?.plusDays(14)?.toString()
        } else if (calendarNext != null && nextDate != null &&
            runCatching { LocalDate.parse(calendarNext) }.getOrNull()?.isBefore(nextDate) == true
        ) {
            nextAdjustWindowDate = calendarNext
        }
        // W1（最近已过调价窗口）优先级：① 接口 prev 存档（滚动窗口链）→ ②
        // 权威日历最后一个 <今天（含搁浅窗口日，不用 next − 14 天推算，节假日不错位）→
        // ③ next − 14 天推算（首次同步 prev 未形成时，10-29 → 10-15，真实窗口）→ ④ 最后兜底 lastWindowChecked
        //（仅当接口无任何窗口信息时；注意核对当天可能不是调价窗口日，故不能作为首选）
        if (adjustWindowDate == null) {
            // 复用函数开头的 todayStr：此处再声明同名局部变量会遮蔽外层，
            // 遮蔽外层——两者口径相同（都是当前日期），但遮蔽会让维护者误以为口径不同
            adjustWindowDate = windowDates.filter { it < todayStr }.lastOrNull()
                ?: runCatching {
                    LocalDate.parse(nextAdjustWindowDate ?: "", DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                }.getOrNull()?.minusDays(14)?.toString()
                ?: OilPriceDateUtil.normalize(lastWindowChecked)
        }
        var windowStranded = false
        var windowRecordMissing = false
        var adjustWindowPrice: String? = null
        var adjustWindowDelta: Double? = null
        var strandedKeptDate: String? = null
        var strandedKeptPrice: String? = null
        if (adjustWindowDate != null) {
            // W1 窗口调价判定（跟随 App 默认省 + 92号汽油——不同省份/油号价格
            // 各不相同，不能用"任意省任一油号"的记录，否则会显示其他省的价格，如 8.57 vs 广东 8.63）。
            // 库内记录生效日 = 窗口日 + 1（窗口 24 时生效），故查「窗口日+1」。
            val home = settings.defaultProvince.first().takeIf { it in OilPriceDbSyncer.PROVINCES } ?: "广东"
            val w1Eff = runCatching { LocalDate.parse(adjustWindowDate).plusDays(1).toString() }.getOrNull() ?: adjustWindowDate
            val w1Row = priceDao.getPrice(home, "92号汽油", w1Eff)
            when {
                w1Row != null && !w1Row.isStranded -> {
                    windowStranded = false
                    adjustWindowPrice = NumberFormat.measure(w1Row.price)
                    // 涨跌额：同省同油号「本期生效日之前」最近一条非搁浅记录，相减即得
                    val prev = runCatching {
                        priceDao.getLatestNonStranded(
                            home, "92号汽油",
                            LocalDate.parse(w1Eff).minusDays(1).toString(),
                        )
                    }.getOrNull()
                    if (prev != null) adjustWindowDelta = w1Row.price - prev.price
                }
                // 真搁浅：该窗口有记录且标了 isStranded，才允许说"搁浅·维持"。
                w1Row != null -> {
                    windowStranded = true
                    // 维持「默认省 92# 上一条调价记录」的价格（如 10-15 窗口维持 09-25 的 ¥8.63），
                    // 而非生效日 10-16（显示生效日会导致歧义）。
                    val kept = priceDao.getLatestNonStranded(home, "92号汽油", w1Eff)
                    if (kept != null) {
                        strandedKeptDate = kept.effectiveDate.takeLast(5)
                        strandedKeptPrice = NumberFormat.measure(kept.price)
                    }
                }
                // 缺数据：该窗口一行都没有（未补灌/被清理/刚切默认省）。与搁浅走同一分支的话，
                // 会向用户断言"发改委确认维持"，并拿上一窗口价格进一步强化错误结论。
                else -> {
                    windowRecordMissing = true
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
            windowRecordMissing = windowRecordMissing,
            adjustWindowPrice = adjustWindowPrice,
            adjustWindowDelta = adjustWindowDelta,
            strandedKeptDate = strandedKeptDate,
            strandedKeptPrice = strandedKeptPrice,
            incompleteProvinceCount = incompleteProvinceCount,
            seedState = syncer.seedState,
            seedDone = syncer.seedDone,
            seedTotal = syncer.seedTotal,
            seedFailedCount = syncer.seedFailedCount,
            versionLabel = versionLabel,
            lastSyncSources = settings.priceDbLastSources.first(),
        )
    }

    /** 立即更新：真联网检查（force=true，绕过开关与 8h 节流） */
    fun checkPriceDbNow() {
        if (_priceDbUpdating.value) return
        _priceDbUpdating.value = true
        viewModelScope.launch {
            try {
                syncer.checkAndUpdate(force = true)
                // 手动「立即更新」成功后也触发调价/窗口提醒（与周期 Worker 共用同一入口）
                syncer.notifyPriceChanges(context)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _exportMessage.value = ExportNotice("pdb_update_failed", listOf(e.message ?: ""))
            } finally {
                _priceDbUpdating.value = false
                loadPriceDbStatus()
            }
        }
    }

    /** 导出油价历史库为 CSV */
    fun exportOilPriceHistory() {
        viewModelScope.launch {
            // 全量油价库可达万级行：查询、拼 CSV 与写文件统一放 IO，避免导出时冻结界面
            withContext(Dispatchers.IO) {
                try {
                    val all = priceDao.getAll()
                    if (all.isEmpty()) {
                        _exportMessage.value = ExportNotice("price_db_empty")
                        return@withContext
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
                            ).joinToString(",") { csvEscape(it) }
                        )
                    }
                    val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
                    val fileName = "KOiL-oil-price-history-$timestamp.csv"
                    // 输出到用户可浏览的公共位置（Download/KOiL/，不写入私有 files 目录）
                    val saved = ExportHelper.saveToDownloads(context, fileName, "\uFEFF" + lines.joinToString("\n"))
                    if (saved == null) {
                        _exportMessage.value = ExportNotice(
                            "export_failed", listOf(context.getString(R.string.koil_ov_save_failed))
                        )
                        return@withContext
                    }
                    _exportMessage.value = ExportNotice("price_exported", listOf(all.size, saved))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _exportMessage.value = ExportNotice("export_failed", listOf(e.message ?: ""))
                }
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
        viewModelScope.launch {
            // 阈值口径三处统一（以设置页为准）：异常油耗 0..50
            settings.setAbnormalThreshold(pct.coerceIn(0.0, 50.0))
        }

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
            // 阈值口径三处统一（以设置页为准）：偏差阈值 1..50（0 会让每次加油都报警，无实用语义）
            val clamped = pct.coerceIn(1.0, 50.0)
            settings.setDeviationThreshold(clamped)
        }

    /**
     * 导出加油记录为 CSV（§8.7 格式，20 列），写入应用 files 目录。
     * 导出后 _exportMessage 显示文件路径或错误信息。
     */
    fun exportCsv() {
        viewModelScope.launch {
            // 全表读 + 全量派生 + 20 列字符串拼接 + MediaStore 写盘整段放到 IO。
            // 不在 viewModelScope 默认的 Main.immediate 上跑（实测派生 2000 条 12.6ms、万条 33.8ms，
            // 手机再乘 3~5 倍，叠加写盘即明显卡顿、万条量级踩 ANR 线）。
            // MutableStateFlow.value 本身线程安全，_exportMessage 直接赋值，无需切回主线程。
            withContext(Dispatchers.IO) {
                try {
                    val records = recordDao.getRange("1970-01-01", "2999-12-31")
                        .inChronologicalOrder()
                    if (records.isEmpty()) {
                        _exportMessage.value = ExportNotice("no_records")
                        return@withContext
                    }

                    val header = CSV_RECORD_HEADER

                    val lines = mutableListOf(header)
                    // 与总览共用 DerivedCalculator，导出真实油耗状态（不写死"正常"），里程保留小数
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
                        // 油耗状态（满箱区间口径，与总览页一致）：
                        // 首条(无上一条)→-；回拨/异常段→异常；短途闭合区间→短途参考；
                        // 未满→待结算；无锚点→无基准；否则按阈值判定 正常/偏高/偏低
                        val status = when {
                            distance == null -> "-"
                            d.distanceAnomaly && d.oilConsumption == null -> "异常"
                            d.oilConsumption != null && d.shortInterval -> "短途参考"
                            d.oilConsumption != null && d.consumptionStatus == ConsumptionStatus.HIGH -> "偏高"
                            d.oilConsumption != null && d.consumptionStatus == ConsumptionStatus.LOW -> "偏低"
                            d.oilConsumption != null -> "正常"
                            d.consumptionStatus == ConsumptionStatus.PENDING -> "待结算"
                            d.consumptionStatus == ConsumptionStatus.NO_BASE -> "无基准"
                            else -> "-"
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
                                if (r.isFullTank) "是" else "否",
                            ).joinToString(",") { csvEscape(it) }
                        )
                    }

                    val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
                    val fileName = "KOiL-records-$timestamp.csv"
                    // 输出到用户可浏览的公共位置（Download/KOiL/，不写入私有 files 目录）
                    val saved = ExportHelper.saveToDownloads(context, fileName, "\uFEFF" + lines.joinToString("\n"))
                    if (saved == null) {
                        _exportMessage.value = ExportNotice("export_failed", listOf(context.getString(R.string.koil_ov_save_failed)))
                        return@withContext
                    }
                    _exportMessage.value = ExportNotice("records_exported", listOf(records.size, saved))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _exportMessage.value = ExportNotice("export_failed", listOf(e.message ?: ""))
                }
            }
        }
    }

    fun clearExportMessage() { _exportMessage.value = ExportNotice() }

    /**
     * 预览 CSV 导入（§7.8）：解析文件，分为 valid/duplicate/mileage_conflict/errors 四组，不入库。
     * ①文件内自身重复/里程链也检测（库内 + 已接受文件行合并视图）；②TreeSet 二分（O(n log n)，避免 O(n²) 全量比较）；
     * ③复合键用 Double 精确比较（不用字符串拼接）；④解析/判定切 Dispatchers.Default，大文件不卡主线程。
     */
    fun previewCsv(uri: Uri) {
        viewModelScope.launch {
            try {
                val content = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                } ?: run { _exportMessage.value = ExportNotice("cannot_read"); return@launch }

                val lines = withContext(Dispatchers.Default) { content.lines().filter { it.isNotBlank() } }
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
                val fullTankIdx = findColumn(headers, listOf("本次加满", "加满", "isFullTank", "fullTank"))

                if (dateIdx < 0 || mileageIdx < 0 || oilAmountIdx < 0 || actualIdx < 0) {
                    _exportMessage.value = ExportNotice("missing_columns")
                    return@launch
                }

                // 解析 + 判定均为 CPU 密集，切后台线程（大文件不卡 UI）
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
                        // 最小 CSV 解析（支持引号包裹字段与 "" 转义）；KOiL 自导出无引号，行为与 split 一致
                        val cols = parseCsvLine(line)
                        val date = cols.getOrNull(dateIdx)?.trim()?.normalizeDate() ?: ""
                        val mileage = cols.getOrNull(mileageIdx)?.toDoubleOrNull()
                        val oilAmount = cols.getOrNull(oilAmountIdx)?.toDoubleOrNull()
                        val actual = cols.getOrNull(actualIdx)?.toDoubleOrNull()

                        val payable = cols.getOrNull(payableIdx)?.toDoubleOrNull() ?: actual

                        // 统一走 RecordValidator：NaN/Infinity、"2026-02-30" 这类伪日期、越界金额
                        // 一律判 format_error，避免脏值入库后把备份/云同步导出卡死。
                        if (date.isBlank() || !CSV_DATE_REGEX.matches(date) ||
                            mileage == null || oilAmount == null || actual == null || payable == null ||
                            !RecordValidator.isValidRecord(date, mileage, oilAmount, payable, actual)
                        ) {
                            errors.add(ParsedRecord(date = date, errorKey = "format_error"))
                            continue
                        }

                        val grade = normalizeGrade(cols.getOrNull(gradeIdx))
                        // 油价列单独校验：NaN/Infinity/非正数都不得入库（否则云备份 JSON 序列化会抛异常）
                        val price = cols.getOrNull(priceIdx)?.toDoubleOrNull()
                            ?.takeIf { it.isFinite() && it > 0 && it <= RecordValidator.MAX_MONEY }
                        val province = cols.getOrNull(provinceIdx)?.trim()?.let { RecordValidator.sanitizeText(it) }
                        // 满箱列：是/yes/true/1 → true；否/no/false/0 → false；缺列/空值 → true（旧 CSV 口径）
                        val isFullTank = parseFullTankColumn(cols.getOrNull(fullTankIdx))
                        parsed.add(
                            ParsedRecord(
                                date = date, mileage = mileage, oilAmount = oilAmount,
                                actualAmount = actual, payableAmount = payable,
                                oilGrade = grade, oilPrice = price, province = province,
                                isFullTank = isFullTank,
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
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _exportMessage.value = ExportNotice("parse_failed", listOf(e.message ?: ""))
            }
        }
    }

    /** 确认导入：将 valid 组批量写入数据库（单事务 + IGNORE，避免逐条 insert 的逐条事务开销与吞异常） */
    fun confirmImport() {
        val preview = _importPreview.value ?: return
        viewModelScope.launch {
            val entities = preview.valid.map { r ->
                KOiLRecordEntity(
                    reoilDate = r.date, mileage = r.mileage, oilAmount = r.oilAmount,
                    payableAmount = r.payableAmount, actualAmount = r.actualAmount,
                    oilPrice = r.oilPrice, oilGrade = r.oilGrade, province = r.province,
                    source = "csv_import", isFullTank = r.isFullTank,
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
     *
     * 导入的性能与校验约束：
     * ① 读文件 + JSON 解析 + 入库整段放在 IO 线程，几千条备份放 Main 线程会卡到 ANR；
     * ② 用单事务 [KOiLRecordDao.insertAllIgnore]（逐条 insert 每条一个事务，5000 条要数秒）；
     * ③ 补数值校验：负里程/负油量/非法日期/NaN 一律跳过，避免脏备份污染派生计算。
     */
    fun importBackup(uri: Uri) {
        viewModelScope.launch {
            val outcome = withContext(Dispatchers.IO) {
                runCatching {
                    val content = context.contentResolver.openInputStream(uri)
                        ?.bufferedReader()?.use { it.readText() }
                        ?: return@runCatching BackupOutcome.Failure("cannot_read")
                    val json = org.json.JSONObject(content)
                    // 显式 version 必须是已知格式（M-05）；缺 version 的旧格式按宽松口径继续解析
                    val v = if (json.has("version")) json.optInt("version", -1) else -1
                    if (v >= 0 && !BackupCodec.isKnownBackupVersion(v)) {
                        return@runCatching BackupOutcome.Failure("restore_failed", "unsupported backup version: $v")
                    }
                    val recordsArr = json.optJSONArray("records")
                    if (recordsArr == null || recordsArr.length() == 0) {
                        return@runCatching BackupOutcome.Failure("backup_no_records")
                    }

                    val entities = ArrayList<KOiLRecordEntity>(recordsArr.length())
                    var skipped = 0
                    for (i in 0 until recordsArr.length()) {
                        val o = recordsArr.optJSONObject(i)
                        if (o == null) { skipped++; continue }
                        val date = o.optString("date")
                        val mileage = o.optDouble("mileage", Double.NaN)
                        val oilAmount = o.optDouble("oilAmount", Double.NaN)
                        val actual = o.optDouble("actualAmount", Double.NaN)
                        val payable = o.optDouble("payableAmount", actual)
                        // 与云同步恢复、CSV 导入共用同一口径（真实日期 + isFinite + 上界）
                        if (!RecordValidator.isValidRecord(date, mileage, oilAmount, payable, actual)) {
                            skipped++
                            continue
                        }
                        entities.add(
                            KOiLRecordEntity(
                                reoilDate = date,
                                mileage = mileage,
                                oilAmount = oilAmount,
                                payableAmount = payable,
                                actualAmount = actual,
                                oilPrice = if (o.isNull("oilPrice")) null
                                else o.optDouble("oilPrice", Double.NaN).takeIf { it.isFinite() && it > 0 },
                                oilGrade = if (!o.has("oilGrade") || o.isNull("oilGrade")) null
                                else o.optString("oilGrade").takeIf { it.isNotEmpty() && it != "null" }?.let { RecordValidator.sanitizeText(it) },
                                province = if (!o.has("province") || o.isNull("province")) null
                                else o.optString("province").takeIf { it.isNotEmpty() && it != "null" }?.let { RecordValidator.sanitizeText(it) },
                                source = if (!o.has("source")) "backup_restore"
                                else if (o.isNull("source")) null
                                else o.optString("source").takeIf { it.isNotEmpty() && it != "null" }?.let { RecordValidator.sanitizeText(it) },
                                // v1 手动备份无此字段：缺省 true（历史口径=全部视为加满）
                                isFullTank = if (o.has("isFullTank") && !o.isNull("isFullTank")) o.optBoolean("isFullTank", true) else true,
                            )
                        )
                    }

                    val inserted = if (entities.isEmpty()) 0
                    else recordDao.insertAllIgnore(entities).count { it != -1L }
                    // 命中唯一索引被忽略的行也算跳过（并发/重复备份）
                    skipped += entities.size - inserted

                    // 恢复设置（v2 云端备份无 settings 块，此处自然跳过）
                    json.optJSONObject("settings")?.let { applyBackupSettings(it) }
                    BackupOutcome.Success(inserted, skipped)
                }.getOrElse { BackupOutcome.Failure("restore_failed", it.message) }
            }
            _exportMessage.value = when (outcome) {
                is BackupOutcome.Success -> ExportNotice("restore_done", listOf(outcome.imported, outcome.skipped))
                is BackupOutcome.Failure -> ExportNotice(outcome.messageKey, listOf(outcome.detail ?: ""))
            }
        }
    }

    private sealed interface BackupOutcome {
        data class Success(val imported: Int, val skipped: Int) : BackupOutcome
        data class Failure(val messageKey: String, val detail: String? = null) : BackupOutcome
    }

    /** 备份设置块回写（仅白名单键，未知键忽略；非法枚举值不改变现状） */
    /**
     * 备份设置块回写（仅白名单键，未知键忽略；非法枚举值不改变现状）。
     * 校验逐项照旧，落盘收口为一次事务（AppSettings.applyBackupValues）——
     * 曾逐项 setXxx 产生 11 次独立 DataStore 事务，每次都让订阅设置的页面整批重算（M-08）。
     */
    private suspend fun applyBackupSettings(obj: org.json.JSONObject) {
        val themeMode = obj.optString("themeMode").takeIf { it.isNotEmpty() }?.let {
            runCatching { AppSettings.ThemeMode.valueOf(it) }.getOrNull()
        }
        val language = obj.optString("language").takeIf { it.isNotEmpty() }?.let {
            runCatching { AppSettings.Language.valueOf(it) }.getOrNull()
        }
        val province = obj.optString("defaultProvince")
            .takeIf { it.isNotEmpty() && Provinces.isValid(it) }
        val grade = obj.optString("defaultGrade").takeIf { it in OilPriceNetwork.GRADE_LABELS }
        val tank = if (!obj.isNull("tankCapacity")) {
            obj.optDouble("tankCapacity", Double.NaN)
                .takeIf { it.isFinite() && it in 5.0..200.0 }
                ?.let { it.toFloat() }
        } else null
        val keep = if (!obj.isNull("keepLastGrade")) obj.optBoolean("keepLastGrade") else null
        // 阈值导入收口：口径以设置页为准（异常 0..50、偏差 1..50），且 UI 步进只收整数，
        // 备份可能写 20.5 这类小数——导入一律四舍五入取整（normalizeThresholdPct，可单测）。
        val abnormal = if (!obj.isNull("abnormalThreshold")) {
            normalizeThresholdPct(obj.optDouble("abnormalThreshold", Double.NaN), 0.0, 50.0)
        } else null
        val deviation = if (!obj.isNull("deviationThreshold")) {
            normalizeThresholdPct(obj.optDouble("deviationThreshold", Double.NaN), 1.0, 50.0)
        } else null
        val alert = if (!obj.isNull("priceAlert")) obj.optBoolean("priceAlert") else null
        val location = if (!obj.isNull("locationEnabled")) obj.optBoolean("locationEnabled") else null
        val autoUpdate = if (!obj.isNull("priceDbAutoUpdate")) obj.optBoolean("priceDbAutoUpdate") else null

        settings.applyBackupValues(
            themeMode = themeMode,
            language = language,
            defaultProvince = province,
            defaultOilGrade = grade,
            tankCapacity = tank,
            keepLastGrade = keep,
            abnormalThreshold = abnormal,
            deviationThreshold = deviation,
            priceAlert = alert,
            locationEnabled = location,
            priceDbAutoUpdate = autoUpdate,
        )
    }

    private fun findColumn(headers: List<String>, candidates: List<String>): Int {
        for ((i, h) in headers.withIndex()) {
            if (candidates.any { h.startsWith(it, ignoreCase = true) }) return i
        }
        return -1
    }

    private fun normalizeGrade(raw: String?): String? = normalizeGradeForImport(raw)
    private fun String.removeBOM(): String = if (startsWith("\uFEFF")) substring(1) else this

    private fun String.normalizeDate(): String = replace("/", "-").let {
        val parts = it.split("-")
        if (parts.size == 3) {
            // 非法数字日期（如 abc-01-01）单行容错——返回原串，由调用方正则判 format_error，
            // 不抛 NumberFormatException 拖垮整文件解析
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
            // 同 exportCsv——全表读 + 逐行派生（含 LocalDate.parse）+ JSON 手拼 + 写盘整段切 IO
            withContext(Dispatchers.IO) {
                try {
                    val records = recordDao.getRange("1970-01-01", "2999-12-31")
                        .inChronologicalOrder()
                    if (records.isEmpty()) {
                        _exportMessage.value = ExportNotice("no_records_backup")
                        return@withContext
                    }

                    // 计算派生字段：统一走 DerivedCalculator（满箱区间口径，与总览/云同步一致）；
                    // abnormalPct 取 0（只影响状态判定，不影响派生数值），脏日期容错在计算器内部
                    val derived = DerivedCalculator.compute(records, 0.0)
                    val recordList = mutableListOf<Map<String, Any?>>()
                    for (d in derived) {
                        val r = d.record
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
                                "distance" to d.distance,
                                "oilDays" to d.oilDays,
                                "oilConsumption" to d.oilConsumption,
                                "costPerKm" to d.costPerKm,
                                "isFullTank" to r.isFullTank,
                            )
                        )
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
                        // 手动备份是独立的 MANUAL_BACKUP_VERSION 格式：含 settings 块；
                        // 与云快照按文件结构（有无 settings）分流，解析入口各自独立。
                        // 版本常量收在 BackupCodec：导出侧写、导入侧读同一个来源（M-05）。
                        "version" to BackupCodec.MANUAL_BACKUP_VERSION,
                        "exportTime" to SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()),
                        "recordCount" to records.size,
                        "records" to recordList,
                        "settings" to settingsMap,
                    )

                    // 手动构建 JSON（避免额外依赖）
                    val json = buildJsonString(backup)
                    val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
                    val fileName = "KOiL_完整备份_$timestamp.json"
                    // 输出到用户可浏览的公共位置（Download/KOiL/，不写入私有 files 目录）
                    val saved = ExportHelper.saveToDownloads(context, fileName, json)
                    if (saved == null) {
                        _exportMessage.value = ExportNotice("backup_failed", listOf(context.getString(R.string.koil_ov_save_failed)))
                        return@withContext
                    }
                    _exportMessage.value = ExportNotice("backup_done", listOf(records.size, saved))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _exportMessage.value = ExportNotice("backup_failed", listOf(e.message ?: ""))
                }
            }
        }
    }

    private fun buildJsonString(obj: Any?): String = when (obj) {
        null -> "null"
        is Map<*, *> -> obj.entries.joinToString(",", "{", "}") { (k, v) ->
            "\"$k\":${buildJsonString(v)}"
        }
        is List<*> -> obj.joinToString(",", "[", "]") { buildJsonString(it) }
        // 用 org.json 的 quote 完整转义：只转义 \ 与 " 时，字段里含换行/制表符会
        // 生成含裸控制字符的非法 JSON，备份无法恢复且只报笼统的「恢复失败」。
        is String -> org.json.JSONObject.quote(obj)
        // NaN/Infinity 输出非法 JSON——防御性写 null（当前数据源不会产生，防外部异常值）
        is Double -> if (obj.isFinite()) obj.toString() else "null"
        is Number -> obj.toString()
        is Boolean -> obj.toString()
        else -> "\"$obj\""
    }

}

// ===== CSV 导入二分判定辅助（O(n log n)，库内 + 文件内合并视图）=====
/** (日期, 里程) 复合键；ISO 日期字符串字典序即时间序，配合 TreeSet 做 lower/higher 二分定位 */
private data class MileageKey(val date: String, val mileage: Double)

/** CSV 日期列格式闸（作为文件级常量，避免每行都 new 一个 Regex、万行文件白建万个对象） */
private val CSV_DATE_REGEX = Regex("""\d{4}-\d{2}-\d{2}""")

/** CSV 行判定结果；group 取 CSV_VALID / CSV_DUP / CSV_CONFLICT，冲突时带 conflictKey/Arg */
private data class CsvVerdict(val group: Int, val conflictKey: String? = null, val conflictArg: Int = 0)

private const val CSV_VALID = 0
private const val CSV_DUP = 1
private const val CSV_CONFLICT = 2

/**
 * CSV 字段转义（M-06）：含逗号/引号/换行的字段用双引号包裹、内部 " 翻倍；
 * 与 [parseCsvLine] 严格互逆，保证「导出 → 再导入」等价。导出侧曾直接 joinToString(",")，
 * 字段里出现逗号或换行时列错位，回环丢数据。
 * 文件级 internal 纯函数：可直接单测。
 */
internal fun csvEscape(field: String): String =
    if (field.any { it == ',' || it == '"' || it == '\n' || it == '\r' })
        "\"" + field.replace("\"", "\"\"") + "\""
    else field

/**
 * 最小 CSV 行解析——双引号包裹字段 + "" 转义；未包裹字段按逗号切分（兼容 KOiL 导出格式，回环等价）。
 * 文件级 internal 纯函数：可直接单测。
 */
internal fun parseCsvLine(line: String): List<String> {
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

/**
 * CSV 油号归一：App 内部口径是 [OilPriceNetwork.GRADE_LABELS] 的键（"92"/"95"/"98"/"0"），
 * 但外部 CSV/备份常写全名（"92号汽油"、"0号柴油"、"柴油"）。原先只 removeSuffix("号")/"#"，
 * "92号汽油" 原样入库 → 与油价库 join、调价核验、备份恢复的油号口径全部错位。
 * 归一顺序：全名精确匹配 → "柴油"/"汽油" 这类简写 → 后缀剥离（"92号"/"92#" → "92"）。
 * 文件级 internal 纯函数：可直接单测，SettingsViewModel 成员委托到此处。
 */
internal fun normalizeGradeForImport(raw: String?): String? {
    val s = raw?.trim()?.let { RecordValidator.sanitizeText(it) } ?: return null
    if (s.isEmpty()) return null
    OilPriceNetwork.GRADE_LABELS.entries.firstOrNull { it.value == s }?.let { return it.key }
    val simplified = mapOf("柴油" to "0", "汽油" to null, "天然气" to null)
    if (s in simplified) return simplified[s]
    return s.removeSuffix("号").removeSuffix("#").takeIf { it.isNotBlank() }
}
/**
 * 备份/恢复里的阈值归一：先四舍五入取整（UI 步进只收整数，备份里可能写 20.5），
 * 再校验口径区间（异常油耗 0..50、偏差 1..50，以设置页为准）。区间外返回 null 表示不采用。
 * 舍入用 [Math.round]（ties 向上，20.5→21），与 UI 显示的 roundToInt 同口径——
 * kotlin.math.round 是 ties-to-even（20.5→20），两者混用会出现"导入 20.5 存成 20、UI 却显示 21"。
 */
internal fun normalizeThresholdPct(raw: Double, min: Double, max: Double): Double? {
    if (!raw.isFinite()) return null
    return Math.round(raw).toDouble().takeIf { it in min..max }
}

/** 记录导出 21 列表头（尾列「本次加满」）；导出侧与单测共用同一来源，避免两侧漂移 */
internal const val CSV_RECORD_HEADER =
    "加油日期,上次加油日期,油号,加油量(L),油价(元/L),应付金额(元),实付金额(元)," +
        "本次里程(km),上次里程(km),行驶里程(km),上次加油量(L),上次实付金额(元)," +
        "应付单价(元/L),实付单价(元/L),用油天数,平均油耗(L/100km),每公里成本(元/km)," +
        "油耗状态,省份,来源,本次加满"

/**
 * CSV 满箱列解析：是/yes/true/1（及其他任何值）→ true；否/no/false/0 → false；
 * 缺列/空值 → true（旧 20 列 CSV 无该列，历史口径=全部视为加满）。
 * 文件级 internal 纯函数：可直接单测。
 */
internal fun parseFullTankColumn(raw: String?): Boolean =
    when (raw?.trim()?.lowercase()) {
        null, "" -> true
        "否", "no", "false", "0" -> false
        else -> true
    }
