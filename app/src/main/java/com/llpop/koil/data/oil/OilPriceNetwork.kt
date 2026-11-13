package com.llpop.koil.data.oil

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.util.Locale

/**
 * 油价数据抓取（apizero 接口已不可用，单源）：
 * - 主源：youjia.abapi.cn（GET chart-data.php，含完整历史曲线 + 今日价）
 * 超时 8s，失败返回 success=false（调用方有兜底链）。
 *
 * 【数据源权威记录】完整来源清单/实测返回/依赖关系见工程根 API_SOURCES.md，
 * 改网络层或换源前必须同步更新该文件；用户要求不在 App 设置页展示数据源。
 * - abapi 提供：价格历史曲线（灌库/涨跌对比/偏差参考）+ 今日价（调价核验）
 * - 窗口日：xxapi/ruseo 锚点（fetchAnchorWindowDate）→ abapi 曲线 → 本地推算（见 OilPriceDbSyncer）
 * - 预测幅度：EIA 官方原油估算 + 腾讯实时降级（见 CrudeOilEstimator）
 * - 节假日：chinese-days CDN（fetchChinaHolidays）
 */
/**
 * EIA 布伦特现货日度序列 + 官方发布日。
 *
 * [points] 已按日期升序、同日以增量为准合并；[releaseDate] 是增量链路的 `release_date`
 * （`yyyy-MM-dd`），拿不到增量时为 null——只降级这个字段，[points] 照常返回 v2 那部分。
 * 两个字段刻意分开：`points.last()` 是「数据截至哪天」，[releaseDate] 是「源端哪天发的」，
 * 实测相差 3 天（11-06 的数据由 11-09 那批发布），混成一个数就没法向用户解释滞后。
 */
data class KOiLEiaBrentSeries(
    val points: List<Pair<String, Double>>,
    val releaseDate: String? = null,
)

/**
 * 源端行政区名 → App 省份主键（[Provinces.ALL]）。
 *
 * xxapi 返回「广东省 / 北京市 / 内蒙古自治区 / 广西壮族自治区 / 新疆维吾尔自治区 …」，
 * 而库主键是简称。**映射错了不会报错，只会让该省永远查不到价格**（四宫格全空），
 * 所以这里统一按后缀剥离 + 用 [Provinces.isValid] 兜底校验：不在 31 省的表（港澳台）一律返回 null。
 */
internal fun normalizeRegionName(raw: String?): String? {
    val name = raw?.trim().orEmpty()
    if (name.isEmpty()) return null
    val stripped = name
        .removeSuffix("特别行政区")
        .removeSuffix("维吾尔自治区")
        .removeSuffix("壮族自治区")
        .removeSuffix("回族自治区")
        .removeSuffix("自治区")
        .removeSuffix("省")
        .removeSuffix("市")
    return stripped.takeIf { Provinces.isValid(it) }
}

// open：单测需要 override fetchYoujiaHistory 注入假曲线（补灌空数据省回归）
open class OilPriceNetwork {

    companion object {
        const val TIMEOUT_MS = 8000L
        /** 实时价源（xxapi 主）——一次返回全国 31 省，含本轮 change */
        private const val XXAPI_URL = "https://v2.xxapi.cn/api/oilPrice"
        /** 实时价源备份（ruseo，与 xxapi 同源镜像，字段名不同） */
        private const val RUSEO_URL = "https://api.ruseo.cn/api/oilprice"
        /** 实测：不带该 UA 时部分节点返回空数组，省份编码错误也一样会静默空返回 */
        private const val LIVE_SOURCE_UA = "KOiL"
        /** 节假日 CDN 单次上限：单次 8s 在弱网下会让首屏明显等待，故压到 4s */
        private const val CDN_TIMEOUT_MS = 4_000L
        /** 发改委公告单页上限：分页抓取最多 11 页，8s/页在弱网下不可接受 */
        private const val NDRC_PAGE_TIMEOUT_MS = 6_000L
        val GRADE_LABELS = mapOf(
            "92" to "92号汽油",
            "95" to "95号汽油",
            "98" to "98号汽油",
            "0" to "0号柴油",
        )
        private val YOUJIA_GRADE_MAP = mapOf(
            "p92" to "92", "p95" to "95", "p98" to "98", "p0" to "0",
        )
    }

