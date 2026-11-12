package com.llpop.koil.data.settings

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.llpop.koil.data.sync.RecordMerger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.KOiLDataStore by preferencesDataStore(name = "KOiL_settings")

/**
 * **云同步应用密码单独存一个文件**，与主设置文件隔离：
 * 凭据不与主题、省份、阈值等普通设置混放，读取与清除都走独立通道。
 * 系统自动备份已关闭，数据只由用户主动的备份导出 / WebDAV 同步管理。
 */
private val Context.KOiLSecretStore by preferencesDataStore(name = "KOiL_secret")

/**
 * DataStore 的 `data` 在**任意** key 变化时都会重发整份 Preferences，`map` 本身不过滤重复值，
 * 于是"改一个无关设置"会唤醒全部 48 个订阅者（AddViewModel 约 12 个 collector，其中
 * priceDbNextAdjustment 还会连带跑 DataStore + DB 查询；设置页 13 个 collectAsState；
 * 总览页 abnormalThreshold 会触发全量派生重算）。
 *
 * 统一在 map 之后接 distinctUntilChanged()：值真的变了才向下游发，订阅者数量不会放大写入成本。
 */
private fun <T> Flow<Preferences>.distinctValue(transform: (Preferences) -> T): Flow<T> =
    map(transform).distinctUntilChanged()

/**
 * 应用设置（对应 Web 端 localStorage 的 14 个 KOiL- 前缀 key，
 * 文档 §17.6：简单键值用 DataStore Preferences，仅业务表用 Room）。
 */
class AppSettings(private val context: Context) {

    enum class ThemeMode { SYSTEM, LIGHT, DARK }

    enum class Language { SYSTEM, ZH_CN, ZH_TW, EN }

