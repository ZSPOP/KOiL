package com.llpop.koil.data.oil

import android.util.Log
import androidx.room.withTransaction
import com.llpop.koil.data.local.AppDatabase
import com.llpop.koil.data.local.dao.KOiLPriceDao
import com.llpop.koil.data.local.entity.KOiLPriceEntity
import com.llpop.koil.data.settings.AppSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate

/**
 * 油价库窗口日历引擎（从 OilPriceDbSyncer 拆出，M-09，行为不变）：
 * - 锚点链（apizero 接口已不可用，4 级）：
 *   ① xxapi date−1 → ② ruseo date−1（同源互备，xxapi 优先）→
 *   ③ abapi 全年曲线最近调价日（生效日−3）→ ④ calculateAdjustDates 本地推算全年窗口日历
 * - 内存 + 持久化缓存复用（重启不空降级）；缓存命中照常清假日期
 * - 幂等清理假生效日（purgeIllegalEffectiveDates）
 *
 * 机制细节见 docs/KOiL油价数据库机制.md。
 */
internal class OilPriceAdjustCalendar(
    private val network: OilPriceNetwork,
    private val settings: AppSettings,
    private val priceDao: KOiLPriceDao,
    private val db: AppDatabase,
) {

    companion object {
        /** 窗口日历整条链路的总预算：NDRC 分页 + 节假日 CDN 即使并行也可能叠加，超时即降级 */
        private const val ADJUST_DATES_TOTAL_TIMEOUT_MS = 20_000L

        /**
         * 自动清理假生效日（只删“同价孤立日”）。
         *
         * 安全闸门：删除前逐条确认该行 isStranded=false 且价格与同省同油号上一条
         * 非搁浅记录完全相同；真实调价必然变价，同价调价属于搁浅、不会以非搁浅写入。
         * 任一日历偏移只会让可疑日期变多，不会让“变价记录”被误判删除；
         * 删除前把日期/省份数/行数/保留行数写入日志，便于核对。
         */
        private const val AUTO_PURGE_ENABLED = true
    }

    /** 推算的当年权威窗口日缓存（锚点=次源 next_adjust_date + chinese-days 节假日推算；失败为空则降级） */
    @Volatile
    private var cachedAdjustDates: List<String>? = null

    /**
     * 主源 abapi 全年曲线 → 最近价格变化日（生效日）− 3 天 = 真实调价窗口日。
     * 实测（2026）：18 次价格变化全部与发改委公告窗口吻合（偏移 1~4 天，最近 4 次均 3 天）；
     * 曲线失败/无变化返回 null（由调用方继续降级）。
     */
    private suspend fun abapiAnchor(): String? {
        val points = network.fetchYoujiaHistory(settings.koilDefaultProvince())
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
    suspend fun getAdjustDates(): List<String> =
        // 总预算 20s：超时返回空，调用方按"日历未就绪"降级（abapi 原日期 / WAITING 重试），
        // 不让首屏在弱网下无限等。缓存命中路径几乎瞬时，不受影响。
        withTimeoutOrNull(ADJUST_DATES_TOTAL_TIMEOUT_MS) { getAdjustDatesInner() } ?: emptyList()

    private suspend fun getAdjustDatesInner(): List<String> {
        // 1) 内存缓存优先（跨年时内存缓存同样失效——App 跨年不重启的场景——清掉走持久化/重算）
        cachedAdjustDates?.let { dates ->
            if (dates.firstOrNull()?.take(4) == LocalDate.now().year.toString()) return dates
            cachedAdjustDates = null
        }
        // 2) 持久化缓存（App 重启后复用上次已推算的窗口日，杜绝次源临时失败导致空降级 → 假生效日）
        val persisted = settings.priceDbAdjustDates.first().let { s ->
            if (s.isBlank()) emptyList() else s.split(",").map { it.trim() }.filter { it.isNotBlank() }
        }
        if (persisted.isNotEmpty()) {
            // 跨年检测：缓存窗口日历的年份 ≠ 今年 → 清缓存重算（跨年后自动滚动到新年日历，
            // 调价次数当年口径从零重计、新窗口正常增量入库，无需清库重装）
            val yearOfCache = persisted.firstOrNull()?.take(4)
            val currentYear = LocalDate.now().year.toString()
            if (yearOfCache != currentYear) {
                cachedAdjustDates = null
                settings.setPriceDbAdjustDates(emptyList())
            } else {
                cachedAdjustDates = persisted
                // 读缓存不做全表 DISTINCT / 删除：getAdjustDates 在同步、补灌判定、
                // 设置页状态刷新里被高频调用，读路径带写操作既拖慢也难推理。
                // 清理改由设置页进入时的维护入口触发；只删同价孤立日（见 AUTO_PURGE_ENABLED）。
                return persisted
            }
        }
        // 3) 拉取推算（仅当无任何缓存时）
        // 锚点回退链（apizero 接口已不可用；同源互备 xxapi 优先）：
        // ① xxapi date−1 → ② ruseo date−1 → ③ 主源 abapi 全年曲线最近调价日（生效日 − 3 天，
        // 实测 18/18 与发改委公告吻合）→ ④ 发改委公告（公告标题年份+月日=权威窗口日，
        // 含搁浅窗口；但它是网页抓取，公告格式一旦变化即抓取为空 → 自动跳过，不影响主链锚点）
        // 锚点命中来源记录（多源一致性展示）——来源跟随实际命中的链路节点
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
        // 三年并行拉取：逐年串行时，弱网 3 年 × 3 CDN 逐级超时会把首屏拖到分钟级
        coroutineScope {
            val results = listOf(todayYear - 1, todayYear, todayYear + 1)
                .map { y -> async { network.fetchChinaHolidays(y) } }
                .awaitAll()
            results.forEach { h ->
                holidaySet.addAll(h.holidayDates)
                workdaySet.addAll(h.workdayDates)
            }
        }
        val dates = calculateAdjustDates(anchor, holidaySet, workdaySet)
        if (dates.isNotEmpty()) {
            cachedAdjustDates = dates
            settings.setPriceDbAdjustDates(dates)
            purgeIllegalEffectiveDates(dates)
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
        // 两个推进函数都加上界。节假日集合来自网络，若上游把连续区间误标成假期
        // （或 workdaySet 为空且假期覆盖整年），不加上界会在此死循环——协程永远不返回，
        // 补灌与启动同步全部挂住，表现为"油价库一直不更新"。
        fun add10(from: LocalDate): LocalDate {
            var d = from.plusDays(1); var n = 0; var guard = 0
            while (guard++ < 400) { if (isWorkday(d)) n++; if (n == 10) return d; d = d.plusDays(1) }
            return from.plusDays(14)
        }
        fun sub10(from: LocalDate): LocalDate {
            var d = from.minusDays(1); var n = 0; var guard = 0
            while (guard++ < 400) { if (isWorkday(d)) n++; if (n == 10) return d; d = d.minusDays(1) }
            return from.minusDays(14)
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

    /**
     * 幂等清理假生效日：删除 effectiveDate 不属于任何"窗口日+1"的孤立记录。
     * 来源：窗口日历为空时的降级路径会把"数据源原日期+1"当生效日写入
     *（如 abapi 点 09-26 → 09-27），若不清除，库内最新生效日与 DISTINCT 日期数都会被污染。
     */
    suspend fun purgeIllegalEffectiveDates(dates: List<String>) {
        val legal = dates.map { addOneDay(it) }.toSet()
        // 只清「当年窗口日历范围内的假日期」：生效日在当年 1-1 ~ 12-31 且不在合法窗口+1 集合
        // （如降级灌入的 09-27）；往年日期一律保留 —— 灌库是一次性起点 + 持续增量模型，
        // 2026 起点灌库后跨年（2027）不重灌、不清库，历史记录持续累加。
        val yearStart = "${LocalDate.now().year}-01-01"
        val yearEnd = "${LocalDate.now().year}-12-31"
        val badDates = priceDao.distinctEffectiveDates().filter {
            it in yearStart..yearEnd && it !in legal
        }
        if (badDates.isEmpty()) return

        // 先取可疑日期全部行；查询失败只记录，不删库（取消必须继续向上抛）。
        val rows = try {
            priceDao.getByEffectiveDates(badDates)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("KOiL", "purge detail query failed: ${e.javaClass.simpleName} ${e.message}")
            return
        }
        if (rows.isEmpty()) return

        // 安全闸门：只删「非搁浅 + 与同省同油号上一条非搁浅记录同价」的孤立假点。
        // 真实调价必然变价；同价调价是搁浅（isStranded=true），不会被这里删除。
        // 日历偏移只会让可疑日期变多，不会把“变价记录”误判成同价假点。
        val suspicious = mutableListOf<KOiLPriceEntity>()
        for (row in rows) {
            if (row.isStranded) continue
            val prev = try {
                priceDao.getPriceBefore(row.province, row.grade, row.effectiveDate)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("KOiL", "purge prev query failed for ${row.province}/${row.grade}/${row.effectiveDate}: ${e.javaClass.simpleName}")
                null
            }
            if (prev != null && kotlin.math.abs(prev.price - row.price) <= 1e-9) {
                suspicious.add(row)
            }
        }

        val detail = badDates.sorted().map { date ->
            val dayRows = rows.filter { it.effectiveDate == date }
            val daySuspicious = suspicious.count { it.effectiveDate == date }
            "$date=${dayRows.map { it.province }.distinct().size}省/${dayRows.size}行(可删$daySuspicious)"
        }.joinToString(", ")

        if (!AUTO_PURGE_ENABLED) {
            Log.w(
                "KOiL",
                "purge skipped (AUTO_PURGE_ENABLED=false): ${badDates.size} suspicious effective dates, " +
                    "total ${rows.size} rows, detail=[${detail.take(400)}]",
            )
            return
        }

        try {
            db.withTransaction {
                suspicious.forEach { row ->
                    priceDao.deleteByKey(row.province, row.grade, row.effectiveDate)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("KOiL", "purge failed: ${e.javaClass.simpleName} ${e.message}; detail=[${detail.take(400)}]")
            return
        }
        Log.w(
            "KOiL",
            "purge applied: ${badDates.size} dates, ${rows.size} rows scanned, " +
                "${suspicious.size} same-price isolated rows deleted, detail=[${detail.take(400)}]",
        )
    }

    /** 公开入口：按已缓存的权威窗口日历清理库内孤立假生效日（幂等；窗口日历为空时跳过）。
     *  供设置页打开/刷新状态时即时调用，避免假日期残留影响"价格维持日"与记录数展示。 */
    suspend fun purgeIllegalEffectiveDatesIfReady() {
        val dates = settings.priceDbAdjustDates.first()
            .split(",").map { it.trim() }.filter { it.isNotBlank() }
        if (dates.isNotEmpty()) purgeIllegalEffectiveDates(dates)
    }
}
