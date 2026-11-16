package com.llpop.koil

import com.llpop.koil.data.local.entity.KOiLRecordEntity
import com.llpop.koil.data.sync.BackupCodec
import com.llpop.koil.data.sync.RecordMerger
import com.llpop.koil.data.sync.SyncReconcile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 首次配对对账的纯计算回归（PR ② 评审必查 1/2 条）：
 * - K 必须减墓碑：本机删了、云端还在的键不能算成「待拉回的云端独有记录」；
 * - 四类 + 墓碑跳过 = 键并集，冲突 ⊆ 双方都有。
 */
class SyncReconcileTest {

    private fun local(
        date: String,
        km: Double,
        amount: Double = 40.0,
        price: Double? = 8.00,
        grade: String? = "92",
        province: String? = "广东",
        source: String? = "entry_page",
        full: Boolean = true,
    ) = KOiLRecordEntity(
        reoilDate = date,
        mileage = km,
        oilAmount = amount,
        payableAmount = amount * (price ?: 8.0),
        actualAmount = amount * (price ?: 8.0),
        oilPrice = price,
        oilGrade = grade,
        province = province,
        source = source,
        isFullTank = full,
    )

    private fun cloud(
        date: String,
        km: Double,
        amount: Double = 40.0,
        price: Double? = 8.00,
        grade: String? = "92",
        province: String? = "广东",
        source: String? = "entry_page",
        full: Boolean = true,
    ) = BackupCodec.RecordData(
        reoilDate = date,
        mileage = km,
        oilAmount = amount,
        payableAmount = amount * (price ?: 8.0),
        actualAmount = amount * (price ?: 8.0),
        oilPrice = price,
        oilGrade = grade,
        province = province,
        source = source,
        isFullTank = full,
    )

    @Test
    fun `本机删了三台云端还有_K必须是零而不是三`() {
        val deletedDates = listOf("2026-01-01", "2026-01-02", "2026-01-03")
        val tombstones = deletedDates.map { RecordMerger.keyOf(it, 1000.0) }.toSet()
        val cloudRecords = deletedDates.map { cloud(it, 1000.0) } + listOf(cloud("2026-01-04", 2000.0))

        val stats = SyncReconcile.compute(
            local = listOf(local("2026-01-05", 3000.0)),
            cloud = cloudRecords,
            tombstones = tombstones,
        )

        // 三条被删的不算云端独有，只算「已删跳过」
        assertEquals(3, stats.tombstoneSkippedCount)
        // 云端 4 条里，被墓碑挡掉 3 条 → K 只剩 1 条（不是 4）
        assertEquals(1, stats.cloudOnlyCount)
        assertTrue(stats.partitioned)
    }

    @Test
    fun `云端独有里混着墓碑_只把未删的算进K`() {
        val tombstones = setOf(RecordMerger.keyOf("2026-02-01", 500.0))
        val stats = SyncReconcile.compute(
            local = listOf(local("2026-02-02", 600.0)),
            cloud = listOf(
                cloud("2026-02-01", 500.0),   // 被删
                cloud("2026-02-03", 700.0),   // 真云端独有
            ),
            tombstones = tombstones,
        )
        assertEquals(1, stats.cloudOnlyCount)
        assertEquals(1, stats.tombstoneSkippedCount)
        assertEquals(1, stats.localOnlyCount)
        assertEquals(0, stats.bothCount)
        assertTrue(stats.partitioned)
    }

    @Test
    fun `四类加墓碑跳过等于键并集`() {
        val tombstones = setOf(RecordMerger.keyOf("2026-03-05", 1500.0))
        val localRecords = listOf(
            local("2026-03-01", 1100.0),                    // 双方都有，内容一致
            local("2026-03-02", 1200.0, amount = 30.0),     // 双方都有，内容不同
            local("2026-03-03", 1300.0),                    // 本机独有
        )
        val cloudRecords = listOf(
            cloud("2026-03-01", 1100.0),
            cloud("2026-03-02", 1200.0, amount = 40.0),
            cloud("2026-03-04", 1400.0),                    // 云端独有
            cloud("2026-03-05", 1500.0),                    // 云端有，本机删了
        )

        val stats = SyncReconcile.compute(localRecords, cloudRecords, tombstones)

        assertEquals(2, stats.bothCount)
        assertEquals(1, stats.conflictCount)
        assertEquals(1, stats.cloudOnlyCount)
        assertEquals(1, stats.localOnlyCount)
        assertEquals(1, stats.tombstoneSkippedCount)
        assertEquals(5, stats.unionCount)
        // 等式本身就是「没有键被算两遍或漏掉」的证明
        assertEquals(
            stats.unionCount,
            stats.bothCount + stats.cloudOnlyCount + stats.localOnlyCount + stats.tombstoneSkippedCount,
        )
        assertTrue(stats.partitioned)
    }