    companion object {
        /** 悬浮导航样式默认值：UI 首帧状态必须引用同一常量，避免首帧闪一下另一套样式 */
        const val DEFAULT_FLOATING_BAR_STYLE = "ios_like"

        private val KEY_THEME_MODE = stringPreferencesKey("KOiL-theme-mode")
        private val KEY_LANGUAGE = stringPreferencesKey("KOiL-language")
        private val KEY_SUMMARY_RANGE = stringPreferencesKey("KOiL-summary-range")
        private val KEY_DEFAULT_PROVINCE = stringPreferencesKey("KOiL-default-province")
        private val KEY_DEFAULT_OIL_GRADE = stringPreferencesKey("KOiL-default-oil-grade")
        private val KEY_TANK_CAPACITY = floatPreferencesKey("KOiL-tank-capacity")
        private val KEY_KEEP_LAST_GRADE = booleanPreferencesKey("KOiL-keep-last-grade")
        private val KEY_ABNORMAL_THRESHOLD = doublePreferencesKey("KOiL-abnormal-threshold")
        private val KEY_PRICE_ALERT = booleanPreferencesKey("KOiL-price-alert")
        private val KEY_PRICE_PUSH = booleanPreferencesKey("KOiL-price-push")
        private val KEY_PRICE_LAST_NOTIFIED_DATE = stringPreferencesKey("KOiL-price-last-notified-date")
        private val KEY_PRICE_LAST_WINDOW_REMINDED = stringPreferencesKey("KOiL-price-last-window-reminded")
        // 窗口日当天"今晚24点生效"提醒去重（按窗口日标记）
        private val KEY_PRICE_LAST_WINDOW_DAY_REMINDED = stringPreferencesKey("KOiL-price-last-window-day-reminded")
        // 最近一次同步来源快照（设置页多源一致性展示）
        private val KEY_PRICE_DB_LAST_SOURCES = stringPreferencesKey("KOiL-price-db-last-sources")
        // 最近一次锚点命中源（xxapi/ruseo/abapi/ndrc/无）
        private val KEY_PRICE_DB_LAST_ANCHOR_SOURCE = stringPreferencesKey("KOiL-price-db-last-anchor-source")
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
        private val KEY_PRICE_DB_ADJUST_DATES = stringPreferencesKey("KOiL-price-db-adjust-dates")
        // ---- 原油估算（本地兜底，不依赖收费的第三方 forecast）：EIA 官方布伦特现货日度 ----
        private val KEY_EIA_API_KEY = stringPreferencesKey("KOiL-eia-api-key")
        private val KEY_EIA_ENABLED = booleanPreferencesKey("KOiL-eia-enabled")
        private val KEY_FORECAST_SNAPSHOT = stringPreferencesKey("KOiL-forecast-snapshot")
        private val KEY_FORECAST_SNAPSHOT_AT = longPreferencesKey("KOiL-forecast-snapshot-at")

        // ---- 云同步（坚果云 WebDAV：私有数据备份/恢复，见 docs/KOiL云同步设计.md）----
        private val KEY_SYNC_ENABLED = booleanPreferencesKey("KOiL-sync-enabled")
        private val KEY_SYNC_URL = stringPreferencesKey("KOiL-sync-url")
        private val KEY_SYNC_USERNAME = stringPreferencesKey("KOiL-sync-username")
        // 应用密码：优先 Android Keystore 密文，Keystore 不可用时明文回退（KOiL-sync-password-encrypted=false）
        private val KEY_SYNC_APP_PASSWORD = stringPreferencesKey("KOiL-sync-app-password")
        private val KEY_SYNC_PASSWORD_ENCRYPTED = booleanPreferencesKey("KOiL-sync-password-encrypted")
        private val KEY_SYNC_LAST_PUSH_AT = longPreferencesKey("KOiL-sync-last-push-at")
        private val KEY_SYNC_LAST_STATUS = stringPreferencesKey("KOiL-sync-last-status")
        private val KEY_SYNC_DELETED_KEYS = stringSetPreferencesKey("KOiL-sync-deleted-keys")
        /** 是否成功推送过云端快照（墓碑判定用；键缺失按 false 处理，下一次成功推送自动补上） */
        private val KEY_SYNC_EVER_CONFIGURED = booleanPreferencesKey("KOiL-sync-ever-configured")
        const val DEFAULT_SYNC_URL = "https://dav.jianguoyun.com/dav/KOiL/"

        // ---- 应用更新检查（冷启动静默查 GitHub Releases；45min 节流 + 回前台也查；查到新版本设置页挂红点）----
        private val KEY_UPDATE_LATEST_TAG = stringPreferencesKey("KOiL-update-latest-tag")
        private val KEY_UPDATE_LAST_CHECK_AT = longPreferencesKey("KOiL-update-last-check-at")
        /** 用户已「知晓」的版本 tag：点开 Release 页 / 关闭首页横幅 / 成功拉起安装器时写入。
         *  与 LATEST_TAG 分开存，而不是清空 LATEST_TAG——清空会连行内「发现新版本」文字一起抹掉，用户想再点就没入口。 */
        private val KEY_UPDATE_SEEN_TAG = stringPreferencesKey("KOiL-update-seen-tag")

        // ---- 导航栏（参考 Mishka：悬浮导航/样式/模式）----
        private val KEY_FLOATING_BOTTOM_BAR = booleanPreferencesKey("KOiL-floating-bottom-bar")
        private val KEY_FLOATING_BOTTOM_BAR_STYLE = stringPreferencesKey("KOiL-floating-bottom-bar-style")
        private val KEY_BOTTOM_BAR_MODE = stringPreferencesKey("KOiL-bottom-bar-mode")

        const val DEFAULT_TANK_CAPACITY = 65f
        const val DEFAULT_ABNORMAL_THRESHOLD = 20.0   // 默认异常油耗阈值 20%（用户要求）
        // 通知权限首次请求标记（允许/拒绝过都记录，启动时只请求一次；Android 13+ 拒绝后系统静默不弹框）
        private val KEY_NOTIFICATION_PERMISSION_REQUESTED = booleanPreferencesKey("KOiL-notification-permission-requested")
        const val DEFAULT_DEVIATION_THRESHOLD = 25.0
        /** EIA 官方免费 API Key（用户注册，内置为默认值；设置页可改，未来失效可自行更换） */
        const val DEFAULT_EIA_API_KEY = "GkLpNbWtHq677CmtfR6qHz9hfqqTgnbpM9PGWnLf"
    }

    // ---- 主题（koil-theme-mode）----
    val themeMode: Flow<ThemeMode> = context.KOiLDataStore.data.distinctValue { p ->
        when (p[KEY_THEME_MODE]) {
            "dark" -> ThemeMode.DARK
            "light" -> ThemeMode.LIGHT
            else -> ThemeMode.SYSTEM
        }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.KOiLDataStore.edit { p -> p[KEY_THEME_MODE] = mode.toPrefValue() }
    }

