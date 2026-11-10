package com.llpop.koil.data.oil

import android.content.Context
import android.util.Log
import com.llpop.koil.data.local.AppDatabase
import com.llpop.koil.data.local.dao.KOiLPriceDao
import com.llpop.koil.data.local.entity.KOiLPriceEntity
import com.llpop.koil.data.settings.AppSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 油价库同步服务（对齐 Web 端 oil-price-db.service.ts）：
 * - checkAndUpdate：主源采样 → 搁浅判定(0.005) → 31省逐省补灌
 * - 8 小时节流 + 自动更新开关 + 防并发
 * - checkDeviation：当日历史参考价偏差预检
 *
 * 内部职责拆分（M-09，公共 API 不变）：
 * - [OilPriceAdjustCalendar]：窗口日历（锚点链 + 本地推算 + 假生效日清理）
 * - [OilPriceSeeder]：灌库与补灌重试循环
 * - [OilPriceChangeNotifier]：调价/窗口提醒通知与调度
 * - 共享工具（归并/换算/默认省）：OilPriceCommon.kt
 *
 * 完整机制见 docs/KOiL油价数据库机制.md。
 */
class OilPriceDbSyncer(
    private val db: AppDatabase,
    private val priceDao: KOiLPriceDao,
    private val settings: AppSettings,
    private val network: OilPriceNetwork,
) {

    companion object {
        const val CHECK_INTERVAL_MS = 8L * 60 * 60 * 1000
        const val STRANDED_EPSILON = 0.005
        val PROVINCES = Provinces.ALL

        /**
         * 只有成功的检测才允许写 8h 节流时间戳。
         * performPriceCheck 双源失败时返回 success=false 而不是抛异常，调用点必须显式判断。
         */
        internal fun shouldWriteLastCheckAt(result: OilPriceSyncResult): Boolean = result.success
    }

    private val checking = AtomicBoolean(false)

    // ===== 内部协作者（M-09 拆分；构造顺序：日历 → 灌库 → 通知） =====

    private val calendar = OilPriceAdjustCalendar(network, settings, priceDao, db)
    private val seeder = OilPriceSeeder(db, priceDao, settings, network, calendar)
    private val notifier = OilPriceChangeNotifier(settings, priceDao)

    /**
     * 全国补灌状态。
     * 设置页据此区分「真的在动」与「已经放弃」——若只有一句恒定的「后台补灌中，完成后自动刷新」，
     * 而重试预算用尽后其实什么都没在跑，等于误导用户干等。
     */
    enum class SeedState {
        /** 未在补灌：无缺口，或一次预算都没用完 */
        IDLE,
        /** 窗口日历未就绪，重试循环还在等（防假生效日，就绪前不能灌） */
        WAITING,
        /** 正在逐省补灌 */
        RUNNING,
        /** 重试预算用尽 / 手动触发失败，需「立即更新」或回前台兜底再试 */
        GAVE_UP,
    }

    // ===== 补灌状态透出（实现移至 OilPriceSeeder，公共 API 不变） =====

    val seedState: SeedState get() = seeder.seedState

    /** 本轮已成功处理的省份数（含"已覆盖直接跳过"的省），驱动设置页 N/31 进度 */
    val seedDone: Int get() = seeder.seedDone

    /** 本轮补灌失败的省份数 / 名单（弱网首装可见性，见 OilPriceSeeder.seedFailedProvinces） */
    val seedFailedCount: Int get() = seeder.seedFailedCount
    val seedFailed: List<String> get() = seeder.seedFailed

    val seedTotal: Int get() = seeder.seedTotal

    private suspend fun defaultProvince(): String = settings.koilDefaultProvince()

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
                latestDate = getLastUpdatedAt().ifBlank { null },
            )
        }
        return try {
            val now = System.currentTimeMillis()

            if (!force && !getAutoEnabled()) {
                return OilPriceSyncResult(
                    success = true, message = "自动更新已关闭，跳过检测",
                        latestDate = getLastUpdatedAt().ifBlank { null },
                )
            }
            if (!force) {
                val lastCheck = getLastCheckAt()
                if (now - lastCheck < CHECK_INTERVAL_MS) {
                    return OilPriceSyncResult(
                        success = true, message = "距上次检查不足 8 小时，跳过",
                                latestDate = getLastUpdatedAt().ifBlank { null },
                    )
                }
            }

            ensureInitConfig()

            val result = performPriceCheck()
            // 窗口日（apizero 预测专接口不可用，无 result.nextAdjustDate；
            // 仅聚合接口 next_adjustment 文本解析 → 本地推算窗口日历下个未来窗口兜底）
            val nextRaw = result.nextAdjustment?.takeIf { it.isNotBlank() }
                ?.let { OilPriceDateUtil.normalize(it) }
                ?: calendar.getAdjustDates().firstOrNull { it >= LocalDate.now().format(KOIL_DATE_FMT) }
            if (nextRaw != null) {
                val newNext = OilPriceDateUtil.normalize(nextRaw)
                if (newNext != null) {
                    val today = LocalDate.now().format(KOIL_DATE_FMT)
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
            // 只有真正成功的检测才写 8h 节流时间戳。performPriceCheck 在双源均失败时
            // 不抛异常、而是返回 success=false；若无条件写，会让一次瞬时失败
            // 被下一次 Worker 当作“不足 8 小时”跳过，自动更新被锁 8 小时。
            if (shouldWriteLastCheckAt(result)) {
                settings.setPriceDbLastCheckAt(now)
            }
            settings.setPriceDbCache(result.message)
            // 多源一致性快照（设置页展示）——锚点源在 getAdjustDates 内记录，今日价恒为 abapi 主源
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
            // 补灌是"顺带"动作，失败不该让本次检测失败；但取消必须继续向上传，
            // 否则 runCatching 会把 CancellationException 吃掉，协程停不下来。
            try {
                seeder.seedRemainingProvinces()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("KOiL", "seedRemainingProvinces failed: ${e.javaClass.simpleName} ${e.message}")
            }
            result
        } catch (e: CancellationException) {
            // 取消不是业务失败：不能落到下面写缓存并返回"油价检测失败"，否则页面会闪一条假的错误
            throw e
        } catch (e: Exception) {
            // 失败不写 lastCheckAt，避免失败也打 8h 节流时间戳：
            // 一次瞬时失败会把自动检测锁死 8 小时（配合无重试的补灌链，用户只能手动点立即更新）；
            // 失败后保持旧时间戳，下次启动/周期 Worker 可立即重试，直到成功才写节流
            val message = "更新失败：${e.message ?: e.javaClass.simpleName}"
            settings.setPriceDbCache(message)
            OilPriceSyncResult(
                success = false,
                message = "油价检测失败",
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

    // ===== 灌库与重试（实现移至 OilPriceSeeder，公共 API 不变） =====

    /** 首拉当前省一整年曲线灌库（仅库空时执行，不覆盖老用户已有数据） */
    suspend fun initPriceDb() = seeder.initPriceDb()

    /** 补全各省窗口日覆盖（幂等）：见 [OilPriceSeeder.seedRemainingProvinces] */
    suspend fun seedRemainingProvinces(): Boolean = seeder.seedRemainingProvinces()

    /** 窗口日历就绪则执行全国补灌，返回是否已执行（启动兜底重试循环用） */
    suspend fun fillRemainingProvincesIfCalendarReady(): Boolean = seeder.fillRemainingProvincesIfCalendarReady()

    /** 补灌重试循环（启动兜底 / 回前台兜底共用），退避计划默认启动档 */
    suspend fun runSeedRetryLoop(scheduleMs: List<Long> = OilPriceSeeder.DEFAULT_SEED_RETRY_MS) =
        seeder.runSeedRetryLoop(scheduleMs)

    /** 预算用尽：转 GAVE_UP，让设置页从"补灌中"改成"点立即更新重试" */
    fun markSeedGaveUp() = seeder.markSeedGaveUp()

    /** 回前台兜底——GAVE_UP / WAITING 时用户切回 App 再试一轮（默认 60s 节流） */
    suspend fun resumeSeedingIfNeeded(minIntervalMs: Long = 60_000L) =
        seeder.resumeSeedingIfNeeded(minIntervalMs)

    /** 按已缓存的权威窗口日历清理库内孤立假生效日（幂等；窗口日历为空时跳过） */
    suspend fun purgeIllegalEffectiveDatesIfReady() = calendar.purgeIllegalEffectiveDatesIfReady()

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

        val today = LocalDate.now().format(KOIL_DATE_FMT)
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
            // 今天若是窗口日（24 点后才生效/确认搁浅），白天即使价格未变也不下"搁浅"结论、
            // 不写 lastWindowChecked=今天——否则数据库卡当天就显示"V今日 擱淺"，等于把窗口日当 0 点生效
            if (calendar.getAdjustDates().contains(today)) {
                return OilPriceSyncResult(
                    success = true,
                    message = "今天是调价窗口日（$today），价格待 24 点后生效",
                    latestDate = latestDate,
                    nextAdjustment = nextAdjustment,
                )
            }
            // 搁浅结论只在「窗口日次日」落账——非窗口日重复检查不滚动 lastWindowChecked
            //（避免普通日检查把版本号滚成检查日，如 V20261031 而非真实窗口 V20261029）
            val lastWindow = calendar.getAdjustDates().filter { it < today }.lastOrNull()
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

        // 检测到变化：默认省按主源增量入库；其它省由 seedProvince 灌库覆盖。
        val (verifiedRecords, failedProvinces) = verifyProvince(defaultProvince(), today)

        val hasNew = verifiedRecords > 0
        val parts = mutableListOf<String>()
        if (verifiedRecords > 0) parts.add("已入库 $verifiedRecords 条")
        if (failedProvinces > 0) parts.add("$failedProvinces 省失败")
        val message = if (parts.isNotEmpty()) parts.joinToString("、") else "无新记录追加"

        return OilPriceSyncResult(
            success = true,
            message = message,
            newAdjustments = if (hasNew) 1 else 0,
            newRecords = verifiedRecords,
            // 没有实际插入时不能把「今天」当作最新调价日回传：
            // checkAndUpdate 会据此写 priceDbLastUpdateDate/LastWindowChecked，污染版本号与「价格维持日」
            latestDate = if (hasNew) today else getLastUpdatedAt().ifBlank { null },
            nextAdjustment = nextAdjustment,
        )
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
     * 单省（默认省）单源核验，返回 (入库数, 失败标记)。
     * 生效日 = 归并窗口日 + 1（窗口日 24 时生效 → 次日实际生效）。
     * 采用 abapi 单源：有价即自动入库；搁浅窗口由 seedRemainingProvinces 补搁浅处理。
     */
    private suspend fun verifyProvince(province: String, today: String): Pair<Int, Int> {
        return try {
            val (primary, _) = network.fetchBothSources(province)
            val primaryMap = primary.data.associateBy { it.grade }
            if (primaryMap.isEmpty()) return 0 to 1
            // 生效日 = 归并窗口日 + 1；窗口日历未就绪（从未成功推算）时跳过本次增量入库
            //（宁缺毋滥，交给 seedProvince 主路径幂等补灌，杜绝"窗口日当天以 today 为生效日"的降级假记录）
            val adjustDates = calendar.getAdjustDates()
            if (adjustDates.isEmpty()) return 0 to 0
            val eff = addOneDay(adjustToWindow(today, adjustDates))
            // 只在「今天 == 窗口日次日（真正生效日）」才允许增量入库——
            // 非窗口日价格抖动（数据源四舍五入/波动）不写"上一窗口生效日"假变化点污染
            // 调价历史/涨跌对比/历史价；窗口日当天（eff > today，B8）与其它日子（eff < today）一律跳过，
            // 真实调价由次日 checkAndUpdate/灌库自然写入。
            if (eff != today) return 0 to 0

            val toInsert = mutableListOf<KOiLPriceEntity>()

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
            }

            if (toInsert.isNotEmpty()) priceDao.insertAll(toInsert)
            // 只回报「真正插入」的条数：遍历核验数不等于插入数，
            // 0 条插入也会返回 4，导致结果页虚报「已入库 4 条」并把 lastUpdateDate 写成检查日。
            toInsert.size to 0
        } catch (_: Exception) {
            0 to 1
        }
    }

    // ===== 调价通知与窗口提醒（实现移至 OilPriceChangeNotifier，公共 API 不变） =====

    /** 调价生效 / 窗口日前一天 / 窗口日当天三类提醒（Worker 与设置页「立即更新」共用） */
    suspend fun notifyPriceChanges(context: Context) = notifier.notifyPriceChanges(context)

    /** 为下一个尚未到来的窗口日调度"当天 00:05"的一次性提醒 Worker */
    suspend fun scheduleWindowDayReminder(context: Context) = notifier.scheduleWindowDayReminder(context)
}
