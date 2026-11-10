package com.llpop.koil.data.sync

import com.llpop.koil.BuildConfig
import com.llpop.koil.data.local.AppDatabase
import com.llpop.koil.data.local.dao.KOiLRecordDao
import com.llpop.koil.data.local.entity.KOiLRecordEntity
import com.llpop.koil.data.settings.AppSettings
import com.llpop.koil.domain.logic.inChronologicalOrder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
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
 * - 推送：本地 ∪ 云端 → 云端镜像。每次推送前先下载云端快照，把云端独有的记录合进本地，
 *   再写回全量快照；PUT 带 If-Match / If-None-Match 前置条件，412 冲突时重新拉取合并后重试。
 *   这样两台设备先后推送不会互相整包覆盖，也不会把对方的新记录冲掉。
 * - 同步范围：仅加油记录（设置不备份/恢复；云端文件中的 settings 块恢复时忽略）
 * - 恢复：仅两条受控路径——①本地空库首次自动恢复；②手动「从云端恢复」（预览合并，不删本地）
 * - 明确不做：油价库上传、设置同步、删除自动同步（删除不自动扩散，靠受控恢复人工确认）
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
        /** 进度推送失败后的进程内补推间隔（避免一次失败后只能等下次启动） */
        private const val FAILURE_RETRY_DELAY_MS = 60_000L
        private val RETRY_DELAYS_MS = longArrayOf(15_000L, 60_000L)

        /**
         * 距离允许下一次推送还需等待的毫秒数。
         * 节流期内收到变更时，不能直接丢事件，要等到窗口结束再推。
         */
        internal fun pushWaitMs(lastPushAt: Long, now: Long): Long =
            (MIN_PUSH_INTERVAL_MS - (now - lastPushAt)).coerceAtLeast(0L)
    }

    private val initialized = AtomicBoolean(false)
    private val pushMutex = Mutex()

    /** 变更触发推送请求：CONFLATED 只保留最新一次，消费者串行处理，避免事件堆积 */
    private val pushRequests = Channel<Unit>(Channel.CONFLATED)

    /** 恢复预览结果（UI 确认后再 commit） */
    data class RestoreResult(
        val valid: List<KOiLRecordEntity>,
        val duplicates: Int,
        val errors: Int,
        val cloudRecordCount: Int,
        val skippedByTombstone: Int = 0,
    )

    // ===== 启动接入（KOiLApplication）=====

    fun init(scope: CoroutineScope) {
        if (!initialized.compareAndSet(false, true)) return
        scope.launch { startupCheck() }
        scope.launch { pushRequestLoop() }
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

    /**
     * 手动立即备份。
     * @param force true（默认）＝无视 30s 频率闸立即推；false ＝ 与变更触发的推送同口径，
     *        距上次成功推送不足 [MIN_PUSH_INTERVAL_MS] 时跳过并返回 false。
     *        force 必须真的被读取，否则"绕过频率闸"只是碰巧成立。
     * @return 是否推送成功；force=false 被节流跳过时返回 false
     */
    suspend fun pushNow(force: Boolean = true): Boolean {
        if (!isConfigured()) return false
        if (!force) {
            val last = settings.syncLastPushAt.first()
            if (System.currentTimeMillis() - last < MIN_PUSH_INTERVAL_MS) return false
        }
        return pushWithRetry()
    }

    /**
     * 变更推送的串行消费者：节流期内不丢事件，等到窗口结束再推；
     * 失败后延迟一段时间把请求重新放回 CONFLATED 队列，进程内补推，不依赖下次启动。
     */
    private suspend fun pushRequestLoop() {
        for (ignored in pushRequests) {
            if (!isConfigured()) continue
            val last = settings.syncLastPushAt.first()
            val wait = pushWaitMs(last, System.currentTimeMillis())
            if (wait > 0) kotlinx.coroutines.delay(wait)
            val ok = try {
                pushWithRetry()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                false
            }
            if (!ok) {
                kotlinx.coroutines.delay(FAILURE_RETRY_DELAY_MS)
                pushRequests.trySend(Unit)
            }
        }
    }

    private suspend fun pushWithRetry(): Boolean {
        pushMutex.withLock {
            var attempt = 0
            while (true) {
                try {
                    pushOnce()
                    return true
                } catch (e: CancellationException) {
                    throw e
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

        // 记录本次推送开始前的墓碑：推送期间用户新删除的键不能被本次 clear 掉
        val tombstonesAtStart = settings.syncDeletedKeys.first()

        // 推送前先读云端快照：把其它设备已推、本机还没有的记录合进本地，
        // 再以「本地 ∪ 云端」的全量写回，避免后写者把先写者的记录整包覆盖。
        val snapshot = client.downloadWithMeta(url, BackupCodec.CLOUD_FILE_NAME, username, password)
        // 文件“存在”应以 404/非 404 为准，不能以内容是否为空判断：
        // 空文件若被当作不存在，上传会带 If-None-Match: *，服务端因文件已存在永久返回 412。
        val cloudExists = snapshot.content != null
        val cloudBody = snapshot.content?.takeIf { it.isNotBlank() }
        if (cloudBody != null) mergeCloudIntoLocal(cloudBody)

        val records = recordDao.getRange("1970-01-01", "2999-12-31").inChronologicalOrder()
        val json = BackupCodec.buildBackup(records, BuildConfig.VERSION_NAME)

        client.upload(
            url, BackupCodec.CLOUD_FILE_NAME, json, username, password,
            expectedEtag = snapshot.etag, cloudExists = cloudExists,
        )

        // 只清“推送开始前已存在”的墓碑；推送期间新增的删除必须保留到下一轮，
        // 否则本轮快照仍含被删记录，云端会把刚删掉的记录重新拉回来。
        settings.clearSyncDeletedKeys(tombstonesAtStart)
        settings.setSyncLastPushAt(System.currentTimeMillis())
        // 首次真正推送成功 = "配置过同步"：墓碑判定据此生效
        settings.setSyncEverConfigured()
        settings.setSyncLastStatus(
            "ok@${SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date())}"
        )
    }

    /**
     * 把云端快照中本机缺少的记录插入本地（同日同里程复合键去重，跳过删除墓碑）。
     * 云端文件损坏/格式不认识时跳过合并，仍允许本地快照覆盖坏文件，避免同步被永久卡死。
     */
    private suspend fun mergeCloudIntoLocal(cloudJson: String): Int {
        val parsed = try {
            BackupCodec.parse(cloudJson)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return 0
        }
        if (parsed.records.isEmpty()) return 0
        val local = recordDao.getRange("1970-01-01", "2999-12-31")
        val tombstones = settings.syncDeletedKeys.first()
        val missing = RecordMerger.missing(parsed.records, local, tombstones)
        if (missing.isEmpty()) return 0
        return recordDao.insertAllIgnore(missing).count { it != -1L }
    }

    // ===== 恢复 =====

    /** 下载云端快照并生成合并预览（不写库）。云端无备份/未配置返回 null */
    suspend fun restorePreview(): RestoreResult? {
        if (!isConfigured()) return null
        val url = settings.syncUrl.first()
        val username = settings.syncUsername.first()
        val password = getAppPassword()

        val json = client.download(url, BackupCodec.CLOUD_FILE_NAME, username, password)
            ?.takeIf { it.isNotBlank() } ?: return null
        // 一次解析同时拿到总条数与有效记录（避免 countRecords + parseRecords 把大 JSON 解析两遍）
        val parsed = BackupCodec.parse(json)
        val total = parsed.total

        // 与本地比对：同日同里程视为重复（沿用 CSV 导入的复合键口径，Double 精确比较）
        val existing = recordDao.getRange("1970-01-01", "2999-12-31")
        val keys = existing.map { it.reoilDate to it.mileage }.toSet()
        // 删除墓碑优先于重复判定：本机刚删掉的键，云端旧快照还在时必须跳过，
        // 不能让“恢复预览”把它重新算成可恢复记录，否则 startupCheck 会复活已删数据。
        val tombstones = settings.syncDeletedKeys.first()
        val candidates = RecordMerger.withoutTombstones(parsed.records, tombstones)
        val skippedByTombstone = parsed.records.size - candidates.size
        val valid = mutableListOf<KOiLRecordEntity>()
        var duplicates = 0
        for (r in candidates) {
            if (r.reoilDate to r.mileage in keys) duplicates++
            else valid.add(r.toEntity())
        }
        return RestoreResult(
            valid = valid,
            duplicates = duplicates,
            errors = (total - parsed.records.size).coerceAtLeast(0),
            cloudRecordCount = total,
            skippedByTombstone = skippedByTombstone,
        )
    }

    /** 提交恢复：批量插入 valid（IGNORE 幂等防重）。返回实际插入数 */
    suspend fun restoreCommit(result: RestoreResult): Int {
        // 预览与确认之间用户可能又删了记录：提交前必须再读一次墓碑，否则预览里的
        // 那条已删记录会被用户点“恢复”重新插回来。
        val tombstones = settings.syncDeletedKeys.first()
        val pending = result.valid.filterNot {
            RecordMerger.isTombstoned(it.reoilDate, it.mileage, tombstones)
        }
        val inserted = if (pending.isEmpty()) 0
        else recordDao.insertAllIgnore(pending).count { it != -1L }
        // 恢复不是推送：不能写 syncLastPushAt（否则设置页显示「已同步」、且 30s 推送节流闸会吞掉
        // 恢复后的首次真实推送）。状态单独标 restore@，与 ok@ 区分。
        // inserted=0（云端与本机一致 / 全被墓碑覆盖）不再写 restore@——否则看起来像"刚恢复过
        // 一批数据"，用户会以为丢了又找回来了；改写 restore-none@ 明确"无需恢复"，
        // 设置页按前缀区分显示。两者都以 restore 开头：曾执行过恢复 = 曾配置过同步。
        settings.setSyncLastStatus(
            (if (inserted > 0) "restore@" else "restore-none@") +
                SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date())
        )
        return inserted
    }

    /** 首拉自动恢复：本地空库且已配置 → 直接恢复（合并预览全 valid 场景） */
    private suspend fun startupCheck() {
        try {
            if (!isConfigured()) return
            if (recordDao.count() == 0) {
                val preview = restorePreview()
                if (preview?.valid?.isNotEmpty() == true) {
                    restoreCommit(preview)
                }
                // 本地空但墓碑非空：云端快照里对应的记录已被本机删除，不能通过
                // startupCheck 自动恢复。全部被墓碑覆盖时 preview.valid 为空，
                // 这里推一次空/剩余快照，清墓碑并让云端镜像本地删除。
                if (settings.syncDeletedKeys.first().isNotEmpty()) {
                    pushWithRetry()
                }
            } else {
                // 本地有数据：上次推送失败则补推一次（失败重试，不进恢复路径）
                val lastStatus = settings.syncLastStatus.first()
                if (lastStatus.startsWith("fail:")) pushWithRetry()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // 启动检查失败不阻断启动，下次记录变更/手动备份会自动重试
        }
    }

    // ===== 变更订阅 =====

    private suspend fun recordChangeCollector() {
        // 采用 Room 表级失效流——只在 koil_record 有写入时发一个"表名集合"信号，
        // 不跑查询、不物化实体，避免 observeAll() 把整张表拉成 List<Entity>
        // 再 distinctUntilChanged() 对列表做全量 equals（每次写入 O(n) 对象比较、O(n) 内存分配）。
        // 失效流对插入/更新/删除都会触发，语义比"行数变化"更完整（编辑记录同样会推）。
        // onStart 补一次：失效流不像 observeAll 那样在订阅瞬间就发首个值，这里显式补上，
        // 实现"每次启动检查一次云端是否需要推送"的语义（内部受 30s 最小间隔节流）。
        db.invalidationTracker.createFlow("koil_record")
            .onStart { emit(setOf("koil_record")) }
            .debounce(RECORD_DEBOUNCE_MS)
            // 只丢一个“有变更”的信号，由 pushRequestLoop 负责节流等待/失败补推；
            // 直接在 collect 里 pushIfAllowed 的话，30s 内的事件会被 return 掉且永不补发。
            .collect { pushRequests.trySend(Unit) }
    }
}
