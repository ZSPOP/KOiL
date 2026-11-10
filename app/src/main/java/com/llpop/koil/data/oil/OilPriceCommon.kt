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
 * 采样/灌库/通知共用的默认省份解析：
 * 用户设置合法则用设置值，否则回退省份列表第一个（原 OilPriceDbSyncer.defaultProvince）。
 */
internal suspend fun AppSettings.koilDefaultProvince(): String =
    defaultProvince.first().takeIf { it in Provinces.ALL } ?: Provinces.ALL[0]
