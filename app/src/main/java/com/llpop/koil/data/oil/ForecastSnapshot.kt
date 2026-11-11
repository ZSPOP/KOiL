package com.llpop.koil.data.oil

/**
 * 预测幅度的本地快照：把上次成功估算的结论落盘，冷启动先显示、后台刷新再覆盖。
 *
 * 只存「元/吨」不存「元/升」：折合按当前油号密度现算（92/95/98 与 0# 不同），
 * 用户切油号时快照不会算错。
 *
 * 编码为 `amount|source|dataDate|releaseDate|sampleDays`，用 `|` 分隔；日期为空写空串，
 * 解析时空串还原成 null（与 [CrudeOilEstimate] 的可空字段一致）。
 */
internal object ForecastSnapshot {
    /** 快照可用时限：超过就当没有，避免久未开 App 时先弹出明显过期的数字 */
    const val MAX_AGE_MS = 24L * 60 * 60 * 1000L

    private const val SEPARATOR = "|"

    fun encode(estimate: CrudeOilEstimate): String = listOf(
        estimate.amountPerTon.toString(),
        estimate.sourceLabel,
        estimate.dataDate ?: "",
        estimate.sourceReleaseDate ?: "",
        estimate.sampleDays?.toString() ?: "",
    ).joinToString(SEPARATOR)

    /**
     * 解析快照；不可用时返回 null，调用方按「没有快照」处理（正常显示占位，等网络结果）。
     *
     * @param at 快照写入时间（毫秒），0 或负数视为没写过
     * @param now 当前时间，仅测试注入
     */
    fun decode(raw: String, at: Long, now: Long = System.currentTimeMillis()): CrudeOilEstimate? {
        if (raw.isBlank() || at <= 0L) return null
        if (now - at > MAX_AGE_MS) return null
        val parts = raw.split(SEPARATOR)
        if (parts.size < 5) return null
        val amount = parts[0].toDoubleOrNull() ?: return null
        val source = parts[1].ifBlank { return null }
        return CrudeOilEstimate(
            amountPerTon = amount,
            sourceLabel = source,
            dataDate = parts[2].ifBlank { null },
            sampleDays = parts[4].toIntOrNull(),
            sourceReleaseDate = parts[3].ifBlank { null },
        )
    }
}
