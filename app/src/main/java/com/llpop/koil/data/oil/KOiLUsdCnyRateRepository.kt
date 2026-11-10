package com.llpop.koil.data.oil

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.abs

/**
 * 汇率缓存单独一个 DataStore 文件。
 *
 * 不放进 `KOiL_settings`：那份文件有 48 个订阅者（`AppSettings` 里每个设置项都是独立 collector），
 * DataStore 在任意 key 变化时都会重发整份 Preferences，汇率每 6 小时刷新一次会把无关订阅者全唤醒。
 */
private val Context.KOiLFxStore by preferencesDataStore(name = "KOiL_fx_rate")

/** 汇率来自哪条链路。仅用于日志与缓存快照，不作为 UI 展示项。 */
@Serializable
enum class KOiLUsdCnyRateSource {
    /** ECB 官方 SDMX API（主源）：一次请求给 USD/EUR 与 CNY/EUR 两个系列，支持任意历史区间 */
    SDMX,

    /** ECB 官方 eurofxref-daily.xml（备源）：只给**最新一个交易日**的一整套汇率，救不了历史月 */
    ECB_XML,

    /** fawazahmed0/currency-api 的 Cloudflare Pages 通道（第三路）：换机构，仅上两路都失败时用 */
    CURRENCY_API,
}

/**
 * USD/CNY 月均值快照。
 *
 * [month] + [rate] 是目标月（最新点所在月）的工作日均值；[sampleDays] 是该月已计入的工作日数，
 * 不足 [KOiLUsdCnyRateRepository.MIN_SAMPLE_DAYS] 时按已裁决口径回退 [fallbackRate]。
 *
 * [dataThrough] 一律取自**响应体里的日期**，不用请求日期——ECB 周六不发布，
 * 按请求日生成会把周五的数据标成周六。
 */
@Serializable
data class KOiLUsdCnyRateSnapshot(
    val month: String,
    val rate: Double,
    val sampleDays: Int,
    val dataThrough: String,
    val fallbackMonth: String,
    val fallbackRate: Double,
    val fetchedAt: Long,
    val source: KOiLUsdCnyRateSource,
)

/**
 * USD/CNY 运行时取数与缓存 —— 替代代码内置的 35 个月静态表（原 `UsdCnyMonthly.RATES`）。
 *
 * 【为什么换掉静态表】静态表只有发版才更新：App 三个月不更新，2026-12 之后的月份全部回退到
 * 2026-11 的 6.6676，误差会随时长线性放大。
 *
 * 【为什么主源是 ECB SDMX 而不是 Frankfurter】
 * - Frankfurter 只是 ECB 数据的镜像，且 v1 响应头已带 `deprecation: @1779103800`（2026-05-18
 *   起标记弃用，至今未关停），不能作长期主源；
 * - 其继任版本 v2 **不是 ECB 定盘口径**：ECB 未定盘的 11-10 它会返回 11-10 的值，
 *   ECB 无数据的周六（11-07）它会编出 11-07 的值。用它会让 `dataThrough` 变成不存在的日期，
 *   且线上数值与回测（ECB 月均值口径）不可比，PR #2 的 133.5 将无法复现。**禁止切 v2。**
 * - ECB SDMX 是官方接口，一次请求同时返回 `USD/EUR` 与 `CNY/EUR` 两个系列，支持任意历史区间。
 *
 * 【汇率为欧元基准，必须交叉换算】SDMX 不直接发布 USD/CNY，取 `CNY/EUR ÷ USD/EUR`。
 * **必须逐日相除再求月均**，不能先算月均 `CNY/EUR` 再除以月均 `USD/EUR`——后者在
 * USD/EUR 波动大的月份会放大误差（本月两者只差 0.0001%，是波动小的巧合）。
 *
 * 【缓存优先，不阻塞首屏】有缓存就立刻返回并后台刷新（ECB 首连实测 11.5s，同步等待会让首屏转圈）；
 * 只有首次安装、连缓存都没有时才同步拉一次，超时放宽到 [FIRST_LOAD_TIMEOUT_MS]。
 *
 * 【取数边界】ECB 14:15 CET 定盘、约 16:00 CET 公布 = 北京 22:15（夏令时）/ 23:15（冬令时），
 * 白天拿到的永远是前一日汇率。对油价测算无实质影响（汇率偏 0.3% ≈ 2 元/吨），
 * 但 [KOiLUsdCnyRateSnapshot.dataThrough] 必须如实反映。
 */