    // ---- 语言（koil-language）----
    val language: Flow<Language> = context.KOiLDataStore.data.distinctValue { p ->
        when (p[KEY_LANGUAGE]) {
            "zh-cn" -> Language.ZH_CN
            "zh-tw" -> Language.ZH_TW
            "en" -> Language.EN
            else -> Language.SYSTEM
        }
    }

    suspend fun setLanguage(lang: Language) {
        context.KOiLDataStore.edit { p -> p[KEY_LANGUAGE] = lang.toPrefValue() }
    }

    // ---- 汇总区间（koil-summary-range）----
    val summaryRange: Flow<String> = context.KOiLDataStore.data.distinctValue { it[KEY_SUMMARY_RANGE] ?: "all" }
    suspend fun setSummaryRange(range: String) {
        context.KOiLDataStore.edit { it[KEY_SUMMARY_RANGE] = range }
    }

    // ---- 默认省份（koil-default-province）----
    val defaultProvince: Flow<String> = context.KOiLDataStore.data.distinctValue { it[KEY_DEFAULT_PROVINCE] ?: "广东" }
    suspend fun setDefaultProvince(province: String) {
        context.KOiLDataStore.edit { it[KEY_DEFAULT_PROVINCE] = province }
    }

    // ---- 默认油号（koil-default-oil-grade）----
    val defaultOilGrade: Flow<String> = context.KOiLDataStore.data.distinctValue { it[KEY_DEFAULT_OIL_GRADE] ?: "92" }
    suspend fun setDefaultOilGrade(grade: String) {
        context.KOiLDataStore.edit { it[KEY_DEFAULT_OIL_GRADE] = grade }
    }

    // ---- 油箱容量（koil-tank-capacity，默认 65L）----
    val tankCapacity: Flow<Float> =
        context.KOiLDataStore.data.distinctValue { it[KEY_TANK_CAPACITY] ?: DEFAULT_TANK_CAPACITY }
    suspend fun setTankCapacity(capacity: Float) {
        context.KOiLDataStore.edit { it[KEY_TANK_CAPACITY] = capacity }
    }

    // ---- 沿用上次油号（koil-keep-last-grade，默认开启；实际取上次记录油号，见 AddViewModel）----
    val keepLastGrade: Flow<Boolean> =
        context.KOiLDataStore.data.distinctValue { it[KEY_KEEP_LAST_GRADE] ?: true }
    suspend fun setKeepLastGrade(keep: Boolean) {
        context.KOiLDataStore.edit { it[KEY_KEEP_LAST_GRADE] = keep }
    }

    // ---- 异常油耗阈值（koil-abnormal-threshold，默认 20%）----
    val abnormalThreshold: Flow<Double> =
        context.KOiLDataStore.data.distinctValue { it[KEY_ABNORMAL_THRESHOLD] ?: DEFAULT_ABNORMAL_THRESHOLD }
    suspend fun setAbnormalThreshold(pct: Double) {
        context.KOiLDataStore.edit { it[KEY_ABNORMAL_THRESHOLD] = pct }
    }

    // ---- 油价调整提醒开关（koil-price-alert）----
    val priceAlert: Flow<Boolean> = context.KOiLDataStore.data.distinctValue { it[KEY_PRICE_ALERT] ?: true }
    suspend fun setPriceAlert(on: Boolean) {
        context.KOiLDataStore.edit { it[KEY_PRICE_ALERT] = on }
    }

    // ---- 调价推送通知开关（koil-price-push，默认开）----
    val pricePush: Flow<Boolean> = context.KOiLDataStore.data.distinctValue { it[KEY_PRICE_PUSH] ?: true }
    suspend fun setPricePush(on: Boolean) {
        context.KOiLDataStore.edit { it[KEY_PRICE_PUSH] = on }
    }

    // ---- 上次已推送调价的生效日（避免重复通知）----
    val priceLastNotifiedDate: Flow<String> =
        context.KOiLDataStore.data.distinctValue { it[KEY_PRICE_LAST_NOTIFIED_DATE] ?: "" }
    suspend fun setPriceLastNotifiedDate(date: String) {
        context.KOiLDataStore.edit { it[KEY_PRICE_LAST_NOTIFIED_DATE] = date }
    }

