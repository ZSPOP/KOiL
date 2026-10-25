package com.llpop.koil.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.llpop.koil.data.local.entity.KOiLPricePendingEntity

@Dao
interface KOiLPricePendingDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(items: List<KOiLPricePendingEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(item: KOiLPricePendingEntity): Long

    @Query("SELECT * FROM koil_price_pending WHERE status = 'pending' ORDER BY effectiveDate ASC")
    suspend fun getPending(): List<KOiLPricePendingEntity>

    @Query(
        "SELECT * FROM koil_price_pending WHERE province = :province AND grade = :grade " +
            "AND effectiveDate = :date LIMIT 1"
    )
    suspend fun getPendingByKey(province: String, grade: String, date: String): KOiLPricePendingEntity?

    /**
     * 双源再次不一致时刷新主次源价并重置为待确认；
     * 同时清掉历史采纳/忽略结果，避免残留旧 resolved 值误导。
     */
    @Query(
        "UPDATE koil_price_pending SET primaryPrice = :pp, primarySource = :ps, " +
            "secondaryPrice = :sp, secondarySource = :ss, status = 'pending', " +
            "resolvedPrice = NULL, resolvedSource = NULL, note = NULL WHERE id = :id"
    )
    suspend fun updateSources(id: Long, pp: Double?, ps: String?, sp: Double?, ss: String?)

    @Query("UPDATE koil_price_pending SET status = :status, resolvedPrice = :price, resolvedSource = :source, note = :note WHERE id = :id")
    suspend fun resolve(id: Long, status: String, price: Double?, source: String?, note: String?)

    /** 仅统计未处理的待确认数（不含历史 accepted/ignored） */
    @Query("SELECT COUNT(*) FROM koil_price_pending WHERE status = 'pending'")
    suspend fun countPending(): Int

    @Query("SELECT COUNT(*) FROM koil_price_pending")
    suspend fun count(): Int

    /** 清空全部待确认表（新机制上线时清理旧滞后机制遗留的假待确认） */
    @Query("DELETE FROM koil_price_pending")
    suspend fun clear()
}
