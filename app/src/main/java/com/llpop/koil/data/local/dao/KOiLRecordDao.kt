package com.llpop.koil.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.llpop.koil.data.local.entity.KOiLRecordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface KOiLRecordDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(record: KOiLRecordEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(records: List<KOiLRecordEntity>)

    @Update
    suspend fun update(record: KOiLRecordEntity)

    @Query("DELETE FROM koil_record WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM koil_record WHERE id = :id")
    suspend fun getById(id: Long): KOiLRecordEntity?

    @Query("SELECT * FROM koil_record ORDER BY reoilDate DESC, id DESC")
    fun observeAll(): Flow<List<KOiLRecordEntity>>

    /** 游标分页：reoilDate DESC, id DESC，每页 20（返回 limit+1 判断是否有下一页） */
    @Query(
        "SELECT * FROM koil_record WHERE reoilDate < :cursorDate OR " +
            "(reoilDate = :cursorDate AND id < :cursorId) " +
            "ORDER BY reoilDate DESC, id DESC LIMIT :limit"
    )
    suspend fun getPage(cursorDate: String, cursorId: Long, limit: Int): List<KOiLRecordEntity>

    @Query("SELECT * FROM koil_record ORDER BY reoilDate DESC, id DESC LIMIT :limit")
    suspend fun getFirstPage(limit: Int): List<KOiLRecordEntity>

    /** 最新一条记录（用于"上次加油"等） */
    @Query("SELECT * FROM koil_record ORDER BY reoilDate DESC, id DESC LIMIT 1")
    suspend fun getLatest(): KOiLRecordEntity?

    /** 最新一条记录实时观察（v1.5.0：首页"上次加油"卡片常驻订阅，任何入口增删改即刷新） */
    @Query("SELECT * FROM koil_record ORDER BY reoilDate DESC, id DESC LIMIT 1")
    fun observeLatest(): Flow<KOiLRecordEntity?>

    /** 最早一条记录（用于日期校验：补录不能早于第一条） */
    @Query("SELECT * FROM koil_record ORDER BY reoilDate ASC, id ASC LIMIT 1")
    suspend fun getEarliest(): KOiLRecordEntity?

    /** 同日同里程完全重复检测（预检） */
    @Query("SELECT COUNT(*) FROM koil_record WHERE reoilDate = :date AND mileage = :mileage")
    suspend fun countSameDayMileage(date: String, mileage: Double): Int

    /** 与某日期间隔最近的前一条记录（用于派生计算：distance / oilConsumption） */
    @Query(
        "SELECT * FROM koil_record WHERE reoilDate < :date OR (reoilDate = :date AND id < :id) " +
            "ORDER BY reoilDate DESC, id DESC LIMIT 1"
    )
    suspend fun getPrevRecord(date: String, id: Long): KOiLRecordEntity?

    /** 指定日期区间内记录（汇总/趋势） */
    @Query("SELECT * FROM koil_record WHERE reoilDate >= :start AND reoilDate <= :end ORDER BY reoilDate ASC")
    suspend fun getRange(start: String, end: String): List<KOiLRecordEntity>

    @Query("SELECT COUNT(*) FROM koil_record")
    suspend fun count(): Int
}