    // ---- 上次已提醒的窗口日（窗口日前一天提醒去重）----
    val priceLastWindowReminded: Flow<String> =
        context.KOiLDataStore.data.distinctValue { it[KEY_PRICE_LAST_WINDOW_REMINDED] ?: "" }
    suspend fun setPriceLastWindowReminded(date: String) {
        context.KOiLDataStore.edit { it[KEY_PRICE_LAST_WINDOW_REMINDED] = date }
    }

    // 上次已提醒的窗口日（窗口日当天"今晚24点生效"提醒去重）
    val priceLastWindowDayReminded: Flow<String> =
        context.KOiLDataStore.data.distinctValue { it[KEY_PRICE_LAST_WINDOW_DAY_REMINDED] ?: "" }
    suspend fun setPriceLastWindowDayReminded(date: String) {
        context.KOiLDataStore.edit { it[KEY_PRICE_LAST_WINDOW_DAY_REMINDED] = date }
    }

    // 最近一次同步来源快照（设置页多源一致性展示，格式如 "锚点:xxapi · 今日价:abapi"）
    val priceDbLastSources: Flow<String> =
        context.KOiLDataStore.data.distinctValue { it[KEY_PRICE_DB_LAST_SOURCES] ?: "" }
    suspend fun setPriceDbLastSources(value: String) {
        context.KOiLDataStore.edit { it[KEY_PRICE_DB_LAST_SOURCES] = value }
    }

    // 最近一次锚点命中源（xxapi/ruseo/abapi/ndrc/无）
    val priceDbLastAnchorSource: Flow<String> =
        context.KOiLDataStore.data.distinctValue { it[KEY_PRICE_DB_LAST_ANCHOR_SOURCE] ?: "" }
    suspend fun setPriceDbLastAnchorSource(value: String) {
        context.KOiLDataStore.edit { it[KEY_PRICE_DB_LAST_ANCHOR_SOURCE] = value }
    }

    // ---- 定位（koil-location-enabled）----
    val locationEnabled: Flow<Boolean> = context.KOiLDataStore.data.distinctValue { it[KEY_LOCATION_ENABLED] ?: false }
    suspend fun setLocationEnabled(on: Boolean) {
        context.KOiLDataStore.edit { it[KEY_LOCATION_ENABLED] = on }
    }

    // ---- 首次启动定位引导（koil-has-prompted-location）----
    val hasPromptedLocation: Flow<Boolean> =
        context.KOiLDataStore.data.distinctValue { it[KEY_HAS_PROMPTED_LOCATION] ?: false }
    suspend fun setHasPromptedLocation(prompted: Boolean) {
        context.KOiLDataStore.edit { it[KEY_HAS_PROMPTED_LOCATION] = prompted }
    }

    // ---- 油价库自动更新（koil-price-db-auto-update，默认开启）----
    val priceDbAutoUpdate: Flow<Boolean> =
        context.KOiLDataStore.data.distinctValue { it[KEY_PRICE_DB_AUTO_UPDATE] ?: true }
    suspend fun setPriceDbAutoUpdate(on: Boolean) {
        context.KOiLDataStore.edit { it[KEY_PRICE_DB_AUTO_UPDATE] = on }
    }

    // ---- 价格偏差阈值（koil-deviation-threshold，Web 实测 3.5，默认 25%）----
    val deviationThreshold: Flow<Double> =
        context.KOiLDataStore.data.distinctValue { it[KEY_DEVIATION_THRESHOLD] ?: DEFAULT_DEVIATION_THRESHOLD }
    suspend fun setDeviationThreshold(pct: Double) {
        context.KOiLDataStore.edit { it[KEY_DEVIATION_THRESHOLD] = pct }
    }

