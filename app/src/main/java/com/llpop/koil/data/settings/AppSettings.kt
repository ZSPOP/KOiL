package com.llpop.koil.data.settings

import android.content.Context
import androidx.annotation.VisibleForTesting
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
import com.llpop.koil.data.sync.RecordMerger
import com.llpop.koil.util.AppClock
import com.llpop.koil.util.SystemLocaleBridge
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
        private val KEY_LOCALE_MIRRORED = booleanPreferencesKey("KOiL-locale-mirrored")
        private val KEY_SUMMARY_RANGE = stringPreferencesKey("KOiL-summary-range")
        private val KEY_DEFAULT_PROVINCE = stringPreferencesKey("KOiL-default-province")
        private val KEY_DEFAULT_OIL_GRADE = stringPreferencesKey("KOiL-default-oil-grade")
        private val KEY_TANK_CAPACITY = floatPreferencesKey("KOiL-tank-capacity")
        private val KEY_KEEP_LAST_GRADE = booleanPreferencesKey("KOiL-keep-last-grade")
        private val KEY_ABNORMAL_THRESHOLD = doublePreferencesKey("KOiL-abnormal-threshold")
        private val KEY_PRICE_ALERT = booleanPreferencesKey("KOiL-price-alert")
        private val KEY_NOTIFY = booleanPreferencesKey("KOiL-notify")
        private val KEY_PRICE_LAST_NOTIFIED_DATE = stringPreferencesKey("KOiL-price-last-notified-date")
        private val KEY_NOTIFY_UPDATE_TAG = stringPreferencesKey("KOiL-notify-update-tag")
        private val KEY_NOTIFY_UPDATE_AT = longPreferencesKey("KOiL-notify-update-at")
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

        /** 上一次启动时的 versionCode。不同 = 刚升级过，启动要绕过 8 小时节流强制核验一次 */
        private val KEY_LAST_RUN_VERSION_CODE = longPreferencesKey("KOiL-last-run-version-code")
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
        /**
         * 是否「设置过」应用密码。
         *
         * 用来区分两件长得一样的事：从没设置过（可以回落内置默认密码）／
         * 设置过又被用户清空（视为主动停用，不得再拿默认密码连上去）。
         * 只看密码是否为空区分不出这两种，旧实现正是因此把「清空」当成了「从没设置」。
         */
        private val KEY_SYNC_PASSWORD_EVER_SET = booleanPreferencesKey("KOiL-sync-password-ever-set")
        private val KEY_SYNC_LAST_PUSH_AT = longPreferencesKey("KOiL-sync-last-push-at")
        private val KEY_SYNC_LAST_STATUS = stringPreferencesKey("KOiL-sync-last-status")
        private val KEY_SYNC_DELETED_KEYS = stringSetPreferencesKey("KOiL-sync-deleted-keys")
        /** 撤销标记（PR ③ 起写入；PR ② 的「以云端为准」需要清空它，字段先落地，避免再走一次迁移） */
        private val KEY_SYNC_REVIVED_KEYS = stringSetPreferencesKey("KOiL-sync-revived-keys")
        /** 是否成功推送过云端快照（墓碑判定用；键缺失按 false 处理，下一次成功推送自动补上） */
        private val KEY_SYNC_EVER_CONFIGURED = booleanPreferencesKey("KOiL-sync-ever-configured")
        /**
         * 删除历史缓存（key → at，序列化成 JSON 数组）。
         *
         * 存在的唯一理由：v1 客户端读到 `deleted` 会把整块抹掉（它重建 JSON）。
         * 所以本机记住自己知道过的删除，下次推送把它补回云端——
         * 用「缓存为空」判首次是不行的（从没删过东西的设备缓存也是空）。
         */
        private val KEY_SYNC_DELETED_CACHE = stringPreferencesKey("KOiL-sync-deleted-cache")
        /** 本机上一次成功推送出去的快照版本；< 2 表示还没写过 v2，需要做一次修复性合并 */
        private val KEY_LAST_PUSHED_BACKUP_VERSION = intPreferencesKey("KOiL-last-pushed-backup-version")
        const val DEFAULT_SYNC_URL = "https://dav.jianguoyun.com/dav/KOiL/"

        // ---- 应用更新检查（冷启动静默查 GitHub Releases；45min 节流 + 回前台也查；查到新版本设置页挂红点）----
        private val KEY_UPDATE_LATEST_TAG = stringPreferencesKey("KOiL-update-latest-tag")
        private val KEY_UPDATE_LAST_CHECK_AT = longPreferencesKey("KOiL-update-last-check-at")

        /** 最近一次检查失败的时间戳（0=最近一次是成功的）。用来把「一直在失败」和「刚查过、没问题」分开 */
        private val KEY_UPDATE_LAST_FAILED_AT = longPreferencesKey("KOiL-update-last-failed-at")

        /** 最新版 APK 的直链（Gitee 资产）。APK 名带 versionCode，App 无法自己拼，
         *  只能每次检查时从 Release 的 assets 里取。 */
        private val KEY_UPDATE_APK_URL = stringPreferencesKey("KOiL-update-apk-url")

        /** 已下好的 tag 落盘副本。`ApkUpdater.readyTag` 是内存变量，进程被杀就丢，
         *  而 `updates/` 下的包还在 —— 不恢复就会把完好的包删掉重下 6.7MB。 */
        private val KEY_UPDATE_READY_TAG = stringPreferencesKey("KOiL-update-ready-tag")

        // ---- 导航栏（悬浮导航 / 样式 / 模式）----
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
        // 同步到系统「每应用语言」（Android 13+）。放在同一个写入口，App 内与系统侧不会分叉；
        // 系统不支持/语言未声明时由桥内部吞掉异常，退回原先的「App 内自绘」路径
        SystemLocaleBridge.apply(context, lang)
    }

    /**
     * 是否已经把「App 内语言」同步给过系统一次（内部状态，不出现在设置界面）。
     *
     * 用途只有一个：升级到带 `localeConfig` 的版本后，系统侧还是空的（用户从没在系统里选过），
     * 这时应该把用户以前在 App 内选的语言推上去，而不是反过来把用户的选择清成「跟随系统」。
     * 推过一次之后系统侧成为可对照的镜像，之后就以系统为准。
     */
    val localeMirrorAdopted: Flow<Boolean> = context.KOiLDataStore.data.distinctValue {
        it[KEY_LOCALE_MIRRORED] ?: false
    }

    suspend fun setLocaleMirrorAdopted() {
        context.KOiLDataStore.edit { it[KEY_LOCALE_MIRRORED] = true }
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

    // ---- 系统通知总开关（koil-notify，默认开）----
    // 管全部系统通知：调价提醒 + 新版本提醒。两个渠道各自独立，
    // 用户仍可在系统设置里单独关掉某一个；这里是 App 侧的总闸。
    val notifyEnabled: Flow<Boolean> = context.KOiLDataStore.data.distinctValue { it[KEY_NOTIFY] ?: true }
    suspend fun setNotifyEnabled(on: Boolean) {
        context.KOiLDataStore.edit { it[KEY_NOTIFY] = on }
    }

    // 新版本提醒不再有独立开关：并入「系统通知」总开关（notifyEnabled）。

    /** 已经通知过的新版本 tag：配合时间戳做 7 天节流，冷启动/回前台反复查到也不会重复打扰 */
    val notifyUpdateTag: Flow<String> =
        context.KOiLDataStore.data.distinctValue { it[KEY_NOTIFY_UPDATE_TAG] ?: "" }
    suspend fun setNotifyUpdateTag(tag: String) {
        context.KOiLDataStore.edit { it[KEY_NOTIFY_UPDATE_TAG] = tag }
    }

    /** 上次发出（或用户点开）新版本通知的时间：同一版本的重发间隔靠它节流 */
    val notifyUpdateAt: Flow<Long> = context.KOiLDataStore.data.distinctValue { it[KEY_NOTIFY_UPDATE_AT] ?: 0L }
    suspend fun setNotifyUpdateAt(at: Long) {
        context.KOiLDataStore.edit { it[KEY_NOTIFY_UPDATE_AT] = at }
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

    // 最近一次同步来源快照（设置页多源一致性展示，格式如 "锚点:xxapi · 今日价:xxapi"；
    // 今日价源随实时源切换，xxapi 不可用时回落 ruseo / youjia，标签由 OilPriceDbSyncer 按实际源写入）
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

    /** 上一次启动的 versionCode（0 = 从未记录，旧版升级上来的首启） */
    val lastRunVersionCode: Flow<Long> =
        context.KOiLDataStore.data.distinctValue { it[KEY_LAST_RUN_VERSION_CODE] ?: 0L }
    suspend fun setLastRunVersionCode(code: Long) {
        context.KOiLDataStore.edit { it[KEY_LAST_RUN_VERSION_CODE] = code }
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
            it[KEY_FORECAST_SNAPSHOT_AT] = AppClock.millis()
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

    /** 空串也回落默认：存过空值（比如旧版本把空草稿落盘）时不能当作"有效地址" */
    val syncUrl: Flow<String> =
        context.KOiLDataStore.data.distinctValue {
            it[KEY_SYNC_URL]?.takeIf { url -> url.isNotBlank() } ?: DEFAULT_SYNC_URL
        }
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

    /**
     * 是否设置过应用密码（与密码同存凭据文件）。
     *
     * 键缺省时**不是**直接 false，而是看密码：旧版本没有这个标记，而旧版本「清空密码」写的是空串、
     * 「设置密码」写的是非空串——所以只要存着非空密码，就一定是用户设置过的。
     * 这就是老用户的升级路径：升级后第一次读，立刻按已有数据推出正确结论，不需要额外迁移任务。
     *
     * **认了的一个缺口**：存储为空时无法区分「从未设置」与「旧版清空过」——旧版清空动作没留任何痕迹，
     * 信息论上推不出来。这两类都按从未设置处理（可回落内置默认）。
     * 后果：旧版点过清空密码的用户，升级后打开开关仍会用内置默认连上，
     * **需要重设一次密码**才算真停用；此后本标记生效，清空即为停用。
     * 这是刻意的选择，不是漏改。
     */
    val syncPasswordEverSet: Flow<Boolean> = context.KOiLSecretStore.data.distinctValue { p ->
        p[KEY_SYNC_PASSWORD_EVER_SET]
            ?: !p[KEY_SYNC_APP_PASSWORD].isNullOrBlank()
    }
    suspend fun setSyncPasswordEverSet(everSet: Boolean) {
        context.KOiLSecretStore.edit { it[KEY_SYNC_PASSWORD_EVER_SET] = everSet }
    }

    /**
     * 清空凭据文件，只给测试用。
     *
     * 凭据 DataStore 是进程级单例，跨测试方法共享状态（`preferencesDataStore` 委托只创建一次），
     * 不清理会发现「上个用例写的密码漏到下个用例」。
     */
    @VisibleForTesting
    internal suspend fun clearSyncSecretForTest() {
        // 地址/账号在普通设置文件，密码/标记在凭据文件，两处都要清
        context.KOiLDataStore.edit {
            it.remove(KEY_SYNC_URL)
            it.remove(KEY_SYNC_USERNAME)
        }
        context.KOiLSecretStore.edit { it.clear() }
    }

    /**
     * 原始存储值（**不经过任何默认兜底**），只给测试用。
     *
     * 为什么需要它：`syncUrl` 等 Flow 自带默认兜底，读出来永远非空，没法用来证明
     * 「默认值没有被写进存储」。要钉住这件事，必须看键的原始值。
     */
    @VisibleForTesting
    internal suspend fun rawSyncConfig(): Triple<String?, String?, String?> {
        // 地址/账号存普通设置文件，密码存凭据文件——两处各读各的
        val prefs = context.KOiLDataStore.data.first()
        val secret = context.KOiLSecretStore.data.first()
        return Triple(
            prefs[KEY_SYNC_URL],
            prefs[KEY_SYNC_USERNAME],
            secret[KEY_SYNC_APP_PASSWORD],
        )
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
        val key = RecordMerger.keyOf(reoilDate, mileage)
        context.KOiLDataStore.edit { prefs ->
            val cur = prefs[KEY_SYNC_DELETED_KEYS] ?: emptySet()
            prefs[KEY_SYNC_DELETED_KEYS] = cur + key
            // 删除必须同时撤掉这个键的撤销标记：先重录（打标记）、再删（清标记），
            // 留下标记的话下次推送会把云端的删除摘掉，等于「删了又活过来」
            prefs[KEY_SYNC_REVIVED_KEYS] = (prefs[KEY_SYNC_REVIVED_KEYS] ?: emptySet()) - key
        }
    }
    suspend fun clearSyncDeletedKeys(pushedKeys: Set<String>) {
        context.KOiLDataStore.edit { prefs ->
            val remaining = (prefs[KEY_SYNC_DELETED_KEYS] ?: emptySet()) - pushedKeys
            if (remaining.isEmpty()) prefs.remove(KEY_SYNC_DELETED_KEYS)
            else prefs[KEY_SYNC_DELETED_KEYS] = remaining
        }
    }

    /** 撤销标记（用户重新录入、需要从云端 deleted 里摘掉的键）。PR ③ 写入；这里供「以云端为准」清空 */
    val syncRevivedKeys: Flow<Set<String>> =
        context.KOiLDataStore.data.distinctValue { it[KEY_SYNC_REVIVED_KEYS] ?: emptySet() }
    suspend fun setSyncRevivedKeys(keys: Set<String>) {
        context.KOiLDataStore.edit { it[KEY_SYNC_REVIVED_KEYS] = keys }
    }
    suspend fun clearRevivedKeys() {
        setSyncRevivedKeys(emptySet())
    }

    /** 用户重录命中墓碑 → 打撤销标记（RecordStore 内调用；同步插入不许走这里） */
    suspend fun markRecordRevived(key: String) {
        context.KOiLDataStore.edit { prefs ->
            prefs[KEY_SYNC_REVIVED_KEYS] = (prefs[KEY_SYNC_REVIVED_KEYS] ?: emptySet()) + key
        }
    }

    // ---- 删除历史缓存（v1 客户端抹掉 deleted 之后靠它把删除补回云端）----

    /** key → at；解析失败按空集（缓存坏了不能把同步卡死） */
    val syncDeletedCache: Flow<Map<String, Long>> =
        context.KOiLDataStore.data.distinctValue { prefs ->
            runCatching {
                val raw = prefs[KEY_SYNC_DELETED_CACHE] ?: return@runCatching emptyMap()
                val arr = org.json.JSONArray(raw)
                val out = HashMap<String, Long>(arr.length())
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val key = o.optString("key").takeIf { it.isNotBlank() } ?: continue
                    val at = o.optLong("at", 0L)
                    val prev = out[key]
                    if (prev == null || at > prev) out[key] = at
                }
                out
            }.getOrDefault(emptyMap())
        }

    suspend fun setSyncDeletedCache(cache: Map<String, Long>) {
        context.KOiLDataStore.edit { prefs ->
            if (cache.isEmpty()) {
                prefs.remove(KEY_SYNC_DELETED_CACHE)
            } else {
                val arr = org.json.JSONArray()
                cache.forEach { (k, at) -> arr.put(org.json.JSONObject().put("key", k).put("at", at)) }
                prefs[KEY_SYNC_DELETED_CACHE] = arr.toString()
            }
        }
    }

    /** 测试隔离：DataStore 是进程级单例，墓碑/撤销/缓存会跨测试方法泄漏 */
    @VisibleForTesting
    suspend fun clearSyncTombstonesForTest() {
        context.KOiLDataStore.edit { prefs ->
            prefs.remove(KEY_SYNC_DELETED_KEYS)
            prefs.remove(KEY_SYNC_REVIVED_KEYS)
            prefs.remove(KEY_SYNC_DELETED_CACHE)
            prefs.remove(KEY_LAST_PUSHED_BACKUP_VERSION)
            prefs.remove(KEY_SYNC_EVER_CONFIGURED)
        }
    }

    /** 0 = 从未推送过；< BACKUP_VERSION 表示本机还没写出过新格式，推送前要做修复性合并 */
    val lastPushedBackupVersion: Flow<Int> =
        context.KOiLDataStore.data.distinctValue { it[KEY_LAST_PUSHED_BACKUP_VERSION] ?: 0 }
    suspend fun setLastPushedBackupVersion(version: Int) {
        context.KOiLDataStore.edit { it[KEY_LAST_PUSHED_BACKUP_VERSION] = version }
    }

    // ---- 应用更新检查 ----

    /** 最近一次「成功」检查的时间戳（毫秒，0=从未成功）——静默检查的 45 分钟节流依据 + 设置页「上次检查」 */
    val updateLastCheckAt: Flow<Long> =
        context.KOiLDataStore.data.distinctValue { it[KEY_UPDATE_LAST_CHECK_AT] ?: 0L }
    suspend fun setUpdateLastCheckAt(at: Long) {
        context.KOiLDataStore.edit { it[KEY_UPDATE_LAST_CHECK_AT] = at }
    }

    /**
     * 最近一次**失败**检查的时间戳（毫秒，0=没有未恢复的失败）。
     *
     * [updateLastCheckAt] 只在成功时推进，单看它分不出「刚查过、一切正常」和「连续查不通」：
     * Gitee 长期不可用时，用户看到的只是「永远没有新版本」，App 侧没有任何解释。
     * 这个值失败时写入、成功时清零，UI 据非零状态提示一句「检查失败」。
     */
    val updateLastFailedAt: Flow<Long> =
        context.KOiLDataStore.data.distinctValue { it[KEY_UPDATE_LAST_FAILED_AT] ?: 0L }
    suspend fun setUpdateLastFailedAt(at: Long) {
        context.KOiLDataStore.edit { it[KEY_UPDATE_LAST_FAILED_AT] = at }
    }

    /**
     * tag 与直链的**同一快照**（一次 map 出来）。
     * UI 分别订阅 [updateLatestTag] 与 [updateApkUrl] 是两条 collect，中间会有一帧
     * 「新 tag + 旧 url」——下的是上一版的包，校验全过，装完仍是旧版还把新版标成已读。
     */
    /** 已下好的 tag；空串表示没有 */
    val updateReadyTag: Flow<String> =
        context.KOiLDataStore.data.distinctValue { it[KEY_UPDATE_READY_TAG] ?: "" }

    suspend fun setUpdateReadyTag(tag: String?) {
        context.KOiLDataStore.edit { p ->
            if (tag.isNullOrBlank()) p.remove(KEY_UPDATE_READY_TAG) else p[KEY_UPDATE_READY_TAG] = tag
        }
    }

    val updateTargetPair: Flow<Pair<String, String>> =
        context.KOiLDataStore.data.distinctValue {
            (it[KEY_UPDATE_LATEST_TAG] ?: "") to (it[KEY_UPDATE_APK_URL] ?: "")
        }

    /**
     * 一次性写入 tag 与直链。
     * 必须用一次 `edit`：拆成两次独立写入时，第二次失败或被进程杀掉会留下
     * 「tag 已是新版、链接还指向上一版」的**持久**错位——下载的是一个真 APK，
     * 三条校验全过，用户装完仍是旧版，还把新版标成已读。
     */
    suspend fun setUpdateTarget(tag: String, apkUrl: String) {
        context.KOiLDataStore.edit {
            it[KEY_UPDATE_LATEST_TAG] = tag
            it[KEY_UPDATE_APK_URL] = apkUrl
        }
    }

    // ---- 底部导航条（悬浮导航体系）----

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
