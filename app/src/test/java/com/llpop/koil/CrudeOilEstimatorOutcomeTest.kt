package com.llpop.koil

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.llpop.koil.data.local.AppDatabase
import com.llpop.koil.data.oil.CrudeOilEstimator
import com.llpop.koil.data.oil.KOiLUsdCnyRateRepository
import com.llpop.koil.data.oil.KOiLUsdCnyRateSnapshot
import com.llpop.koil.data.oil.OilPriceNetwork
import com.llpop.koil.data.settings.AppSettings
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
 * - 汇率可用 + 腾讯可用 → Success
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

    /** 汇率固定返回注入值（null = 模拟「无缓存且所有源取数失败」） */
    private inner class FakeFx(private val rate: Double?) :
        KOiLUsdCnyRateRepository(
            ApplicationProvider.getApplicationContext<Context>(),
            MockEngine { respond("", HttpStatusCode.InternalServerError) },
        ) {
        override suspend fun rateFor(date: String): Double? = rate
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
        val outcome = estimator(FakeNetwork(105.0 to 104.0), FakeFx(fx)).estimateDetailed()
        val estimate = (outcome as? CrudeOilEstimator.EstimateOutcome.Success)?.estimate
        assertNotNull(estimate)
        assertEquals("腾讯", estimate!!.sourceLabel)
        // ΔP=1.0 USD/bbl × 7.33 桶/吨 × 汇率
        assertEquals(1.0 * 7.33 * fx, estimate.amountPerTon, 0.01)
    }
}
