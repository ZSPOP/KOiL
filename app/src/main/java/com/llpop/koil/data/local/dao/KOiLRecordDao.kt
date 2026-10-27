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

    /** 排除指定 id 外的最早一条（编辑校验：自身是最早记录时不得把自己当"前一条"） */
    @Query("SELECT * FROM koil_record WHERE id != :excludeId ORDER BY reoilDate ASC, id ASC LIMIT 1")
    suspend fun getEarliestExcluding(excludeId: Long): KOiLRecordEntity?

    /** 指定日期之前最近一条（< date，v1.5.10 复刻 Web checkMileageChain prev） */
    @Query("SELECT * FROM koil_record WHERE reoilDate < :date ORDER BY reoilDate DESC, id DESC LIMIT 1")
    suspend fun getBeforeDate(date: String): KOiLRecordEntity?

    /** 指定日期之后最近一条（> date，v1.5.10 复刻 Web checkMileageChain next） */
    @Query("SELECT * FROM koil_record WHERE reoilDate > :date ORDER BY reoilDate ASC, id ASC LIMIT 1")
    suspend fun getAfterDate(date: String): KOiLRecordEntity?

    /** 指定日期全部记录（同日多条：里程/油量/实付，v1.5.10 复刻 Web checkDuplicate） */
    @Query("SELECT * FROM koil_record WHERE reoilDate = :date ORDER BY mileage ASC")
    suspend fun getSameDayRecords(date: String): List<KOiLRecordEntity>

    /** 指定日期、排除指定 id 的全部记录（编辑校验：不与自己判重） */
    @Query("SELECT * FROM koil_record WHERE reoilDate = :date AND id != :excludeId ORDER BY mileage ASC")
    suspend fun getSameDayRecordsExcluding(date: String, excludeId: Long): List<KOiLRecordEntity>

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

    /**
     * v1.5.16 一次性数据修复：历史因"无参考价且未手填应付"入库的 payable=0 记录，
     * 用实付金额回填（应付=实付，优惠 0）。返回受影响行数。
     */
    @Query("UPDATE koil_record SET payableAmount = actualAmount WHERE payableAmount = 0 AND actualAmount > 0")
    suspend fun fixZeroPayable(): Int
}
