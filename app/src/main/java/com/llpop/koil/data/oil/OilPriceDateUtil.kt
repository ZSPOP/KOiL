package com.llpop.koil.data.oil

import java.time.LocalDate

/**
 * 调价窗口日期规范化工具。
 * 接口（apizero / abapi）返回的 next_adjustment 格式不统一，可能是：
 *   - "2026-10-29" / "2026/10/29" / "2026.10.29"
 *   - "2026年10月29日"
 *   - "10月29日"（无年份）
 *   - "10-29"（无年份）
 * 统一输出 yyyy-MM-dd；无法识别返回 null。
 * 无年份时按当前年推断；若月份早于当前月则视为下一年，但**推断结果必须落在今天前后 183 天内**，
 * 超出即判为不可识别——否则接口缓存的上一窗口（如 11 月收到 "10-29"）会被推成明年同日。
 */
object OilPriceDateUtil {

    /** 无年份推断结果的允许跨度（天）。半年足够覆盖"下个窗口"，又挡住"推到明年"的错值 */
    private const val MAX_INFER_DAYS = 183L

    private val PATTERN_ISO = Regex("""(\d{4})[-/.](\d{1,2})[-/.](\d{1,2})""")
    private val PATTERN_CN_FULL = Regex("""(\d{4})年(\d{1,2})月(\d{1,2})日""")
    private val PATTERN_CN_SHORT = Regex("""(\d{1,2})月(\d{1,2})日""")
    private val PATTERN_MD = Regex("""^(\d{1,2})[-/.](\d{1,2})$""")

    fun normalize(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val s = raw.trim()

        // matchEntire 完整匹配（避免 find 部分匹配把"2026-10-29 剩余文本"也误命中，宽容输入）
        // 带年份的两个分支同样要过 LocalDate.of：只拼字符串时 "2026-02-30"/"2026-13-01"
        // 会被原样写进设置与价格库，窗口推算和统计口径随之漂移。
        PATTERN_ISO.matchEntire(s)?.let {
            return validated(
                it.groupValues[1].toIntOrNull() ?: return null,
                it.groupValues[2].toIntOrNull() ?: return null,
                it.groupValues[3].toIntOrNull() ?: return null,
            )
        }
        PATTERN_CN_FULL.matchEntire(s)?.let {
            return validated(
                it.groupValues[1].toIntOrNull() ?: return null,
                it.groupValues[2].toIntOrNull() ?: return null,
                it.groupValues[3].toIntOrNull() ?: return null,
            )
        }
        PATTERN_CN_SHORT.matchEntire(s)?.let {
            val mm = it.groupValues[1].toIntOrNull() ?: return null
            val dd = it.groupValues[2].toIntOrNull() ?: return null
            return withYear(mm, dd)
        }
        PATTERN_MD.matchEntire(s)?.let {
            val mm = it.groupValues[1].toIntOrNull() ?: return null
            val dd = it.groupValues[2].toIntOrNull() ?: return null
            return withYear(mm, dd)
        }
        return null
    }

    /**
     * 带年份日期：先做真实日期校验再输出。
     * LocalDate.of 会拒绝 2 月 30 日、13 月、0 月这类伪日期；[LocalDate.toString] 自带补零，
     * 与无年份分支的输出格式保持完全一致。
     */
    private fun validated(year: Int, mm: Int, dd: Int): String? =
        runCatching { LocalDate.of(year, mm, dd) }.getOrNull()?.toString()

    /**
     * 无年份日期补年份。
     *
     * 单纯按「月份 < 当前月 → 明年」推断，会把接口缓存的**上一窗口**（如 11 月收到 "10-29"）
     * 推成明年同日：首页"下次调价"显示一年后的日期，且下一轮 normalize 仍会推出同一个错值、不会自愈。
     * 因此这里给推断加年龄闸：超出 [MAX_INFER_DAYS] 的结果一律判为不可识别，由调用方降级到其它锚点。
     * 同时用 LocalDate.of 做真实日期校验，顺带拒绝 "2-30" 这类伪日期（直接拼字符串会放行）。
     */
    private fun withYear(mm: Int, dd: Int): String? {
        val now = LocalDate.now()
        val year = if (mm < now.monthValue) now.year + 1 else now.year
        val candidate = runCatching { LocalDate.of(year, mm, dd) }.getOrNull() ?: return null
        val days = java.time.temporal.ChronoUnit.DAYS.between(now, candidate)
        if (days > MAX_INFER_DAYS || days < -MAX_INFER_DAYS) return null
        return candidate.toString()
    }
}
