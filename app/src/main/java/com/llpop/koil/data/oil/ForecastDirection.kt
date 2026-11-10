package com.llpop.koil.data.oil

import kotlin.math.abs

/**
 * 官方搁浅门槛：调价幅度低于每吨 50 元时，本轮**不作调整**（《石油价格管理办法》，未调金额纳入下次累加或冲抵）。
 *
 * 这不是 UI 的显示偏好，是真实规则——25 轮公告里有 3 轮就是这样被搁浅掉的。
 * 界面上必须把这种情形单独说出来，否则会显示成「预计下调约 0 元/吨」或「预计上调约 47 元/吨」，
 * 用户以为会调价，实际公告是「不调」。
 */
internal const val STRANDED_THRESHOLD_PER_TON = 50.0

/** 预测幅度方向（含搁浅） */
internal enum class ForecastDirection {
    UP,
    DOWN,
    /** |幅度| < [STRANDED_THRESHOLD_PER_TON]：官方不作调整 */
    STRANDED,
}

/**
 * 由幅度（元/吨，正涨负跌）判定方向。
 *
 * 边界取「严格小于」：官方口径是「低于每吨 50 元不作调整」，正好 50 元属于要调。
 * 抽成纯函数是为了能单测——这段判断原先散在 Composable 里，一旦被改回 `forecast > 0`
 * 就又会冒出「预计下调约 0 元/吨」，而这种回归没有任何测试会拦住。
 */
internal fun forecastDirection(
    amountPerTon: Double,
    threshold: Double = STRANDED_THRESHOLD_PER_TON,
): ForecastDirection = when {
    abs(amountPerTon) < threshold -> ForecastDirection.STRANDED
    amountPerTon > 0 -> ForecastDirection.UP
    else -> ForecastDirection.DOWN
}
