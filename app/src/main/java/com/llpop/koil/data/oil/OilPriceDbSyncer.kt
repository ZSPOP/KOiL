package com.llpop.koil.data.oil

import androidx.room.withTransaction
import com.llpop.koil.data.local.AppDatabase
import com.llpop.koil.data.local.dao.FuelPriceDao
import com.llpop.koil.data.local.dao.KOiLPricePendingDao
import com.llpop.koil.data.local.entity.FuelPriceEntity
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
 */
class OilPriceDbSyncer(
    private val db: AppDatabase,
    private val priceDao: FuelPriceDao,
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
     * 获取当年权威窗口日（升序，含历史与未来）。
     * 锚点 = 次源 next_adjust_date；节假日 = chinese-days（放假日 + 调休补班日）；
     * 规则：每 10 个工作日一个窗口、24 时生效、遇节假日顺延、调休补班算工作日。
     * 从锚点向前倒推回溯当年首调、向后顺推至当年末。缓存复用；失败返回 emptyList（降级用 abapi 原日期）。
     */
    private suspend fun getAdjustDates(): List<String> {
        // 1) 内存缓存优先
        cachedAdjustDates?.let { return it }
        // 2) 持久化缓存（App 重启后复用上次已推算的窗口日，杜绝次源临时失败导致空降级 → 假生效日）
        val persisted = settings.priceDbAdjustDates.first().let { s ->
            if (s.isBlank()) emptyList() else s.split(",").map { it.trim() }.filter { it.isNotBlank() }
        }
        if (persisted.isNotEmpty()) {
            cachedAdjustDates = persisted
            // 缓存命中时也执行假生效日清理（此前清理只在推算分支执行，缓存命中会跳过 → 降级假日期残留）
            purgeIllegalEffectiveDates(persisted)
            return persisted
        }
        // 3) 拉取推算（仅当无任何缓存时）
        val anchor = network.fetchForecast(defaultProvince()).nextAdjustDate
        if (anchor == null) {
            // 从未成功推算且次源失败 → 空降级（seedProvince 用 abapi 原日期）
            return emptyList()
        }
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
        val badDates = priceDao.distinctEffectiveDates().filter { it !in legal }
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
            // 窗口日：结构化日期（预测专接口 yyyy-MM-dd，无需再解析）优先，回退聚合接口文本解析
            val nextRaw = result.nextAdjustDate
                ?: result.nextAdjustment?.takeIf { it.isNotBlank() }
            if (nextRaw != null) {
                val newNext = if (result.nextAdjustDate != null) {
                    // 结构化日期已是 yyyy-MM-dd，直接规范化（防御性保留 normalize）
                    OilPriceDateUtil.normalize(nextRaw)
                } else {
                    // 文本解析：兼容 "2026-10-29" / "2026/10/29" / "10月29日" 等多种格式
                    OilPriceDateUtil.normalize(nextRaw)
                }
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
            result.forecast?.takeIf { it.isNotBlank() }?.let { settings.setPriceDbForecast(it) }
            settings.setPriceDbLastCheckAt(now)
            settings.setPriceDbCache(result.message)
            if (result.newAdjustments > 0 && result.latestDate != null) {
                settings.setPriceDbLastUpdateDate(result.latestDate!!)
                settings.setPriceDbLastWindowChecked(result.latestDate!!)
            } else if (result.strandedWindowDate != null) {
                settings.setPriceDbLastWindowChecked(result.strandedWindowDate!!)
            }
            // 顺带补灌缺失省份一整年曲线（幂等：只拉库内尚未覆盖的省）：
            // 全新安装只灌了当前省/断网部分省失败的场景下，"立即更新"或定时自动更新
            // 完成后也会继续把全国数据补全，无需杀掉进程重启触发
            runCatching { seedRemainingProvinces() }
            result
        } catch (e: Exception) {
            settings.setPriceDbLastCheckAt(System.currentTimeMillis())
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

    /** 对齐 Web lazyInitIfEmpty：已有数据时补齐初始化/最新日期配置（种子由 SeedInitializer 负责灌入） */
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
     * 首拉当前省一整年曲线灌库（仅 fuel_prices 为空时执行，不覆盖老用户已有数据）。
     * 其余省份由 [seedRemainingProvinces] 在后台补全。无网或接口失败时静默跳过。
     */
    suspend fun initPriceDb() {
        if (priceDao.count() > 0) return
        val home = settings.defaultProvince.first().takeIf { it in PROVINCES } ?: PROVINCES[0]
        // 抓发改委当年权威窗口日（失败为空 → seedProvince 降级用 abapi 原日期）
        val adjustDates = getAdjustDates()
        seedProvince(home, adjustDates)
    }

    /** 后台补全缺失省份一整年曲线（幂等：只拉库内尚未覆盖的省，全部覆盖后直接跳过——根治每次冷启动重复联网灌库） */
    suspend fun seedRemainingProvinces() {
        if (priceDao.count() == 0) return
        // 互斥：启动兜底与 checkAndUpdate 尾部可能同时触发，只允许一路执行
        if (!seedLock.compareAndSet(false, true)) return
        try {
            val seeded = priceDao.distinctProvinces().toSet()
            val missing = PROVINCES.filter { it !in seeded }
            if (missing.isEmpty()) return
            val skip = settings.defaultProvince.first().takeIf { it in PROVINCES } ?: PROVINCES[0]
            val adjustDates = getAdjustDates()
            missing.forEach { province ->
                seedProvince(province, adjustDates)
                kotlinx.coroutines.delay(200)
            }
        } finally {
            seedLock.set(false)
        }
    }

    /**
     * 拉单个省当年（1-1 至今）曲线并写入 fuel_prices（主键 province+grade+effectiveDate REPLACE），返回写入条数。
     * 去重压缩：仅存"价格有变化"的点（调价日）。偏差预检 getLatestPrice(≤date 最近价) 会回退到最近调价日价，
     * 非调价日沿用上次价的语义等价于逐日全存，故跳过重复价日可瘦身约 90% 且功能不变。
     * 生效日校正：传入 [adjustDates]（发改委权威窗口日）时，把 abapi 滞后日期归并到「≤它的最近窗口日」，
     * 使数据库生效日对齐真实调价日（9-27→9-24）；[adjustDates] 为空则降级用 abapi 原日期。
     */
    private suspend fun seedProvince(province: String, adjustDates: List<String> = emptyList()): Int {
        val points = network.fetchYoujiaHistory(province)
        if (points.isEmpty()) return 0
        val today = LocalDate.now().format(DATE_FMT)
        val yearStart = "${LocalDate.now().year}-01-01"
        val entities = mutableListOf<FuelPriceEntity>()
        var last92: Double? = null
        var last95: Double? = null
        var last98: Double? = null
        var last0: Double? = null
        points.forEach { pt ->
            if (pt.date.isBlank()) return@forEach
            // 只保留当年 1-1 至今（用户口径：首拉当年调价记录）
            if (pt.date < yearStart || pt.date > today) return@forEach
            val eff = if (adjustDates.isEmpty()) pt.date else addOneDay(adjustToWindow(pt.date, adjustDates))
            pt.p92?.let { v -> if (last92 == null || kotlin.math.abs(v - last92!!) > 1e-9) { entities.add(FuelPriceEntity(province, "92号汽油", eff, v, "youjia.abapi.cn range=year 归并发改委窗口", false)); last92 = v } }
            pt.p95?.let { v -> if (last95 == null || kotlin.math.abs(v - last95!!) > 1e-9) { entities.add(FuelPriceEntity(province, "95号汽油", eff, v, "youjia.abapi.cn range=year 归并发改委窗口", false)); last95 = v } }
            pt.p98?.let { v -> if (last98 == null || kotlin.math.abs(v - last98!!) > 1e-9) { entities.add(FuelPriceEntity(province, "98号汽油", eff, v, "youjia.abapi.cn range=year 归并发改委窗口", false)); last98 = v } }
            pt.p0?.let { v -> if (last0 == null || kotlin.math.abs(v - last0!!) > 1e-9) { entities.add(FuelPriceEntity(province, "0号柴油", eff, v, "youjia.abapi.cn range=year 归并发改委窗口", false)); last0 = v } }
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
        // 预测专接口（结构化窗口日）：与双源并行；失败不影响主流程，nextAdjustDate 回退文本解析
        val forecastResult = network.fetchForecast(sampleProvince)

        if (!samplePrimary.success && !sampleSecondary.success) {
            return OilPriceSyncResult(
                success = false,
                message = samplePrimary.error ?: sampleSecondary.error ?: "实时油价获取失败",
                latestDate = getLastUpdatedAt().ifBlank { null },
                nextAdjustDate = forecastResult.nextAdjustDate,
            )
        }

        val today = LocalDate.now().format(DATE_FMT)
        val nextAdjustment = samplePrimary.nextAdjustment ?: sampleSecondary.nextAdjustment
        val forecast = samplePrimary.forecast ?: sampleSecondary.forecast
        // 结构化窗口日优先（预测专接口 yyyy-MM-dd），缺失时回退聚合接口文本解析
        val nextAdjustDate = forecastResult.nextAdjustDate
            ?: OilPriceDateUtil.normalize(nextAdjustment)

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
                forecast = forecast,
                nextAdjustDate = nextAdjustDate,
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
            return OilPriceSyncResult(
                success = true,
                message = "已核对至 $today，油价无变化（搁浅）",
                latestDate = latestDate,
                strandedWindowDate = today,
                nextAdjustment = nextAdjustment,
                forecast = forecast,
                nextAdjustDate = nextAdjustDate,
            )
        }

        // 今日已有调价记录
        if (latestDate == today) {
            return OilPriceSyncResult(
                success = true,
                message = "今日已有调价记录，跳过",
                latestDate = today,
                nextAdjustment = nextAdjustment,
                forecast = forecast,
                nextAdjustDate = nextAdjustDate,
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
            forecast = forecast,
            nextAdjustDate = nextAdjustDate,
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
                        FuelPriceEntity(
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
     * 单省（默认省）双源核验，返回 (入库数, 待确认数, 失败标记)。
     * 生效日 = 归并窗口日 + 1（窗口日 24 时生效 → 次日实际生效）。
     * 双源一致 → 入库；双源均有但不一致 → 入/更新 pending（等待用户采纳，一直挂着，不自动处理）；
     * 单源缺失（一源失效）→ 用有价的源自动入库（不提示）。
     */
    private suspend fun verifyProvince(province: String, today: String): Triple<Int, Int, Int> {
        return try {
            val (primary, secondary) = network.fetchBothSources(province)
            val primaryMap = primary.data.associateBy { it.grade }
            val secondaryMap = secondary.data.associateBy { it.grade }
            val allGrades = (primaryMap.keys + secondaryMap.keys).toSet()
            if (allGrades.isEmpty()) return Triple(0, 0, 1)
            // 生效日 = 归并窗口日 + 1；窗口日为空（从未成功推算）时用 today 原值，避免"today+1"假生效日
            val eff = getAdjustDates().let { if (it.isEmpty()) today else addOneDay(adjustToWindow(today, it)) }

            val toInsert = mutableListOf<FuelPriceEntity>()
            val toPending = mutableListOf<KOiLPricePendingEntity>()
            var verified = 0
            var pending = 0

            for (grade in allGrades) {
                val pItem = primaryMap[grade]
                val sItem = secondaryMap[grade]
                val gradeLabel = OilPriceNetwork.GRADE_LABELS[grade] ?: grade
                val pPrice = pItem?.price
                val sPrice = sItem?.price
                val pSource = pItem?.source ?: "youjia.abapi.cn"
                val sSource = sItem?.source ?: "v1.apizero.cn"

                val bothPresent = pPrice != null && sPrice != null
                val matches = bothPresent && kotlin.math.abs(pPrice!! - sPrice!!) < STRANDED_EPSILON

                when {
                    matches -> {
                        // 双源一致：自动入库
                        if (priceDao.getPrice(province, gradeLabel, eff) == null) {
                            toInsert.add(FuelPriceEntity(province, gradeLabel, eff, pPrice!!, pSource, true))
                        }
                        verified++
                    }
                    bothPresent -> {
                        // 双源均有但不一致：入 pending 等待用户采纳（一直挂着，不自动处理）
                        val existing = pendingDao.getPendingByKey(province, gradeLabel, eff)
                        if (existing != null) {
                            pendingDao.updateSources(existing.id, pPrice, pSource, sPrice, sSource)
                        } else {
                            toPending.add(
                                KOiLPricePendingEntity(
                                    province = province, grade = gradeLabel, effectiveDate = eff,
                                    primaryPrice = pPrice, primarySource = pSource,
                                    secondaryPrice = sPrice, secondarySource = sSource,
                                    status = "pending", resolvedPrice = null, resolvedSource = null,
                                    note = null, createdAt = System.currentTimeMillis(),
                                )
                            )
                        }
                        pending++
                    }
                    else -> {
                        // 单源缺失（一源失效）：用有价的源自动入库，不提示
                        val price = pPrice ?: sPrice
                        val source = if (pPrice != null) pSource else sSource
                        if (price != null && priceDao.getPrice(province, gradeLabel, eff) == null) {
                            toInsert.add(FuelPriceEntity(province, gradeLabel, eff, price, source, true))
                        }
                        verified++
                    }
                }
            }

            if (toInsert.isNotEmpty()) priceDao.insertAll(toInsert)
            if (toPending.isNotEmpty()) pendingDao.insertAll(toPending)
            Triple(verified, pending, 0)
        } catch (_: Exception) {
            Triple(0, 0, 1)
        }
    }
}
