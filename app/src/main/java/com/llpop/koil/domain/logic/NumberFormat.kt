package com.llpop.koil.domain.logic

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * 数字显示规范化（v1.5.31，全局统一口径）：
 * - 金额类（应付/实付/优惠/每公里成本/总额）：formatMoney —— 固定 2 位小数（人民币分币显式）
 * - 度量类（油价/油耗/油量/单价）：formatMeasure —— 最多 2 位小数，尾零裁剪，整数不显示小数点
 * - 百分比类（异常偏差等）：formatPct —— 最多 1 位小数，尾零裁剪，整数不显示小数点
 *
 * v1.6.2 健壮性重写（压测发现三处可复现缺陷）：
 * ① `BigDecimal(double)` 走二进制精确值，2.675 → 2.67499999… → 舍成 2.67（用户可见的"少一分钱"），
 *    统一改 `BigDecimal.valueOf` （即 Double.toString 的十进制语义），与用户心算一致；
 * ② `-0.0001` 舍入后是 -0.00，界面出现"负零"，统一把零值归一为正零；
 * ③ NaN / ±Infinity（除零、上游脏数据）会让 `BigDecimal` 构造抛 NumberFormatException，
 *    而调用点全在 UI 绘制与 CSV 导出路径上（=闪退），改为返回占位符 `--`。
 */
object NumberFormat {

    /** 非有限值占位符（与各页面 "--" 空态一致，避免把 NaN/∞ 当数字展示） */
    const val PLACEHOLDER = "--"

    /** 金额：固定 2 位小数 */
    fun money(v: Double): String = fixed(v, 2)

    /** 度量值：最多 2 位小数，剪尾零，整数不带小数点（7.00 → 7，7.50 → 7.5，7.856 → 7.86） */
    fun measure(v: Double): String = trimmed(v, 2)

    /** 固定 2 位小数（不剪尾零）：用于卡片内需要位数对齐的度量（油耗 5.80、油价 8.10） */
    fun fixed2(v: Double): String = fixed(v, 2)

    /** 百分比：最多 1 位小数，剪尾零，整数不带小数点（20.0 → 20，20.55 → 20.6） */
    fun pct(v: Double): String = trimmed(v, 1)

    private fun fixed(v: Double, scale: Int): String =
        scaled(v, scale)?.toPlainString() ?: PLACEHOLDER

    private fun trimmed(v: Double, scale: Int): String {
        val bd = scaled(v, scale) ?: return PLACEHOLDER
        val s = bd.stripTrailingZeros()
        return if (s.scale() <= 0) s.toBigInteger().toString() else s.toPlainString()
    }

    /** 十进制四舍五入到 scale 位；非有限值返回 null；零值统一正零（消除 "-0.00"） */
    private fun scaled(v: Double, scale: Int): BigDecimal? {
        if (!v.isFinite()) return null
        val bd = BigDecimal.valueOf(v).setScale(scale, RoundingMode.HALF_UP)
        return if (bd.signum() == 0) bd.abs() else bd
    }
}