    private val json = Json { ignoreUnknownKeys = true }

    private val client: HttpClient by lazy {
        HttpClient {
            install(HttpTimeout) {
                requestTimeoutMillis = TIMEOUT_MS
                connectTimeoutMillis = TIMEOUT_MS
                socketTimeoutMillis = TIMEOUT_MS
            }
            install(ContentNegotiation) { json(json) }
        }
    }

    // ===== 主源：youjia.abapi.cn =====

    @Serializable
    private data class YoujiaChartResponse(
        val dates: List<String> = emptyList(),
        val p92: List<Double> = emptyList(),
        val p95: List<Double> = emptyList(),
        val p98: List<Double> = emptyList(),
        val p0: List<Double> = emptyList(),
    )

    suspend fun fetchFromYoujia(province: String): OilPriceResponse {
        val url = "https://youjia.abapi.cn/api/chart-data.php?province=${java.net.URLEncoder.encode(province, "UTF-8")}"
        return try {
            val body = client.get(url).bodyAsText()
            val resp = json.decodeFromString<YoujiaChartResponse>(body)
            if (resp.dates.isEmpty() || resp.p92.isEmpty()) {
                OilPriceResponse(success = false, error = "接口返回数据不完整")
            } else {
                val lastIdx = resp.dates.size - 1
                // 主源曲线最后日期（可能是历史滞后日期）：规范化后返回，由 syncer 校验是否为今日
                val updateDate = OilPriceDateUtil.normalize(resp.dates[lastIdx])
                val data = YOUJIA_GRADE_MAP.mapNotNull { (key, grade) ->
                    val prices = when (key) {
                        "p92" -> resp.p92
                        "p95" -> resp.p95
                        "p98" -> resp.p98
                        else -> resp.p0
                    }
                    // dates 与各油号数组不保证等长（98/0 号常短一截）。越界时只跳过该油号，
                    // 不能让一个短数组把整次请求打成 success=false —— 那会连 92/95 一起丢。
                    val px = prices.getOrNull(lastIdx) ?: return@mapNotNull null
                    OilPriceInfo(
                        grade = grade,
                        gradeLabel = GRADE_LABELS[grade] ?: grade,
                        price = px,
                        updateDate = updateDate ?: "",
                        source = "今日油价 youjia.abapi.cn",
                    )
                }
                OilPriceResponse(
                    success = data.isNotEmpty() && updateDate != null,
                    province = province,
                    updateDate = updateDate,
                    data = data,
                    error = if (data.isEmpty()) "接口返回数据不完整" else if (updateDate == null) "主源日期无法识别" else null,
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            OilPriceResponse(success = false, error = e.message ?: "Youjia 源请求失败")
        }
    }

    /** 主源一整年历史曲线点（range=year 实测 2026-10-24：约 220~222 点，覆盖近一年，含调价窗口跳跃） */
    data class YoujiaHistoryPoint(
        val date: String,
        val p92: Double?,
        val p95: Double?,
        val p98: Double?,
        val p0: Double?,
    )

    /**
     * 拉取主源一整年历史曲线：chart-data.php?province=X&range=year。
     * 返回曲线点列表（date + 4 油号），失败/空返回 emptyList（由调用方决定是否重试/跳过）。
     */
    open suspend fun fetchYoujiaHistory(province: String): List<YoujiaHistoryPoint> {
        val url = "https://youjia.abapi.cn/api/chart-data.php?province=${java.net.URLEncoder.encode(province, "UTF-8")}&range=year"
        return try {
            val body = client.get(url).bodyAsText()
            val resp = json.decodeFromString<YoujiaChartResponse>(body)
            if (resp.dates.isEmpty()) return emptyList()
            resp.dates.mapIndexedNotNull { i, date ->
                val norm = OilPriceDateUtil.normalize(date) ?: return@mapIndexedNotNull null
                YoujiaHistoryPoint(
                    date = norm,
                    p92 = resp.p92.getOrNull(i),
                    p95 = resp.p95.getOrNull(i),
                    p98 = resp.p98.getOrNull(i),
                    p0 = resp.p0.getOrNull(i),
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyList()
        }
    }

    // ===== 权威调价窗口日：国家发改委官网 xwfb 列表（当年 1-1 至今，含调价与搁浅） =====
    // 实测（2026-10-24）：https://www.ndrc.gov.cn/xwdt/xwfb/ 列表页 + index_N.html 分页可回溯任意年份；
    // 公告标题格式 "2026年10月15日国内成品油价格不作调整" / "2026年9月24日国家继续实施调控，成品油价格适当调整"，
    // 年份+月日即权威调价窗口日。抓取失败返回 emptyList（由调用方降级为 abapi 原日期）。
    private val ndrcDateRegex = Regex("""title="(\d{4})年(\d{1,2})月(\d{1,2})日[^"]*成品油[^"]*""")

    /**
     * 抓取发改委当年（1 月 1 日至今）的全部调价窗口日（yyyy-MM-dd 升序、去重，含搁浅窗口）。
     * 翻页从首页到 index_N，直到某页无当年公告（已到上一年）即停；单页解析失败跳过继续，全部失败返回 emptyList。
     */
    suspend fun fetchNdrcAdjustDates(): List<String> {
        val today = LocalDate.now()
        val year = today.year
        val dates = sortedSetOf<String>()
        var page = 0
        while (page <= 10) {
            val url = if (page == 0) "https://www.ndrc.gov.cn/xwdt/xwfb/"
            else "https://www.ndrc.gov.cn/xwdt/xwfb/index_$page.html"
            val html = try {
                // 单页 6s 上限：整条链路最坏情况曾到 11 页 × 8s，弱网首装首屏长时间空白
                withTimeoutOrNull(NDRC_PAGE_TIMEOUT_MS) { client.get(url).bodyAsText() } ?: break
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                break
            }
            if (html.isBlank()) break
            var anyCurrentYear = false
            ndrcDateRegex.findAll(html).forEach { m ->
                val y = m.groupValues[1].toInt()
                val mon = m.groupValues[2].toInt()
                val d = m.groupValues[3].toInt()
                if (y == year && (mon in 1..12) && (d in 1..31)) {
                    anyCurrentYear = true
                    val date = "%04d-%02d-%02d".format(Locale.ROOT, y, mon, d)
                    if (date <= today.format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE)) {
                        dates.add(date)
                    }
                } else if (y > year) {
                    // 罕见：未来年份公告，忽略
                }
            }
            // 当前页已无当年公告 → 已回溯到上一年，停止
            if (!anyCurrentYear) break
            page++
            delay(150)
        }
        return dates.toList()
    }

    // ===== 节假日来源：chinese-days（jsDelivr CDN + npm，含调休补班） =====
    // 实测（2026-10-24）：https://cdn.jsdelivr.net/npm/chinese-days/dist/years/{年}.json
    // 返回 {"holidays":{日期:信息}, "workdays":{日期:信息}, "inLieuDays":{...}}；
    // holidays=放假日，workdays=调休补班日（周末上班）。据此可按《石油价格管理办法》
    // （每 10 个工作日、遇节假日顺延、调休补班算工作日）推算全年调价窗口日。
    // jsDelivr CDN + npm 分发、GitHub Action 自动抓取国务院通知 → 稳定、长期维护、免 key。
    @Serializable
    private data class ChineseDaysPayload(
        val holidays: Map<String, String> = emptyMap(),
        val workdays: Map<String, String> = emptyMap(),
    )

    /** 当年节假日数据：放假日 + 调休补班日（日期集合 yyyy-MM-dd）。抓取失败返回空（调用方降级）。 */
    data class ChinaHolidays(val holidayDates: Set<String>, val workdayDates: Set<String>) {
        companion object { val EMPTY = ChinaHolidays(emptySet(), emptySet()) }
    }

    /** 拉取指定年份节假日+调休（chinese-days）；失败/无数据返回空（调用方降级用 abapi 原日期）。 */
    suspend fun fetchChinaHolidays(year: Int): ChinaHolidays {
        // 多 CDN 回退：jsDelivr（实测部分网络 502）→ 国内 npmmirror → unpkg 兜底。
        // 每个 CDN 单独 4s 上限：共用 8s 全局超时的话，弱网 3 年 × 3 源串行最坏 72s。
        val urls = listOf(
            "https://cdn.jsdelivr.net/npm/chinese-days/dist/years/$year.json",
            "https://registry.npmmirror.com/chinese-days/latest/files/dist/years/$year.json",
            "https://unpkg.com/chinese-days/dist/years/$year.json",
        )
        for (url in urls) {
            try {
                val body = withTimeoutOrNull(CDN_TIMEOUT_MS) { client.get(url).bodyAsText() } ?: continue
                val payload = json.decodeFromString<ChineseDaysPayload>(body)
                if (payload.holidays.isEmpty() && payload.workdays.isEmpty()) continue
                return ChinaHolidays(payload.holidays.keys, payload.workdays.keys)
            } catch (e: CancellationException) {
                // 外层取消（离开页面/进程回收）必须上抛；超时由 withTimeoutOrNull 返回 null，不走这里
                throw e
            } catch (e: Exception) {
                continue
            }
        }
        return ChinaHolidays.EMPTY
    }

    /**
     * 今日价抓取：实时源优先，历史曲线源兜底。
     *
     * 两个源角色不同（V1.1.14 起，实测依据见 API_SOURCES.md）：
     * - 今日价：xxapi（主）→ ruseo（同源镜像备）→ youjia（兜底，滞后 1~2 天）
     * - 历史曲线 / 灌库：youjia `range=year`（唯一提供全年曲线的源，延时已被窗口归并消化）
     *
     * 实测（2026-11-13）：官方 11-12 24 时降 645 元/吨，youjia 到 11-13 仍是旧价 8.91
     * 且日期已推到 11-13，导致核验「库内 8.91 = 采样 8.91 → 无变化 → 窗口次日 → 搁浅」；
     * 同刻 xxapi 已给出 8.39 / change -0.52。故今日价必须走实时源。
     *
     * 返回 (primary, secondary)：primary 是能代表今日价的那个源；实时源全失败时降级为 youjia，
     * 此时 [OilPriceInfo.change] 为 null，核验侧据此不下"搁浅"结论（见 OilPriceDbSyncer）。
     */
    suspend fun fetchBothSources(province: String): Pair<OilPriceResponse, OilPriceResponse> =
        coroutineScope {
            val liveDeferred = async {
                fetchFromXxapi(province).takeIf { it.success } ?: fetchFromRuseo(province)
            }
            val historyDeferred = async { fetchFromYoujia(province) }
            val live = liveDeferred.await()
            val history = historyDeferred.await()
            if (live.success) live to history else history to live
        }

    /** 实时价源 xxapi：一次返回全国 31 省 × 89/92/95/98/0，并带本轮 change */
    open suspend fun fetchFromXxapi(province: String): OilPriceResponse = try {
        val body = client.get(XXAPI_URL) {
            header("User-Agent", LIVE_SOURCE_UA)
        }.bodyAsText()
        val items = json.decodeFromString<XxapiResponse>(body).data
        val matched = items.firstOrNull { normalizeRegionName(it.regionName) == province }
        if (matched == null) {
            OilPriceResponse(
                success = false,
                province = province,
                error = "xxapi 未匹配到省份「$province」（源共 ${items.size} 条）",
            )
        } else {
            liveResponse(
                province = province,
                date = matched.date,
                source = "xxapi",
                rows = listOfNotNull(
                    matched.n92?.let { "92" to (it to matched.n92Change) },
                    matched.n95?.let { "95" to (it to matched.n95Change) },
                    matched.n98?.let { "98" to (it to matched.n98Change) },
                    matched.n0?.let { "0" to (it to matched.n0Change) },
                ),
            )
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        OilPriceResponse(success = false, province = province, error = e.message ?: "xxapi 源请求失败")
    }

    /** 实时价源 ruseo（与 xxapi 同源镜像，字段名不同）：xxapi 失败时的备份 */
    open suspend fun fetchFromRuseo(province: String): OilPriceResponse = try {
        val body = client.get(RUSEO_URL) {
            header("User-Agent", LIVE_SOURCE_UA)
        }.bodyAsText()
        val items = json.decodeFromString<RuseoResponse>(body).data?.list.orEmpty()
        val matched = items.firstOrNull { normalizeRegionName(it.region) == province }
        if (matched == null) {
            OilPriceResponse(
                success = false,
                province = province,
                error = "ruseo 未匹配到省份「$province」（源共 ${items.size} 条）",
            )
        } else {
            liveResponse(
                province = province,
                date = matched.date,
                source = "ruseo",
                rows = listOfNotNull(
                    matched.g92?.let { "92" to (it to matched.g92Chg) },
                    matched.g95?.let { "95" to (it to matched.g95Chg) },
                    matched.g98?.let { "98" to (it to matched.g98Chg) },
                    matched.d0?.let { "0" to (it to matched.d0Chg) },
                ),
            )
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        OilPriceResponse(success = false, province = province, error = e.message ?: "ruseo 源请求失败")
    }

    /** 把实时源的「油号 → (价, 变化量)」装配成 [OilPriceResponse]（xxapi / ruseo 共用） */
    private fun liveResponse(
        province: String,
        date: String?,
        source: String,
        rows: List<Pair<String, Pair<Double, Double?>>>,
    ): OilPriceResponse {
        val normDate = date?.let { OilPriceDateUtil.normalize(it) }
        val data = rows.mapNotNull { (grade, pair) ->
            val (price, change) = pair
            OilPriceInfo(
                grade = grade,
                gradeLabel = GRADE_LABELS[grade] ?: grade,
                price = price,
                updateDate = normDate ?: "",
                source = source,
                change = change,
            )
        }
        return OilPriceResponse(
            success = data.isNotEmpty() && normDate != null,
            province = province,
            updateDate = normDate,
            data = data,
            error = when {
                data.isEmpty() -> "源未返回该省价格"
                normDate == null -> "源日期无法识别"
                else -> null
            },
        )
    }

    // ===== 锚点备用源：ruseo / xxapi =====
    // 实测（2026-10-26）：两源同源镜像（价格一致），date 均为最近窗口生效日
    // （如 10-16 = 10-15 窗口 24 时生效）；故 date − 1 = 真实窗口日（含搁浅窗口）。
    // 两源仅用于取锚点窗口日，历史曲线由 abapi（fetchYoujiaHistory）提供。

    @Serializable
    private data class RuseoPriceItem(
        val region: String? = null,
        val date: String? = null,
        @SerialName("gasoline_92") val g92: Double? = null,
        @SerialName("gasoline_95") val g95: Double? = null,
        @SerialName("gasoline_98") val g98: Double? = null,
        @SerialName("diesel_0") val d0: Double? = null,
        @SerialName("gasoline_92_chg") val g92Chg: Double? = null,
        @SerialName("gasoline_95_chg") val g95Chg: Double? = null,
        @SerialName("gasoline_98_chg") val g98Chg: Double? = null,
        @SerialName("diesel_0_chg") val d0Chg: Double? = null,
    )

    @Serializable
    private data class RuseoData(
        val success: Boolean = false,
        val count: Int = 0,
        val list: List<RuseoPriceItem> = emptyList(),
    )

    @Serializable
    private data class RuseoResponse(
        val code: Int = -1,
        val msg: String? = null,
        val data: RuseoData? = null,
    )

    @Serializable
    private data class XxapiPriceItem(
        @SerialName("regionName") val regionName: String? = null,
        val date: String? = null,
        val n92: Double? = null,
        val n95: Double? = null,
        val n98: Double? = null,
        val n0: Double? = null,
        @SerialName("n92Change") val n92Change: Double? = null,
        @SerialName("n95Change") val n95Change: Double? = null,
        @SerialName("n98Change") val n98Change: Double? = null,
        @SerialName("n0Change") val n0Change: Double? = null,
    )

    @Serializable
    private data class XxapiResponse(
        val code: Int = -1,
        val msg: String? = null,
        val data: List<XxapiPriceItem> = emptyList(),
    )

    /** 锚点窗口日：xxapi → ruseo（xxapi 优先，同源互备），
     *  取最近窗口生效日（date）− 1 天；两者皆失败返回 null。
     *  返回带来源标签（xxapi/ruseo），供设置页"多源一致性"展示。 */
    suspend fun fetchAnchorWindowDate(): AnchorWindow? {
        val xxapiDate = runCatching {
            val body = client.get(XXAPI_URL).bodyAsText()
            json.decodeFromString<XxapiResponse>(body).data
                .firstOrNull { !it.date.isNullOrBlank() }?.date
        }.getOrNull()
        // 日期格式无法解析时 toAnchorWindow 返回 null。此处不能直接 return，
        // 否则会把「拿到日期但格式不认识」误当成「已得结果」，跳过 ruseo 备源白降一级。
        if (xxapiDate != null) {
            xxapiDate.toAnchorWindow("xxapi")?.let { return it }
        }
        val ruseoDate = runCatching {
            val body = client.get(RUSEO_URL).bodyAsText()
            json.decodeFromString<RuseoResponse>(body).data?.list
                ?.firstOrNull { !it.date.isNullOrBlank() }?.date
        }.getOrNull()
        if (ruseoDate != null) {
            return ruseoDate.toAnchorWindow("ruseo")
        }
        return null
    }

    /** 锚点窗口日 + 命中来源（功能3 多源一致性展示用） */
    data class AnchorWindow(val date: String, val source: String)

    private fun String.toAnchorWindow(source: String): AnchorWindow? =
        runCatching { AnchorWindow(LocalDate.parse(this).minusDays(1).toString(), source) }.getOrNull()

    // ================= 原油估算数据源（预测幅度兜底，apizero forecast 收费/不可用）=================

    @Serializable
    private data class EiaSeriesResponse(
        val response: EiaResponseBody? = null,
        val error: EiaError? = null,
    )

    @Serializable
    private data class EiaResponseBody(val total: Long = 0, val data: List<EiaPoint> = emptyList())

    @Serializable
    private data class EiaPoint(
        val period: String? = null,
        val value: Double? = null,
        val series: String? = null,
        val unit: String? = null,
    )

    @Serializable
    private data class EiaError(val code: String? = null, val message: String? = null)

    /**
     * EIA 官方布伦特现货日度价（RBRTE 系列，美元/桶，交易日数据，含完整历史）。
     * 返回 [KOiLEiaBrentSeries]：按日期升序的点集（v2 的 `length` 条 + WPSR 增量最多 6 条按日期合并，
     * 重叠日期以增量为准，所以条数可能超过 `length`，默认最多 30 + 6），外加源端发布日。
     * 不做 `takeLast(length)` 截断——被截掉的正是最老那几天，而基准窗口就落在最老端，
     * 截一刀会让基准样本不足，整块预测直接消失。
     * Key 无效/接口失败返回空序列（[KOiLEiaBrentSeries.points] 为空）。
     *
     * 数据取自两条同源链路的并集：v2 API 拿历史（实测比 WPSR 价格表慢 3 天），
     * WPSR 价格表 JSON 补最近 6 个交易日（见 [KOiLEiaSpotTableParser]）。
     * 增量失败一律静默降级为纯 v2——功能不挂，只是数据旧几天、且没有发布日可显示。
     */
    // open：单测需要注入假 EIA 曲线（验证「EIA 汇率失败后腾讯路径不再重复同步取数」）
    open suspend fun fetchEiaBrentDaily(apiKey: String, length: Int = 30): KOiLEiaBrentSeries {
        if (apiKey.isBlank()) return KOiLEiaBrentSeries(emptyList())
        val url = "https://api.eia.gov/v2/petroleum/pri/spt/data/" +
            "?frequency=daily" +
            "&data%5B0%5D=value" +
            "&facets%5Bseries%5D%5B%5D=RBRTE" +
            "&sort%5B0%5D%5Bcolumn%5D=period&sort%5B0%5D%5Bdirection%5D=desc" +
            "&length=$length&api_key=$apiKey"
        val base = try {
            val body = client.get(url).bodyAsText()
            val resp = json.decodeFromString<EiaSeriesResponse>(body)
            if (resp.error != null || resp.response == null) {
                emptyList()
            } else {
                resp.response.data
                    .mapNotNull { p ->
                        val period = p.period?.takeIf { it.length >= 10 } ?: return@mapNotNull null
                        val value = p.value?.takeIf { it.isFinite() && it > 0 } ?: return@mapNotNull null
                        period to value
                    }
                    .sortedBy { it.first }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyList()
        }
        // v2 没拿到任何点就不补增量：只有 6 个点也过不了 MIN_EIA_POINTS，
        // 反而会把「主源失败」伪装成「拿到了数据」
        if (base.isEmpty()) return KOiLEiaBrentSeries(emptyList())
        val table = try {
            KOiLEiaSpotTableParser.parseWithMeta(client.get(KOiLEiaSpotTableParser.URL).bodyAsText())
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            KOiLEiaSpotTableParser.EiaSpotTable(emptyList())
        }
        // 发布日按**合并后**的最终点集再校验一次：增量自查只保证「发布日 ≥ 增量最后一天」，
        // 若 v2 反而更新（增量到 11-06、v2 到 11-10、release_date 11-09），合并后会显示
        // 「截至 11-10 · 源端 11-09 发布」——发布日早于数据日，正是本 PR 第三条不变量要挡的情况。
        // 只丢 releaseDate，points 保留 v2 数据。
        val merged = KOiLEiaSpotTableParser.merge(base, table.points)
        return KOiLEiaBrentSeries(
            points = merged,
            releaseDate = KOiLEiaSpotTableParser.resolveReleaseDate(table.releaseDate, merged),
        )
    }

    /**
     * 腾讯财经布伦特原油期货实时行情（qt.gtimg.cn，公开免 Key）：
     * 返回 (现价, 昨收) 美元/桶；失败返回 null。
     * 实测返回示例：v_hf_OIL="110.45,-1.12,110.39,110.40,110.98,109.22,15:46:57,111.70,110.85,0,3,4,2026-10-26,布伦特原油"
     * 字段：index0=现价、index7=昨收（验证：现价110.45/涨跌幅-1.12% → 昨收=110.45/(1-0.0112)=111.70 ✓）
     */
    open suspend fun fetchTencentBrent(): Pair<Double, Double>? {
        return try {
            val body = client.get("https://qt.gtimg.cn/q=hf_OIL") {
                header("Referer", "https://gu.qq.com/")
                header("User-Agent", "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36")
            }.bodyAsText()
            val m = Regex("""v_hf_OIL="([^"]*)"""").find(body) ?: return null
            val fields = m.groupValues[1].split(",")
            if (fields.size < 8) return null
            val current = fields[0].toDoubleOrNull() ?: return null
            val prevClose = fields[7].toDoubleOrNull() ?: return null
            if (current <= 0 || prevClose <= 0) null else current to prevClose
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

}
