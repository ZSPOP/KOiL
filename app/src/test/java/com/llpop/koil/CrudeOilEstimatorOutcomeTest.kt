package com.llpop.koil

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.llpop.koil.data.local.AppDatabase
import com.llpop.koil.data.oil.CrudeOilEstimator
import com.llpop.koil.data.oil.KOiLUsdCnyRateRepository
import com.llpop.koil.data.oil.KOiLUsdCnyRateSnapshot
import com.llpop.koil.data.oil.KOiLUsdCnyRateSource
import com.llpop.koil.data.oil.OilPriceNetwork
import com.llpop.koil.data.oil.RateResult
import com.llpop.koil.data.settings.AppSettings
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * estimateDetailed 的结果分类回归（Robolectric：DataStore/Room 需要真实 Context）。
 *
 * 覆盖 UI 空态的依据链——失败必须带原因，否则「汇率没取到（快照到位后能自动出数）」
 * 和「EIA/腾讯都不可用」在 UI 上只能混成一种空态：
 * - 只有汇率取不到 → FxUnavailable
 * - 腾讯也挂了 → Unavailable
 * - 汇率可用 + 腾讯可用 → Success（并携带本次实际依据的快照，UI 判 stale 用它而非事后重读）
 *
 * 汇率用 Fake 注入而不是真实仓库：Robolectric 同配置的用例共享 DataStore 单例，
 * 其他测试类落过缓存会让「全失败」路径读到值，结果随用例执行顺序漂移。
 * 仓库本身的取数/回退链在 [KOiLUsdCnyRateRepositoryTest] 覆盖。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class CrudeOilEstimatorOutcomeTest {

    private lateinit var db: AppDatabase
    private lateinit var settings: AppSettings

    /**
     * 汇率固定返回注入值（null = 模拟「无缓存且所有源取数失败」）；
     * [rateForCalls] 计数同步取数尝试次数——首次安装无缓存时每次 rateForDetailed
     * 都是一轮 15s 级同步等待，估算器一轮内只允许发生一次。
     */
    private inner class FakeFx(private val rate: Double?) :
        KOiLUsdCnyRateRepository(
            ApplicationProvider.getApplicationContext<Context>(),
            MockEngine { respond("", HttpStatusCode.InternalServerError) },
        ) {
        var rateForCalls = 0
        val fxSnapshot = KOiLUsdCnyRateSnapshot(
            month = "2026-11",
            rate = 6.6712,
            sampleDays = 21,
            dataThrough = "2026-11-10",
            fallbackMonth = "2026-10",
            fallbackRate = 6.66,
            fetchedAt = System.currentTimeMillis(),
            source = KOiLUsdCnyRateSource.SDMX,
        )

        override suspend fun rateForDetailed(date: String): RateResult? {
            rateForCalls++ // 每次调用 = 一轮 15s 级同步尝试（无缓存场景）
            return rate?.let { RateResult(it, fxSnapshot) }
        }

        override suspend fun currentSnapshot(): KOiLUsdCnyRateSnapshot? = null
    }

    /** 腾讯源固定返回 105.0 / 104.0（涨 1 USD/bbl）；传 null 模拟挂掉 */
    private inner class FakeNetwork(private val quote: Pair<Double, Double>?) : OilPriceNetwork() {
        override suspend fun fetchTencentBrent(): Pair<Double, Double>? = quote
    }

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        settings = AppSettings(context)
        settings.setEiaEnabled(false) // 只测腾讯路径；EIA 路径的窗口逻辑在 CrudeOilEstimatorTest
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun estimator(network: OilPriceNetwork, fx: KOiLUsdCnyRateRepository) =
        CrudeOilEstimator(network, settings, db.koilPriceDao(), fx)

    @Test
    fun `汇率取不到而腾讯可用时报 FxUnavailable 而不是普通 null`() = runBlocking {
        val outcome = estimator(FakeNetwork(105.0 to 104.0), FakeFx(null)).estimateDetailed()
        assertEquals(CrudeOilEstimator.EstimateOutcome.FxUnavailable, outcome)
    }

    @Test
    fun `腾讯也挂了时报 Unavailable`() = runBlocking {
        val outcome = estimator(FakeNetwork(null), FakeFx(null)).estimateDetailed()
        assertEquals(CrudeOilEstimator.EstimateOutcome.Unavailable, outcome)
    }

    @Test
    fun `汇率可用且腾讯可用时成功并按汇率折算`() = runBlocking {
        val fx = 6.6712
        val fxRepo = FakeFx(fx)
        val outcome = estimator(FakeNetwork(105.0 to 104.0), fxRepo).estimateDetailed()
        val success = outcome as? CrudeOilEstimator.EstimateOutcome.Success
        val estimate = success?.estimate
        assertNotNull(estimate)
        assertEquals("腾讯", estimate!!.sourceLabel)
        // ΔP=1.0 USD/bbl × 7.33 桶/吨 × 汇率
        assertEquals(1.0 * 7.33 * fx, estimate.amountPerTon, 0.01)
        // Success 必须带回本次实际依据的快照：UI 判 stale 用它，不事后重读（与后台刷新有竞态）
        assertSame(fxRepo.fxSnapshot, success.fxSnapshot)
    }

    @Test
    fun `EIA路径汇率失败后腾讯路径复用失败不再重复同步取数`() = runBlocking {
        settings.setEiaEnabled(true) // 默认内置 Key 非空 → EIA 路径会执行
        val eiaPoints = (1..25).map { d ->
            val day = d.toString().padStart(2, '0')
            "2026-10-$day" to (70.0 + d * 0.1)
        }
        val network = object : OilPriceNetwork() {
            override suspend fun fetchEiaBrentDaily(apiKey: String, length: Int) =
                com.llpop.koil.data.oil.KOiLEiaBrentSeries(eiaPoints)

            override suspend fun fetchTencentBrent(): Pair<Double, Double> = 105.0 to 104.0
        }
        val fxRepo = FakeFx(null) // 无缓存且全源失败
        val outcome = estimator(network, fxRepo).estimateDetailed()
        // EIA 路径已同步失败过一次；腾讯报价再可用也不得再等一轮 15s——rateForDetailed 总数必须停在 1
        assertEquals(CrudeOilEstimator.EstimateOutcome.FxUnavailable, outcome)
        assertEquals(1, fxRepo.rateForCalls)
    }
}