class KOiLUsdCnyRateRepository(
    private val context: Context,
    engine: HttpClientEngine? = null,
) {

    companion object {
        /** 首次安装无缓存时的同步等待上限：ECB 首连实测 11.5s，原 8s 会直接把它砍掉 */
        const val FIRST_LOAD_TIMEOUT_MS = 15_000L

        /** 常规后台刷新上限 */
        const val REFRESH_TIMEOUT_MS = 8_000L

        /** 目标月至少要这么多工作日才直接用，否则回退上一个完整月（已裁决口径） */
        const val MIN_SAMPLE_DAYS = 3

        /** 缓存超过这么多久仍可用，但标 stale */
        const val STALE_DAYS = 90L

        /** 与上一次缓存偏差超过该比例视为异常（脏数据/中间人改写），保留旧值 */
        const val MAX_DEVIATION = 0.03

        /** USD/CNY 合理区间：超出即判脏。历史区间约 6.0~7.4，留足余量但不接受 0 或 60 */
        const val MIN_PLAUSIBLE_RATE = 5.0
        const val MAX_PLAUSIBLE_RATE = 9.0

        /** 同一进程内两次刷新之间的最小间隔（跨进程靠 [KOiLUsdCnyRateSnapshot.fetchedAt]） */
        private const val REFRESH_MIN_INTERVAL_MS = 6L * 60 * 60 * 1000

        private val KEY_SNAPSHOT = stringPreferencesKey("KOiL-usd-cny-rate")

        /**
         * SDMX 主源。`USD+CNY` 里的 `+` 在 **path** 中是合法字面字符（HTTP 规范只对 query 的
         * form-urlencoded 语义把 `+` 当空格），Ktor 也不会改写它；`%2B` 亦可（已实测两种都 200），
         * 但字面 `+` 更快，故用字面量。不要把「必须编码」写死成约束。
         */
        private const val SDMX_URL =
            "https://data-api.ecb.europa.eu/service/data/EXR/D.USD+CNY.EUR.SP00.A"

        private const val ECB_XML_URL =
            "https://www.ecb.europa.eu/stats/eurofxref/eurofxref-daily.xml"

        private const val CURRENCY_API_URL =
            "https://latest.currency-api.pages.dev/v1/currencies/usd.json"
    }

    private val json = Json { ignoreUnknownKeys = true }

    private val client: HttpClient = if (engine == null) {
        HttpClient {
            install(HttpTimeout) {
                requestTimeoutMillis = FIRST_LOAD_TIMEOUT_MS
                connectTimeoutMillis = FIRST_LOAD_TIMEOUT_MS
                socketTimeoutMillis = FIRST_LOAD_TIMEOUT_MS
            }
        }
    } else {
        HttpClient(engine) {
            install(HttpTimeout) {
                requestTimeoutMillis = FIRST_LOAD_TIMEOUT_MS
                connectTimeoutMillis = FIRST_LOAD_TIMEOUT_MS
                socketTimeoutMillis = FIRST_LOAD_TIMEOUT_MS
            }
        }
    }

    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _snapshot = MutableStateFlow<KOiLUsdCnyRateSnapshot?>(null)

    /** 当前缓存快照；后台刷新成功后会更新，供 UI/ViewModel 感知并重算预测 */
    val snapshot: StateFlow<KOiLUsdCnyRateSnapshot?> = _snapshot.asStateFlow()

    @Volatile
    private var lastAttemptAt = 0L

    /**
     * 取 [date]（`yyyy-MM-dd` 或 `yyyy-MM`）所属月份应使用的汇率。
     *
     * 缓存优先：有缓存立刻返回并在后台刷新；无缓存才同步拉一次（首次安装路径）。
     * 全部失败返回 null —— 调用方应显示「汇率获取中」，不得用 0/NaN 冒充。
     */
    suspend fun rateFor(date: String): Double? {
        val cached = loadSnapshot()
        if (cached != null) {
            refreshInBackground()
            return pick(cached, date)
        }
        return refreshNow(FIRST_LOAD_TIMEOUT_MS)?.let { pick(it, date) }
    }

    /** 缓存是否已过期（超过 [STALE_DAYS] 天）：仍可用，但 UI 可据此弱化表述 */
    fun isStale(snapshot: KOiLUsdCnyRateSnapshot, now: Long = System.currentTimeMillis()): Boolean =
        now - snapshot.fetchedAt > STALE_DAYS * 24 * 60 * 60 * 1000

    /**
     * 按已裁决口径选值：
     * - 目标月就是缓存月且样本够 → 用月均值；
     * - 目标月样本不足 [MIN_SAMPLE_DAYS] → 回退上一个完整月；
     * - 目标月不是缓存月（跨月/补算历史）→ 用缓存月均值兜底；
     * - 上月也没有可用值 → 用当前值（宁可给一个近似，也不返回 null 让预测整块消失）。
     */
    internal fun pick(snapshot: KOiLUsdCnyRateSnapshot, date: String): Double? {
        val month = date.take(7)
        return when {
            snapshot.month != month -> snapshot.rate
            snapshot.sampleDays >= MIN_SAMPLE_DAYS -> snapshot.rate
            snapshot.fallbackRate in MIN_PLAUSIBLE_RATE..MAX_PLAUSIBLE_RATE -> snapshot.fallbackRate
            else -> snapshot.rate
        }
    }

    // ---------- 缓存读写 ----------

    private suspend fun loadSnapshot(): KOiLUsdCnyRateSnapshot? {
        _snapshot.value?.let { return it }
        val raw = runCatching { context.KOiLFxStore.data.first()[KEY_SNAPSHOT] }.getOrNull()
            ?: return null
        val parsed = runCatching { json.decodeFromString<KOiLUsdCnyRateSnapshot>(raw) }.getOrNull()
            ?: return null
        _snapshot.value = parsed
        return parsed
    }

    private suspend fun save(snapshot: KOiLUsdCnyRateSnapshot) {
        runCatching {
            context.KOiLFxStore.edit { it[KEY_SNAPSHOT] = json.encodeToString(snapshot) }
        }
    }

    /** 后台刷新：6 小时内不重复、同一时刻只跑一个（tryLock 失败即让路，不排队） */
    private fun refreshInBackground() {
        val now = System.currentTimeMillis()
        if (now - lastAttemptAt < REFRESH_MIN_INTERVAL_MS) return
        lastAttemptAt = now
        scope.launch { runCatching { refreshNow(REFRESH_TIMEOUT_MS) } }
    }

    /**
     * 同步刷新一次（供首次安装与测试）。
     *
     * 已有一轮刷新在跑时直接返回 null（不排队等待），避免 ViewModel 因重复触发而堆叠请求。
     */
    suspend fun refreshNow(timeoutMs: Long = REFRESH_TIMEOUT_MS): KOiLUsdCnyRateSnapshot? {
        if (!mutex.tryLock()) return null
        try {
            val previous = _snapshot.value ?: loadSnapshot()
            val fresh = fetchFirstAvailable(timeoutMs, previous) ?: return null
            save(fresh)
            _snapshot.value = fresh
            return fresh
        } finally {
            mutex.unlock()
        }
    }

    // ---------- 回退链 ----------

    /**
     * 按 SDMX → ECB XML → currency-api 顺序取第一个可用结果。
     *
     * 前两路是同一机构（ECB）：SDMX 接口挂掉时 XML 还在；但两者**不构成数据级冗余**，
     * ECB 定盘本身异常时它们会一起错。真正的跨机构冗余由第三路承担。
     */
    private suspend fun fetchFirstAvailable(
        timeoutMs: Long,
        previous: KOiLUsdCnyRateSnapshot?,
    ): KOiLUsdCnyRateSnapshot? {
        val attempts = listOf<suspend () -> List<Pair<String, Double>>>(
            { fetchSdmx() },
            { fetchEcbXml() },
            { fetchCurrencyApi() },
        )
        val sources = listOf(
            KOiLUsdCnyRateSource.SDMX,
            KOiLUsdCnyRateSource.ECB_XML,
            KOiLUsdCnyRateSource.CURRENCY_API,
        )
        for (i in attempts.indices) {
            val points = withTimeoutOrNull(timeoutMs) { attempts[i]() } ?: continue
            val snapshot = aggregate(points, sources[i], previous) ?: continue
            return snapshot
        }
        return null
    }

    /**
     * 日度点集 → 月均值快照。同时负责三道护栏：
     * 1. 单点必须落在 [MIN_PLAUSIBLE_RATE]~[MAX_PLAUSIBLE_RATE]；
     * 2. 与上期同月缓存偏差 > [MAX_DEVIATION] 判为异常，整条丢弃（保留旧值）；
     * 3. [KOiLUsdCnyRateSnapshot.dataThrough] 取点集里的最大日期，不用「今天」。
     */
    internal fun aggregate(
        points: List<Pair<String, Double>>,
        source: KOiLUsdCnyRateSource,
        previous: KOiLUsdCnyRateSnapshot?,
    ): KOiLUsdCnyRateSnapshot? {
        val usable = points.filter { (date, value) ->
            date.length >= 10 && value.isFinite() && value in MIN_PLAUSIBLE_RATE..MAX_PLAUSIBLE_RATE
        }
        if (usable.isEmpty()) return null
        val sorted = usable.sortedBy { it.first }
        val latest = sorted.last()
        val month = latest.first.take(7)
        val inMonth = sorted.filter { it.first.take(7) == month }
        if (inMonth.isEmpty()) return null
        val rate = inMonth.sumOf { it.second } / inMonth.size

        if (previous != null && previous.month == month && previous.rate > 0 &&
            abs(rate - previous.rate) / previous.rate > MAX_DEVIATION
        ) {
            return null
        }

        val previousMonth = YearMonth.parse(month).minusMonths(1).toString()
        val previousPoints = sorted.filter { it.first.take(7) == previousMonth }
        val fallbackRate = if (previousPoints.isNotEmpty()) {
            previousPoints.sumOf { it.second } / previousPoints.size
        } else {
            previous?.rate ?: 0.0
        }

        return KOiLUsdCnyRateSnapshot(
            month = month,
            rate = rate,
            sampleDays = inMonth.size,
            dataThrough = latest.first,
            fallbackMonth = previousMonth,
            fallbackRate = fallbackRate,
            fetchedAt = System.currentTimeMillis(),
            source = source,
        )
    }

    // ---------- 三个源的取数与解析 ----------

    /**
     * 主源：ECB SDMX csvdata。
     *
     * 区间取「上月 1 日 ~ 今天」，是为了同时拿到目标月与上一个完整月——`sampleDays < 3` 时
     * 需要用上月均值回退，只取当月会让回退没有数据可用。
     */
    private suspend fun fetchSdmx(): List<Pair<String, Double>> {
        val today = LocalDate.now()
        val start = today.withDayOfMonth(1).minusMonths(1)
        val url = "$SDMX_URL?startPeriod=$start&endPeriod=$today&format=csvdata"
        return parseSdmx(client.get(url).bodyAsText())
    }

    /**
     * 解析 SDMX CSV。列固定为
     * `KEY,FREQ,CURRENCY,CURRENCY_DENOM,EXR_TYPE,EXR_SUFFIX,TIME_PERIOD,OBS_VALUE,...`，
     * 其后还有含逗号的 `TITLE`（带引号），故用 `limit = 9` 只切前 9 段，避免被引号内容干扰。
     */
    internal fun parseSdmx(body: String): List<Pair<String, Double>> {
        val usdPerEur = HashMap<String, Double>()
        val cnyPerEur = HashMap<String, Double>()
        body.lineSequence().forEach { line ->
            val cols = line.split(",", limit = 9)
            if (cols.size < 8) return@forEach
            val key = cols[0]
            if (!key.startsWith("EXR.")) return@forEach
            val period = cols[6].trim()
            if (period.length < 10) return@forEach
            val value = cols[7].trim().toDoubleOrNull() ?: return@forEach
            if (value <= 0) return@forEach
            when {
                key.startsWith("EXR.D.USD.") -> usdPerEur[period] = value
                key.startsWith("EXR.D.CNY.") -> cnyPerEur[period] = value
            }
        }
        return usdPerEur.keys.intersect(cnyPerEur.keys).sorted().mapNotNull { date ->
            val usd = usdPerEur.getValue(date)
            val cny = cnyPerEur.getValue(date)
            // 逐日相除：USD/CNY = (CNY per EUR) ÷ (USD per EUR)
            if (usd > 0 && cny > 0) date to cny / usd else null
        }
    }

    /**
     * 备源：ECB 官方 XML。
     *
     * 能力边界写死：文件里只有一个 `<Cube time='yyyy-mm-dd'>`，即**最新一个交易日**的 29 个货币，
     * 它给不了历史月、也给不了「当月末点」。回退链里只用来补「最新这一天」。
     */
    private suspend fun fetchEcbXml(): List<Pair<String, Double>> =
        parseEcbXml(client.get(ECB_XML_URL).bodyAsText())

    internal fun parseEcbXml(body: String): List<Pair<String, Double>> {
        val time = Regex("""time='(\d{4}-\d{2}-\d{2})'""").find(body)?.groupValues?.get(1)
            ?: return emptyList()
        fun rate(code: String): Double? = Regex("""<Cube currency='$code' rate='([0-9.]+)'/>""")
            .find(body)?.groupValues?.get(1)?.toDoubleOrNull()
        val usd = rate("USD") ?: return emptyList()
        val cny = rate("CNY") ?: return emptyList()
        if (usd <= 0 || cny <= 0) return emptyList()
        return listOf(time to cny / usd)
    }

    /**
     * 第三路：fawazahmed0/currency-api（Cloudflare Pages 通道）。
     *
     * 它内部是聚合源，交叉汇率不自洽（实测用它自己的 `usd.eur` 反推 EUR/CNY 与 ECB 差 0.26%），
     * 所以只取 `usd.cny` 单独使用，且**只认响应体里的 `date`**；jsDelivr 通道日期会落后一天，
     * 故固定走 Pages 域名。
     */
    private suspend fun fetchCurrencyApi(): List<Pair<String, Double>> =
        parseCurrencyApi(client.get(CURRENCY_API_URL).bodyAsText())

    internal fun parseCurrencyApi(body: String): List<Pair<String, Double>> {
        val root = runCatching { json.decodeFromString<CurrencyApiResponse>(body) }.getOrNull()
            ?: return emptyList()
        if (root.date.length < 10) return emptyList()
        val cny = root.usd["cny"] ?: return emptyList()
        if (!cny.isFinite() || cny <= 0) return emptyList()
        return listOf(root.date to cny)
    }

    @Serializable
    private data class CurrencyApiResponse(
        val date: String = "",
        val usd: Map<String, Double> = emptyMap(),
    )
}
