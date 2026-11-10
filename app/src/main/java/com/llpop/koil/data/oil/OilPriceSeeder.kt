package com.llpop.koil.data.oil

import android.util.Log
import androidx.room.withTransaction
import com.llpop.koil.data.local.AppDatabase
import com.llpop.koil.data.local.dao.KOiLPriceDao
import com.llpop.koil.data.local.entity.KOiLPriceEntity
import com.llpop.koil.data.oil.OilPriceDbSyncer.SeedState
import com.llpop.koil.data.settings.AppSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * 油价库灌库器（从 OilPriceDbSyncer 拆出，M-09，行为不变）：
 * - initPriceDb：首拉当前省一整年曲线灌库（仅库空时执行）
 * - seedRemainingProvinces：31 省幂等补灌（4 路并行 + 失败省单独记账）
 * - runSeedRetryLoop / resumeSeedingIfNeeded：启动兜底与回前台兜底的重试循环
 *
 * —— 灌库（seedProvince）——
 * 幂等（省|油号|生效日键）；只存价格变化点（abapi 滞后日期归并到窗口日+1）；
 * 补搁浅：已过窗口无生效记录 → 插 isStranded=true 维持价记录，
 *   维持价 = abapi ≤窗口日的最后价格（首窗口搁浅回退上年末价，不设当年下界）；
 * 同价假记录清理：isStranded=false 且价格与上一条相同的历史假点先删（放行搁浅幂等）。
 *
 * 补灌状态（SeedState / seedDone / seedFailed）由 OilPriceDbSyncer 门面透出，设置页据此展示。
 * 机制细节见 docs/KOiL油价数据库机制.md。
 */
