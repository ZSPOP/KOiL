package com.llpop.koil

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.llpop.koil.data.local.AppDatabase
import com.llpop.koil.data.local.entity.KOiLPriceEntity
import com.llpop.koil.data.oil.OilPriceDbSyncer
import com.llpop.koil.data.oil.OilPriceNetwork
import com.llpop.koil.data.settings.AppSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * 油价库维护回归（Robolectric：需要真实 Room/DataStore）：
 * - 空数据省必须进失败名单，不能被吞成补灌成功（走 seedRemainingProvinces 生产入口）
 * - 删除记录写墓碑；显式标记键缺失时回退到既有证据
 * - 自动清理只删同价孤立假日期，保留真实变价
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class PriceDbMaintenanceRobolectricTest {

    private lateinit var db: AppDatabase
    private lateinit var settings: AppSettings
    private lateinit var syncer: OilPriceDbSyncer

    /** 只给指定省返回空曲线，其余省返回两条变化点（视为补灌成功） */
    private inner class FakeNetwork(private val emptyProvinces: Set<String>) : OilPriceNetwork() {
        override suspend fun fetchYoujiaHistory(province: String): List<OilPriceNetwork.YoujiaHistoryPoint> {
            if (province in emptyProvinces) return emptyList()
            val today = LocalDate.now()
            // 两条不同价的变化点，落在当年且已过的窗口附近即可触发 insert > 0
            return listOf(
                OilPriceNetwork.YoujiaHistoryPoint(today.minusDays(9).toString(), 8.00, 8.50, 9.00, 7.50),
                OilPriceNetwork.YoujiaHistoryPoint(today.minusDays(4).toString(), 8.30, 8.80, 9.30, 7.80),
            )
        }
    }

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        settings = AppSettings(context)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun price(province: String, grade: String, date: String, value: Double) =
        KOiLPriceEntity(province, grade, date, value, "test", false)

    /** ===== 空数据省必须进失败名单 ===== */
    @Test
    fun `空数据省记为补灌失败而不是吞成成功`() = runBlocking {
        val today = LocalDate.now()
        // 两个已过窗口（当年），对应生效日 = 窗口日 + 1
        val windows = listOf(today.minusDays(10), today.minusDays(5)).map { it.toString() }
        settings.setPriceDbAdjustDates(windows)

        // 「北京」预置覆盖全部生效日且无搁浅缺口 → 走"已覆盖直接跳过"分支，仍计成功
        db.koilPriceDao().insertAll(
            listOf(
                price("北京", "92号汽油", today.minusDays(9).toString(), 8.00),
                price("北京", "92号汽油", today.minusDays(4).toString(), 8.30),
            )
        )

        syncer = OilPriceDbSyncer(db, db.koilPriceDao(), settings, FakeNetwork(setOf("上海")))

        val ok = syncer.seedRemainingProvinces()

        // 上海：网络成功但曲线为空 → seedProvince 返回 0 → 必须记失败、返回 false、转 GAVE_UP
        assertFalse("空数据省被吞成成功，缺口不会再被重试", ok)
        assertEquals(OilPriceDbSyncer.SeedState.GAVE_UP, syncer.seedState)
        assertTrue(syncer.seedFailed.contains("上海"))
        assertEquals(1, syncer.seedFailedCount)
        // 其余 30 省（含跳过的北京）都计成功
        assertEquals(30, syncer.seedDone)
    }

    /** 反向对照：数据齐全时不得误报失败 */
    @Test
    fun `全部省份有数据时补灌成功`() = runBlocking {
        val today = LocalDate.now()
        val windows = listOf(today.minusDays(10), today.minusDays(5)).map { it.toString() }
        settings.setPriceDbAdjustDates(windows)
        db.koilPriceDao().insertAll(
            listOf(
                price("北京", "92号汽油", today.minusDays(9).toString(), 8.00),
                price("北京", "92号汽油", today.minusDays(4).toString(), 8.30),
            )
        )

        syncer = OilPriceDbSyncer(db, db.koilPriceDao(), settings, FakeNetwork(emptySet()))

        val ok = syncer.seedRemainingProvinces()

        assertTrue(ok)
        assertEquals(OilPriceDbSyncer.SeedState.IDLE, syncer.seedState)
        assertEquals(0, syncer.seedFailedCount)
        assertEquals(OilPriceDbSyncer.PROVINCES.size, syncer.seedDone)
    }

    /** ===== 删除记录写墓碑（含显式键缺失时的回退） ===== */
    @Test
    fun `有推送时间戳但无显式标记时仍视为已配置`() = runBlocking {
        // 模拟显式标记键尚未写入：既有证据只有 lastPushAt > 0
        settings.setSyncLastPushAt(System.currentTimeMillis())
        assertTrue(
            "键缺失回退失败：先删记录会被判为未配置，不写墓碑 → push 时云端旧记录复活",
            settings.syncEverConfigured.first(),
        )
    }

    @Test
    fun `状态行为ok或restore也算曾配置`() = runBlocking {
        settings.setSyncLastStatus("ok@11-09 16:12")
        assertTrue(settings.syncEverConfigured.first())

        settings.setSyncLastStatus("restore@11-09 16:13")
        assertTrue(settings.syncEverConfigured.first())
    }

    @Test
    fun `失败状态与标记缺省不触发墓碑判定`() = runBlocking {
        // Robolectric 下同一测试类的 DataStore 文件会延续，显式清零既有证据后验证纯失败态
        settings.setSyncLastPushAt(0L)
        // 只有失败记录（从未成功推送）：仍视为未配置
        settings.setSyncLastStatus("fail:connect timeout")
        assertFalse(settings.syncEverConfigured.first())
    }

    /** ===== 自动清理只删同价孤立假日期 ===== */
    @Test
    fun `自动清理只删同价孤立日并保留真实变价`() = runBlocking {
        val today = LocalDate.now()
        val window = today.minusDays(10).toString()
        val legalDate = today.minusDays(9).toString()
        settings.setPriceDbAdjustDates(listOf(window))

        // 合法窗口+1：上一价基准
        db.koilPriceDao().insertAll(
            listOf(
                price("广东", "92号汽油", legalDate, 8.15),
                price("北京", "92号汽油", legalDate, 8.20),
            )
        )
        // 今天不在合法集合内：广东与上一价相同（假点，应删）；北京变价（真实记录，应保留）
        db.koilPriceDao().insertAll(
            listOf(
                price("广东", "92号汽油", today.toString(), 8.15),
                price("北京", "92号汽油", today.toString(), 8.25),
            )
        )
        assertEquals(2, db.koilPriceDao().distinctEffectiveDates().size)

        syncer = OilPriceDbSyncer(db, db.koilPriceDao(), settings, FakeNetwork(emptySet()))
        syncer.purgeIllegalEffectiveDatesIfReady()

        assertEquals("只删广东同价假点，北京变价保留", 3, db.koilPriceDao().count())
        // 北京变价仍留在今天 → 合法日 + 今天两条日期
        assertEquals(2, db.koilPriceDao().distinctEffectiveDates().size)
        assertEquals(null, db.koilPriceDao().getPrice("广东", "92号汽油", today.toString()))
        assertEquals(8.25, db.koilPriceDao().getPrice("北京", "92号汽油", today.toString())?.price ?: 0.0, 0.0)
    }

    @Test
    fun `无窗口日历时清理直接跳过`() = runBlocking {
        settings.setPriceDbAdjustDates(emptyList())
        val today = LocalDate.now()
        db.koilPriceDao().insertAll(listOf(price("广东", "92号汽油", today.toString(), 8.15)))

        syncer = OilPriceDbSyncer(db, db.koilPriceDao(), settings, FakeNetwork(emptySet()))
        syncer.purgeIllegalEffectiveDatesIfReady()

        assertEquals(1, db.koilPriceDao().count())
    }
}
