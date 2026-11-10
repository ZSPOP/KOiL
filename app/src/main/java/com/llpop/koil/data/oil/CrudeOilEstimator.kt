package com.llpop.koil.data.oil

import com.llpop.koil.data.local.dao.KOiLPriceDao
import com.llpop.koil.data.settings.AppSettings
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/** 原油估算结果：有符号元/吨（正涨负跌）+ 来源说明 + 数据截至日期（EIA 为 T+1，窗口日当天缺末段） */
data class CrudeOilEstimate(
    val amountPerTon: Double,
    val sourceLabel: String,
    val dataDate: String? = null,   // "yyyy-MM-dd"；EIA 路径=已用到的最后交易日，腾讯路径=null（实时）
    val sampleDays: Int? = null,    // EIA 路径=本窗口已计入的交易日数（官方口径 10 天）；腾讯路径=null
    // "yyyy-MM-dd"；EIA 路径=源端发布这批数据的日期（WPSR 增量链路的 release_date），
    // 拿不到增量时为 null。晚于 dataDate 3 天左右，UI 用来解释「数据为什么旧」而不是让用户以为卡住。
    // 命名与 dataDate 显式区分：dataDate 是「数据截至哪天」，sourceReleaseDate 是「源端哪天发的」。
    val sourceReleaseDate: String? = null,
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
 * - 基准日先吸附到窗口网格（生效日 = 窗口日 + 1 天，且 abapi 锚点可能「生效日 − 3 天」，不能硬减 1 天）
 * - 基准均值 = EIA 中【上一期窗口跨度】(上一窗口日, 基准窗口日] 内交易日均价（不是固定前取 10 个交易日——
 *   跨国庆/春节的周期有 11~15 个交易日，固定前取会丢掉周期头部，把基准挪向周期末段）
 * - 本窗口均值 = (上一窗口日, 今天] 内的 EIA 交易日均价，**只取窗口内，不足 10 天不回补**
 * - 幅度 = (本窗口均价 − 基准均价) × 7.33(桶/吨) × 汇率(CNY/USD) × k
 * 旧实现用「变化率 × 56.5 元/吨」：56.5 其实是（布伦特 108 美元、汇率 7.14）那天的快照
 * （`56.5 = 0.01 × 7.33 × 7.14 × 108`），把汇率与油价一起冻在快照里，一漂移就失真——实测隐含系数
 * 在 23.4~69.5 之间波动，固定 56.5 与单位换算口径差均值 74.9、最大 362.4 元/吨。现改为显式单位换算。
 * passthrough 系数 k 经 23 轮公告回测（最小二乘）为 0.978≈1，故取 1，不引入拟合参数：
 * 加任何动态/滚动/分段拟合都让 MAE 变差（194.2 → 153.0 是 k=1 带来的，再乘中位 k=1.17 会回退到 176.2）。
 * 基准缺失（库里暂无任何非搁浅调价）→ 回退「本窗口 vs 上窗口」环比（仅作兜底方向参考）。
 *
 * 【源】主源 EIA 官方（布伦特现货 RBRTE，免费 Key 内置默认）；降级腾讯实时（单日方向，UI 标「单日参考」）。
 * 任何源失败 → 返回 null（UI 显示「待检测」，不显示假值）。
 */
class CrudeOilEstimator(
    private val network: OilPriceNetwork,
    private val settings: AppSettings,
    private val priceDao: KOiLPriceDao,
    private val fxRepository: KOiLUsdCnyRateRepository,
) {

    companion object {
        /**
         * 原油体积重量换算：1 吨 ≈ 7.33 桶。
         *
         * 原挂在 `UsdCnyMonthly` 上，汇率改为运行时取数、静态月表删除后移到这里——
         * 它和汇率没有关系，本就不该被一个「USD/CNY 月表」对象持有。
         */
        const val BARRELS_PER_TON = 7.33

        /** EIA 需要至少 20 条日度数据才够 10+10 均值比较 */
        private const val MIN_EIA_POINTS = 20
        /** 官方口径：一个计价期 = 10 个工作日（UI 用它判断是否标注「已计入 N 个交易日」，故对外可见） */
        const val WINDOW_DAYS = 10
        /** 基准锚点吸附容差（天）：生效日 = 窗口日 + 1，abapi 锚点又是「生效日 − 3 天」，故留 3 天余量 */
        private const val WINDOW_SNAP_DAYS = 3L
        /** 基准期最少交易日数：一个完整周期 ≈ 9~15 个交易日，少于 5 个说明序列断档，宁可不显示 */
        private const val MIN_BASE_DAYS = 5

        /**
         * 把生效日吸附到窗口网格上最近的窗口日。
         *
         * 生效日 = 窗口日 + 1 天，直接拿生效日当基准锚点会让基准期整体前移一天（实测 30 个窗口里
         * 24 个的生效日本身就是 EIA 交易日）。也不能硬减 1 天：abapi 锚点是「生效日 − 3 天」，
         * 那些记录减 1 天仍偏。故取「±3 天内最近的窗口日」；网格没覆盖该日期时保留原值交给调用方兜底。
         */
        private fun snapToWindow(raw: String, adjustDates: List<String>): String? {
            val rawDate = runCatching { LocalDate.parse(raw) }.getOrNull() ?: return null
            val within = adjustDates
                .mapNotNull { d -> runCatching { LocalDate.parse(d) }.getOrNull()?.let { d to it } }
                .filter { (_, d) -> abs(ChronoUnit.DAYS.between(d, rawDate)) <= WINDOW_SNAP_DAYS }
            return within.minByOrNull { (_, d) -> abs(ChronoUnit.DAYS.between(d, rawDate)) }?.first ?: raw
        }

        /**
         * EIA 布伦特现货日度序列（升序）→ 估算幅度（元/吨）。
         *
         * 【本窗口】严格只取 (上一窗口日, 今天] 内的交易日，**不足 10 天绝不回补上一窗口**：
         * 旧实现是「在全序列里往前取 10 个交易日」，调价后头几天会把上一窗口的走势混进本窗口，
         * 把幅度稀释到接近 0（实测某窗口本窗口仅 2 天，混入 8 天旧数据后幅度只剩十分之一左右，
         * 而仅用窗口内两天为 -678.6 元/吨）。
         * 官方口径随窗口推进从 N 天收敛到 10 天，样本天数由 [CrudeOilEstimate.sampleDays] 透出，
         * 由 UI 标注「已计入 N 个交易日」，不假装是完整窗口。
         *
         * 【基准】先把基准日吸附到窗口网格，再取**上一期窗口跨度** (上一窗口日, 基准窗口日] 的交易日均价。
         * 不用「往前数 10 个交易日」：跨国庆/春节的周期有 11~15 个交易日，固定前取会丢掉周期头部，
         * 把基准挪向周期末段（实测跨假期窗口基准偏差最大 90.3 元/吨）。
         * 网格里没有更早的窗口（基准期即最早一期）→ 退回「往前数 10 个交易日」。
         * 窗口日历未就绪 → 回退「最近 10 vs 前 10」硬切。
         *
         * @param today 传入当天日期（默认系统日期），便于单测固定时间轴
         * @param fxRate 汇率（CNY/USD），**必填、无默认值**：汇率已改为运行时从 ECB 取（见
         *   [KOiLUsdCnyRateRepository]），生产代码不再内置月表。给默认值会让「表删了但还留一个
         *   隐式引用」的尾巴留下来，PR #2 的验收就不再是纯显式汇率。单测显式传固定值即可。
         */
        internal fun calcFromEiaSeries(
            points: List<Pair<String, Double>>,
            adjustDates: List<String>,
            baseDate: String?,
            today: String = LocalDate.now().toString(),
            fxRate: Double,
        ): CrudeOilCalcResult? {
            if (points.size < MIN_EIA_POINTS) return null
            val prevWindow = adjustDates.filter { it < today }.lastOrNull()

            // 基准日：锚点优先「最近非搁浅调价生效日」（缺失时退上一窗口），再吸附到窗口网格
            val rawAnchor = baseDate ?: prevWindow
            val baseWindow = rawAnchor?.let { snapToWindow(it, adjustDates) }
            val basePrev = baseWindow?.let { w -> adjustDates.getOrNull(adjustDates.indexOf(w) - 1) }

            val base: List<Double> = when {
                // 正路：基准期 = 上一期窗口跨度，按跨度取交易日（国内假期内的交易日照常计入）
                baseWindow != null && basePrev != null ->
                    points.filter { it.first > basePrev && it.first <= baseWindow }.map { it.second }

                // 基准期就是网格里最早的一期，没有「上一窗口日」→ 退回往前数 10 个交易日
                baseWindow != null -> {
                    val idx0 = points.indexOfLast { it.first <= baseWindow }
                    if (idx0 < 0) return null
                    points.subList(maxOf(0, idx0 - (WINDOW_DAYS - 1)), idx0 + 1).map { it.second }
                }

                // 库内无任何调价记录 → 最近 10 vs 前 10 硬切（纯兜底，仅方向参考）
                else -> points.drop((points.size - WINDOW_DAYS * 2).coerceAtLeast(0))
                    .take(WINDOW_DAYS).map { it.second }
            }
            // 基准样本过少说明 EIA 序列在基准期断档，与完整窗口不可比，宁可不显示
            if (base.size < MIN_BASE_DAYS) return null

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
            val latest = points.lastOrNull { it.first <= today }?.first ?: return null
            // 元/吨 = 原油价差(USD/bbl) × 桶吨换算 × 汇率(CNY/USD)，k=1
            // 不再走「变化率 × 56.5」：那个系数把油价与汇率一起冻在快照上，油价中枢一偏就系统性失真
            val deltaP = recent.average() - baseAvg
            return CrudeOilCalcResult(deltaP * BARRELS_PER_TON * fxRate, latest, recent.size)
        }
    }

    /**
     * 取不到时的结果要分原因：失败不带原因，UI 只能统一显示「待检测」，
     * 「汇率没取到（快照到位后能自动出数）」和「EIA/腾讯都不可用」就会被混成一种，
     * 用户分不清是卡死还是真的没数据。
     */
    sealed interface EstimateOutcome {
        data class Success(
            val estimate: CrudeOilEstimate,
            /** 本次预测实际依据的汇率快照：UI 据此判 stale，不事后重读（重读会与后台刷新竞态） */
            val fxSnapshot: KOiLUsdCnyRateSnapshot,
        ) : EstimateOutcome

        /** 汇率不可用：无缓存且所有汇率源取数失败。快照到位后 [KOiLUsdCnyRateRepository.snapshot] 会触发重算 */
        data object FxUnavailable : EstimateOutcome

        /** EIA/腾讯均不可用等其它原因，与汇率无关 */
        data object Unavailable : EstimateOutcome
    }

    suspend fun estimateDetailed(): EstimateOutcome {
        val today = LocalDate.now().toString()
        var fxFailed = false
        // 数据源开关——关 = 直接用腾讯（不请求 EIA）；开 = EIA 主源 → 失败降级腾讯
        val eiaOn = settings.eiaEnabled.first()
        if (eiaOn) {
            // 主源：EIA 官方布伦特现货日度 → 基准锚定均值比较（完整历史，最接近官方口径）
            val key = settings.eiaApiKey.first()
            if (key.isNotBlank()) {
                val eia = runCatching { network.fetchEiaBrentDaily(key, length = 30) }.getOrNull()
                if (eia != null && eia.points.size >= MIN_EIA_POINTS) {
                    val adjustDates = settings.priceDbAdjustDates.first()
                        .split(",").map { it.trim() }.filter { it.isNotBlank() }
                    // 基准日 = 库内最近非搁浅调价生效日（10-15 搁浅不重置基准）
                    val baseDate = priceDao.getLatestNonStrandedEffectiveDates().firstOrNull()
                    // 汇率按「数据最后一天」所属月份取，不是按今天：EIA 数据常停在上一月时，
                    // 用今天所属月份会拿到一个还没数据的新月份，与窗口内油价不同期
                    val latest = eia.points.lastOrNull { it.first <= today }?.first ?: today
                    val fx = fxRepository.rateForDetailed(latest)
                    if (fx != null) {
                        val calc = calcFromEiaSeries(eia.points, adjustDates, baseDate, today, fx.rate)
                        if (calc != null) {
                            return EstimateOutcome.Success(
                                CrudeOilEstimate(
                                    amountPerTon = calc.amountPerTon,
                                    sourceLabel = "EIA",
                                    dataDate = calc.dataDate,
                                    sampleDays = calc.sampleDays,
                                    sourceReleaseDate = eia.releaseDate,
                                ),
                                fxSnapshot = fx.snapshot,
                            )
                        }
                    } else {
                        fxFailed = true
                    }
                }
            }
        }
        // 降级/直选：腾讯布伦特期货实时（单日涨跌，仅方向参考——UI 按来源标注「单日参考」，不冒充窗口幅度）
        val tencent = runCatching { network.fetchTencentBrent() }.getOrNull()
        if (tencent != null) {
            // EIA 路径刚经历一轮全源同步取数失败且未落缓存（rateForDetailed 返回 null 只可能是
            // 无缓存且 fetchFirstAvailable 全失败）：同一快照池立刻重试只会再等一轮 15s，
            // 结果几乎必然还是失败——直接复用同一次失败，守住「首次同步等待 ≤ 15s」的预算。
            if (fxFailed) return EstimateOutcome.FxUnavailable
            // 汇率拿不到就不给假值：乘 0 会把幅度静默算成 0，比不显示更糟
            val fx = fxRepository.rateForDetailed(today)
            if (fx == null) {
                fxFailed = true
            } else {
                val deltaP = tencent.first - tencent.second
                return EstimateOutcome.Success(
                    CrudeOilEstimate(deltaP * BARRELS_PER_TON * fx.rate, "腾讯"),
                    fxSnapshot = fx.snapshot,
                )
            }
        }
        // 汇率确实尝试过且失败的，报 FxUnavailable（这是能自动恢复的那种失败，空态文案不同）；
        // 其余（腾讯也挂了/EIA 序列断档）报 Unavailable。两者都拿不到数，但 UI 表述要分开。
        return if (fxFailed) EstimateOutcome.FxUnavailable else EstimateOutcome.Unavailable
    }
}
