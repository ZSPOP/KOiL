package com.llpop.koil.data.oil

import com.llpop.koil.data.settings.AppSettings
import kotlinx.coroutines.flow.first

/** 原油估算结果：有符号元/吨（正涨负跌）+ 来源说明 */
data class CrudeOilEstimate(val amountPerTon: Double, val sourceLabel: String)

/**
 * 原油价格本地估算器 —— 预测幅度兜底（apizero forecast 收费/不可用后，App 不再依赖付费接口）。
 *
 * 【机制】成品油调价 = 前 10 个工作日国际原油均价 vs 上期 10 日均价的变化率 × 系数：
 * - 主源：EIA 官方 API（布伦特现货 RBRTE 日度，免费 Key 内置默认，含完整历史——与用户打开频率无关，断档不存在）
 * - 降级：腾讯财经实时（hf_OIL 布伦特期货，公开免 Key）——现价 vs 昨收 当日变化率（方向可靠、幅度粗）
 * - 经验系数：变化率 1% ≈ 56.5 元/吨（成品油调价常用近似；新闻实证：变化率 5.4% → 预计上调 305 元/吨 ≈ 56.5/1%）
 * 任何源失败 → 返回 null（UI 显示「待检测」，不显示假值）。
 */
class CrudeOilEstimator(
    private val network: OilPriceNetwork,
    private val settings: AppSettings,
) {

    companion object {
        /** 元/吨 每 1% 变化率（经验系数，官方机制为三地原油均值挂靠，本地近似） */
        const val COEFF_PER_PERCENT = 56.5
        /** EIA 需要至少 20 条日度数据才够 10+10 日均值比较 */
        private const val MIN_EIA_POINTS = 20
    }

    suspend fun estimate(): CrudeOilEstimate? {
        // 主源：EIA 官方布伦特现货日度 → 10 交易日均值比较（完整历史，最接近官方口径）
        val key = settings.eiaApiKey.first()
        if (key.isNotBlank()) {
            val eia = runCatching { network.fetchEiaBrentDaily(key, length = 30) }.getOrNull()
            if (eia != null && eia.size >= MIN_EIA_POINTS) {
                val amount = calcFromEiaSeries(eia)
                if (amount != null) return CrudeOilEstimate(amount, "EIA")
            }
        }
        // 降级：腾讯布伦特期货实时（当日涨跌幅，方向可靠、幅度粗）
        val tencent = runCatching { network.fetchTencentBrent() }.getOrNull()
        if (tencent != null) {
            val pct = (tencent.first - tencent.second) / tencent.second
            return CrudeOilEstimate(pct * 100 * COEFF_PER_PERCENT, "腾讯")
        }
        return null
    }

    /**
     * EIA 布伦特现货日度序列（升序）→ 估算幅度（元/吨）：
     * 最近 10 个交易日均值 vs 前 10 个交易日均值 → 变化率 → × 系数。
     */
    private fun calcFromEiaSeries(points: List<Pair<String, Double>>): Double? {
        if (points.size < MIN_EIA_POINTS) return null
        val recent = points.takeLast(10).map { it.second }
        val previous = points.drop(points.size - 20).take(10).map { it.second }
        val prevAvg = previous.average()
        if (prevAvg <= 0) return null
        val changePct = (recent.average() - prevAvg) / prevAvg * 100.0
        return changePct * COEFF_PER_PERCENT
    }
}
