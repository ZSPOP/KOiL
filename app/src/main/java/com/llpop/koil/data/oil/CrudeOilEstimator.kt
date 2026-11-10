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
    val sampleDays: Int? = null,    // EIA 路径=本窗口已计入的交易日数（官方口径 10 天）；腾讯路径=null
)

/** 内部计算结果：幅度 + 数据截至日期 + 本窗口样本天数（internal 仅为单测可达，外部不应依赖） */
internal data class CrudeOilCalcResult(
    val amountPerTon: Double,
    val dataDate: String,
    val sampleDays: Int,
)

/**
 * 原油价格本地估算器 —— 预测幅度兜底（apizero forecast 收费/不可用，App 不依赖付费接口）。
 *
 * 【机制】成品油调价变化率 = (本计价期均价 − 上次调价落地基准价) ÷ 基准价：
 * - 基准日 = 库内最近一条【非搁浅调价】的生效日（10-15 搁浅轮不重置基准——官方规则，基准仍锚 9-24 调价）
 * - 基准均值 = EIA 中基准日及其往前 10 个交易日的均价（模拟官方「调价落地时的参考油价」）
 * - 本窗口均值 = (上一窗口日, 今天] 内的 EIA 交易日均价，**只取窗口内，不足 10 天不回补**
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
        /** 官方口径：一个计价期 = 10 个工作日（UI 用它判断是否标注「样本 N/10」，故对外可见） */
        const val WINDOW_DAYS = 10

        /**
         * EIA 布伦特现货日度序列（升序）→ 估算幅度（元/吨）。
         *
         * 【本窗口】严格只取 (上一窗口日, 今天] 内的交易日，**不足 10 天绝不回补上一窗口**：
         * 旧实现是「在全序列里往前取 10 个交易日」，调价后头几天会把上一窗口的走势混进本窗口，
         * 把幅度稀释到接近 0（实测某窗口本窗口仅 2 天、混入 8 天旧数据，输出 -91.5，仅窗口内为 -647.8）。
         * 官方口径随窗口推进从 N 天收敛到 10 天，样本天数由 [CrudeOilEstimate.sampleDays] 透出，
         * 由 UI 标注「样本 N/10 天」，不假装是完整窗口。
         *
         * 【基准】= 基准日（最近非搁浅调价生效日）往前 10 个交易日均价；基准日为空回退上一窗口。
         * 窗口日历未就绪 → 回退「最近 10 vs 前 10」硬切。
         *
         * @param today 传入当天日期（默认系统日期），便于单测固定时间轴
         */
        internal fun calcFromEiaSeries(
            points: List<Pair<String, Double>>,
            adjustDates: List<String>,
            baseDate: String?,
            today: String = LocalDate.now().toString(),
        ): CrudeOilCalcResult? {
            if (points.size < MIN_EIA_POINTS) return null
            val prevWindow = adjustDates.filter { it < today }.lastOrNull()

            // 基准：上期 10 个交易日均价（锚点优先用「最近非搁浅调价生效日」，缺失时退上一窗口）
            val baseAnchor = baseDate ?: prevWindow
            val base: List<Double> = if (baseAnchor != null) {
                val idx0 = points.indexOfLast { it.first <= baseAnchor }
                if (idx0 < 0) return null
                points.subList(maxOf(0, idx0 - (WINDOW_DAYS - 1)), idx0 + 1).map { it.second }
            } else {
                // 库内无任何调价记录 → 最近 10 vs 前 10 硬切（纯兜底，仅方向参考）
                points.drop((points.size - WINDOW_DAYS * 2).coerceAtLeast(0)).take(WINDOW_DAYS).map { it.second }
            }
            // 基准不足 10 天说明 EIA 序列头部数据不够，与完整窗口不可比，宁可不显示
            if (base.size < WINDOW_DAYS) return null

            // 本窗口：从上一窗口日（当日 24 时生效）之后到今天，只取窗口内交易日
            val recent: List<Double> = if (prevWindow != null) {
                points.filter { it.first > prevWindow && it.first <= today }.map { it.second }
            } else {
                // 无窗口日历 → 用最近 10 个交易日（此时没有「窗口内」概念，只能整段比较）
                points.takeLast(WINDOW_DAYS).map { it.second }
            }
            if (recent.isEmpty()) return null
            if (prevWindow == null && recent.size < WINDOW_DAYS) return null

            val baseAvg = base.average()
            if (baseAvg <= 0) return null
            val changePct = (recent.average() - baseAvg) / baseAvg * 100.0
            val latest = points.lastOrNull { it.first <= today }?.first ?: return null
            return CrudeOilCalcResult(changePct * COEFF_PER_PERCENT, latest, recent.size)
        }
    }

    suspend fun estimate(): CrudeOilEstimate? {
        // 数据源开关——关 = 直接用腾讯（不请求 EIA）；开 = EIA 主源 → 失败降级腾讯
        val eiaOn = settings.eiaEnabled.first()
        if (eiaOn) {
            // 主源：EIA 官方布伦特现货日度 → 基准锚定均值比较（完整历史，最接近官方口径）
            val key = settings.eiaApiKey.first()
            if (key.isNotBlank()) {
                val eia = runCatching { network.fetchEiaBrentDaily(key, length = 30) }.getOrNull()
                if (eia != null && eia.size >= MIN_EIA_POINTS) {
                    val adjustDates = settings.priceDbAdjustDates.first()
                        .split(",").map { it.trim() }.filter { it.isNotBlank() }
                    // 基准日 = 库内最近非搁浅调价生效日（10-15 搁浅不重置基准）
                    val baseDate = priceDao.getLatestNonStrandedEffectiveDates().firstOrNull()
                    val calc = calcFromEiaSeries(eia, adjustDates, baseDate)
                    if (calc != null) {
                        return CrudeOilEstimate(calc.amountPerTon, "EIA", calc.dataDate, calc.sampleDays)
                    }
                }
            }
        }
        // 降级/直选：腾讯布伦特期货实时（单日涨跌，仅方向参考——UI 按来源标注「单日参考」，不冒充窗口幅度）
        val tencent = runCatching { network.fetchTencentBrent() }.getOrNull()
        if (tencent != null) {
            val pct = (tencent.first - tencent.second) / tencent.second
            return CrudeOilEstimate(pct * 100 * COEFF_PER_PERCENT, "腾讯")
        }
        return null
    }
}
