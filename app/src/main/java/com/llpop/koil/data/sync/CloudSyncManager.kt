package com.llpop.koil.data.sync

import com.llpop.koil.BuildConfig
import com.llpop.koil.data.local.AppDatabase
import com.llpop.koil.data.local.dao.KOiLRecordDao
import com.llpop.koil.data.local.entity.KOiLRecordEntity
import com.llpop.koil.data.settings.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 云同步编排（docs/KOiL云同步设计.md）：
 * - 推送：本地权威 → 云端镜像（全量快照 PUT 幂等覆盖），触发 = 记录变更（防抖）+ 手动
 * - 同步范围：仅加油记录（v1.5.44 起设置不再备份/恢复；云端旧文件中的 settings 块恢复时忽略）
 * - 恢复：仅两条受控路径——①本地空库首次自动恢复；②手动「从云端恢复」（预览合并，不删本地）
 * - 明确不做：油价库上传、设置同步、双向增量合并（删除不自动同步，靠受控恢复人工确认）
 * - 失败重试：指数退避（15s → 60s，共 3 次尝试）后记状态，下次启动/变更自动重试
 */
@OptIn(kotlinx.coroutines.FlowPreview::class)
class CloudSyncManager(
    private val settings: AppSettings,
    private val recordDao: KOiLRecordDao,
    private val client: WebDavClient,
    private val db: AppDatabase,
) {

    companion object {
        /** 变更推送最小间隔（30s）：连续录入抖动合并为一次推送的兜底频率闸 */
        private const val MIN_PUSH_INTERVAL_MS = 30_000L
        private const val RECORD_DEBOUNCE_MS = 5_000L
        private val RETRY_DELAYS_MS = longArrayOf(15_000L, 60_000L)
    }

    private val initialized = AtomicBoolean(false)
    private val pushMutex = Mutex()

    /** 恢复预览结果（UI 确认后再 commit） */
    data class RestoreResult(
        val valid: List<KOiLRecordEntity>,
        val duplicates: Int,
        val errors: Int,
        val cloudRecordCount: Int,
    )

    // ===== 启动接入（KOiLApplication）=====

    fun init(scope: CoroutineScope) {
        if (!initialized.compareAndSet(false, true)) return
        scope.launch { startupCheck() }
        scope.launch { recordChangeCollector() }
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
        val json = BackupCodec.buildBackup(records, BuildConfig.VERSION_NAME)

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
            cloudRecordCount = total,
        )
    }

    /** 提交恢复：批量插入 valid（IGNORE 幂等防重）。返回实际插入数 */
    suspend fun restoreCommit(result: RestoreResult): Int {
        val inserted = if (result.valid.isEmpty()) 0
        else recordDao.insertAllIgnore(result.valid).count { it != -1L }
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
        // v1.6.2：改用 Room 表级失效流——只在 koil_record 有写入时发一个"表名集合"信号，
        // 不跑查询、不物化实体。原实现 observeAll() 会把整张表拉成 List<Entity>，
        // 再 distinctUntilChanged() 对列表做全量 equals（每次写入 O(n) 对象比较、O(n) 内存分配）。
        // 失效流对插入/更新/删除都会触发，语义比"行数变化"更完整（编辑记录同样会推）。
        // onStart 补一次：失效流不像 observeAll 那样在订阅瞬间就发首个值，这里显式补上，
        // 保留"每次启动检查一次云端是否需要推送"的旧语义（内部仍受 30s 最小间隔节流）。
        db.invalidationTracker.createFlow("koil_record")
            .onStart { emit(setOf("koil_record")) }
            .debounce(RECORD_DEBOUNCE_MS)
            .collect { pushIfAllowed() }
    }
}