    /**
     * 一次事务写入多个设置项；参数为 null 表示该项不动。
     * 备份恢复曾逐项调用 setXxx——11 次独立事务，每次让订阅全部设置 Flow 的页面整批重算；
     * 恢复是"整包替换"语义，单事务既减少重算次数，也保证部分失败时不出现半套设置。
     */
    suspend fun applyBackupValues(
        themeMode: ThemeMode? = null,
        language: Language? = null,
        defaultProvince: String? = null,
        defaultOilGrade: String? = null,
        tankCapacity: Float? = null,
        keepLastGrade: Boolean? = null,
        abnormalThreshold: Double? = null,
        deviationThreshold: Double? = null,
        priceAlert: Boolean? = null,
        locationEnabled: Boolean? = null,
        priceDbAutoUpdate: Boolean? = null,
    ) {
        context.KOiLDataStore.edit { p ->
            themeMode?.let { p[KEY_THEME_MODE] = it.toPrefValue() }
            language?.let { p[KEY_LANGUAGE] = it.toPrefValue() }
            defaultProvince?.let { p[KEY_DEFAULT_PROVINCE] = it }
            defaultOilGrade?.let { p[KEY_DEFAULT_OIL_GRADE] = it }
            tankCapacity?.let { p[KEY_TANK_CAPACITY] = it }
            keepLastGrade?.let { p[KEY_KEEP_LAST_GRADE] = it }
            abnormalThreshold?.let { p[KEY_ABNORMAL_THRESHOLD] = it }
            deviationThreshold?.let { p[KEY_DEVIATION_THRESHOLD] = it }
            priceAlert?.let { p[KEY_PRICE_ALERT] = it }
            locationEnabled?.let { p[KEY_LOCATION_ENABLED] = it }
            priceDbAutoUpdate?.let { p[KEY_PRICE_DB_AUTO_UPDATE] = it }
        }
    }

    private fun ThemeMode.toPrefValue(): String = when (this) {
        ThemeMode.SYSTEM -> "system"
        ThemeMode.LIGHT -> "light"
        ThemeMode.DARK -> "dark"
    }

    private fun Language.toPrefValue(): String = when (this) {
        Language.SYSTEM -> "system"
        Language.ZH_CN -> "zh-cn"
        Language.ZH_TW -> "zh-tw"
        Language.EN -> "en"
    }

    // ---- 油价库缓存状态（koil-price-db-cache，lastUpdateMessage 等）----
    val priceDbCache: Flow<String> =
        context.KOiLDataStore.data.distinctValue { it[KEY_PRICE_DB_CACHE] ?: "" }
    suspend fun setPriceDbCache(message: String) {
        context.KOiLDataStore.edit { it[KEY_PRICE_DB_CACHE] = message }
    }

    // ---- 油价库同步状态（对齐 Web appConfig：koil_price_last_check_at 等）----

    /** 最近一次检查时间戳（毫秒），0 表示从未检查 */
    val priceDbLastCheckAt: Flow<Long> =
        context.KOiLDataStore.data.distinctValue { it[KEY_PRICE_DB_LAST_CHECK_AT] ?: 0L }
    suspend fun setPriceDbLastCheckAt(ts: Long) {
        context.KOiLDataStore.edit { it[KEY_PRICE_DB_LAST_CHECK_AT] = ts }
    }

    /** 最近一次调价更新日期（yyyy-MM-dd） */
    val priceDbLastUpdateDate: Flow<String> =
        context.KOiLDataStore.data.distinctValue { it[KEY_PRICE_DB_LAST_UPDATE_DATE] ?: "" }
    suspend fun setPriceDbLastUpdateDate(date: String) {
        context.KOiLDataStore.edit { it[KEY_PRICE_DB_LAST_UPDATE_DATE] = date }
    }

    /** 初始化时间戳（毫秒） */
    val priceDbInitAt: Flow<Long> =
        context.KOiLDataStore.data.distinctValue { it[KEY_PRICE_DB_INIT_AT] ?: 0L }
    suspend fun setPriceDbInitAt(ts: Long) {
        context.KOiLDataStore.edit { it[KEY_PRICE_DB_INIT_AT] = ts }
    }

    /** 最近一次核验窗口日期（搁浅时=今日） */
    val priceDbLastWindowChecked: Flow<String> =
        context.KOiLDataStore.data.distinctValue { it[KEY_PRICE_DB_LAST_WINDOW] ?: "" }
    suspend fun setPriceDbLastWindowChecked(date: String) {
        context.KOiLDataStore.edit { it[KEY_PRICE_DB_LAST_WINDOW] = date }
    }

    /** 下次调价日期（来自接口 next_adjustment，yyyy-MM-dd） */
    val priceDbNextAdjustment: Flow<String> =
        context.KOiLDataStore.data.distinctValue { it[KEY_PRICE_DB_NEXT_ADJUSTMENT] ?: "" }
    suspend fun setPriceDbNextAdjustment(date: String) {
        context.KOiLDataStore.edit { it[KEY_PRICE_DB_NEXT_ADJUSTMENT] = date }
    }