    @Test
    fun `冲突是双方都有的子集_且不能超过M`() {
        val stats = SyncReconcile.compute(
            local = listOf(local("2026-04-01", 100.0, amount = 10.0)),
            cloud = listOf(cloud("2026-04-01", 100.0, amount = 20.0)),
            tombstones = emptySet(),
        )
        assertEquals(1, stats.bothCount)
        assertEquals(1, stats.conflictCount)
        assertTrue(stats.conflictCount <= stats.bothCount)
    }

    @Test
    fun `内容完全一致时没有冲突`() {
        val stats = SyncReconcile.compute(
            local = listOf(local("2026-05-01", 100.0)),
            cloud = listOf(cloud("2026-05-01", 100.0)),
            tombstones = emptySet(),
        )
        assertEquals(1, stats.bothCount)
        assertEquals(0, stats.conflictCount)
        assertTrue(stats.identical)
    }

    @Test
    fun `任一基础字段不同都算冲突`() {
        val base = local("2026-06-01", 100.0)
        val variants = listOf(
            cloud("2026-06-01", 100.0, amount = 41.0),
            cloud("2026-06-01", 100.0, price = 8.10),
            cloud("2026-06-01", 100.0, grade = "95"),
            cloud("2026-06-01", 100.0, province = "湖南"),
            cloud("2026-06-01", 100.0, source = "backfill_modal"),
            cloud("2026-06-01", 100.0, full = false),
        )
        variants.forEach { v ->
            statsOf(base, v).let { assertEquals(1, it.conflictCount) }
        }
    }

    private fun statsOf(l: KOiLRecordEntity, c: BackupCodec.RecordData) =
        SyncReconcile.compute(listOf(l), listOf(c), emptySet())

    @Test
    fun `两边都空_并集为零`() {
        val stats = SyncReconcile.compute(emptyList(), emptyList(), emptySet())
        assertEquals(0, stats.unionCount)
        assertEquals(0, stats.conflictCount)
        assertTrue(stats.partitioned)
        assertTrue(stats.identical)
    }

    @Test
    fun `只有本机或只有云端`() {
        val onlyLocal = SyncReconcile.compute(listOf(local("2026-07-01", 100.0)), emptyList(), emptySet())
        assertEquals(1, onlyLocal.localOnlyCount)
        assertEquals(0, onlyLocal.cloudOnlyCount)
        assertEquals(0, onlyLocal.bothCount)

        val onlyCloud = SyncReconcile.compute(emptyList(), listOf(cloud("2026-07-02", 200.0)), emptySet())
        assertEquals(1, onlyCloud.cloudOnlyCount)
        assertEquals(0, onlyCloud.localOnlyCount)
        assertEquals(0, onlyCloud.bothCount)
    }

    @Test
    fun `同键重复记录只算一条_不能把并集撑大`() {
        val stats = SyncReconcile.compute(
            local = listOf(local("2026-08-01", 100.0), local("2026-08-01", 100.0, amount = 50.0)),
            cloud = listOf(cloud("2026-08-01", 100.0), cloud("2026-08-01", 100.0, amount = 60.0)),
            tombstones = emptySet(),
        )
        assertEquals(1, stats.unionCount)
        assertEquals(1, stats.bothCount)
        assertEquals(1, stats.localCount)
        assertEquals(1, stats.cloudCount)
    }

    @Test
    fun `合并只增不减_云端独有就是插入条数`() {
        val tombstones = setOf(RecordMerger.keyOf("2026-09-03", 300.0))
        val stats = SyncReconcile.compute(
            local = listOf(local("2026-09-01", 100.0)),
            cloud = listOf(
                cloud("2026-09-01", 100.0),
                cloud("2026-09-02", 200.0),
                cloud("2026-09-03", 300.0),
            ),
            tombstones = tombstones,
        )
        assertEquals(1, stats.mergeInserts)
        assertFalse(stats.mergeInserts < 0)
    }
}
