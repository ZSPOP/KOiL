package com.llpop.koil.data.oil

import com.llpop.koil.data.local.dao.KOiLPriceDao
import com.llpop.koil.data.settings.AppSettings
import kotlinx.coroutines.flow.first
import java.time.LocalDate

/** 原油估算结果：有符号元/吨（正涨负跌）+ 来源说明 + 数据截至日期（EIA 为 T+1，窗口日当天缺末段） */
data class CrudeOilEstimate(
    val amountPerTon: Double,
    val sourceLabel: String,
    val dataDate: String? = null,   // "yyyy-MM-dd"；EIA 路径=已用到的最后交易日，腾讯路径=null（实时）
)

/**
 * 原油价格本地估算器 —— 预测幅度兜底（apizero forecast 收费/不可用后，App 不再依赖付费接口）。
 *
 * 【机制 v1.5.23】成品油调价变化率 = (本计价期 10 日均价 − 上次调价落地基准价) ÷ 基准价：
 * - 基准日 = 库内最近一条【非搁浅调价】的生效日（10-15 搁浅轮不重置基准——官方规则，基准仍锚 9-24 调价）
 * - 基准均值 = EIA 中基准日及其往前 10 个交易日的均价（模拟官方「调价落地时的参考油价」）
 * - 本窗口均值 = (上一窗口日, 今天] 的 EIA 交易日均价
 * - 变化率 = (本窗口均值 − 基准均值) ÷ 基准均值 × 100
 * - 系数：1% ≈ 56.5 元/吨（新闻实证：变化率 5.4% → 约 305 元/吨）
 * 基准缺失（库里暂无任何非搁浅调价）→ 回退「本窗口 vs 上窗口」环比（仅作兜底方向参考）。
 *
 * 【源】主源 EIA 官方（布伦特现货 RBRTE，免费 Key 内置默认）；降级腾讯实时（单日方向，UI 标「单日参考」）。
 * 任何源失败 → 返回 null（UI 显示「待检测」，不显示假值）。
 */
class CrudeOilEstimator(
    private val network: OilPriceNetwork,
    private val settings: AppSettings,
    private val priceDao: KOiLPriceDao,
) {

    companion object {
        /** 元/吨 每 1% 变化率（经验系数，官方机制为三地原油均值挂靠，本地仅布伦特单源近似） */
        const val COEFF_PER_PERCENT = 56.5
        /** EIA 需要至少 20 条日度数据才够 10+10 均值比较 */
        private const val MIN_EIA_POINTS = 20
    }

    /** 内部计算结果：幅度 + 数据截至日期 */
    private data class CalcResult(val amountPerTon: Double, val dataDate: String)

    suspend fun estimate(): CrudeOilEstimate? {
        // v1.5.1：数据源开关——关 = 直接用腾讯（不请求 EIA）；开 = EIA 主源 → 失败降级腾讯
        val eiaOn = settings.eiaEnabled.first()
        if (eiaOn) {
            // 主源：EIA 官方布伦特现货日度 → 基准锚定均值比较（完整历史，最接近官方口径）
            val key = settings.eiaApiKey.first()
            if (key.isNotBlank()) {
                val eia = runCatching { network.fetchEiaBrentDaily(key, length = 30) }.getOrNull()
                if (eia != null && eia.size >= MIN_EIA_POINTS) {
                    val adjustDates = settings.priceDbAdjustDates.first()
                        .split(",").map { it.trim() }.filter { it.isNotBlank() }
                    // v1.5.23：基准日 = 库内最近非搁浅调价生效日（10-15 搁浅不重置基准）
                    val baseDate = priceDao.getLatestNonStrandedEffectiveDate()
                    val calc = calcFromEiaSeries(eia, adjustDates, baseDate)
                    if (calc != null) return CrudeOilEstimate(calc.amountPerTon, "EIA", calc.dataDate)
                }
            }
        }
        // 降级/直选：腾讯布伦特期货实时（单日涨跌，仅方向参考——v1.5.22 UI 按来源标注「单日参考」，不冒充窗口幅度）
        val tencent = runCatching { network.fetchTencentBrent() }.getOrNull()
        if (tencent != null) {
            val pct = (tencent.first - tencent.second) / tencent.second
            return CrudeOilEstimate(pct * 100 * COEFF_PER_PERCENT, "腾讯")
        }
        return null
    }

    /**
     * EIA 布伦特现货日度序列（升序）→ 估算幅度（元/吨）。
     * v1.5.23 基准锚定：本窗口 = (上一窗口日, 今天]；基准 = 基准日（最近非搁浅调价生效日）往前 10 个 EIA 交易日。
     * 基准日为空（库内无任何调价记录）→ 回退「本窗口 vs 上窗口」环比（纯兜底）。
     * 窗口日历未就绪 → 回退「最近 10 vs 前 10」硬切。
     */
    private fun calcFromEiaSeries(
        points: List<Pair<String, Double>>,
        adjustDates: List<String>,
        baseDate: String?,
    ): CalcResult? {
        if (points.size < MIN_EIA_POINTS) return null
        val today = LocalDate.now().toString()
        val prevWindow = adjustDates.filter { it < today }.lastOrNull()
        val recent: List<Double>
        val base: List<Double>
        if (prevWindow != null) {
            val idx1 = points.indexOfLast { it.first > prevWindow && it.first <= today }
            if (idx1 < 0) return null
            recent = points.subList(maxOf(0, idx1 - 9), idx1 + 1).map { it.second }
            if (baseDate != null) {
                // v1.5.23：基准 = 最近非搁浅调价生效日及其前 9 个交易日（官方「调价落地参考油价」近似）
                val idx0 = points.indexOfLast { it.first <= baseDate }
                if (idx0 < 0) return null
                base = points.subList(maxOf(0, idx0 - 9), idx0 + 1).map { it.second }
            } else {
                // 无基准 → 回退环比上窗口
                val idx0 = points.indexOfLast { it.first <= prevWindow }
                if (idx0 < 0) return null
                base = points.subList(maxOf(0, idx0 - 9), idx0 + 1).map { it.second }
            }
        } else {
            recent = points.takeLast(10).map { it.second }
            base = points.drop(points.size - 20).take(10).map { it.second }
        }
        val baseAvg = base.average()
        if (baseAvg <= 0) return null
        val changePct = (recent.average() - baseAvg) / baseAvg * 100.0
        val latest = points.lastOrNull { it.first <= today }?.first ?: return null
        return CalcResult(changePct * COEFF_PER_PERCENT, latest)
    }
}