    /** 最近一个已过的调价窗口日（滚动存档：上次接口预告的 next_adjustment，现已过期） */
    val priceDbPrevNextAdjustment: Flow<String> =
        context.KOiLDataStore.data.distinctValue { it[KEY_PRICE_DB_PREV_ADJUSTMENT] ?: "" }
    suspend fun setPriceDbPrevNextAdjustment(date: String) {
        context.KOiLDataStore.edit { it[KEY_PRICE_DB_PREV_ADJUSTMENT] = date }
    }

    /** 通知权限是否已请求过（首次安装启动弹框一次；拒绝后记录，启动时只请求一次） */
    val notificationPermissionRequested: Flow<Boolean> =
        context.KOiLDataStore.data.distinctValue { it[KEY_NOTIFICATION_PERMISSION_REQUESTED] ?: false }
    suspend fun setNotificationPermissionRequested(requested: Boolean) {
        context.KOiLDataStore.edit { it[KEY_NOTIFICATION_PERMISSION_REQUESTED] = requested }
    }

    /** 推算的当年权威窗口日（逗号分隔 yyyy-MM-dd，持久化复用；App 重启后仍有效，杜绝次源偶发失败导致的空降级） */
    val priceDbAdjustDates: Flow<String> =
        context.KOiLDataStore.data.distinctValue { it[KEY_PRICE_DB_ADJUST_DATES] ?: "" }
    suspend fun setPriceDbAdjustDates(dates: List<String>) {
        context.KOiLDataStore.edit { it[KEY_PRICE_DB_ADJUST_DATES] = dates.joinToString(",") }
    }

    /** EIA 官方 API Key（默认内置用户注册 Key，设置页可改）——原油估算主源 */
    val eiaApiKey: Flow<String> =
        context.KOiLDataStore.data.distinctValue { it[KEY_EIA_API_KEY] ?: DEFAULT_EIA_API_KEY }
    suspend fun setEiaApiKey(key: String) {
        context.KOiLDataStore.edit { it[KEY_EIA_API_KEY] = key }
    }

    /**
     * 上次成功预测的幅度快照，格式 `amount|source|dataDate|releaseDate|sampleDays`，附带写入时间（毫秒）。
     * 冷启动时先拿它占位（进程重启后内存里的结果已经没了），后台 refreshForecast 拿到新值再覆盖。
     * 返回 Pair 而不是两条 Flow：调用点只需要一次读取。
     */
    suspend fun forecastSnapshotOnce(): Pair<String, Long> {
        val prefs = context.KOiLDataStore.data.first()
        return (prefs[KEY_FORECAST_SNAPSHOT] ?: "") to (prefs[KEY_FORECAST_SNAPSHOT_AT] ?: 0L)
    }

    suspend fun setForecastSnapshot(snapshot: String) {
        context.KOiLDataStore.edit {
            it[KEY_FORECAST_SNAPSHOT] = snapshot
            it[KEY_FORECAST_SNAPSHOT_AT] = System.currentTimeMillis()
        }
    }

    /** EIA 原油估算源启用开关（默认开=用 EIA；关=直接用腾讯，输入框显示供修改） */
    val eiaEnabled: Flow<Boolean> =
        context.KOiLDataStore.data.distinctValue { it[KEY_EIA_ENABLED] ?: true }
    suspend fun setEiaEnabled(enabled: Boolean) {
        context.KOiLDataStore.edit { it[KEY_EIA_ENABLED] = enabled }
    }

    // ---- 云同步（坚果云 WebDAV，docs/KOiL云同步设计.md）----
    val syncEnabled: Flow<Boolean> =
        context.KOiLDataStore.data.distinctValue { it[KEY_SYNC_ENABLED] ?: false }
    suspend fun setSyncEnabled(enabled: Boolean) {
        context.KOiLDataStore.edit { it[KEY_SYNC_ENABLED] = enabled }
    }

    val syncUrl: Flow<String> =
        context.KOiLDataStore.data.distinctValue { it[KEY_SYNC_URL] ?: DEFAULT_SYNC_URL }
    suspend fun setSyncUrl(url: String) {
        context.KOiLDataStore.edit { it[KEY_SYNC_URL] = url.trim() }
    }

