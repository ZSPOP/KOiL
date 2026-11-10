package com.llpop.koil

import androidx.test.core.app.ApplicationProvider
import com.llpop.koil.data.oil.KOiLUsdCnyRateRepository
import com.llpop.koil.data.oil.KOiLUsdCnyRateSnapshot
import com.llpop.koil.data.oil.KOiLUsdCnyRateSource
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 汇率仓库回归（Robolectric：DataStore 需要真实 Context）。
 *
 * 覆盖三层：单源解析 → 月均聚合与护栏 → 三级回退链。
 * 所有样本都是各源**真实响应的裁剪版**，不是构造的理想数据——
 * 理想数据测不出「SDMX 两个系列跨日对齐」「XML 只给一天」这些真实约束。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class KOiLUsdCnyRateRepositoryTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    // ---------- 样本 ----------

    /** SDMX csvdata 裁剪版：CNY/EUR 与 USD/EUR 各 3 天。真实响应有 35 列，这里保留前 9 列的形状 */
    private val sdmxCsv = """
        KEY,FREQ,CURRENCY,CURRENCY_DENOM,EXR_TYPE,EXR_SUFFIX,TIME_PERIOD,OBS_VALUE,OBS_STATUS
        EXR.D.CNY.EUR.SP00.A,D,CNY,EUR,SP00,A,2026-11-05,7.4846,A
        EXR.D.CNY.EUR.SP00.A,D,CNY,EUR,SP00,A,2026-11-06,7.4485,A
        EXR.D.CNY.EUR.SP00.A,D,CNY,EUR,SP00,A,2026-11-09,7.4689,A
        EXR.D.USD.EUR.SP00.A,D,USD,EUR,SP00,A,2026-11-05,1.1224,A
        EXR.D.USD.EUR.SP00.A,D,USD,EUR,SP00,A,2026-11-06,1.1165,A
        EXR.D.USD.EUR.SP00.A,D,USD,EUR,SP00,A,2026-11-09,1.1193,A
    """.trimIndent()

    /** ECB 官方 XML 裁剪版：只有一个 <Cube time>，即最新一个交易日 */
    private val ecbXml = """
        <gesmes:Envelope xmlns:gesmes="http://www.gesmes.org/xml/2002-08-01">
          <Cube>
            <Cube time='2026-11-09'>
              <Cube currency='USD' rate='1.1193'/>
              <Cube currency='CNY' rate='7.4689'/>
              <Cube currency='JPY' rate='176.79'/>
            </Cube>
          </Cube>
        </gesmes:Envelope>
    """.trimIndent()

    /** currency-api：聚合源，只取 usd.cny，日期以响应体为准 */
    private val currencyApiJson = """{"date":"2026-11-07","usd":{"cny":6.6642,"eur":0.89065292}}"""

    private fun repository(
        sdmx: Pair<HttpStatusCode, String>? = null,
        xml: Pair<HttpStatusCode, String>? = null,
        api: Pair<HttpStatusCode, String>? = null,
    ): KOiLUsdCnyRateRepository {
        val engine = MockEngine { request ->
            val url = request.url.toString()
            val chosen = when {
                url.contains("data-api.ecb.europa.eu") -> sdmx
                url.contains("eurofxref") -> xml
                url.contains("currency-api") -> api
                else -> null
            }
            if (chosen == null) {
                respond("", HttpStatusCode.NotFound)
            } else {
                respond(chosen.second, chosen.first)
            }
        }
        return KOiLUsdCnyRateRepository(context, engine)
    }

    // ---------- 解析 ----------

    @Test
    fun `SDMX 按日交叉相除而不是先月均再相除`() {
        val repo = repository()
        val points = repo.parseSdmx(sdmxCsv)
        assertEquals(3, points.size)
        // 逐日：7.4846÷1.1224、7.4485÷1.1165、7.4689÷1.1193
        assertEquals("2026-11-05", points[0].first)
        assertEquals(6.6684, points[0].second, 0.0002)
        assertEquals(6.6728, points[2].second, 0.0002)
    }

    @Test
    fun `SDMX 缺一个系列或该日脏值只跳过当天`() {
        val repo = repository()
        // 11-06 只有 CNY 没有 USD → 该日无交叉汇率，跳过；其余两天不受影响
        val body = sdmxCsv.lineSequence()
            .filterNot { it.startsWith("EXR.D.USD.") && it.contains("2026-11-06") }
            .joinToString("\n")
        val points = repo.parseSdmx(body)
        assertEquals(listOf("2026-11-05", "2026-11-09"), points.map { it.first })
    }

    @Test
    fun `ECB XML 只产出最新一天一个点`() {
        val repo = repository()
        val points = repo.parseEcbXml(ecbXml)
        assertEquals(1, points.size)
        assertEquals("2026-11-09", points[0].first)
        assertEquals(6.6728, points[0].second, 0.0002)
    }

    @Test
    fun `ECB XML 缺 CNY 时返回空而不是拿半个值`() {
        val repo = repository()
        assertTrue(repo.parseEcbXml(ecbXml.replace("currency='CNY'", "currency='XXX'")).isEmpty())
    }

    @Test
    fun `currency-api 只认响应体里的日期`() {
        val repo = repository()
        val points = repo.parseCurrencyApi(currencyApiJson)
        // 请求发生在今天（2026-11-10），但响应体说 11-07 —— 必须用 11-07，
        // 否则会把周五的数据标成周六，dataThrough 就是编的
        assertEquals("2026-11-07", points[0].first)
        assertEquals(6.6642, points[0].second, 0.0001)
    }

    @Test
    fun `currency-api 缺 cny 或日期非法时返回空`() {
        val repo = repository()
        assertTrue(repo.parseCurrencyApi("""{"date":"2026-11-07","usd":{"eur":0.89}}""").isEmpty())
        assertTrue(repo.parseCurrencyApi("""{"date":"","usd":{"cny":6.66}}""").isEmpty())
        assertTrue(repo.parseCurrencyApi("not json").isEmpty())
    }

    // ---------- 聚合与护栏 ----------

    @Test
    fun `聚合取月内均值并标记样本天数与数据日`() {
        val repo = repository()
        val points = repo.parseSdmx(sdmxCsv)
        val snap = repo.aggregate(points, KOiLUsdCnyRateSource.SDMX, null)!!
        assertEquals("2026-11", snap.month)
        assertEquals(3, snap.sampleDays)
        assertEquals("2026-11-09", snap.dataThrough)
        // (6.6684 + 6.6713 + 6.6728) / 3
        assertEquals(6.6708, snap.rate, 0.0005)
    }

    @Test
    fun `上月均值作为样本不足时的回退值`() {
        val repo = repository()
        val points = listOf(
            "2026-10-29" to 6.6850,
            "2026-10-30" to 6.6850,
            "2026-11-02" to 6.6483, // 本月仅 1 天
        )
        val snap = repo.aggregate(points, KOiLUsdCnyRateSource.SDMX, null)!!
        assertEquals(1, snap.sampleDays)
        assertEquals("2026-10", snap.fallbackMonth)
        assertEquals(6.6850, snap.fallbackRate, 0.0001)
        // sampleDays < 3 → 回退上月，而不是拿单日值冒充月均
        assertEquals(6.6850, repo.pick(snap, "2026-11-05")!!, 0.0001)
    }

    @Test
    fun `汇率超出合理区间时整条丢弃`() {
        val repo = repository()
        // 被劫持/解析错位可能给出 60 或 660 这种量级，宁可当次刷新失败也不写进缓存
        assertNull(repo.aggregate(listOf("2026-11-09" to 60.0), KOiLUsdCnyRateSource.SDMX, null))
        assertNull(repo.aggregate(listOf("2026-11-09" to 0.0), KOiLUsdCnyRateSource.SDMX, null))
        assertNull(repo.aggregate(listOf("2026-11-09" to -6.6), KOiLUsdCnyRateSource.SDMX, null))
    }

    @Test
    fun `与上次缓存偏差超过百分之三时丢弃本次结果`() {
        val repo = repository()
        val previous = KOiLUsdCnyRateSnapshot(
            month = "2026-11",
            rate = 6.6676,
            sampleDays = 6,
            dataThrough = "2026-11-09",
            fallbackMonth = "2026-10",
            fallbackRate = 6.6850,
            fetchedAt = System.currentTimeMillis(),
            source = KOiLUsdCnyRateSource.SDMX,
        )
        // 6.6676 → 7.0000 是 5%，超阈值：判为异常，保留旧值
        assertNull(repo.aggregate(listOf("2026-11-09" to 7.0), KOiLUsdCnyRateSource.SDMX, previous))
        // 6.6676 → 6.7000 是 0.5%，正常波动，接受
        assertNotNull(repo.aggregate(listOf("2026-11-09" to 6.70), KOiLUsdCnyRateSource.SDMX, previous))
    }

    @Test
    fun `目标月与缓存月不同时直接用缓存值`() {
        val repo = repository()
        val snap = KOiLUsdCnyRateSnapshot(
            month = "2026-11",
            rate = 6.6676,
            sampleDays = 6,
            dataThrough = "2026-11-09",
            fallbackMonth = "2026-10",
            fallbackRate = 6.6850,
            fetchedAt = System.currentTimeMillis(),
            source = KOiLUsdCnyRateSource.SDMX,
        )
        // 目标日期不在缓存月内（缓存已过期、预测日期更新）→ 用缓存月均值兜底，
        // 不是用 fallbackRate：后者是「当月样本不足」时的替代，两者触发条件不同
        assertEquals(6.6676, repo.pick(snap, "2026-10-15")!!, 0.0001)
        assertEquals(6.6676, repo.pick(snap, "2026-11-05")!!, 0.0001) // 当月且样本够 → 月均值
    }

    // ---------- 回退链 ----------

    @Test
    fun `主源可用时用 SDMX`() = runBlocking {
        val repo = repository(sdmx = HttpStatusCode.OK to sdmxCsv)
        val snap = repo.refreshNow()!!
        assertEquals(KOiLUsdCnyRateSource.SDMX, snap.source)
        assertEquals(3, snap.sampleDays)
    }

    @Test
    fun `SDMX 失败时回退 ECB XML`() = runBlocking {
        val repo = repository(
            sdmx = HttpStatusCode.InternalServerError to "",
            xml = HttpStatusCode.OK to ecbXml,
        )
        val snap = repo.refreshNow()!!
        assertEquals(KOiLUsdCnyRateSource.ECB_XML, snap.source)
        assertEquals(1, snap.sampleDays) // XML 只给最新一天
        assertEquals("2026-11-09", snap.dataThrough)
    }

    @Test
    fun `SDMX 与 XML 都失败时回退 currency-api`() = runBlocking {
        val repo = repository(
            sdmx = HttpStatusCode.InternalServerError to "",
            xml = HttpStatusCode.InternalServerError to "",
            api = HttpStatusCode.OK to currencyApiJson,
        )
        val snap = repo.refreshNow()!!
        assertEquals(KOiLUsdCnyRateSource.CURRENCY_API, snap.source)
        assertEquals("2026-11-07", snap.dataThrough) // 以响应体日期为准
    }

    @Test
    fun `三个源全失败时返回 null 而不是假值`() = runBlocking {
        val repo = repository(
            sdmx = HttpStatusCode.InternalServerError to "",
            xml = HttpStatusCode.InternalServerError to "",
            api = HttpStatusCode.InternalServerError to "",
        )
        assertNull(repo.refreshNow())
    }
}
