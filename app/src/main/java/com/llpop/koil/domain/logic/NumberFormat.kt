package com.llpop.koil.domain.logic

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale

/**
 * 数字显示规范化（v1.5.31，全局统一口径）：
 * - 金额类（应付/实付/优惠/每公里成本/总额）：formatMoney —— 固定 2 位小数（人民币分币显式）
 * - 度量类（油价/油耗/油量/单价）：formatMeasure —— 最多 2 位小数，尾零裁剪，整数不显示小数点
 * - 百分比类（异常偏差等）：formatPct —— 最多 1 位小数，尾零裁剪，整数不显示小数点
 */
object NumberFormat {
    /** 金额：固定 2 位小数 */
    fun money(v: Double): String = String.format(Locale.ROOT, "%.2f", v)

    /** 度量值：最多 2 位小数，剪尾零，整数不带小数点（7.00 → 7，7.50 → 7.5，7.856 → 7.86） */
    fun measure(v: Double): String = strip(BigDecimal(v).setScale(2, RoundingMode.HALF_UP))

    /** 百分比：最多 1 位小数，剪尾零，整数不带小数点（20.0 → 20，20.55 → 20.6） */
    fun pct(v: Double): String = strip(BigDecimal(v).setScale(1, RoundingMode.HALF_UP))

    private fun strip(bd: BigDecimal): String {
        val s = bd.stripTrailingZeros()
        return if (s.scale() <= 0) s.toBigInteger().toString() else s.toPlainString()
    }
}
