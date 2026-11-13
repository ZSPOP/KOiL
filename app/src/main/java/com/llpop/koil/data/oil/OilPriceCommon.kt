package com.llpop.koil.data.oil

import com.llpop.koil.data.settings.AppSettings
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 油价库共享工具（从 OilPriceDbSyncer 拆出，M-09）：
 * 窗口日归并、生效日换算、默认省份解析——窗口日历、灌库器与同步器三方共用，
 * 抽到文件级避免互相引用私有实现。均为纯函数/纯解析，无行为改动。
 */

/** 全链路统一日期格式（原 OilPriceDbSyncer.DATE_FMT） */
internal val KOIL_DATE_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

/** 归并：把 abapi 滞后日期校正到「≤ 它的最近权威窗口日」；窗口日为空或找不到时保留原日期（降级） */
internal fun adjustToWindow(date: String, adjustDates: List<String>): String {
    if (adjustDates.isEmpty()) return date
    var target: String? = null
    for (w in adjustDates) {
        if (w <= date) target = w else break
    }
    return target ?: date
}

/** 实际生效交易日 = 权威窗口日 24 时起 → 次日（窗口日 + 1 天） */
internal fun addOneDay(date: String): String {
    return try { LocalDate.parse(date).plusDays(1).toString() } catch (e: Exception) { date }
}

/**
 * 实时源能否对库内「最近一次生效日」做纠错。
 *
 * 库内最新日期比实时源的 date 还新（说明库里已经推进到更晚的窗口）时不往回改，
 * 否则一次源端的旧快照会把已有记录覆盖成更早的价。
 */
internal fun shouldReconcileWindow(latestDate: String?, liveDate: String): Boolean =
    latestDate == null || latestDate <= liveDate

/**
 * 库内今日是否已有**真实调价**记录（有则本次核验可跳过）。
 *
 * 只看日期会漏掉纠错：V1.1.13 在生效日当天会写一条「搁浅」维持价记录（如 11-13 搁浅 8.91），
 * 若把搁浅也算作「今日已有记录」，实时源随后报出的真实调价（8.39）就永远写不进去，
 * 用户会一直看到旧价。所以搁浅记录**不算**，必须放行让实时源纠正。
 */
internal fun hasTodayAdjustment(latestDate: String?, latestIsStranded: Boolean?, today: String): Boolean =
    latestDate == today && latestIsStranded != true

/**
 * 本轮是否「没有变价」（搁浅判据）。
 *
 * - 实时源（xxapi / ruseo）给出本轮 [change] 时以它为准：`|change| ≈ 0` 才算搁浅。
 *   源自身滞后骗不了它——这正是 V1.1.13 事故的核心修正点：youjia 把日期推到 11-13、
 *   价格仍停在上一轮的 8.91，靠价格差会判成「搁浅」，而官方其实降了 645 元/吨。
 * - 只有历史曲线源（youjia）时 [change] 为 null，退回「采样价 vs 库内最近价」差值判。
 *   该判法无法区分「真搁浅」与「源没更新」，所以调用方**不得据此下搁浅结论**。
 */
internal fun priceUnchanged(
    change: Double?,
    sampledPrice: Double,
    latestPrice: Double?,
    eps: Double = 0.005,
): Boolean = if (change != null) {
    kotlin.math.abs(change) < eps
} else {
    latestPrice != null && kotlin.math.abs(latestPrice - sampledPrice) < eps
}

/**
 * 采样/灌库/通知共用的默认省份解析：
 * 用户设置合法则用设置值，否则回退省份列表第一个（原 OilPriceDbSyncer.defaultProvince）。
 */
internal suspend fun AppSettings.koilDefaultProvince(): String =
    defaultProvince.first().takeIf { it in Provinces.ALL } ?: Provinces.ALL[0]