    val syncUsername: Flow<String> =
        context.KOiLDataStore.data.distinctValue { it[KEY_SYNC_USERNAME] ?: "" }
    suspend fun setSyncUsername(username: String) {
        context.KOiLDataStore.edit { it[KEY_SYNC_USERNAME] = username.trim() }
    }

    /** 应用密码存储值（密文或明文回退，原样返回；是否密文看 syncPasswordEncrypted）。
     *  读写独立的凭据文件 KOiL_secret，不与主设置混放 */
    val syncAppPassword: Flow<String> =
        context.KOiLSecretStore.data.distinctValue { it[KEY_SYNC_APP_PASSWORD] ?: "" }
    suspend fun setSyncAppPassword(encryptedOrPlain: String) {
        context.KOiLSecretStore.edit { it[KEY_SYNC_APP_PASSWORD] = encryptedOrPlain }
    }

    val syncPasswordEncrypted: Flow<Boolean> =
        context.KOiLSecretStore.data.distinctValue { it[KEY_SYNC_PASSWORD_ENCRYPTED] ?: false }
    suspend fun setSyncPasswordEncrypted(encrypted: Boolean) {
        context.KOiLSecretStore.edit { it[KEY_SYNC_PASSWORD_ENCRYPTED] = encrypted }
    }

    val syncLastPushAt: Flow<Long> =
        context.KOiLDataStore.data.distinctValue { it[KEY_SYNC_LAST_PUSH_AT] ?: 0L }
    suspend fun setSyncLastPushAt(at: Long) {
        context.KOiLDataStore.edit { it[KEY_SYNC_LAST_PUSH_AT] = at }
    }

    /**
     * 是否**成功推送过**云端快照。
     *
     * 判定不能用 `syncUrl.isNotBlank() && syncUsername.isNotBlank()`：syncUrl 有默认值（恒非空），
     * 实际只看用户名——用户在设置页点开表单、保存过用户名，就会开始无意义累积删除墓碑，
     * 即使从未开启过同步。因此改由首次成功 push 置位，与任何默认值无关。
     *
     * 键缺失时的回退：显式标记键可能尚未写入（清除数据，或本次安装还没成功推送过），
     * 此时回退到既有证据 `syncLastPushAt > 0` 或状态行已是 `ok@` / `restore@`
     * （二者只有成功同步/恢复后才会写入）。否则先删除记录、还没成功推送时删除不写墓碑，
     * 下一次 push 拉云端快照会把这条记录合并回来（即"删除复活"）。
     * 首次成功 push 后显式键自然置位。
     */
    val syncEverConfigured: Flow<Boolean> =
        context.KOiLDataStore.data.distinctValue { p ->
            p[KEY_SYNC_EVER_CONFIGURED] == true ||
                (p[KEY_SYNC_LAST_PUSH_AT] ?: 0L) > 0L ||
                // restore@（有恢复）与 restore-none@（无新记录的恢复）都以 restore 开头：
                // 能执行恢复 = 云同步配置过，与成功推送同属证据（restore / restore-none 前缀一致）
                p[KEY_SYNC_LAST_STATUS]?.startsWith("restore") == true ||
                p[KEY_SYNC_LAST_STATUS]?.startsWith("ok@") == true
        }
    suspend fun setSyncEverConfigured() {
        context.KOiLDataStore.edit { it[KEY_SYNC_EVER_CONFIGURED] = true }
    }

    /** 同步状态（"ok@MM-dd HH:mm" / "restore@MM-dd HH:mm" / "fail:<原因>" / 空=未同步过），设置页状态行展示 */
    val syncLastStatus: Flow<String> =
        context.KOiLDataStore.data.distinctValue { it[KEY_SYNC_LAST_STATUS] ?: "" }
    suspend fun setSyncLastStatus(status: String) {
        context.KOiLDataStore.edit { it[KEY_SYNC_LAST_STATUS] = status }
    }

