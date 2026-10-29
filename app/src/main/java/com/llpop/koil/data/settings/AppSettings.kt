package com.llpop.koil.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.File

private val Context.KOiLDataStore by preferencesDataStore(name = "KOiL_settings")

/** 历史设置文件路径（迁移专用，勿改）：v1.4.50 及更早版本使用 koil_settings */
private const val LEGACY_SETTINGS_FILE = "koil_settings.preferences_pb"

/**
 * 应用设置（对应 Web 端 localStorage 的 14 个 KOiL- 前缀 key，
 * 文档 §17.6：简单键值用 DataStore Preferences，仅业务表用 Room）。
 */
class AppSettings(private val context: Context) {

    /** 一次性迁移：历史 koil_settings 文件 → KOiL_settings，键前缀 koil- → KOiL-（幂等，保留全部设置） */
    fun migrateLegacyIfNeeded() {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching {
                val legacy: DataStore<Preferences> = PreferenceDataStoreFactory.create(
                    scope = CoroutineScope(Dispatchers.IO),
                    produceFile = { File(context.filesDir, "datastore/$LEGACY_SETTINGS_FILE") },
                )
                val oldData = legacy.data.first()
                if (oldData.asMap().isEmpty()) return@runCatching
                // 新 store 已有 KOiL- 键 → 已迁移过，跳过
                val newData = context.KOiLDataStore.data.first()
                if (newData.asMap().any { it.key.name.startsWith("KOiL-") }) return@runCatching

                context.KOiLDataStore.edit { new ->
                    oldData.asMap().forEach { (k, v) ->
                        val newName = k.name.replace("koil-", "KOiL-")
                        when (v) {
                            is String -> new[stringPreferencesKey(newName)] = v
                            is Set<*> -> new[stringSetPreferencesKey(newName)] = v as Set<String>
                            is Int -> new[intPreferencesKey(newName)] = v
                            is Long -> new[longPreferencesKey(newName)] = v
                            is Float -> new[floatPreferencesKey(newName)] = v
                            is Double -> new[doublePreferencesKey(newName)] = v
                            is Boolean -> new[booleanPreferencesKey(newName)] = v
                        }
                    }
                }
                legacy.edit { it.clear() }
            }
        }
    }

    enum class ThemeMode { SYSTEM, LIGHT, DARK }

    enum class Language { SYSTEM, ZH_CN, ZH_TW, EN }

    companion object {
        private val KEY_THEME_MODE = stringPreferencesKey("KOiL-theme-mode")
        private val KEY_LANGUAGE = stringPreferencesKey("KOiL-language")
        private val KEY_SUMMARY_RANGE = stringPreferencesKey("KOiL-summary-range")
        private val KEY_DEFAULT_PROVINCE = stringPreferencesKey("KOiL-default-province")
        private val KEY_DEFAULT_OIL_GRADE = stringPreferencesKey("KOiL-default-oil-grade")
        private val KEY_TANK_CAPACITY = floatPreferencesKey("KOiL-tank-capacity")
        private val KEY_KEEP_LAST_GRADE = booleanPreferencesKey("KOiL-keep-last-grade")
        private val KEY_LAST_GRADE = stringPreferencesKey("KOiL-last-grade")
        private val KEY_ABNORMAL_THRESHOLD = doublePreferencesKey("KOiL-abnormal-threshold")
        private val KEY_PRICE_ALERT = booleanPreferencesKey("KOiL-price-alert")
        private val KEY_PRICE_PUSH = booleanPreferencesKey("KOiL-price-push")
        private val KEY_PRICE_LAST_NOTIFIED_DATE = stringPreferencesKey("KOiL-price-last-notified-date")
        private val KEY_PRICE_LAST_WINDOW_REMINDED = stringPreferencesKey("KOiL-price-last-window-reminded")
        private val KEY_LOCATION_ENABLED = booleanPreferencesKey("KOiL-location-enabled")
        private val KEY_HAS_PROMPTED_LOCATION = booleanPreferencesKey("KOiL-has-prompted-location")
        private val KEY_PRICE_DB_AUTO_UPDATE = booleanPreferencesKey("KOiL-price-db-auto-update")
        private val KEY_DEVIATION_THRESHOLD = doublePreferencesKey("KOiL-deviation-threshold")
        private val KEY_PRICE_DB_CACHE = stringPreferencesKey("KOiL-price-db-cache")
        // ---- 油价库同步状态（对齐 Web 端 appConfig 的 koil_price_* 键）----
        private val KEY_PRICE_DB_LAST_CHECK_AT = longPreferencesKey("KOiL-price-db-last-check-at")
        private val KEY_PRICE_DB_LAST_UPDATE_DATE = stringPreferencesKey("KOiL-price-db-last-update-date")
        private val KEY_PRICE_DB_INIT_AT = longPreferencesKey("KOiL-price-db-init-at")
        private val KEY_PRICE_DB_LAST_WINDOW = stringPreferencesKey("KOiL-price-db-last-window-checked")
        private val KEY_PRICE_DB_NEXT_ADJUSTMENT = stringPreferencesKey("KOiL-price-db-next-adjustment")
        private val KEY_PRICE_DB_PREV_ADJUSTMENT = stringPreferencesKey("KOiL-price-db-prev-adjustment")
        private val KEY_PRICE_DB_LEGACY_PENDING_CLEARED = booleanPreferencesKey("KOiL-price-db-legacy-pending-cleared")
        private val KEY_PRICE_DB_ADJUST_DATES = stringPreferencesKey("KOiL-price-db-adjust-dates")
        private val KEY_PRICE_DB_CALENDAR_186_MIGRATED = booleanPreferencesKey("KOiL-price-db-calendar-186-migrated")
        private val KEY_PRICE_DB_RESEED_189 = booleanPreferencesKey("KOiL-price-db-reseed-189")
        private val KEY_PRICE_DB_ADJUST_DATES_LEANED = booleanPreferencesKey("KOiL-price-db-adjust-dates-leaned")
        // ---- 原油估算（apizero forecast 收费后本地兜底）：EIA 官方布伦特现货日度 ----
        private val KEY_EIA_API_KEY = stringPreferencesKey("KOiL-eia-api-key")
        private val KEY_EIA_ENABLED = booleanPreferencesKey("KOiL-eia-enabled")

        // ---- 导航栏（参考 Mishka：悬浮导航/样式/模式）----
        private val KEY_FLOATING_BOTTOM_BAR = booleanPreferencesKey("KOiL-floating-bottom-bar")
        private val KEY_FLOATING_BOTTOM_BAR_STYLE = stringPreferencesKey("KOiL-floating-bottom-bar-style")
        private val KEY_BOTTOM_BAR_MODE = stringPreferencesKey("KOiL-bottom-bar-mode")

        const val DEFAULT_TANK_CAPACITY = 65f
        const val DEFAULT_ABNORMAL_THRESHOLD = 30.0
        // v1.5.16 一次性数据修复标记：历史 payable=0 记录回填为 actual（仅执行一次）
        private val KEY_RECORD_PAYABLE_ZERO_FIXED = booleanPreferencesKey("KOiL-record-payable-zero-fixed")
        // v1.5.21 一次性清理标记：apizero 时代残留的 KOiL-price-db-forecast 键已删除（forecast 功能已彻底移除）
        private val KEY_PRICE_DB_FORECAST_CLEARED = booleanPreferencesKey("KOiL-price-db-forecast-cleared")
        // v1.5.21 一次性清理标记：库内"未来生效日"脏记录（effectiveDate > 今天）已删除（窗口日 24 点生效边界修复）
        private val KEY_PRICE_DB_WINDOW_DAY_CLEARED = booleanPreferencesKey("KOiL-price-db-window-day-cleared")
        const val DEFAULT_DEVIATION_THRESHOLD = 25.0
        /** EIA 官方免费 API Key（用户注册，内置为默认值；设置页可改，未来失效可自行更换） */
        const val DEFAULT_EIA_API_KEY = "GkLpNbWtHq677CmtfR6qHz9hfqqTgnbpM9PGWnLf"
    }

    // ---- 主题（koil-theme-mode）----
    val themeMode: Flow<ThemeMode> = context.KOiLDataStore.data.map { p ->
        when (p[KEY_THEME_MODE]) {
            "dark" -> ThemeMode.DARK
            "light" -> ThemeMode.LIGHT
            else -> ThemeMode.SYSTEM
        }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.KOiLDataStore.edit { p ->
            p[KEY_THEME_MODE] = when (mode) {
                ThemeMode.SYSTEM -> "system"
                ThemeMode.LIGHT -> "light"
                ThemeMode.DARK -> "dark"
            }
        }
    }

    // ---- 语言（koil-language）----
    val language: Flow<Language> = context.KOiLDataStore.data.map { p ->
        when (p[KEY_LANGUAGE]) {
            "zh-cn" -> Language.ZH_CN
            "zh-tw" -> Language.ZH_TW
            "en" -> Language.EN
            else -> Language.SYSTEM
        }
    }

    suspend fun setLanguage(lang: Language) {
        context.KOiLDataStore.edit { p ->
            p[KEY_LANGUAGE] = when (lang) {
                Language.SYSTEM -> "system"
                Language.ZH_CN -> "zh-cn"
                Language.ZH_TW -> "zh-tw"
                Language.EN -> "en"
            }
        }
    }

    // ---- 汇总区间（koil-summary-range）----
    val summaryRange: Flow<String> = context.KOiLDataStore.data.map { it[KEY_SUMMARY_RANGE] ?: "all" }
    suspend fun setSummaryRange(range: String) {
        context.KOiLDataStore.edit { it[KEY_SUMMARY_RANGE] = range }
    }

    // ---- 默认省份（koil-default-province）----
    val defaultProvince: Flow<String> = context.KOiLDataStore.data.map { it[KEY_DEFAULT_PROVINCE] ?: "广东" }
    suspend fun setDefaultProvince(province: String) {
        context.KOiLDataStore.edit { it[KEY_DEFAULT_PROVINCE] = province }
    }

    // ---- 默认油号（koil-default-oil-grade）----
    val defaultOilGrade: Flow<String> = context.KOiLDataStore.data.map { it[KEY_DEFAULT_OIL_GRADE] ?: "92" }
    suspend fun setDefaultOilGrade(grade: String) {
        context.KOiLDataStore.edit { it[KEY_DEFAULT_OIL_GRADE] = grade }
    }

    // ---- 油箱容量（koil-tank-capacity，默认 65L）----
    val tankCapacity: Flow<Float> =
        context.KOiLDataStore.data.map { it[KEY_TANK_CAPACITY] ?: DEFAULT_TANK_CAPACITY }
    suspend fun setTankCapacity(capacity: Float) {
        context.KOiLDataStore.edit { it[KEY_TANK_CAPACITY] = capacity }
    }

    // ---- 沿用上次油号（koil-keep-last-grade，默认开启）----
    val keepLastGrade: Flow<Boolean> =
        context.KOiLDataStore.data.map { it[KEY_KEEP_LAST_GRADE] ?: true }
    val lastGrade: Flow<String> = context.KOiLDataStore.data.map { it[KEY_LAST_GRADE] ?: "92" }
    suspend fun setLastGrade(grade: String) {
        context.KOiLDataStore.edit { it[KEY_LAST_GRADE] = grade }
    }
    suspend fun setKeepLastGrade(keep: Boolean) {
        context.KOiLDataStore.edit { it[KEY_KEEP_LAST_GRADE] = keep }
    }

    // ---- 异常油耗阈值（koil-abnormal-threshold，默认 30%）----
    val abnormalThreshold: Flow<Double> =
        context.KOiLDataStore.data.map { it[KEY_ABNORMAL_THRESHOLD] ?: DEFAULT_ABNORMAL_THRESHOLD }
    suspend fun setAbnormalThreshold(pct: Double) {
        context.KOiLDataStore.edit { it[KEY_ABNORMAL_THRESHOLD] = pct }
    }

    // ---- v1.5.16 一次性数据修复：历史 payable=0 记录是否已回填为 actual ----
    val recordPayableZeroFixed: Flow<Boolean> =
        context.KOiLDataStore.data.map { it[KEY_RECORD_PAYABLE_ZERO_FIXED] ?: false }
    suspend fun setRecordPayableZeroFixed(fixed: Boolean) {
        context.KOiLDataStore.edit { it[KEY_RECORD_PAYABLE_ZERO_FIXED] = fixed }
    }

    // ---- 油价调整提醒开关（koil-price-alert）----
    val priceAlert: Flow<Boolean> = context.KOiLDataStore.data.map { it[KEY_PRICE_ALERT] ?: true }
    suspend fun setPriceAlert(on: Boolean) {
        context.KOiLDataStore.edit { it[KEY_PRICE_ALERT] = on }
    }

    // ---- 调价推送通知开关（koil-price-push，默认开）----
    val pricePush: Flow<Boolean> = context.KOiLDataStore.data.map { it[KEY_PRICE_PUSH] ?: true }
    suspend fun setPricePush(on: Boolean) {
        context.KOiLDataStore.edit { it[KEY_PRICE_PUSH] = on }
    }

    // ---- 上次已推送调价的生效日（避免重复通知）----
    val priceLastNotifiedDate: Flow<String> =
        context.KOiLDataStore.data.map { it[KEY_PRICE_LAST_NOTIFIED_DATE] ?: "" }
    suspend fun setPriceLastNotifiedDate(date: String) {
        context.KOiLDataStore.edit { it[KEY_PRICE_LAST_NOTIFIED_DATE] = date }
    }

    // ---- 上次已提醒的窗口日（窗口日前一天提醒去重）----
    val priceLastWindowReminded: Flow<String> =
        context.KOiLDataStore.data.map { it[KEY_PRICE_LAST_WINDOW_REMINDED] ?: "" }
    suspend fun setPriceLastWindowReminded(date: String) {
        context.KOiLDataStore.edit { it[KEY_PRICE_LAST_WINDOW_REMINDED] = date }
    }

    // ---- 定位（koil-location-enabled）----
    val locationEnabled: Flow<Boolean> = context.KOiLDataStore.data.map { it[KEY_LOCATION_ENABLED] ?: false }
    suspend fun setLocationEnabled(on: Boolean) {
        context.KOiLDataStore.edit { it[KEY_LOCATION_ENABLED] = on }
    }

    // ---- 首次启动定位引导（koil-has-prompted-location）----
    val hasPromptedLocation: Flow<Boolean> =
        context.KOiLDataStore.data.map { it[KEY_HAS_PROMPTED_LOCATION] ?: false }
    suspend fun setHasPromptedLocation(prompted: Boolean) {
        context.KOiLDataStore.edit { it[KEY_HAS_PROMPTED_LOCATION] = prompted }
    }

    // ---- 油价库自动更新（koil-price-db-auto-update，默认开启）----
    val priceDbAutoUpdate: Flow<Boolean> =
        context.KOiLDataStore.data.map { it[KEY_PRICE_DB_AUTO_UPDATE] ?: true }
    suspend fun setPriceDbAutoUpdate(on: Boolean) {
        context.KOiLDataStore.edit { it[KEY_PRICE_DB_AUTO_UPDATE] = on }
    }

    // ---- 价格偏差阈值（koil-deviation-threshold，Web 实测 3.5，默认 3.0）----
    val deviationThreshold: Flow<Double> =
        context.KOiLDataStore.data.map { it[KEY_DEVIATION_THRESHOLD] ?: DEFAULT_DEVIATION_THRESHOLD }
    suspend fun setDeviationThreshold(pct: Double) {
        context.KOiLDataStore.edit { it[KEY_DEVIATION_THRESHOLD] = pct }
    }

    // ---- 油价库缓存状态（koil-price-db-cache，lastUpdateMessage 等）----
    val priceDbCache: Flow<String> =
        context.KOiLDataStore.data.map { it[KEY_PRICE_DB_CACHE] ?: "" }
    suspend fun setPriceDbCache(message: String) {
        context.KOiLDataStore.edit { it[KEY_PRICE_DB_CACHE] = message }
    }

    // ---- 油价库同步状态（对齐 Web appConfig：koil_price_last_check_at 等）----

    /** 最近一次检查时间戳（毫秒），0 表示从未检查 */
    val priceDbLastCheckAt: Flow<Long> =
        context.KOiLDataStore.data.map { it[KEY_PRICE_DB_LAST_CHECK_AT] ?: 0L }
    suspend fun setPriceDbLastCheckAt(ts: Long) {
        context.KOiLDataStore.edit { it[KEY_PRICE_DB_LAST_CHECK_AT] = ts }
    }

    /** 最近一次调价更新日期（yyyy-MM-dd） */
    val priceDbLastUpdateDate: Flow<String> =
        context.KOiLDataStore.data.map { it[KEY_PRICE_DB_LAST_UPDATE_DATE] ?: "" }
    suspend fun setPriceDbLastUpdateDate(date: String) {
        context.KOiLDataStore.edit { it[KEY_PRICE_DB_LAST_UPDATE_DATE] = date }
    }

    /** 初始化时间戳（毫秒） */
    val priceDbInitAt: Flow<Long> =
        context.KOiLDataStore.data.map { it[KEY_PRICE_DB_INIT_AT] ?: 0L }
    suspend fun setPriceDbInitAt(ts: Long) {
        context.KOiLDataStore.edit { it[KEY_PRICE_DB_INIT_AT] = ts }
    }

    /** 最近一次核验窗口日期（搁浅时=今日） */
    val priceDbLastWindowChecked: Flow<String> =
        context.KOiLDataStore.data.map { it[KEY_PRICE_DB_LAST_WINDOW] ?: "" }
    suspend fun setPriceDbLastWindowChecked(date: String) {
        context.KOiLDataStore.edit { it[KEY_PRICE_DB_LAST_WINDOW] = date }
    }

    /** 下次调价日期（来自接口 next_adjustment，yyyy-MM-dd） */
    val priceDbNextAdjustment: Flow<String> =
        context.KOiLDataStore.data.map { it[KEY_PRICE_DB_NEXT_ADJUSTMENT] ?: "" }
    suspend fun setPriceDbNextAdjustment(date: String) {
        context.KOiLDataStore.edit { it[KEY_PRICE_DB_NEXT_ADJUSTMENT] = date }
    }

    /** 最近一个已过的调价窗口日（滚动存档：上次接口预告的 next_adjustment，现已过期） */
    val priceDbPrevNextAdjustment: Flow<String> =
        context.KOiLDataStore.data.map { it[KEY_PRICE_DB_PREV_ADJUSTMENT] ?: "" }
    suspend fun setPriceDbPrevNextAdjustment(date: String) {
        context.KOiLDataStore.edit { it[KEY_PRICE_DB_PREV_ADJUSTMENT] = date }
    }

    /** v1.5.21 一次性清理：删除 apizero 时代残留的 forecast 键（幂等；forecast 功能已彻底移除，键不再被任何代码读写） */
    suspend fun clearLegacyPriceDbForecast() {
        // 旧键名（已从常量表删除，用字面量定位物理键）
        val legacyKey = stringPreferencesKey("KOiL-price-db-forecast")
        context.KOiLDataStore.edit { p ->
            p.remove(legacyKey)
            p[KEY_PRICE_DB_FORECAST_CLEARED] = true
        }
    }

    val priceDbForecastCleared: Flow<Boolean> =
        context.KOiLDataStore.data.map { it[KEY_PRICE_DB_FORECAST_CLEARED] ?: false }

    /** v1.5.21 一次性标记：库内"未来生效日"脏记录是否已清理（窗口日 24 点前禁止出现 effectiveDate > 今天 的记录） */
    val priceDbWindowDayCleared: Flow<Boolean> =
        context.KOiLDataStore.data.map { it[KEY_PRICE_DB_WINDOW_DAY_CLEARED] ?: false }
    suspend fun setPriceDbWindowDayCleared(cleared: Boolean) {
        context.KOiLDataStore.edit { it[KEY_PRICE_DB_WINDOW_DAY_CLEARED] = cleared }
    }

    /** 旧机制（双源当日价核验）遗留假 pending 是否已清理（一次性，新机制首次跑通后置 true） */
    val priceDbLegacyPendingCleared: Flow<Boolean> =
        context.KOiLDataStore.data.map { it[KEY_PRICE_DB_LEGACY_PENDING_CLEARED] ?: false }
    suspend fun setPriceDbLegacyPendingCleared(cleared: Boolean) {
        context.KOiLDataStore.edit { it[KEY_PRICE_DB_LEGACY_PENDING_CLEARED] = cleared }
    }

    /** v1.4.190 一次性标记：189+ 升级后首次立即更新强制全量补灌（恢复被 v1.4.188 误替换成搁浅的调价记录） */
    val priceDbReseed189: Flow<Boolean> =
        context.KOiLDataStore.data.map { it[KEY_PRICE_DB_RESEED_189] ?: false }
    suspend fun setPriceDbReseed189(migrated: Boolean) {
        context.KOiLDataStore.edit { it[KEY_PRICE_DB_RESEED_189] = migrated }
    }

    /** v1.4.186 一次性标记：升级后强制清旧窗口日历缓存重算（锚点失败落 abapi 推算的日历可能漏窗口，如无 10-15） */
    val priceDbCalendar186Migrated: Flow<Boolean> =
        context.KOiLDataStore.data.map { it[KEY_PRICE_DB_CALENDAR_186_MIGRATED] ?: false }
    suspend fun setPriceDbCalendar186Migrated(migrated: Boolean) {
        context.KOiLDataStore.edit { it[KEY_PRICE_DB_CALENDAR_186_MIGRATED] = migrated }
    }

    /** 推算的当年权威窗口日（逗号分隔 yyyy-MM-dd，持久化复用；App 重启后仍有效，杜绝次源临时失败导致的空降级） */
    val priceDbAdjustDates: Flow<String> =
        context.KOiLDataStore.data.map { it[KEY_PRICE_DB_ADJUST_DATES] ?: "" }
    suspend fun setPriceDbAdjustDates(dates: List<String>) {
        context.KOiLDataStore.edit { it[KEY_PRICE_DB_ADJUST_DATES] = dates.joinToString(",") }
    }

    /** EIA 官方 API Key（默认内置用户注册 Key，设置页可改）——原油估算主源 */
    val eiaApiKey: Flow<String> =
        context.KOiLDataStore.data.map { it[KEY_EIA_API_KEY] ?: DEFAULT_EIA_API_KEY }
    suspend fun setEiaApiKey(key: String) {
        context.KOiLDataStore.edit { it[KEY_EIA_API_KEY] = key }
    }

    /** EIA 原油估算源启用开关（v1.5.1：默认开=用 EIA；关=直接用腾讯，输入框显示供修改） */
    val eiaEnabled: Flow<Boolean> =
        context.KOiLDataStore.data.map { it[KEY_EIA_ENABLED] ?: true }
    suspend fun setEiaEnabled(enabled: Boolean) {
        context.KOiLDataStore.edit { it[KEY_EIA_ENABLED] = enabled }
    }

    /** 异常生效日（不在任何窗口日+1 内的假记录，如"原日期+1"产生的 10-25）是否已一次性清理 */
    val priceDbAdjustDatesLeaned: Flow<Boolean> =
        context.KOiLDataStore.data.map { it[KEY_PRICE_DB_ADJUST_DATES_LEANED] ?: false }
    suspend fun setPriceDbAdjustDatesLeaned(leaned: Boolean) {
        context.KOiLDataStore.edit { it[KEY_PRICE_DB_ADJUST_DATES_LEANED] = leaned }
    }

    // ---- 底部导航条（参考 Mishka 悬浮导航体系）----

    /** 悬浮导航条开关（默认开启，用户要求从普通导航改为悬浮导航） */
    val floatingBottomBar: Flow<Boolean> =
        context.KOiLDataStore.data.map { it[KEY_FLOATING_BOTTOM_BAR] ?: true }
    suspend fun setFloatingBottomBar(on: Boolean) {
        context.KOiLDataStore.edit { it[KEY_FLOATING_BOTTOM_BAR] = on }
    }

    /** 悬浮样式：miuix（Miuix 胶囊悬浮）/ ios_like（iOS 液态玻璃） */
    val floatingBottomBarStyle: Flow<String> =
        context.KOiLDataStore.data.map { it[KEY_FLOATING_BOTTOM_BAR_STYLE] ?: "ios_like" }
    suspend fun setFloatingBottomBarStyle(style: String) {
        context.KOiLDataStore.edit { it[KEY_FLOATING_BOTTOM_BAR_STYLE] = style }
    }

    /** 底栏模式：icon_and_text（图标+文字）/ icon_only（仅图标） */
    val bottomBarMode: Flow<String> =
        context.KOiLDataStore.data.map { it[KEY_BOTTOM_BAR_MODE] ?: "icon_and_text" }
    suspend fun setBottomBarMode(mode: String) {
        context.KOiLDataStore.edit { it[KEY_BOTTOM_BAR_MODE] = mode }
    }
}
