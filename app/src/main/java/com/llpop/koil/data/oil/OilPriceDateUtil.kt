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
 * 无年份时按当前年推断；若月份早于当前月则视为下一年。
 */
object OilPriceDateUtil {

    private val PATTERN_ISO = Regex("""(\d{4})[-/.](\d{1,2})[-/.](\d{1,2})""")
    private val PATTERN_CN_FULL = Regex("""(\d{4})年(\d{1,2})月(\d{1,2})日""")
    private val PATTERN_CN_SHORT = Regex("""(\d{1,2})月(\d{1,2})日""")
    private val PATTERN_MD = Regex("""^(\d{1,2})[-/.](\d{1,2})$""")

    fun normalize(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val s = raw.trim()

        PATTERN_ISO.find(s)?.let {
            return "${it.groupValues[1]}-${it.groupValues[2].padStart(2, '0')}-${it.groupValues[3].padStart(2, '0')}"
        }
        PATTERN_CN_FULL.find(s)?.let {
            return "${it.groupValues[1]}-${it.groupValues[2].padStart(2, '0')}-${it.groupValues[3].padStart(2, '0')}"
        }
        PATTERN_CN_SHORT.find(s)?.let {
            val mm = it.groupValues[1].toIntOrNull() ?: return null
            val dd = it.groupValues[2].toIntOrNull() ?: return null
            return withYear(mm, dd)
        }
        PATTERN_MD.find(s)?.let {
            val mm = it.groupValues[1].toIntOrNull() ?: return null
            val dd = it.groupValues[2].toIntOrNull() ?: return null
            return withYear(mm, dd)
        }
        return null
    }

    private fun withYear(mm: Int, dd: Int): String {
        val now = LocalDate.now()
        val year = if (mm < now.monthValue) now.year + 1 else now.year
        return "$year-${mm.toString().padStart(2, '0')}-${dd.toString().padStart(2, '0')}"
    }
}