    /**
     * 本机主动删除、尚未成功推送到云端的记录键（RecordMerger.keyOf）。
     * 推送前合并云端快照时跳过这些键，避免删掉的记录被拉回；推送成功后清空。
     */
    val syncDeletedKeys: Flow<Set<String>> =
        context.KOiLDataStore.data.distinctValue { it[KEY_SYNC_DELETED_KEYS] ?: emptySet() }
    suspend fun markRecordDeleted(reoilDate: String, mileage: Double) {
        context.KOiLDataStore.edit { prefs ->
            val cur = prefs[KEY_SYNC_DELETED_KEYS] ?: emptySet()
            prefs[KEY_SYNC_DELETED_KEYS] = cur + RecordMerger.keyOf(reoilDate, mileage)
        }
    }
    suspend fun clearSyncDeletedKeys(pushedKeys: Set<String>) {
        context.KOiLDataStore.edit { prefs ->
            val remaining = (prefs[KEY_SYNC_DELETED_KEYS] ?: emptySet()) - pushedKeys
            if (remaining.isEmpty()) prefs.remove(KEY_SYNC_DELETED_KEYS)
            else prefs[KEY_SYNC_DELETED_KEYS] = remaining
        }
    }

    // ---- 应用更新检查 ----

    /** 最近一次查到的 Release tag（原始形式如 "V1.0.0"；空串=从未查到过）——设置页据此判断是否挂红点 */
    val updateLatestTag: Flow<String> =
        context.KOiLDataStore.data.distinctValue { it[KEY_UPDATE_LATEST_TAG] ?: "" }
    suspend fun setUpdateLatestTag(tag: String) {
        context.KOiLDataStore.edit { it[KEY_UPDATE_LATEST_TAG] = tag }
    }

    /** 最近一次「成功」检查的时间戳（毫秒，0=从未成功）——静默检查的 45 分钟节流依据 + 设置页「上次检查」 */
    val updateLastCheckAt: Flow<Long> =
        context.KOiLDataStore.data.distinctValue { it[KEY_UPDATE_LAST_CHECK_AT] ?: 0L }
    suspend fun setUpdateLastCheckAt(at: Long) {
        context.KOiLDataStore.edit { it[KEY_UPDATE_LAST_CHECK_AT] = at }
    }

    /**
     * 用户已知晓的版本 tag（原始形式如 "V1.1.3"；空串=从未知晓过）。
     * 红点条件 = 有更新版本 && LATEST_TAG != SEEN_TAG：
     * - 点过之后同一版本不再重复提醒；
     * - 发新版时 LATEST_TAG 变化，比较自然重新成立，无需额外清理；
     * - 不进状态清理路径（下载失败、取消都不写），否则提醒会与强提示同时消失，用户彻底失联。
     */
    val updateSeenTag: Flow<String> =
        context.KOiLDataStore.data.distinctValue { it[KEY_UPDATE_SEEN_TAG] ?: "" }
    suspend fun setUpdateSeenTag(tag: String) {
        context.KOiLDataStore.edit { it[KEY_UPDATE_SEEN_TAG] = tag }
    }

    // ---- 底部导航条（参考 Mishka 悬浮导航体系）----

    /** 悬浮导航条开关（默认开启，按用户要求采用悬浮导航） */
    val floatingBottomBar: Flow<Boolean> =
        context.KOiLDataStore.data.distinctValue { it[KEY_FLOATING_BOTTOM_BAR] ?: true }
    suspend fun setFloatingBottomBar(on: Boolean) {
        context.KOiLDataStore.edit { it[KEY_FLOATING_BOTTOM_BAR] = on }
    }

    /** 悬浮样式：miuix（Miuix 胶囊悬浮）/ ios_like（iOS 液态玻璃） */
    val floatingBottomBarStyle: Flow<String> =
        context.KOiLDataStore.data.distinctValue {
            it[KEY_FLOATING_BOTTOM_BAR_STYLE] ?: DEFAULT_FLOATING_BAR_STYLE
        }
    suspend fun setFloatingBottomBarStyle(style: String) {
        context.KOiLDataStore.edit { it[KEY_FLOATING_BOTTOM_BAR_STYLE] = style }
    }

    /** 底栏模式：icon_and_text（图标+文字）/ icon_only（仅图标） */
    val bottomBarMode: Flow<String> =
        context.KOiLDataStore.data.distinctValue { it[KEY_BOTTOM_BAR_MODE] ?: "icon_and_text" }
    suspend fun setBottomBarMode(mode: String) {
        context.KOiLDataStore.edit { it[KEY_BOTTOM_BAR_MODE] = mode }
    }
}
