package com.llpop.koil.data.oil

import android.content.Context
import androidx.room.withTransaction
import com.llpop.koil.R
import com.llpop.koil.data.local.AppDatabase
import com.llpop.koil.data.local.dao.KOiLPriceDao
import com.llpop.koil.data.local.dao.KOiLPricePendingDao
import com.llpop.koil.data.local.entity.KOiLPriceEntity
import com.llpop.koil.data.local.entity.KOiLPricePendingEntity
import com.llpop.koil.data.settings.AppSettings
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 油价库同步服务（对齐 Web 端 oil-price-db.service.ts）：
 * - checkAndUpdate：双源采样 → 搁浅判定(0.005) → 31省逐省双源核验 → 一致入库 / 不一致入 pending
 * - 8 小时节流 + 自动更新开关 + 防并发
 * - acceptPending：事务（入库 + 标记 accepted）
 * - checkDeviation：当日历史参考价偏差预检
 *
 * —— 锚点链（v1.4.194 apizero 已收费移除，4 级）——
 * ① xxapi date−1 → ② ruseo date−1（同源互备，xxapi 优先）→
 * ③ abapi 全年曲线最近调价日（生效日−3）→ ④ calculateAdjustDates 本地推算全年窗口日历。
 * 内存+持久化缓存复用（重启不空降级）；缓存命中照常清假日期。
 *
 * —— 灌库（seedProvince）——
 * 幂等（省|油号|生效日键）；只存价格变化点（abapi 滞后日期归并到窗口日+1）；
 * 补搁浅（v1.4.181）：已过窗口无生效记录 → 插 isStranded=true 维持价记录，
 *   维持价 = abapi ≤窗口日的最后价格（首窗口搁浅回退上年末价，v1.4.182 去当年下界）；
 * 同价假记录清理（v1.4.182）：isStranded=false 且价格与上一条相同的历史假点先删（放行搁浅幂等）。
 *
 * —— 清理与口径 ——
 * purge 只清「当年 1-1~12-31 内非窗口日+1」的假日期，往年保留（v1.4.183）；
 * 調價 N = 窗口日历已过窗口数（含搁浅）；記錄 N = 库内全量；擱淺 N = count(isStranded)。
 *
 * 完整机制见 docs/KOiL油价数据库机制.md。
 */
