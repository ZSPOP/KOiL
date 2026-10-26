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
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate

/**
 * 油价双源抓取（对齐 Web 端 oil-price.service.ts）：
 * - 主源：youjia.abapi.cn（GET chart-data.php，含完整历史曲线）
 * - 次源：v1.apizero.cn（POST oil-price，含 next_adjustment / forecast）
 * 两源独立超时 8s，任一失败不影响另一源。
 *
 * 【数据源权威记录】完整来源清单/实测返回/依赖关系见工程根 API_SOURCES.md，
 * 改网络层或换源前必须同步更新该文件；用户要求不在 App 设置页展示数据源。
 * - 主源提供：价格历史曲线（涨跌对比、偏差参考）
 * - 次源提供：今日价 + 调价窗口（next_adjustment）+ 预测（forecast）
 * - ⚠️ 窗口/预测/版本号 100% 依赖次源 apizero，属单点风险
 * - APIZERO_TYPE_MAP 需同时兼容 "92#" 旧格式与 "gasoline_92" 实测新格式
 */
class OilPriceNetwork {

    companion object {
        const val TIMEOUT_MS = 8000L
        val GRADE_LABELS = mapOf(
            "92" to "92号汽油",
            "95" to "95号汽油",
            "98" to "98号汽油",
            "0" to "0号柴油",
        )
        // 实测（2026-10-22）apizero 返回 type 为 "gasoline_92"/"gasoline_95"/"gasoline_98"/"diesel_0"；
        // 兼容旧格式 "92#"/"95#"/"98#"/"0#"（历史接口）
        private val APIZERO_TYPE_MAP = mapOf(
            "92#" to "92", "95#" to "95", "98#" to "98", "0#" to "0",
            "gasoline_92" to "92", "gasoline_95" to "95",
            "gasoline_98" to "98", "diesel_0" to "0",
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
                    if (prices.isEmpty()) null
                    else OilPriceInfo(
                        grade = grade,
                        gradeLabel = GRADE_LABELS[grade] ?: grade,
                        price = prices[lastIdx],
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
    suspend fun fetchYoujiaHistory(province: String): List<YoujiaHistoryPoint> {
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
                client.get(url).bodyAsText()
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
                    val date = "%04d-%02d-%02d".format(y, mon, d)
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
        // 多 CDN 回退：jsDelivr（实测部分网络 502）→ unpkg → 国内 npmmirror
        val urls = listOf(
            "https://cdn.jsdelivr.net/npm/chinese-days/dist/years/$year.json",
            "https://unpkg.com/chinese-days/dist/years/$year.json",
            "https://registry.npmmirror.com/chinese-days/latest/files/dist/years/$year.json",
        )
        for (url in urls) {
            try {
                val body = client.get(url).bodyAsText()
                val payload = json.decodeFromString<ChineseDaysPayload>(body)
                if (payload.holidays.isEmpty() && payload.workdays.isEmpty()) continue
                return ChinaHolidays(payload.holidays.keys, payload.workdays.keys)
            } catch (e: Exception) {
                continue
            }
        }
        return ChinaHolidays.EMPTY
    }

    @Serializable
    private data class ApiZeroPriceItem(
        val type: String = "",
        val price: Double = 0.0,
        val name: String = "",
    )

    @Serializable
    private data class ApiZeroData(
        val province: String? = null,
        @SerialName("update_date") val updateDate: String? = null,
        @SerialName("next_adjustment") val nextAdjustment: String? = null,
        val forecast: String? = null,
        val prices: List<ApiZeroPriceItem> = emptyList(),
    )

    @Serializable
    private data class ApiZeroResponse(
        val code: Int = -1,
        val message: String? = null,
        val msg: String? = null,
        val data: ApiZeroData? = null,
    )

    suspend fun fetchFromApiZero(province: String): OilPriceResponse {
        return try {
            val body = client.post("https://v1.apizero.cn/api/oil-price") {
                contentType(ContentType.Application.Json)
                setBody("""{"province":"$province"}""")
            }.bodyAsText()
            val resp = json.decodeFromString<ApiZeroResponse>(body)
            val data = resp.data
            if ((resp.code != 200 && resp.code != 0) || data?.prices.isNullOrEmpty()) {
                OilPriceResponse(
                    success = false,
                    error = resp.message ?: resp.msg ?: "接口返回异常",
                )
            } else {
                val items = data!!.prices.mapNotNull { item ->
                    val grade = APIZERO_TYPE_MAP[item.type] ?: return@mapNotNull null
                    OilPriceInfo(
                        grade = grade,
                        gradeLabel = GRADE_LABELS[grade] ?: grade,
                        price = item.price,
                        updateDate = data.updateDate ?: "",
                        source = "ApiZero 油价",
                    )
                }
                OilPriceResponse(
                    success = items.isNotEmpty(),
                    province = province,
                    updateDate = OilPriceDateUtil.normalize(data.updateDate),
                    nextAdjustment = data.nextAdjustment,
                    forecast = data.forecast,
                    data = items,
                    error = if (items.isEmpty()) "接口返回数据不完整" else null,
                )
            }
        } catch (e: Exception) {
            OilPriceResponse(success = false, error = e.message ?: "ApiZero 源请求失败")
        }
    }

    /** 双源并行抓取（对齐 Web fetchBothSources：主源优先语义由调用方处理） */
    suspend fun fetchBothSources(province: String): Pair<OilPriceResponse, OilPriceResponse> =
        coroutineScope {
            val primaryDeferred = async { fetchFromYoujia(province) }
            val secondaryDeferred = async { fetchFromApiZero(province) }
            primaryDeferred.await() to secondaryDeferred.await()
        }

    // ===== 预测专接口：v1.apizero.cn /api/oil-price-forecast（窗口日精确化） =====
    // 实测（2026-10-22）：返回 next_adjust_date（yyyy-MM-dd 精确窗口日）+ days_remaining
    // + prediction{direction, estimated_change_per_ton, confidence}。
    // 注意：预测幅度口径与本源聚合接口 forecast 不一致（专接口偏激，如 1215 vs 270 元/吨），
    // 故仅采用其结构化窗口日/方向/置信度，预测幅度仍以聚合接口 forecast 为准。

    @Serializable
    private data class ForecastPrediction(
        val direction: String? = null,
        val confidence: String? = null,
    )

    @Serializable
    private data class ForecastData(
        @SerialName("next_adjust_date") val nextAdjustDate: String? = null,
        @SerialName("days_remaining") val daysRemaining: Int? = null,
        val prediction: ForecastPrediction? = null,
    )

    @Serializable
    private data class ForecastResponse(
        val code: Int = -1,
        val msg: String? = null,
        val message: String? = null,
        val data: ForecastData? = null,
    )

    /** 抓取预测专接口（窗口日/方向/置信度；失败时 success=false，不影响主流程） */
    suspend fun fetchForecast(province: String): OilPriceResponse {
        return try {
            val body = client.post("https://v1.apizero.cn/api/oil-price-forecast") {
                contentType(ContentType.Application.Json)
                setBody("""{"province":"$province"}""")
            }.bodyAsText()
            val resp = json.decodeFromString<ForecastResponse>(body)
            val data = resp.data
            if ((resp.code != 200 && resp.code != 0) || data?.nextAdjustDate.isNullOrBlank()) {
                OilPriceResponse(
                    success = false,
                    province = province,
                    error = resp.message ?: resp.msg ?: "预测接口返回异常",
                )
            } else {
                val normalized = OilPriceDateUtil.normalize(data!!.nextAdjustDate)
                OilPriceResponse(
                    success = normalized != null,
                    province = province,
                    nextAdjustDate = normalized,
                    direction = data.prediction?.direction,
                    confidence = data.prediction?.confidence,
                    error = if (normalized == null) "预测接口窗口日无法识别" else null,
                )
            }
        } catch (e: Exception) {
            OilPriceResponse(success = false, error = e.message ?: "预测接口请求失败")
        }
    }
    // ===== 锚点备用源：ruseo / xxapi（全国油价快照） =====
    // 实测（2026-10-26）：两源同源镜像（价格一致），date 均为最近窗口生效日
    // （如 10-16 = 10-15 窗口 24 时生效）；故 date − 1 = 真实窗口日（含搁浅窗口）。
    // 全国快照仅含最新一期（无历史曲线），用于锚点 + 补灌各省最新价（省份覆盖提速）。

    /** 全国快照单省数据（date = 生效日；价格滞后但真实） */
    data class NationwidePrice(
        val region: String,
        val date: String?,
        val g92: Double?,
        val g95: Double?,
        val g98: Double?,
        val d0: Double?,
    )

    @Serializable
    private data class RuseoPriceItem(
        val region: String? = null,
        val date: String? = null,
        @SerialName("gasoline_92") val g92: Double? = null,
        @SerialName("gasoline_95") val g95: Double? = null,
        @SerialName("gasoline_98") val g98: Double? = null,
        @SerialName("diesel_0") val d0: Double? = null,
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
    )

    @Serializable
    private data class XxapiResponse(
        val code: Int = -1,
        val msg: String? = null,
        val data: List<XxapiPriceItem> = emptyList(),
    )

    /** 锚点窗口日：xxapi → ruseo（v1.4.184 起 xxapi 优先，同源互备），
     *  取最近窗口生效日（date）− 1 天；两者皆失败返回 null。 */
    suspend fun fetchAnchorWindowDate(): String? {
        val date = runCatching {
            val body = client.get("https://v2.xxapi.cn/api/oilPrice").bodyAsText()
            json.decodeFromString<XxapiResponse>(body).data
                .firstOrNull { !it.date.isNullOrBlank() }?.date
        }.getOrNull()
            ?: runCatching {
                val body = client.get("https://api.ruseo.cn/api/oilprice").bodyAsText()
                json.decodeFromString<RuseoResponse>(body).data?.list
                    ?.firstOrNull { !it.date.isNullOrBlank() }?.date
            }.getOrNull()
        return date?.let { runCatching { LocalDate.parse(it).minusDays(1).toString() }.getOrNull() }
    }

    /** 全国最新价快照（ruseo 优先 → xxapi 兜底）；两者皆失败返回空列表。 */
    suspend fun fetchNationwideSnapshot(): List<NationwidePrice> {
        val ruseo = runCatching { fetchRuseoSnapshot() }.getOrDefault(emptyList())
        if (ruseo.isNotEmpty()) return ruseo
        return runCatching { fetchXxapiSnapshot() }.getOrDefault(emptyList())
    }

    private suspend fun fetchRuseoSnapshot(): List<NationwidePrice> {
        val body = client.get("https://api.ruseo.cn/api/oilprice").bodyAsText()
        val resp = json.decodeFromString<RuseoResponse>(body)
        if (resp.code != 0) return emptyList()
        return resp.data?.list.orEmpty().mapNotNull { item ->
            val region = item.region ?: return@mapNotNull null
            NationwidePrice(region, item.date, item.g92, item.g95, item.g98, item.d0)
        }
    }

    private suspend fun fetchXxapiSnapshot(): List<NationwidePrice> {
        val body = client.get("https://v2.xxapi.cn/api/oilPrice").bodyAsText()
        val resp = json.decodeFromString<XxapiResponse>(body)
        if (resp.code != 200) return emptyList()
        return resp.data.mapNotNull { item ->
            val region = item.regionName ?: return@mapNotNull null
            NationwidePrice(region, item.date, item.n92, item.n95, item.n98, item.n0)
        }
    }

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
     * 返回按日期升序的 (date, price) 列表（最多 length 条）；Key 无效/接口失败返回空列表。
     * 实测：免费注册 Key 即用，配额 1000 次/日；数据 T+1 更新（美国能源署）。
     */
    suspend fun fetchEiaBrentDaily(apiKey: String, length: Int = 30): List<Pair<String, Double>> {
        if (apiKey.isBlank()) return emptyList()
        val url = "https://api.eia.gov/v2/petroleum/pri/spt/data/" +
            "?frequency=daily" +
            "&data%5B0%5D=value" +
            "&facets%5Bseries%5D%5B%5D=RBRTE" +
            "&sort%5B0%5D%5Bcolumn%5D=period&sort%5B0%5D%5Bdirection%5D=desc" +
            "&length=$length&api_key=$apiKey"
        return try {
            val body = client.get(url).bodyAsText()
            val resp = json.decodeFromString<EiaSeriesResponse>(body)
            if (resp.error != null || resp.response == null) return emptyList()
            resp.response.data
                .mapNotNull { p -> p.period?.takeIf { it.length >= 10 }?.let { it to (p.value ?: 0.0) } }
                .sortedBy { it.first }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * 腾讯财经布伦特原油期货实时行情（qt.gtimg.cn，公开免 Key）：
     * 返回 (现价, 昨收) 美元/桶；失败返回 null。
     * 实测返回示例：v_hf_OIL="110.45,-1.12,110.39,110.40,110.98,109.22,15:46:57,111.70,110.85,0,3,4,2026-10-26,布伦特原油"
     * 字段：index0=现价、index7=昨收（验证：现价110.45/涨跌幅-1.12% → 昨收=110.45/(1-0.0112)=111.70 ✓）
     */
    suspend fun fetchTencentBrent(): Pair<Double, Double>? {
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
        } catch (_: Exception) {
            null
        }
    }

}
