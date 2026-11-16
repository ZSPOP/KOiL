package com.llpop.koil

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.llpop.koil.data.local.AppDatabase
import com.llpop.koil.data.local.dao.KOiLPriceDao
import com.llpop.koil.data.local.entity.KOiLPriceEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 油价表的查询契约（Robolectric：真实 Room，跑的是真 SQL 而不是 mock）。
 *
 * 重点是 `getPriceBefore` 与 `getLatestPrice` 的**开闭区间差异**：
 * 前者的「上一价」被 purge 的安全闸门（`getPriceBefore` + 同价判定）直接依赖，
 * 若把 `<` 写成 `<=`，搁浅窗口会被当成「上一价就是自己」，历史变价会被误删。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class PriceDaoContractTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: KOiLPriceDao

    private val p = "广东"
    private val g = "92号汽油"

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.koilPriceDao()
    }

    @After
    fun tearDown() = db.close()

    private fun row(date: String, price: Double, stranded: Boolean = false) = KOiLPriceEntity(
        province = p,
        grade = g,
        effectiveDate = date,
        price = price,
        source = "test",
        isVerified = true,
        isStranded = stranded,
    )

    @Test
    fun `getPriceBefore 严格小于，不把自己当上一价`() = runBlocking {
        dao.insertAll(listOf(row("2026-11-10", 8.00), row("2026-11-17", 8.30)))

        // 以 11-17 为界：上一价必须是 11-10
        assertEquals("2026-11-10", dao.getPriceBefore(p, g, "2026-11-17")?.effectiveDate)
        // 边界正好落在第一条上：没有更早的，返回 null（不是返回自己）
        assertNull(dao.getPriceBefore(p, g, "2026-11-10"))
    }

    @Test
    fun `getLatestPrice 允许等于查询日`() = runBlocking {
        dao.insertAll(listOf(row("2026-11-10", 8.00), row("2026-11-17", 8.30)))
        assertEquals("2026-11-17", dao.getLatestPrice(p, g, "2026-11-17")?.effectiveDate)
        // 查询日更早时不能看到未来价
        assertEquals("2026-11-10", dao.getLatestPrice(p, g, "2026-11-16")?.effectiveDate)
    }

    @Test
    fun `getPrevious 跳过最新一条，取再上一条`() = runBlocking {
        dao.insertAll(
            listOf(row("2026-11-03", 7.90), row("2026-11-10", 8.00), row("2026-11-17", 8.30)),
        )
        assertEquals("2026-11-10", dao.getPrevious(p, g, "2026-11-17")?.effectiveDate)
        // 只有一条时没有「上一调价日」
        assertNull(dao.getPrevious(p, g, "2026-11-03"))
    }

    @Test
    fun `getPrevious 不跨进未来`() = runBlocking {
        dao.insertAll(listOf(row("2026-11-10", 8.00), row("2026-11-24", 9.00)))
        // 今天 11-17：最新可见是 11-10，再往上没有
        assertNull(dao.getPrevious(p, g, "2026-11-17"))
    }

    @Test
    fun `主键冲突是覆盖而不是新增`() = runBlocking {
        dao.insert(row("2026-11-10", 8.00))
        dao.insert(row("2026-11-10", 8.66))
        assertEquals(1, dao.count())
        assertEquals(8.66, dao.getPrice(p, g, "2026-11-10")!!.price, 0.0001)
    }

    @Test
    fun `省份与油号不同互不干扰`() = runBlocking {
        dao.insertAll(
            listOf(
                row("2026-11-10", 8.00),
                KOiLPriceEntity("浙江", g, "2026-11-10", 7.80, "test", true),
                KOiLPriceEntity(p, "95号汽油", "2026-11-10", 8.60, "test", true),
            ),
        )
        // 广东同一天插了两条（92/95），仍是 1 个生效日；浙江那条不能算进广东
        assertEquals(8.00, dao.getLatestPrice(p, g, "2026-11-17")!!.price, 0.0001)
        assertEquals(1, dao.countDatesForProvince(p))
        assertEquals(2, dao.countProvinces())
        assertEquals(2, dao.countGrades())
    }

    @Test
    fun `去重生效日数按全部省份统计`() = runBlocking {
        dao.insertAll(
            listOf(
                row("2026-11-10", 8.00),
                KOiLPriceEntity("浙江", g, "2026-11-10", 7.80, "test", true),
                row("2026-11-17", 8.30),
            ),
        )
        // 11-10 两个省只算一次调价
        assertEquals(2, dao.countAdjustments())
    }
}
