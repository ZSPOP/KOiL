package com.llpop.koil

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.llpop.koil.data.local.AppDatabase
import com.llpop.koil.data.local.entity.KOiLPriceEntity
import com.llpop.koil.data.oil.OilPriceChangeNotifier
import com.llpop.koil.data.settings.AppSettings
import com.llpop.koil.util.AppClock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/**
 * 通知器的集成回归（Robolectric：真实 Room + DataStore + ShadowNotificationManager）。
 *
 * 单测只盯判定函数，这里盯「判定接进流程之后的结果」——去重锚点有没有真的落库、
 * 三条通知的 ID 是否互不覆盖、第二次调用会不会重复弹。这些都是只能靠集成验的。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class OilPriceNotifierRobolectricTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var settings: AppSettings
    private lateinit var notifier: OilPriceChangeNotifier

    private val province = "广东"
    private val grade = "92号汽油"
    /** 固定"今天"= 2026-11-17（周二），避免跨时区跑测试时日期漂移 */
    private val today = "2026-11-17"

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        shadowOf(ApplicationProvider.getApplicationContext<android.app.Application>())
            .grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        AppClock.setTestClock(
            Clock.fixed(Instant.parse("2026-11-17T02:00:00Z"), ZoneId.of("Asia/Shanghai")),
        )
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        settings = AppSettings(context)
        notifier = OilPriceChangeNotifier(settings, db.koilPriceDao())
        // 每个用例从同一份干净状态出发
        settings.setNotifyEnabled(true)
        settings.setDefaultProvince(province)
        settings.setPriceLastNotifiedDate("")
        settings.setPriceLastWindowReminded("")
        settings.setPriceLastWindowDayReminded("")
        settings.setPriceDbAdjustDates(emptyList())
    }

    @After
    fun tearDown() {
        AppClock.setTestClock(null)
        db.close()
    }

    private fun price(date: String, value: Double, stranded: Boolean = false) = KOiLPriceEntity(
        province = province,
        grade = grade,
        effectiveDate = date,
        price = value,
        source = "test",
        isVerified = true,
        isStranded = stranded,
    )

    private fun notifications() =
        shadowOf(context.getSystemService(NotificationManager::class.java)).allNotifications

    @Test
    fun `首次启用只锚定生效日，不弹通知`() = runBlocking {
        db.koilPriceDao().insertAll(listOf(price("2026-11-10", 8.00)))
        notifier.notifyPriceChanges(context)

        assertEquals("2026-11-10", settings.priceLastNotifiedDate.first())
        assertTrue("首次启用不该打扰用户", notifications().isEmpty())
    }

    @Test
    fun `价格真调了才弹调价通知`() = runBlocking {
        db.koilPriceDao().insertAll(
            listOf(price("2026-11-10", 8.00), price("2026-11-17", 8.30)),
        )
        settings.setPriceLastNotifiedDate("2026-11-10")
        notifier.notifyPriceChanges(context)

        val all = notifications()
        assertEquals(1, all.size)
        assertEquals("2026-11-17", settings.priceLastNotifiedDate.first())
    }

    @Test
    fun `搁浅窗口同日入库不误发调价通知`() = runBlocking {
        // 生效日刷新到窗口日，但价格维持 8.00 —— 这是「搁浅」，不是调价
        db.koilPriceDao().insertAll(
            listOf(price("2026-11-10", 8.00), price("2026-11-17", 8.00, stranded = true)),
        )
        settings.setPriceLastNotifiedDate("2026-11-10")
        notifier.notifyPriceChanges(context)

        assertTrue("维持价不该弹「调价至 ¥8.00」", notifications().isEmpty())
        // 锚点仍要前移，否则下一次真调价会被当成「同一生效日」而吞掉
        assertEquals("2026-11-17", settings.priceLastNotifiedDate.first())
    }

    @Test
    fun `窗口日前一天提醒只弹一次`() = runBlocking {
        settings.setPriceDbAdjustDates(listOf("2026-11-18"))
        notifier.notifyPriceChanges(context)
        notifier.notifyPriceChanges(context)

        assertEquals(1, notifications().size)
        assertEquals("2026-11-18", settings.priceLastWindowReminded.first())
    }

    @Test
    fun `窗口日当天提醒只弹一次`() = runBlocking {
        settings.setPriceDbAdjustDates(listOf(today))
        notifier.notifyPriceChanges(context)
        notifier.notifyPriceChanges(context)

        assertEquals(1, notifications().size)
        assertEquals(today, settings.priceLastWindowDayReminded.first())
    }

    @Test
    fun `三类通知使用互不相同的 ID`() = runBlocking {
        // 同一天同时满足「调价」「窗口日当天」：两条通知必须都在，不能被同一个 ID 覆盖
        db.koilPriceDao().insertAll(
            listOf(price("2026-11-10", 8.00), price(today, 8.30)),
        )
        settings.setPriceLastNotifiedDate("2026-11-10")
        settings.setPriceDbAdjustDates(listOf(today))
        notifier.notifyPriceChanges(context)

        assertEquals(2, notifications().size)
    }

    @Test
    fun `推送开关关闭时一条都不发，也不写锚点`() = runBlocking {
        settings.setNotifyEnabled(false)
        db.koilPriceDao().insertAll(listOf(price("2026-11-10", 8.00)))
        settings.setPriceDbAdjustDates(listOf(today))
        notifier.notifyPriceChanges(context)

        assertTrue(notifications().isEmpty())
        assertEquals("", settings.priceLastNotifiedDate.first())
    }
}