internal class OilPriceSeeder(
    private val db: AppDatabase,
    private val priceDao: KOiLPriceDao,
    private val settings: AppSettings,
    private val network: OilPriceNetwork,
    private val calendar: OilPriceAdjustCalendar,
) {

    companion object {
        private val PROVINCES = Provinces.ALL

        /** 启动补灌重试退避（累计约 6.7 分钟）：若用固定 30s×6≈3 分钟，弱网首装常在拿到日历前就耗尽预算 */
        internal val DEFAULT_SEED_RETRY_MS = listOf(3_000L, 10_000L, 30_000L, 60_000L, 120_000L, 180_000L)
        /** 回前台兜底的短重试（切回 App 时用户在场，不必再等分钟级） */
        private val FOREGROUND_SEED_RETRY_MS = listOf(5_000L, 20_000L, 60_000L)
    }

    private val seedLock = AtomicBoolean(false)
    /** 重试循环互斥：启动兜底与回前台兜底可能同时触发，只允许一路在跑 */
    private val seedLoopLock = AtomicBoolean(false)

    /**
     * 全国补灌状态。
     * 设置页据此区分「真的在动」与「已经放弃」——若只有一句恒定的「后台补灌中，完成后自动刷新」，
     * 而重试预算用尽后其实什么都没在跑，等于误导用户干等。
     */
    @Volatile
    var seedState: SeedState = SeedState.IDLE
        private set

    /** 本轮已成功处理的省份数（含"已覆盖直接跳过"的省），驱动设置页 N/31 进度 */
    private val seedDoneCounter = AtomicInteger(0)
    val seedDone: Int get() = seedDoneCounter.get()

    /**
     * 本轮补灌失败的省份。弱网首装最容易被吞成"31/31 完成"：单省 catch 只写日志、
     * 计数照加、状态照 IDLE，重试循环以为完成而退出，缺口再也不会自动补。
     * 失败省必须单独可见，并让 [seedRemainingProvinces] 返回 false 让重试继续。
     */
    private val seedFailedProvinces: MutableSet<String> =
        java.util.concurrent.ConcurrentHashMap.newKeySet()
    val seedFailedCount: Int get() = seedFailedProvinces.size
    val seedFailed: List<String> get() = seedFailedProvinces.toSortedSet().toList()

    @Volatile
    var seedTotal: Int = 0
        private set

    /** 上次发起补灌的墙钟时间（回前台兜底用，防止频繁切前台反复打网络） */
    @Volatile
    private var lastSeedAttemptAt: Long = 0L

    /**
     * 首拉当前省一整年曲线灌库（仅 koil_prices 为空时执行，不覆盖老用户已有数据）。
     * 其余省份由 [seedRemainingProvinces] 在后台补全。无网或接口失败时静默跳过。
     */
    suspend fun initPriceDb() {
        if (priceDao.count() > 0) return
        val home = settings.koilDefaultProvince()
        // 抓发改委当年权威窗口日（失败为空 → seedProvince 降级用 abapi 原日期）
        val adjustDates = calendar.getAdjustDates()
        seedProvince(home, adjustDates)
    }

    /**
     * 补全各省窗口日覆盖（幂等，REPLACE）。
     * 判定标准：**窗口日覆盖数 < 应有量则重灌该省**，而非"省是否已在库"——
     * 假生效日被清理后各省可能只剩零星记录却仍被判"已覆盖"，导致缺口永不补全。
     * 窗口日历未就绪时跳过（避免降级用 abapi 原日期再产生假日期）；单省失败只留缺口，不阻塞其他省。
     *
     * @return 是否真的执行了补灌。空库、日历未就绪、已有另一路在跑都返回 false——
     *         调用方的重试循环靠这个决定是"已完成"还是"继续等"，不能笼统当成成功。
     */
    suspend fun seedRemainingProvinces(): Boolean {
        if (priceDao.count() == 0) return false
        // 互斥：启动兜底与 checkAndUpdate 尾部可能同时触发，只允许一路执行
        if (!seedLock.compareAndSet(false, true)) return false
        try {
            val adjustDates = calendar.getAdjustDates()
            if (adjustDates.isEmpty()) {
                // 日历未就绪不灌（防假生效日），但必须把状态说出来——
                // 否则重试循环和用户都以为"补灌已完成"，而这正是首装/弱网最常见的卡点。
                seedState = SeedState.WAITING
                return false
            }
            // 期望覆盖已过窗口日数（含搁浅窗口，搁浅入库后各省应含维持记录）；
            // 每省独立拉取，失败不影响其他省
            // 窗口日 24 点前不算已过 → 严格 < today（今天窗口日不计入期望覆盖）
            val threshold = adjustDates.count { it < LocalDate.now().format(KOIL_DATE_FMT) }
            lastSeedAttemptAt = System.currentTimeMillis()
            seedState = SeedState.RUNNING
            seedTotal = PROVINCES.size
            seedDoneCounter.set(0)
            seedFailedProvinces.clear()
            // 并发补灌：4 路并行（避免 31 省串行等待；组间 200ms 缓解 abapi 限速）
            coroutineScope {
                PROVINCES.chunked(4).forEach { chunk ->
                    chunk.map { province ->
                        async {
                            // 单省整段包 try/catch。若只给 countDatesForProvince 包，
                            // 注释写着"单省失败只留缺口"，但 seedProvince 抛异常会让 awaitAll 连坐整轮、
                            // 再把异常上抛终止启动重试循环——弱网首装时这里一炸，后面 30 个省全部跳过。
                            // 但取消不能吞：吞掉会让整个补灌在用户退出后继续跑（async 拿不到取消信号）。
                            try {
                                val covered = priceDao.countDatesForProvince(province)
                                // covered < threshold（窗口数不够）或存在搁浅缺口（窗口日+1 无记录/同价假记录占位）
                                // 都触发补灌——防止 verifyProvince 抢插的同价假记录让 covered 达标后搁浅永远缺失
                                if (covered < threshold || hasStrandedGap(province, adjustDates)) {
                                    // seedProvince 在「网络成功但没数据」时返回 0 且**不抛异常**
                                    // （fetchYoujiaHistory 空 → return 0），不看返回值就会把空数据省
                                    // increment 成功 → seedState 走 IDLE、seedRemainingProvinces 恒 true，
                                    // 缺口永远不会被重试补上。返回 0 一律记失败省。
                                    val inserted = seedProvince(province, adjustDates)
                                    if (inserted > 0) {
                                        seedDoneCounter.incrementAndGet()
                                    } else {
                                        seedFailedProvinces.add(province)
                                        Log.w(
                                            "KOiL",
                                            "seedProvince($province) inserted 0 rows " +
                                                "(fetch empty or nothing to write)",
                                        )
                                    }
                                } else {
                                    // 该省已覆盖、直接跳过：仍计成功
                                    seedDoneCounter.incrementAndGet()
                                }
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                // 失败省单独记账：不计入进度，也不并入"完成"
                                seedFailedProvinces.add(province)
                                Log.w("KOiL", "seedProvince($province) failed: ${e.javaClass.simpleName} ${e.message}")
                            }
                        }
                    }.awaitAll()
                    delay(200)
                }
            }
            // 有失败省就不算完成：返回 false 让 runSeedRetryLoop 继续下一轮，
            // seedState 转 GAVE_UP 让设置页从"补灌中"变成"失败 N 省，点立即更新重试"。
            if (seedFailedProvinces.isNotEmpty()) {
                seedState = SeedState.GAVE_UP
                Log.w("KOiL", "seed finished with ${seedFailedProvinces.size} failed: ${seedFailed}")
                return false
            }
            seedState = SeedState.IDLE
            return true
        } finally {
            // 异常路径别把状态永久卡在 RUNNING（否则设置页显示"正在补灌"却什么都没跑）
            if (seedState == SeedState.RUNNING) seedState = SeedState.IDLE
            seedLock.set(false)
        }
    }

    /**
     * 窗口日历就绪则执行全国补灌，返回是否已执行。
     * 用于启动兜底的重试循环——重装/弱网下锚点链（xxapi→ruseo→abapi→发改委）启动瞬间失败时，
     * getAdjustDates() 为空 → seedRemainingProvinces 静默跳过，补灌永久等待，只有点「立即更新」(force) 才能绕过。
     * 本方法把"日历未就绪"变成可观测状态，让启动任务 30s 轮询重试直到就绪（保留防假日期纪律：就绪前不灌）。
     *
     * 返回值必须透传 [seedRemainingProvinces] 的实际执行结果：空库时它直接返回，
     * 若此处仍硬编码 true，重试循环会认为"已执行"提前退出，补灌永久停在等待态。
     */
    suspend fun fillRemainingProvincesIfCalendarReady(): Boolean {
        if (calendar.getAdjustDates().isEmpty()) {
            seedState = SeedState.WAITING
            return false
        }
        return seedRemainingProvinces()
    }

    /**
     * 补灌重试循环（启动兜底 / 回前台兜底共用）。
     * 退避递增：固定 30s×6 的总预算约 3 分钟太短，弱网首装经常还没拿到窗口日历就耗尽，
     * 之后就再没有任何触发点，表现为"杀进程冷启动才继续灌"。
     * 每轮独立 try/catch —— 任何一轮抛异常不终止后面的重试。
     */
    suspend fun runSeedRetryLoop(scheduleMs: List<Long> = DEFAULT_SEED_RETRY_MS) {
        if (!seedLoopLock.compareAndSet(false, true)) return
        try {
            for (wait in scheduleMs) {
                delay(wait)
                val done = runCatching { fillRemainingProvincesIfCalendarReady() }
                    .onFailure { Log.w("KOiL", "seed retry round failed: ${it.message}") }
                    .getOrDefault(false)
                if (done) return
            }
            markSeedGaveUp()
        } finally {
            seedLoopLock.set(false)
        }
    }

    /** 预算用尽：转 GAVE_UP，让设置页从"补灌中"改成"点立即更新重试" */
    fun markSeedGaveUp() {
        if (seedState != SeedState.RUNNING) seedState = SeedState.GAVE_UP
    }

    /**
     * 回前台兜底——GAVE_UP / WAITING 时用户切回 App 再试一轮。
     * 有节流（默认 60s），避免快速切前后台反复打网络；31 省 COUNT 查询是纯本地，开销可忽略。
     */
    suspend fun resumeSeedingIfNeeded(minIntervalMs: Long = 60_000L) {
        if (seedState == SeedState.RUNNING) return
        if (seedState != SeedState.GAVE_UP && seedState != SeedState.WAITING) return
        val now = System.currentTimeMillis()
        if (now - lastSeedAttemptAt < minIntervalMs) return
        lastSeedAttemptAt = now
        runSeedRetryLoop(FOREGROUND_SEED_RETRY_MS)
    }

    /**
     * 该省是否存在「搁浅缺口」：已过窗口日+1 无生效记录，或存在同价假记录
     * （isStranded=false 且价格与**上一窗口**相同——真实调价必然变价，同价且非搁浅 = 假记录）。
     * 用于补灌触发判定，避免 covered 达标后搁浅被假记录挡住而永久缺失。
     */
    private suspend fun hasStrandedGap(province: String, adjustDates: List<String>): Boolean {
        val today = LocalDate.now().format(KOIL_DATE_FMT)
        val grades = OilPriceNetwork.GRADE_LABELS.values.toList()
        // 一次取本省全部价格，在内存里判缺口：「4 油号 × 全部已过窗口」逐条查库
        //（约 25 窗口 × 4 × 2 条 SQL），31 省补灌时上万次查询，明显拖慢补全国。
        val rows = runCatching { priceDao.getByProvince(province) }.getOrDefault(emptyList())
        if (rows.isEmpty()) return true
        // 同省同油号内按 effectiveDate 升序（SQL 已排序），用于取「< eff 的最近一条」
        val byGrade = rows.groupBy { it.grade }
        val byKey = rows.associateBy { it.grade to it.effectiveDate }
        // 窗口日 24 点前不算已过 → 严格 < today（今天窗口不判缺口，避免误触发补灌）
        return adjustDates.filter { it < today }.any { w ->
            val eff = runCatching { LocalDate.parse(w).plusDays(1).toString() }.getOrNull() ?: return@any false
            grades.any { key ->
                val e = byKey[key to eff]
                when {
                    e == null -> true
                    !e.isStranded -> {
                        // 比「上一窗口价」而非「≤eff 最近价」：后者（getLatestPrice）会取回 e 自身，
                        // 主键保证同日唯一 → 差值恒 0 → 每次真实调价都被误判成缺口，31 省反复全量重灌。
                        val prev = byGrade[key]?.lastOrNull { it.effectiveDate < eff }
                        prev != null && kotlin.math.abs(prev.price - e.price) < 1e-9
                    }
                    else -> false
                }
            }
        }
    }

    /**
     * 拉单个省当年（1-1 至今）曲线并写入 koil_prices（主键 province+grade+effectiveDate REPLACE），返回写入条数。
     * 去重压缩：仅存"价格有变化"的点（调价日）。偏差预检 getLatestPrice(≤date 最近价) 会回退到最近调价日价，
     * 非调价日沿用上次价的语义等价于逐日全存，故跳过重复价日可瘦身约 90% 且功能不变。
     * 生效日校正：传入 [adjustDates]（发改委权威窗口日）时，把 abapi 滞后日期归并到「≤它的最近窗口日」，
     * 使数据库生效日对齐真实调价日（9-27→9-24）；[adjustDates] 为空则降级用 abapi 原日期。
     */
    private suspend fun seedProvince(province: String, adjustDates: List<String> = emptyList()): Int {
        val points = network.fetchYoujiaHistory(province)
        if (points.isEmpty()) return 0
        // 网络请求放在事务外：删除、构建与插入整体原子，中途异常不会留下半灌的省份
        return db.withTransaction {
            // 同价假记录清理：窗口归档产生的 isStranded=false 同价记录（如 10-16 ¥8.63 与
            // 09-25 相同——真实调价必然变价，同价且非搁浅 = 假变化点）→ 删除，让补搁浅幂等键放行。
            // 幂等：假记录删一次后即不存在，重复补灌不重复删。
            try {
                priceDao.getNonStrandedByProvince(province)
                    .groupBy { it.grade }
                    .forEach { (_, list) ->
                        list.zipWithNext().forEach { (a, b) ->
                            if (kotlin.math.abs(b.price - a.price) < 1e-9) {
                                priceDao.deleteByKey(b.province, b.grade, b.effectiveDate)
                            }
                        }
                    }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("KOiL", "same-price cleanup failed", e)
            }

            val today = LocalDate.now().format(KOIL_DATE_FMT)
            val yearStart = "${LocalDate.now().year}-01-01"
            // 幂等键：省|油号|生效日（重灌/补灌重复触发时不重复插入）；mutable：变化点覆盖搁浅时移除键
            val existing = priceDao.keysForProvince(province).toMutableSet()
            val entities = mutableListOf<KOiLPriceEntity>()
            var last92: Double? = null
            var last95: Double? = null
            var last98: Double? = null
            var last0: Double? = null
            points.forEach { pt ->
                if (pt.date.isBlank()) return@forEach
                // 只保留当年 1-1 至今（用户口径：首拉当年调价记录）
                if (pt.date < yearStart || pt.date > today) return@forEach
                val eff = if (adjustDates.isEmpty()) pt.date else addOneDay(adjustToWindow(pt.date, adjustDates))
                // 写入闸：生效日 > 今天（窗口日当天数据源若提前放次日新价）一律跳过，
                // 等次日归并自然写入——库里永不出现"未来生效"记录
                if (eff > today) return@forEach
                pt.p92?.let { v -> if (last92 == null || kotlin.math.abs(v - last92!!) > 1e-9) {
                    val key = "$province|92号汽油|$eff"
                    if (key !in existing) entities.add(KOiLPriceEntity(province, "92号汽油", eff, v, "youjia.abapi.cn range=year 归并发改委窗口", false))
                    else {
                        // 库里该生效日是搁浅记录（isStranded=true）但 abapi 该窗口有变化
                        // → 删除搁浅 + 插变化点（恢复被误替换成搁浅的调价记录）
                        val row = priceDao.getPrice(province, "92号汽油", eff)
                        if (row != null && row.isStranded) { priceDao.deleteByKey(province, "92号汽油", eff); existing.remove(key); entities.add(KOiLPriceEntity(province, "92号汽油", eff, v, "youjia.abapi.cn range=year 归并发改委窗口", false)) }
                    }
                    last92 = v
                } }
                pt.p95?.let { v -> if (last95 == null || kotlin.math.abs(v - last95!!) > 1e-9) {
                    val key = "$province|95号汽油|$eff"
                    if (key !in existing) entities.add(KOiLPriceEntity(province, "95号汽油", eff, v, "youjia.abapi.cn range=year 归并发改委窗口", false))
                    else {
                        val row = priceDao.getPrice(province, "95号汽油", eff)
                        if (row != null && row.isStranded) { priceDao.deleteByKey(province, "95号汽油", eff); existing.remove(key); entities.add(KOiLPriceEntity(province, "95号汽油", eff, v, "youjia.abapi.cn range=year 归并发改委窗口", false)) }
                    }
                    last95 = v
                } }
                pt.p98?.let { v -> if (last98 == null || kotlin.math.abs(v - last98!!) > 1e-9) {
                    val key = "$province|98号汽油|$eff"
                    if (key !in existing) entities.add(KOiLPriceEntity(province, "98号汽油", eff, v, "youjia.abapi.cn range=year 归并发改委窗口", false))
                    else {
                        val row = priceDao.getPrice(province, "98号汽油", eff)
                        if (row != null && row.isStranded) { priceDao.deleteByKey(province, "98号汽油", eff); existing.remove(key); entities.add(KOiLPriceEntity(province, "98号汽油", eff, v, "youjia.abapi.cn range=year 归并发改委窗口", false)) }
                    }
                    last98 = v
                } }
                pt.p0?.let { v -> if (last0 == null || kotlin.math.abs(v - last0!!) > 1e-9) {
                    val key = "$province|0号柴油|$eff"
                    if (key !in existing) entities.add(KOiLPriceEntity(province, "0号柴油", eff, v, "youjia.abapi.cn range=year 归并发改委窗口", false))
                    else {
                        val row = priceDao.getPrice(province, "0号柴油", eff)
                        if (row != null && row.isStranded) { priceDao.deleteByKey(province, "0号柴油", eff); existing.remove(key); entities.add(KOiLPriceEntity(province, "0号柴油", eff, v, "youjia.abapi.cn range=year 归并发改委窗口", false)) }
                    }
                    last0 = v
                } }
            }
            // —— 补搁浅窗口（搁浅入库）——
            // 已过窗口 + 该窗口无生效记录 = 搁浅：插维持价记录（isStranded=true）。
            // 维持价 = abapi 曲线中 ≤ 窗口日的最后价格（首窗口搁浅（如 1-06）时 = 上年末价，
            // 即 abapi 当年首点 1-01，落在 yearStart..w 内）；幂等：跳过库内已有与本次变化点。
            // 窗口日 24 点前不算已过 → 严格 < today（今天窗口日不提前补搁浅，
            // 否则窗口日白天库里就出现"次日生效"的搁浅记录，等于把窗口日当 0 点处理）
            fun hasChange(g: String, eff: String) = entities.any {
                !it.isStranded && it.province == province && it.grade == g && it.effectiveDate == eff
            }
            adjustDates.filter { it < today }.forEach { w ->
                val eff = runCatching { LocalDate.parse(w).plusDays(1).toString() }.getOrNull() ?: return@forEach
                // 维持价取 abapi 曲线中 ≤ 窗口日的最后价格，不设"当年 1-1 起"下界：
                // 首窗口搁浅（如 1-06）时 abapi 当年曲线 1-06 前无点（实测首点 01-23），
                // 需回退到上年最后一个调价点（2025-12-24 ¥6.73）= 上年末价
                val keptPoint = points.lastOrNull { it.date <= w && !it.date.isBlank() }
                if (keptPoint == null) return@forEach
                // 补搁浅：只补「库里无记录」的已过窗口（hasChange 防本次新增变化点重复补）；
                // 已有的记录（调价或搁浅）一律不删不动——误删已有调价记录会导致全搁浅。
                if (keptPoint.p92 != null && !hasChange("92号汽油", eff) && ("$province|92号汽油|$eff") !in existing) {
                    entities.add(KOiLPriceEntity(province, "92号汽油", eff, keptPoint.p92, "窗口搁淺·維持(abapi)", false, true))
                }
                if (keptPoint.p95 != null && !hasChange("95号汽油", eff) && ("$province|95号汽油|$eff") !in existing) {
                    entities.add(KOiLPriceEntity(province, "95号汽油", eff, keptPoint.p95, "窗口搁淺·維持(abapi)", false, true))
                }
                if (keptPoint.p98 != null && !hasChange("98号汽油", eff) && ("$province|98号汽油|$eff") !in existing) {
                    entities.add(KOiLPriceEntity(province, "98号汽油", eff, keptPoint.p98, "窗口搁淺·維持(abapi)", false, true))
                }
                if (keptPoint.p0 != null && !hasChange("0号柴油", eff) && ("$province|0号柴油|$eff") !in existing) {
                    entities.add(KOiLPriceEntity(province, "0号柴油", eff, keptPoint.p0, "窗口搁淺·維持(abapi)", false, true))
                }
            }
            if (entities.isEmpty()) return@withTransaction 0
            entities.chunked(500).forEach { priceDao.insertAll(it) }
            return@withTransaction entities.size
        }
    }
}
