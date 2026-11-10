package com.llpop.koil.domain.logic

import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 加油记录**入库前的唯一校验口径**。
 *
 * 以前 CSV 导入、手动备份恢复、云同步恢复各写一套：只判正负、不判 NaN/Infinity，
 * 日期只过 `\d{4}-\d{2}-\d{2}` 正则（"2026-02-30" 能入库），金额无上界。
 * 结果是一条脏记录就能让 LocalDate.parse 在导出备份时抛异常，云同步和手动备份同时失败，
 * 而且 NaN 会顺着 DerivedCalculator 的 sumOf/average 污染整条派生链。
 *
 * 三处入口统一调用 [isValidRecord]；[sanitizeText] 用于文本字段（防手拼 JSON 出现裸控制字符）。
 */
object RecordValidator {
    /** 合理上界：真实车辆里程 < 1000 万 km，单次加油 < 1000 L，单次金额 < 10 万元 */
    const val MAX_MILEAGE = 10_000_000.0
    const val MAX_OIL_AMOUNT = 1_000.0
    const val MAX_MONEY = 100_000.0
    const val MIN_MILEAGE = 1.0
    const val MIN_OIL_AMOUNT = 0.01

    private val DATE_REGEX = Regex("""\d{4}-\d{2}-\d{2}""")

    /** 真实存在的 ISO 日期：正则先挡格式，再用严格解析（ofPattern 默认 SMART 会把 02-30 纠正成 02-28） */
    fun isRealDate(date: String): Boolean =
        DATE_REGEX.matches(date) &&
            runCatching { LocalDate.parse(date, DateTimeFormatter.ISO_LOCAL_DATE) }.isSuccess

    /**
     * 是否晚于今天。
     * UI 的日期选择器有 `maxDate=today`，但 CSV 导入、手动备份、云同步恢复都绕过 UI，
     * 未来记录会变成 `getLatest()` 的"上次里程"，反过来阻塞当天正常录入。
     */
    fun isFutureDate(date: String, today: LocalDate = LocalDate.now()): Boolean =
        runCatching { LocalDate.parse(date, DateTimeFormatter.ISO_LOCAL_DATE) }
            .getOrNull()
            ?.isAfter(today) == true

    /** NaN / ±Infinity 会同时让所有比较失效，必须显式排除 */
    fun finiteInRange(v: Double, min: Double, max: Double): Boolean =
        v.isFinite() && v >= min && v <= max

    /**
     * 一条记录的五个核心字段（应付缺失时由调用方回落实付后再传入）。
     *
     * [allowFuture] 默认 false：三处导入口（CSV / 手动备份 / 云恢复）走严格模式；
     * UI 录入路径同样受日期选择器约束，保持严格不会误伤。将来若有"预约加油"类需求，
     * 由调用方显式传 true，而不是放松全局口径。
     */
    fun isValidRecord(
        date: String,
        mileage: Double,
        oilAmount: Double,
        payable: Double,
        actual: Double,
        allowFuture: Boolean = false,
        today: LocalDate = LocalDate.now(),
    ): Boolean =
        isRealDate(date) &&
            (allowFuture || !isFutureDate(date, today)) &&
            finiteInRange(mileage, MIN_MILEAGE, MAX_MILEAGE) &&
            finiteInRange(oilAmount, MIN_OIL_AMOUNT, MAX_OIL_AMOUNT) &&
            finiteInRange(payable, 0.0, MAX_MONEY) &&
            finiteInRange(actual, 0.0, MAX_MONEY)

    /** 去掉换行/制表符与其它控制字符：这些字段会被写进手拼的备份 JSON 字符串 */
    fun sanitizeText(raw: String): String = buildString(raw.length) {
        for (c in raw) {
            when {
                c == '\n' || c == '\r' || c == '\t' -> append(' ')
                c.code in 0..0x1F -> Unit
                else -> append(c)
            }
        }
    }.trim()
}