class OilPriceDbSyncer(
    private val db: AppDatabase,
    private val priceDao: KOiLPriceDao,
    private val pendingDao: KOiLPricePendingDao,
    private val settings: AppSettings,
    private val network: OilPriceNetwork,
) {

    companion object {
        const val CHECK_INTERVAL_MS = 8L * 60 * 60 * 1000
        const val STRANDED_EPSILON = 0.005
        private val DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val PROVINCES = listOf(
            "广东", "北京", "上海", "江苏", "浙江", "山东", "四川", "湖北", "湖南",
            "河南", "河北", "福建", "安徽", "江西", "辽宁", "陕西", "云南", "广西",
            "重庆", "天津", "山西", "贵州", "黑龙江", "吉林", "甘肃", "内蒙古",
            "新疆", "海南", "宁夏", "青海", "西藏",
        )
    }

    private val checking = AtomicBoolean(false)
    private val seedLock = AtomicBoolean(false)

    /** 推算的当年权威窗口日缓存（锚点=次源 next_adjust_date + chinese-days 节假日推算；失败为空则降级） */
    @Volatile
    private var cachedAdjustDates: List<String>? = null

    private suspend fun defaultProvince(): String =
        settings.defaultProvince.first().takeIf { it in PROVINCES } ?: PROVINCES[0]

    /**
     * 主源 abapi 全年曲线 → 最近价格变化日（生效日）− 3 天 = 真实调价窗口日。
     * 实测（2026）：18 次价格变化全部与发改委公告窗口吻合（偏移 1~4 天，最近 4 次均 3 天）；
     * 曲线失败/无变化返回 null（由调用方继续降级）。
     */
    private suspend fun abapiAnchor(): String? {
        val points = network.fetchYoujiaHistory(defaultProvince())
        var last: Double? = null
        var change: String? = null
        points.forEach { pt ->
            val p = pt.p92
            if (p != null) {
                if (last != null && kotlin.math.abs(p - last) > 1e-9) change = pt.date
                last = p
            }
        }
        return change?.let { runCatching { LocalDate.parse(it).minusDays(3).toString() }.getOrNull() }
    }

    /**
     * 获取当年权威窗口日（升序，含历史与未来）。
     * 锚点 = 次源 next_adjust_date；节假日 = chinese-days（放假日 + 调休补班日）；
     * 规则：每 10 个工作日一个窗口、24 时生效、遇节假日顺延、调休补班算工作日。
     * 从锚点向前倒推回溯当年首调、向后顺推至当年末。缓存复用；失败返回 emptyList（降级用 abapi 原日期）。
     */
    private suspend fun getAdjustDates(): List<String> {
        // v1.4.186：一次性清旧窗口日历缓存重算——锚点失败（落 abapi 推算）的日历可能漏窗口
        // （如无 10-15），升级后强制重算一次，避免错日历长期复用导致窗口/搁浅缺失。
        if (!settings.priceDbCalendar186Migrated.first()) {
            cachedAdjustDates = null
            settings.setPriceDbAdjustDates(emptyList())
            settings.setPriceDbCalendar186Migrated(true)
        }
        // 1) 内存缓存优先（v1.4.191：跨年时内存缓存同样失效——App 跨年不重启的场景——清掉走持久化/重算）
        cachedAdjustDates?.let { dates ->
            if (dates.firstOrNull()?.take(4) == LocalDate.now().year.toString()) return dates
            cachedAdjustDates = null
        }
        // 2) 持久化缓存（App 重启后复用上次已推算的窗口日，杜绝次源临时失败导致空降级 → 假生效日）
        val persisted = settings.priceDbAdjustDates.first().let { s ->
            if (s.isBlank()) emptyList() else s.split(",").map { it.trim() }.filter { it.isNotBlank() }
        }
        if (persisted.isNotEmpty()) {
            // v1.4.191 跨年检测：缓存窗口日历的年份 ≠ 今年 → 清缓存重算（跨年后自动滚动到新年日历，
            // 调价次数当年口径从零重计、新窗口正常增量入库，无需清库重装）
            val yearOfCache = persisted.firstOrNull()?.take(4)
            val currentYear = LocalDate.now().year.toString()
            if (yearOfCache != currentYear) {
                cachedAdjustDates = null
                settings.setPriceDbAdjustDates(emptyList())
            } else {
                cachedAdjustDates = persisted
                // 缓存命中时也执行假生效日清理（此前清理只在推算分支执行，缓存命中会跳过 → 降级假日期残留）
                purgeIllegalEffectiveDates(persisted)
                return persisted
            }
        }
        // 3) 拉取推算（仅当无任何缓存时）
        // 锚点回退链（v1.4.194 apizero 已收费移除；同源互备 xxapi 优先）：
        // ① xxapi date−1 → ② ruseo date−1 → ③ 主源 abapi 全年曲线最近调价日（生效日 − 3 天，
        // 实测 18/18 与发改委公告吻合）→ ④ 发改委公告（v1.5.23 D4：公告标题年份+月日=权威窗口日，
        // 含搁浅窗口；但它是网页抓取，公告格式一旦变化即抓取为空 → 自动跳过，不影响主链锚点）
        // v1.5.27：锚点命中来源记录（多源一致性展示）——来源跟随实际命中的链路节点
        var anchorSource = ""
        val anchor = network.fetchAnchorWindowDate()?.also { anchorSource = it.source }?.date
            ?: abapiAnchor().also { if (it != null) anchorSource = "abapi" }
            ?: runCatching { network.fetchNdrcAdjustDates() }.getOrNull()
                ?.lastOrNull()?.also { anchorSource = "ndrc" }
        if (anchor == null) {
            // 从未成功推算且次源失败 → 空降级（seedProvince 用 abapi 原日期）
            settings.setPriceDbLastAnchorSource("无")
            return emptyList()
        }
        settings.setPriceDbLastAnchorSource(anchorSource)
        val todayYear = LocalDate.now().year
        // 拉当年 + 前后各一年节假日（向前倒推/向后顺推可能跨年；缺失年份为空 → 该年按纯工作日推）
        val holidaySet = mutableSetOf<String>()
        val workdaySet = mutableSetOf<String>()
        listOf(todayYear - 1, todayYear, todayYear + 1).forEach { y ->
            val h = network.fetchChinaHolidays(y)
            holidaySet.addAll(h.holidayDates)
            workdaySet.addAll(h.workdayDates)
        }
        val dates = calculateAdjustDates(anchor, holidaySet, workdaySet)
        if (dates.isNotEmpty()) {
            cachedAdjustDates = dates
            settings.setPriceDbAdjustDates(dates)
            purgeIllegalEffectiveDates(dates)
        }
        // 新机制首次跑通：一次性清空旧机制（双源当日价核验）遗留的假 pending，避免堆积
        if (!settings.priceDbLegacyPendingCleared.first()) {
            pendingDao.clear()
            settings.setPriceDbLegacyPendingCleared(true)
        }
        return dates
    }

    /** 从锚点推算"今年"全年窗口日（升序，含历史与未来）。锚点非法返回 emptyList。 */
    private fun calculateAdjustDates(anchor: String, holidaySet: Set<String>, workdaySet: Set<String>): List<String> {
        val yearStart = LocalDate.of(LocalDate.now().year, 1, 1)
        val yearEnd = LocalDate.of(LocalDate.now().year, 12, 31)
        val anchorDate = try { LocalDate.parse(anchor) } catch (e: Exception) { return emptyList() }
        fun isWorkday(d: LocalDate): Boolean {
            val s = d.toString()
            if (s in workdaySet) return true
            if (d.dayOfWeek.value >= 6) return false
            return s !in holidaySet
        }
        fun add10(from: LocalDate): LocalDate {
            var d = from.plusDays(1); var n = 0
            while (true) { if (isWorkday(d)) n++; if (n == 10) return d; d = d.plusDays(1) }
        }
        fun sub10(from: LocalDate): LocalDate {
            var d = from.minusDays(1); var n = 0
            while (true) { if (isWorkday(d)) n++; if (n == 10) return d; d = d.minusDays(1) }
        }
        val result = sortedSetOf<String>()
        // 向后顺推（含锚点本身若在本年）
        var cur = anchorDate
        if (!cur.isBefore(yearStart) && !cur.isAfter(yearEnd)) result.add(cur.toString())
        while (true) { cur = add10(cur); if (cur.isAfter(yearEnd)) break; result.add(cur.toString()) }
        // 向前倒推
        cur = anchorDate
        while (true) { cur = sub10(cur); if (cur.isBefore(yearStart)) break; result.add(cur.toString()) }
        return result.toList()
    }

    /** 归并：把 abapi 滞后日期校正到「≤ 它的最近权威窗口日」；窗口日为空或找不到时保留原日期（降级） */
    private fun adjustToWindow(date: String, adjustDates: List<String>): String {
        if (adjustDates.isEmpty()) return date
        var target: String? = null
        for (w in adjustDates) {
            if (w <= date) target = w else break
        }
        return target ?: date
    }

    /** 实际生效交易日 = 权威窗口日 24 时起 → 次日（窗口日 + 1 天） */
    private fun addOneDay(date: String): String {
        return try { LocalDate.parse(date).plusDays(1).toString() } catch (e: Exception) { date }
    }

    /**
     * 幂等清理假生效日：删除 effectiveDate 不属于任何"窗口日+1"的孤立记录。
     * 来源：窗口日历为空时的降级路径曾把"数据源原日期+1"当生效日写入
     *（如 abapi 点 09-26 → 09-27），若不清除，库内最新生效日与 DISTINCT 日期数都会被污染。
     */
    private suspend fun purgeIllegalEffectiveDates(dates: List<String>) {
        val legal = dates.map { addOneDay(it) }.toSet()
        // 只清「当年窗口日历范围内的假日期」：生效日在当年 1-1 ~ 12-31 且不在合法窗口+1 集合
        // （如降级灌入的 09-27）；往年日期一律保留 —— 灌库是一次性起点 + 持续增量模型，
        // 2026 起点灌库后跨年（2027）不重灌、不清库，历史记录持续累加。
        val yearStart = "${LocalDate.now().year}-01-01"
        val yearEnd = "${LocalDate.now().year}-12-31"
        val badDates = priceDao.distinctEffectiveDates().filter {
            it in yearStart..yearEnd && it !in legal
        }
        if (badDates.isNotEmpty()) {
            priceDao.getAll().filter { it.effectiveDate in badDates }
                .forEach { priceDao.deleteByKey(it.province, it.grade, it.effectiveDate) }
        }
    }

    /** 公开入口：按已缓存的权威窗口日历清理库内孤立假生效日（幂等；窗口日历为空时跳过）。
     *  供设置页打开/刷新状态时即时调用，避免假日期残留影响"价格维持日"与记录数展示。 */
    suspend fun purgeIllegalEffectiveDatesIfReady() {
        val dates = settings.priceDbAdjustDates.first()
            .split(",").map { it.trim() }.filter { it.isNotBlank() }
        if (dates.isNotEmpty()) purgeIllegalEffectiveDates(dates)
    }

    // ===== 配置读写（对齐 Web getConfig/setConfig）=====

    private suspend fun getLastCheckAt(): Long = settings.priceDbLastCheckAt.first()

    private suspend fun getLastUpdatedAt(): String = settings.priceDbLastUpdateDate.first()

    private suspend fun getInitAt(): Long = settings.priceDbInitAt.first()

    private suspend fun getAutoEnabled(): Boolean = settings.priceDbAutoUpdate.first()

    // ===== 主入口：checkAndUpdate(force) =====

    suspend fun checkAndUpdate(force: Boolean = false): OilPriceSyncResult {
        if (!checking.compareAndSet(false, true)) {
            return OilPriceSyncResult(
                success = false,
                message = "正在更新中，请稍后再试",
                pendingCount = pendingDao.countPending(),
                latestDate = getLastUpdatedAt().ifBlank { null },
            )
        }
        return try {
            val now = System.currentTimeMillis()

            if (!force && !getAutoEnabled()) {
                return OilPriceSyncResult(
                    success = true, message = "自动更新已关闭，跳过检测",
                    pendingCount = pendingDao.countPending(),
                    latestDate = getLastUpdatedAt().ifBlank { null },
                )
            }
            if (!force) {
                val lastCheck = getLastCheckAt()
                if (now - lastCheck < CHECK_INTERVAL_MS) {
                    return OilPriceSyncResult(
                        success = true, message = "距上次检查不足 8 小时，跳过",
                        pendingCount = pendingDao.countPending(),
                        latestDate = getLastUpdatedAt().ifBlank { null },
                    )
                }
            }

            ensureInitConfig()

            val result = performPriceCheck()
            // 窗口日（v1.5.21：apizero 预测专接口已移除，不再有 result.nextAdjustDate；
            // 仅聚合接口 next_adjustment 文本解析 → 本地推算窗口日历下个未来窗口兜底）
            val nextRaw = result.nextAdjustment?.takeIf { it.isNotBlank() }
                ?.let { OilPriceDateUtil.normalize(it) }
                ?: getAdjustDates().firstOrNull { it >= LocalDate.now().format(DATE_FMT) }
            if (nextRaw != null) {
                val newNext = OilPriceDateUtil.normalize(nextRaw)
                if (newNext != null) {
                    val today = LocalDate.now().format(DATE_FMT)
                    if (newNext < today) {
                        // 接口预告已过期（检测延迟/接口缓存旧值）：该窗口已过 → 直接作为 prev 存档，
                        // 避免"首次同步即拿到过期窗口 → prev 永久缺失 → 版本号 V——"；
                        // 旧过期 next 由下一次未来预告触发 prev 升级自愈
                        val oldPrev = OilPriceDateUtil.normalize(settings.priceDbPrevNextAdjustment.first())
                        if (oldPrev == null || newNext > oldPrev) {
                            settings.setPriceDbPrevNextAdjustment(newNext)
                        }
                    } else {
                        // 接口窗口滚动存档：旧 next（下一次窗口）已过 → 升级为 prev（最近已过窗口）
                        val oldNext = OilPriceDateUtil.normalize(settings.priceDbNextAdjustment.first())
                        if (oldNext != null && oldNext < newNext) {
                            settings.setPriceDbPrevNextAdjustment(oldNext)
                        }
                        settings.setPriceDbNextAdjustment(newNext)
                    }
                }
            }
            settings.setPriceDbLastCheckAt(now)
            settings.setPriceDbCache(result.message)
            // v1.5.27：多源一致性快照（设置页展示）——锚点源在 getAdjustDates 内记录，今日价恒为 abapi 主源
            val anchorSrc = settings.priceDbLastAnchorSource.first().ifBlank { "—" }
            settings.setPriceDbLastSources("锚点:$anchorSrc · 今日价:abapi")
            if (result.newAdjustments > 0 && result.latestDate != null) {
                settings.setPriceDbLastUpdateDate(result.latestDate)
                settings.setPriceDbLastWindowChecked(result.latestDate)
            } else if (result.strandedWindowDate != null) {
                settings.setPriceDbLastWindowChecked(result.strandedWindowDate)
            }
            // 顺带补灌缺失省份一整年曲线（幂等：只拉库内尚未覆盖的省）：
            // 全新安装只灌了当前省/断网部分省失败的场景下，"立即更新"或定时自动更新
            // 完成后也会继续把全国数据补全，无需杀掉进程重启触发
            runCatching { seedRemainingProvinces() }
            result
        } catch (e: Exception) {
            // v1.5.26：失败不再写 lastCheckAt——原实现失败也打 8h 节流时间戳，
            // 一次瞬时失败会把自动检测锁死 8 小时（配合无重试的补灌链，用户只能手动点立即更新）；
            // 失败后保持旧时间戳，下次启动/周期 Worker 可立即重试，直到成功才写节流
            val message = "更新失败：${e.message ?: e.javaClass.simpleName}"
            settings.setPriceDbCache(message)
            OilPriceSyncResult(
                success = false,
                message = "油价检测失败",
                pendingCount = pendingDao.countPending(),
                latestDate = getLastUpdatedAt().ifBlank { null },
            )
        } finally {
            checking.set(false)
        }
    }

    /** 对齐 Web lazyInitIfEmpty：已有数据时补齐初始化/最新日期配置（油价库由 initPriceDb 联网灌库） */
    private suspend fun ensureInitConfig() {
        val total = priceDao.count()
        if (total > 0) {
            if (getInitAt() == 0L) {
                settings.setPriceDbInitAt(System.currentTimeMillis())
            }
            if (getLastUpdatedAt().isBlank()) {
                val latest = priceDao.maxDate()
                if (latest != null) settings.setPriceDbLastUpdateDate(latest)
            }
        }
    }

    // ===== 方案X：首次联网灌库（替代内置油价快照） =====

    /**
     * 首拉当前省一整年曲线灌库（仅 koil_prices 为空时执行，不覆盖老用户已有数据）。
     * 其余省份由 [seedRemainingProvinces] 在后台补全。无网或接口失败时静默跳过。
     */
    suspend fun initPriceDb() {
        if (priceDao.count() > 0) return
        val home = settings.defaultProvince.first().takeIf { it in PROVINCES } ?: PROVINCES[0]
        // 抓发改委当年权威窗口日（失败为空 → seedProvince 降级用 abapi 原日期）
        val adjustDates = getAdjustDates()
        seedProvince(home, adjustDates)
    }

    /**
     * 补全各省窗口日覆盖（幂等，REPLACE）。
     * 判定标准：**窗口日覆盖数 < 应有量则重灌该省**，而非"省是否已在库"——
     * 假生效日被清理后各省可能只剩零星记录却仍被判"已覆盖"，导致缺口永不补全。
     * 窗口日历未就绪时跳过（避免降级用 abapi 原日期再产生假日期）；单省失败只留缺口，不阻塞其他省。
     */
    suspend fun seedRemainingProvinces() {
        if (priceDao.count() == 0) return
        // 互斥：启动兜底与 checkAndUpdate 尾部可能同时触发，只允许一路执行
        if (!seedLock.compareAndSet(false, true)) return
        try {
            val adjustDates = getAdjustDates()
            if (adjustDates.isEmpty()) return
            // 期望覆盖已过窗口日数（含搁浅窗口，搁浅入库后各省应含维持记录）；
            // 每省独立拉取，失败不影响其他省
            // v1.5.21 B2：窗口日 24 点前不算已过 → 严格 < today（今天窗口日不计入期望覆盖）
            val threshold = adjustDates.count { it < LocalDate.now().format(DATE_FMT) }
            // v1.4.190：189+ 升级后首次立即更新强制全量补灌一次——v1.4.188 曾把已有调价记录
            // 误替换成搁浅（覆盖升级后 hasStrandedGap 检测不到"搁浅占位"），需强制跑 seedProvince
            // 让「变化点覆盖搁浅」恢复调价记录；一次后恢复常规触发条件。
            val forceReseed = !settings.priceDbReseed189.first()
            if (forceReseed) settings.setPriceDbReseed189(true)
            // 并发补灌：4 路并行（避免 31 省串行等待；组间 200ms 缓解 abapi 限速）
            coroutineScope {
                PROVINCES.chunked(4).forEach { chunk ->
                    chunk.map { province ->
                        async {
                            val covered = runCatching { priceDao.countDatesForProvince(province) }.getOrDefault(0)
                            // covered < threshold（窗口数不够）或存在搁浅缺口（窗口日+1 无记录/同价假记录占位）
                            // 都触发补灌——防止 verifyProvince 抢插的同价假记录让 covered 达标后搁浅永远缺失
                            if (covered < threshold || hasStrandedGap(province, adjustDates) || forceReseed) {
                                seedProvince(province, adjustDates)
                            }
                        }
                    }.awaitAll()
                    kotlinx.coroutines.delay(200)
                }
            }
        } finally {
            seedLock.set(false)
        }
    }

    /**
     * v1.5.26：窗口日历就绪则执行全国补灌，返回是否已执行。
     * 用于启动兜底的重试循环——重装/弱网下锚点链（xxapi→ruseo→abapi→发改委）启动瞬间失败时，
     * getAdjustDates() 为空 → seedRemainingProvinces 静默跳过，补灌永久等待，只有点「立即更新」(force) 才能绕过。
     * 本方法把"日历未就绪"变成可观测状态，让启动任务 30s 轮询重试直到就绪（保留防假日期纪律：就绪前不灌）。
     */
    suspend fun fillRemainingProvincesIfCalendarReady(): Boolean {
        if (getAdjustDates().isEmpty()) return false
        seedRemainingProvinces()
        return true
    }

    /**
     * 该省是否存在「搁浅缺口」：已过窗口日+1 无生效记录，或存在同价假记录
     * （isStranded=false 且价格与最近调价价相同——真实调价必然变价，同价且非搁浅 = 假记录）。
     * 用于补灌触发判定，避免 covered 达标后搁浅被假记录挡住而永久缺失。
     */
    private suspend fun hasStrandedGap(province: String, adjustDates: List<String>): Boolean {
        val today = LocalDate.now().format(DATE_FMT)
        val grades = OilPriceNetwork.GRADE_LABELS.values.toList()
        // v1.5.21 B2：窗口日 24 点前不算已过 → 严格 < today（今天窗口不判缺口，避免误触发补灌）
        return adjustDates.filter { it < today }.any { w ->
            val eff = runCatching { LocalDate.parse(w).plusDays(1).toString() }.getOrNull() ?: return@any false
            grades.any { key ->
                val e = priceDao.getPrice(province, key, eff)
                when {
                    e == null -> true
                    !e.isStranded -> {
                        val latest = priceDao.getLatestPrice(province, key, eff)
                        latest != null && kotlin.math.abs(latest.price - e.price) < 1e-9
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
        // 同价假记录清理：历史"窗口归档"遗留的 isStranded=false 同价记录（如 10-16 ¥8.63 与
        // 09-25 相同——真实调价必然变价，同价且非搁浅 = 假变化点）→ 删除，让补搁浅幂等键放行。
        // 幂等：假记录删一次后不再存在，重复补灌不重复删。
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
        } catch (_: Exception) {
        }
        val points = network.fetchYoujiaHistory(province)
        if (points.isEmpty()) return 0
        val today = LocalDate.now().format(DATE_FMT)
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
            // v1.5.21 B8 写入闸：生效日 > 今天（窗口日当天数据源若提前放次日新价）一律跳过，
            // 等次日归并自然写入——库里永不出现"未来生效"记录
            if (eff > today) return@forEach
            pt.p92?.let { v -> if (last92 == null || kotlin.math.abs(v - last92!!) > 1e-9) {
                val key = "$province|92号汽油|$eff"
                if (key !in existing) entities.add(KOiLPriceEntity(province, "92号汽油", eff, v, "youjia.abapi.cn range=year 归并发改委窗口", false))
                else {
                    // v1.4.189：库里该生效日是搁浅记录（isStranded=true）但 abapi 该窗口有变化
                    // → 删除搁浅 + 插变化点（恢复被 v1.4.188 误替换成搁浅的调价记录）
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
        // —— 补搁浅窗口（v1.4.181 搁浅入库）——
        // 已过窗口 + 该窗口无生效记录 = 搁浅：插维持价记录（isStranded=true）。
        // 维持价 = abapi 曲线中 ≤ 窗口日的最后价格（首窗口搁浅（如 1-06）时 = 上年末价，
        // 即 abapi 当年首点 1-01，落在 yearStart..w 内）；幂等：跳过库内已有与本次变化点。
        // v1.5.21 B1：窗口日 24 点前不算已过 → 严格 < today（今天窗口日不提前补搁浅，
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
            // 补搁浅（v1.4.189 回滚）：只补「库里无记录」的已过窗口（hasChange 防本次新增变化点重复补）；
            // 已有的记录（调价或搁浅）一律不删不动——v1.4.188 曾误删已有调价记录导致全搁浅。
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
        if (entities.isEmpty()) return 0
        entities.chunked(500).forEach { priceDao.insertAll(it) }
        return entities.size
    }

    // ===== 调价检测与全量核验 =====

    private suspend fun performPriceCheck(): OilPriceSyncResult {
        if (PROVINCES.isEmpty()) {
            return OilPriceSyncResult(success = false, message = "省份列表为空")
        }

        // 采样省份：默认省份（用户相关），缺省回退列表第一个
        val sampleProvince = settings.defaultProvince.first().takeIf { it in PROVINCES } ?: PROVINCES[0]
        val (samplePrimary, sampleSecondary) = network.fetchBothSources(sampleProvince)

        if (!samplePrimary.success && !sampleSecondary.success) {
            return OilPriceSyncResult(
                success = false,
                message = samplePrimary.error ?: sampleSecondary.error ?: "实时油价获取失败",
                latestDate = getLastUpdatedAt().ifBlank { null },
            )
        }

        val today = LocalDate.now().format(DATE_FMT)
        val nextAdjustment = samplePrimary.nextAdjustment ?: sampleSecondary.nextAdjustment

        // 只信任"数据日期 == 今天"的源参与今日核验；主/次源均滞后时跳过核验（窗口存档仍生效，不产生假调价记录）
        val usableSources = listOf(samplePrimary, sampleSecondary)
            .filter { it.success && it.data.isNotEmpty() && it.updateDate == today }
        if (usableSources.isEmpty()) {
            val lagDate = (samplePrimary.updateDate ?: sampleSecondary.updateDate) ?: "未知"
            return OilPriceSyncResult(
                success = true,
                message = "数据源日期滞后（$lagDate），跳过本次核验",
                latestDate = getLastUpdatedAt().ifBlank { null },
                nextAdjustment = nextAdjustment,
            )
        }

        val sampleLive = if (samplePrimary.success && samplePrimary.data.isNotEmpty() && samplePrimary.updateDate == today) {
            samplePrimary
        } else {
            usableSources.first()
        }
        val sampleItem = sampleLive.data.first()

        val latestRow = priceDao.getNewest(sampleProvince, sampleItem.gradeLabel)
        val latestPrice = latestRow?.price
        val latestDate = latestRow?.effectiveDate

        // 搁浅判定：采样价与库内最新价差 < 0.005
        if (latestPrice != null && kotlin.math.abs(latestPrice - sampleItem.price) < STRANDED_EPSILON) {
            // v1.5.21 B3：今天若是窗口日（24 点后才生效/确认搁浅），白天即使价格未变也不下"搁浅"结论、
            // 不写 lastWindowChecked=今天——否则数据库卡当天就显示"V今日 擱淺"，等于把窗口日当 0 点生效
            if (getAdjustDates().contains(today)) {
                return OilPriceSyncResult(
                    success = true,
                    message = "今天是调价窗口日（$today），价格待 24 点后生效",
                    latestDate = latestDate,
                    nextAdjustment = nextAdjustment,
                )
            }
            // v1.5.23 D2：搁浅结论只在「窗口日次日」落账——非窗口日重复检查不再滚动 lastWindowChecked
            //（此前普通日检查也会把版本号滚成检查日，如 V20261031 而非真实窗口 V20261029）
            val lastWindow = getAdjustDates().filter { it < today }.lastOrNull()
            val strandedConfirmDay = lastWindow?.let { LocalDate.parse(it).plusDays(1).toString() }
            if (strandedConfirmDay != null && strandedConfirmDay == today) {
                return OilPriceSyncResult(
                    success = true,
                    message = "已核对至 $today，油价无变化（搁浅）",
                    latestDate = latestDate,
                    strandedWindowDate = today,
                    nextAdjustment = nextAdjustment,
                )
            }
            return OilPriceSyncResult(
                success = true,
                message = "已核对至 $today，油价无变化（非窗口日，正常维持）",
                latestDate = latestDate,
                nextAdjustment = nextAdjustment,
            )
        }

        // 今日已有调价记录
        if (latestDate == today) {
            return OilPriceSyncResult(
                success = true,
                message = "今日已有调价记录，跳过",
                latestDate = today,
                nextAdjustment = nextAdjustment,
            )
        }

        // 检测到变化：仅默认省（用户当前省）双源核验 → 一致入库 / 不一致入 pending（等待采纳）；
        // 其它省由历史灌库 seedProvince 覆盖（主源归并窗口日），不在此刻逐省双源核验，避免旧机制的假 pending 堆积。
        val (verifiedRecords, pendingRecords, failedProvinces) = verifyProvince(defaultProvince(), today)

        val hasNew = verifiedRecords > 0
        val parts = mutableListOf<String>()
        if (verifiedRecords > 0) parts.add("已入库 $verifiedRecords 条")
        if (pendingRecords > 0) parts.add("$pendingRecords 条待确认")
        if (failedProvinces > 0) parts.add("$failedProvinces 省失败")
        val message = if (parts.isNotEmpty()) parts.joinToString("、") else "无新记录追加"

        return OilPriceSyncResult(
            success = true,
            message = message,
            newAdjustments = if (hasNew) 1 else 0,
            newRecords = verifiedRecords,
            pendingCount = pendingRecords,
            latestDate = today,
            nextAdjustment = nextAdjustment,
        )
    }

    // ===== 采纳 / 忽略（对齐 Web acceptPending：事务）=====

    suspend fun acceptPending(id: Long, usePrimary: Boolean): Boolean {
        val item = pendingDao.getPending().find { it.id == id } ?: return false
        val price = if (usePrimary) item.primaryPrice else item.secondaryPrice
        val source = if (usePrimary) item.primarySource else item.secondarySource
        if (price == null) return false

        return try {
            db.withTransaction {
                // 当日同键已存在则跳过
                if (priceDao.getPrice(item.province, item.grade, item.effectiveDate) == null) {
                    priceDao.insert(
                        KOiLPriceEntity(
                            province = item.province,
                            grade = item.grade,
                            effectiveDate = item.effectiveDate,
                            price = price,
                            source = source,
                            isVerified = true,
                        )
                    )
                }
                pendingDao.resolve(id, "accepted", price, source, null)
            }
            settings.setPriceDbLastUpdateDate(item.effectiveDate)
            val lastWindow = settings.priceDbLastWindowChecked.first()
            if (lastWindow.isBlank() || item.effectiveDate > lastWindow) {
                settings.setPriceDbLastWindowChecked(item.effectiveDate)
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun ignorePending(id: Long) {
        pendingDao.resolve(id, "ignored", null, null, "用户忽略")
    }

    // ===== 偏差预检（对齐 Web checkPriceDeviation：当日历史参考价）=====

    suspend fun checkDeviation(
        province: String,
        grade: String,
        date: String,
        oilAmount: Double,
        actualAmount: Double,
    ): DeviationCheckResult {
        val threshold = settings.deviationThreshold.first()
        val actualUnitPrice = if (oilAmount > 0) actualAmount / oilAmount else 0.0

        if (province.isBlank() || grade.isBlank() || date.isBlank() || oilAmount <= 0 || actualAmount <= 0) {
            return DeviationCheckResult(
                hasDeviation = false,
                actualUnitPrice = actualUnitPrice,
                thresholdPercent = threshold,
            )
        }

        val gradeLabel = OilPriceNetwork.GRADE_LABELS[grade] ?: grade
        // 不晚于录入日期的历史参考价（对齐 Web getHistoryPrice lte date）
        val history = priceDao.getLatestPrice(province, gradeLabel, date) ?: return DeviationCheckResult(
            hasDeviation = false,
            actualUnitPrice = actualUnitPrice,
            thresholdPercent = threshold,
        )
        val referencePrice = history.price
        if (referencePrice <= 0) {
            return DeviationCheckResult(
                hasDeviation = false,
                actualUnitPrice = actualUnitPrice,
                referencePrice = referencePrice,
                referenceDate = history.effectiveDate,
                thresholdPercent = threshold,
            )
        }

        val latest = priceDao.getNewest(province, gradeLabel)
        val isLatestVerified = latest?.effectiveDate == history.effectiveDate
        val pendingAfter = latest != null && date > latest.effectiveDate

        val deviationPercent = kotlin.math.abs((actualUnitPrice - referencePrice) / referencePrice) * 100
        val hasDeviation = deviationPercent > threshold

        return DeviationCheckResult(
            hasDeviation = hasDeviation,
            actualUnitPrice = actualUnitPrice,
            referencePrice = referencePrice,
            referenceDate = history.effectiveDate,
            deviationPercent = deviationPercent,
            thresholdPercent = threshold,
            pendingAfter = pendingAfter,
            isLatestVerified = isLatestVerified,
        )
    }

    /**
     * 单省（默认省）单源核验，返回 (入库数, 待确认数, 失败标记)。
     * 生效日 = 归并窗口日 + 1（窗口日 24 时生效 → 次日实际生效）。
     * v1.5.0：apizero 已移除（v1.4.194），双源比对/pending 分支成为死代码删除——
     * 现为 abapi 单源：有价即自动入库；搁浅窗口由 seedRemainingProvinces 补搁浅处理。
     */
    private suspend fun verifyProvince(province: String, today: String): Triple<Int, Int, Int> {
        return try {
            val (primary, _) = network.fetchBothSources(province)
            val primaryMap = primary.data.associateBy { it.grade }
            if (primaryMap.isEmpty()) return Triple(0, 0, 1)
            // v1.5.21 B5：生效日 = 归并窗口日 + 1；窗口日历未就绪（从未成功推算）时跳过本次增量入库
            //（宁缺毋滥，交给 seedProvince 主路径幂等补灌，杜绝"窗口日当天以 today 为生效日"的降级假记录）
            val adjustDates = getAdjustDates()
            if (adjustDates.isEmpty()) return Triple(0, 0, 0)
            val eff = addOneDay(adjustToWindow(today, adjustDates))
            // v1.5.36 P8：只在「今天 == 窗口日次日（真正生效日）」才允许增量入库——
            // 非窗口日价格抖动（数据源四舍五入/波动）不再写"上一窗口生效日"假变化点污染
            // 调价历史/涨跌对比/历史价；窗口日当天（eff > today，B8）与其它日子（eff < today）一律跳过，
            // 真实调价由次日 checkAndUpdate/灌库自然写入。
            if (eff != today) return Triple(0, 0, 0)

            val toInsert = mutableListOf<KOiLPriceEntity>()
            var verified = 0

            for ((grade, pItem) in primaryMap) {
                val gradeLabel = OilPriceNetwork.GRADE_LABELS[grade] ?: grade
                val pPrice = pItem.price
                val pSource = pItem.source.ifBlank { "youjia.abapi.cn" }
                // 单源有价即自动入库（真实调价必然变价；同价非搁浅假点由 purge 清理）；pPrice 已由 PrimaryPrice 保证非空
                val latest = priceDao.getLatestPrice(province, gradeLabel, eff)
                if ((latest == null || kotlin.math.abs(latest.price - pPrice) > 1e-9) &&
                    priceDao.getPrice(province, gradeLabel, eff) == null
                ) {
                    toInsert.add(KOiLPriceEntity(province, gradeLabel, eff, pPrice, pSource, true))
                }
                verified++
            }

            if (toInsert.isNotEmpty()) priceDao.insertAll(toInsert)
            Triple(verified, 0, 0)
        } catch (_: Exception) {
            Triple(0, 0, 1)
        }
    }

    // ===== 调价通知（v1.5.22：从 OilPriceSyncWorker 抽出，Worker 与设置页「立即更新」共用；
    //     独立于同步成功与否——网络挂时窗口提醒也不丢）=====

    suspend fun notifyPriceChanges(context: Context) {
        if (!settings.pricePush.first()) return

        // 1) 调价生效通知：默认省 + 默认油号的最新生效日变化。
        // v1.5.22 修复：仅价格实际变化（prev==null 或 prev.price != newest.price）才推送——
        // 搁浅窗口（维持价）不再误发"调价至同价"通知；价格未变只锚定 lastNotified，
        // 后续真调价仍正常推送（防旧锚点干扰）。
        val province = defaultProvince()
        val gradeCode = settings.defaultOilGrade.first()
            .takeIf { it in OilPriceNetwork.GRADE_LABELS } ?: "92"
        val grade = OilPriceNetwork.GRADE_LABELS[gradeCode] ?: gradeCode
        val newest = priceDao.getLatestPrice(province, grade, LocalDate.now().format(DATE_FMT))
        if (newest != null) {
            val lastNotified = settings.priceLastNotifiedDate.first()
            if (lastNotified.isBlank()) {
                // 首次启用：静默锚定当前生效日，不打扰
                settings.setPriceLastNotifiedDate(newest.effectiveDate)
            } else if (newest.effectiveDate != lastNotified) {
                val prev = priceDao.getPrevious(province, grade)
                val priceChanged = prev == null || prev.price != newest.price
                if (priceChanged) {
                    val gradeLabel = OilPriceNetwork.GRADE_LABELS[grade] ?: grade
                    val title = context.getString(R.string.koil_noti_title_price_adjust)
                    val message = if (prev != null && prev.effectiveDate < newest.effectiveDate) {
                        context.getString(
                            R.string.koil_noti_msg_price_adjust,
                            province, gradeLabel, prev.price, newest.price,
                        )
                    } else {
                        context.getString(
                            R.string.koil_noti_msg_price_adjust,
                            province, gradeLabel, newest.price, newest.price,
                        )
                    }
                    OilPriceNotifier.notify(context, title, message)
                }
                settings.setPriceLastNotifiedDate(newest.effectiveDate)
            }
        }

        // 2) 窗口日前一天提醒：下一个未过期窗口 == 明天
        val adjustDates = settings.priceDbAdjustDates.first()
            .split(",").map { it.trim() }.filter { it.isNotBlank() }
        if (adjustDates.isNotEmpty()) {
            val today = LocalDate.now()
            val tomorrow = today.plusDays(1).toString()
            val next = adjustDates.firstOrNull { it >= today.toString() }
            val reminded = settings.priceLastWindowReminded.first()
            if (next == tomorrow && reminded != next) {
                OilPriceNotifier.notify(
                    context,
                    context.getString(R.string.koil_noti_title_window_tomorrow),
                    context.getString(R.string.koil_noti_msg_window_tomorrow, next),
                    OilPriceNotifier.NOTIFICATION_ID_WINDOW_REMINDER,   // v1.5.23 D3：独立 ID 不覆盖调价通知
                )
                settings.setPriceLastWindowReminded(next)
            }

            // 3) 窗口日当天提醒（v1.5.27）：今天 == 窗口日 → "今晚 24 时生效"
            // 触发点：一次性 Worker（窗口日 00:05 精准）+ 启动/8h 周期兜底（notifyPriceChanges 幂等去重）
            val todayStr = today.toString()
            if (adjustDates.contains(todayStr)) {
                val remindedDay = settings.priceLastWindowDayReminded.first()
                if (remindedDay != todayStr) {
                    OilPriceNotifier.notify(
                        context,
                        context.getString(R.string.koil_noti_title_window_today),
                        context.getString(R.string.koil_noti_msg_window_today, todayStr),
                        OilPriceNotifier.NOTIFICATION_ID_WINDOW_DAY,   // v1.5.27：独立 ID
                    )
                    settings.setPriceLastWindowDayReminded(todayStr)
                }
            }
        }
    }

    // ===== v1.5.27：窗口日当天提醒调度 =====

    /**
     * 为下一个窗口日调度"当天 00:05"的一次性提醒 Worker（REPLACE 策略：日历更新/重复调度覆盖旧任务）。
     * 窗口日当天已过 00:05（如当天启动）→ delay=0 立即执行（Worker 内幂等，已提醒则跳过）。
     * 日历为空（从未同步成功）时跳过——首次同步成功后由启动链/周期 Worker 重新调度。
     */
    suspend fun scheduleWindowDayReminder(context: Context) {
        val adjustDates = settings.priceDbAdjustDates.first()
            .split(",").map { it.trim() }.filter { it.isNotBlank() }
        val next = adjustDates.firstOrNull { it > LocalDate.now().format(DATE_FMT) } ?: return
        val delayMs = runCatching {
            val target = LocalDate.parse(next).atTime(0, 5)
                .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
            (target - System.currentTimeMillis()).coerceAtLeast(0L)
        }.getOrNull() ?: return
        val request = androidx.work.OneTimeWorkRequestBuilder<OilPriceWindowDayWorker>()
            .setInitialDelay(delayMs, java.util.concurrent.TimeUnit.MILLISECONDS)
            .build()
        androidx.work.WorkManager.getInstance(context).enqueueUniqueWork(
            "KOiL_window_day_reminder",
            androidx.work.ExistingWorkPolicy.REPLACE,
            request,
        )
    }
}
