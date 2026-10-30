package com.llpop.koil.data.sync

import com.llpop.koil.BuildConfig
import com.llpop.koil.data.local.dao.KOiLRecordDao
import com.llpop.koil.data.local.entity.KOiLRecordEntity
import com.llpop.koil.data.settings.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 云同步编排（docs/KOiL云同步设计.md）：
 * - 推送：本地权威 → 云端镜像（全量快照 PUT 幂等覆盖），触发 = 记录变更 / 设置变更（防抖）+ 手动
 * - 恢复：仅两条受控路径——①本地空库首次自动恢复；②手动「从云端恢复」（预览合并，不删本地）
 * - 明确不做：油价库上传、双向增量合并（删除不自动同步，靠受控恢复人工确认）
 * - 失败重试：指数退避（15s → 60s，共 3 次尝试）后记状态，下次启动/变更自动重试
 */
@OptIn(kotlinx.coroutines.FlowPreview::class)
class CloudSyncManager(
    private val settings: AppSettings,
    private val recordDao: KOiLRecordDao,
    private val client: WebDavClient,
) {

    companion object {
        /** 变更推送最小间隔（30s）：连续录入/设置抖动合并为一次推送的兜底频率闸 */
        private const val MIN_PUSH_INTERVAL_MS = 30_000L
        private const val RECORD_DEBOUNCE_MS = 5_000L
        private const val SETTING_DEBOUNCE_MS = 3_000L
        private val RETRY_DELAYS_MS = longArrayOf(15_000L, 60_000L)
    }

    private val initialized = AtomicBoolean(false)
    private val pushMutex = Mutex()

    /** 恢复预览结果（UI 确认后再 commit） */
    data class RestoreResult(
        val valid: List<KOiLRecordEntity>,
        val duplicates: Int,
        val errors: Int,
        val settings: Map<String, Any?>,
        val cloudRecordCount: Int,
    )

    // ===== 启动接入（KOiLApplication）=====

    fun init(scope: CoroutineScope) {
        if (!initialized.compareAndSet(false, true)) return
        scope.launch { startupCheck() }
        scope.launch { recordChangeCollector() }
        scope.launch { settingsChangeCollector() }
    }

    // ===== 配置状态 =====

    /** 是否已配置（URL/账号/密码齐备；密码按密文或明文回退取值） */
    suspend fun isConfigured(): Boolean {
        if (!settings.syncEnabled.first()) return false
        val url = settings.syncUrl.first()
        val username = settings.syncUsername.first()
        if (url.isBlank() || username.isBlank()) return false
        return getAppPassword().isNotBlank()
    }

    /** 应用密码（Keystore 密文优先，解密失败视为未配置） */
    suspend fun getAppPassword(): String {
        val stored = settings.syncAppPassword.first()
        if (stored.isBlank()) return ""
        return if (settings.syncPasswordEncrypted.first()) {
            CryptoHelper.decrypt(stored) ?: ""
        } else stored
    }

    /** 连接测试（设置页「保存并测试连接」）：读取最新配置，PROPFIND 验证认证与可达性 */
    suspend fun testConnection(): Boolean {
        val url = settings.syncUrl.first()
        val username = settings.syncUsername.first()
        val password = getAppPassword()
        if (url.isBlank() || username.isBlank() || password.isBlank()) return false
        return client.checkConnection(url, username, password)
    }

    // ===== 推送 =====

    /** 手动立即备份（force=true 绕过 30s 频率闸）。成功返回 true；失败返回 false */
    suspend fun pushNow(force: Boolean = true): Boolean {
        if (!isConfigured()) return false
        return pushWithRetry()
    }

    /** 变更触发推送（防抖 + 30s 频率闸 + 并发锁），失败静默记状态 */
    private suspend fun pushIfAllowed() {
        if (!isConfigured()) return
        val last = settings.syncLastPushAt.first()
        if (System.currentTimeMillis() - last < MIN_PUSH_INTERVAL_MS) return
        runCatching { pushWithRetry() }
    }

    private suspend fun pushWithRetry(): Boolean {
        pushMutex.withLock {
            var attempt = 0
            while (true) {
                try {
                    pushOnce()
                    return true
                } catch (e: Exception) {
                    attempt++
                    if (attempt <= RETRY_DELAYS_MS.size) {
                        kotlinx.coroutines.delay(RETRY_DELAYS_MS[attempt - 1])
                    } else {
                        settings.setSyncLastStatus("fail:${e.message?.take(60) ?: "网络错误"}")
                        return false
                    }
                }
            }
        }
    }

    private suspend fun pushOnce() {
        val url = settings.syncUrl.first()
        val username = settings.syncUsername.first()
        val password = getAppPassword()

        val records = recordDao.getRange("1970-01-01", "2999-12-31").sortedBy { it.reoilDate }
        val settingsMap = collectSettingsMap()
        val json = BackupCodec.buildBackup(records, settingsMap, BuildConfig.VERSION_NAME)

        client.upload(url, BackupCodec.CLOUD_FILE_NAME, json, username, password)

        settings.setSyncLastPushAt(System.currentTimeMillis())
        settings.setSyncLastStatus(
            "ok@${SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date())}"
        )
    }

    // ===== 恢复 =====

    /** 下载云端快照并生成合并预览（不写库）。云端无备份/未配置返回 null */
    suspend fun restorePreview(): RestoreResult? {
        if (!isConfigured()) return null
        val url = settings.syncUrl.first()
        val username = settings.syncUsername.first()
        val password = getAppPassword()

        val json = client.download(url, BackupCodec.CLOUD_FILE_NAME, username, password) ?: return null
        val total = BackupCodec.countRecords(json)
        val parsed = BackupCodec.parseRecords(json)
        val settingsMap = BackupCodec.parseSettings(json)

        // 与本地比对：同日同里程视为重复（沿用 CSV 导入的复合键口径，Double 精确比较）
        val existing = recordDao.getRange("1970-01-01", "2999-12-31")
        val keys = existing.map { it.reoilDate to it.mileage }.toSet()
        val valid = mutableListOf<KOiLRecordEntity>()
        var duplicates = 0
        for (r in parsed) {
            if (r.reoilDate to r.mileage in keys) duplicates++
            else valid.add(r.toEntity())
        }
        return RestoreResult(
            valid = valid,
            duplicates = duplicates,
            errors = (total - parsed.size).coerceAtLeast(0),
            settings = settingsMap,
            cloudRecordCount = total,
        )
    }

    /** 提交恢复：批量插入 valid（IGNORE 幂等防重）+ 应用设置。返回实际插入数 */
    suspend fun restoreCommit(result: RestoreResult): Int {
        val inserted = if (result.valid.isEmpty()) 0
        else recordDao.insertAllIgnore(result.valid).count { it != -1L }
        applySettingsMap(result.settings)
        settings.setSyncLastPushAt(System.currentTimeMillis())
        settings.setSyncLastStatus(
            "ok@${SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date())}"
        )
        return inserted
    }

    /** 首拉自动恢复：本地空库且已配置 → 直接恢复（合并预览全 valid 场景） */
    private suspend fun startupCheck() {
        runCatching {
            if (!isConfigured()) return@runCatching
            if (recordDao.count() == 0) {
                val preview = restorePreview() ?: return@runCatching
                if (preview.valid.isNotEmpty() || preview.duplicates > 0 || preview.errors > 0) {
                    restoreCommit(preview)
                }
            } else {
                // 本地有数据：上次推送失败则补推一次（失败重试，不进恢复路径）
                val lastStatus = settings.syncLastStatus.first()
                if (lastStatus.startsWith("fail:")) pushWithRetry()
            }
        }
    }

    // ===== 变更订阅 =====

    private suspend fun recordChangeCollector() {
        recordDao.observeAll()
            .debounce(RECORD_DEBOUNCE_MS)
            .distinctUntilChanged()
            .collect { pushIfAllowed() }
    }

    /** 13 项用户私有设置变化订阅（不含同步配置键自身，避免配置输入过程触发推送） */
    private suspend fun settingsChangeCollector() {
        combine<Any, List<Any>>(
            settings.themeMode,
            settings.language,
            settings.defaultProvince,
            settings.defaultOilGrade,
            settings.tankCapacity,
            settings.keepLastGrade,
            settings.abnormalThreshold,
            settings.priceAlert,
            settings.locationEnabled,
            settings.priceDbAutoUpdate,
            settings.deviationThreshold,
            settings.eiaEnabled,
            settings.eiaApiKey,
        ) { values -> values.toList() }
            .debounce(SETTING_DEBOUNCE_MS)
            .distinctUntilChanged()
            .collect { pushIfAllowed() }
    }

    /** 推送用的设置快照（与 §7.10 手动备份的 11 项 + 云同步扩展 2 项一致） */
    private suspend fun collectSettingsMap(): Map<String, Any?> = mapOf(
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
        "eiaEnabled" to settings.eiaEnabled.first(),
        "eiaApiKey" to settings.eiaApiKey.first(),
    )

    /** 恢复时应用云端设置（未知字段忽略；缺失字段保持本地当前值） */
    private suspend fun applySettingsMap(map: Map<String, Any?>) {
        (map["themeMode"] as? String)?.let { name ->
            if (AppSettings.ThemeMode.entries.any { it.name == name }) {
                runCatching { settings.setThemeMode(AppSettings.ThemeMode.valueOf(name)) }
            }
        }
        (map["language"] as? String)?.let { name ->
            if (AppSettings.Language.entries.any { it.name == name }) {
                runCatching { settings.setLanguage(AppSettings.Language.valueOf(name)) }
            }
        }
        (map["defaultProvince"] as? String)?.takeIf { it.isNotBlank() }?.let { settings.setDefaultProvince(it) }
        (map["defaultGrade"] as? String)?.takeIf { it.isNotBlank() }?.let { settings.setDefaultOilGrade(it) }
        (map["tankCapacity"] as? Double)?.let { settings.setTankCapacity(it.toFloat()) }
        (map["keepLastGrade"] as? Boolean)?.let { settings.setKeepLastGrade(it) }
        (map["abnormalThreshold"] as? Double)?.let { settings.setAbnormalThreshold(it) }
        (map["deviationThreshold"] as? Double)?.let { settings.setDeviationThreshold(it) }
        (map["priceAlert"] as? Boolean)?.let { settings.setPriceAlert(it) }
        (map["locationEnabled"] as? Boolean)?.let { settings.setLocationEnabled(it) }
        (map["priceDbAutoUpdate"] as? Boolean)?.let { settings.setPriceDbAutoUpdate(it) }
        (map["eiaEnabled"] as? Boolean)?.let { settings.setEiaEnabled(it) }
        (map["eiaApiKey"] as? String)?.let { settings.setEiaApiKey(it) }
    }
}
