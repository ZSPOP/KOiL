package com.llpop.koil.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.llpop.koil.data.local.entity.KOiLPriceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface KOiLPriceDao {

    /** v1.5.23：最近一次【非搁浅调价】的生效日（各省窗口一致，全国统一调价；搁浅轮不重置估值基准） */
    @Query("SELECT effectiveDate FROM koil_prices WHERE isStranded = 0 ORDER BY effectiveDate DESC LIMIT 1")
    suspend fun getLatestNonStrandedEffectiveDates(): List<String>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(prices: List<KOiLPriceEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(price: KOiLPriceEntity)

    @Query("DELETE FROM koil_prices")
    suspend fun clear()

    /** 指定省份 + 油号 + 生效日期的价格 */
    @Query(
        "SELECT * FROM koil_prices WHERE province = :province AND grade = :grade " +
            "AND effectiveDate = :date LIMIT 1"
    )
    suspend fun getPrice(province: String, grade: String, date: String): KOiLPriceEntity?

    /** 不晚于指定日期的最新价格（价格偏差检查 / 展示） */
    @Query(
        "SELECT * FROM koil_prices WHERE province = :province AND grade = :grade " +
            "AND effectiveDate <= :date ORDER BY effectiveDate DESC LIMIT 1"
    )
    suspend fun getLatestPrice(province: String, grade: String, date: String): KOiLPriceEntity?

    /** 全部价格（导出备份） */
    @Query("SELECT * FROM koil_prices ORDER BY effectiveDate ASC")
    fun observeAll(): Flow<List<KOiLPriceEntity>>

    /**
     * v1.6.2：指定省份的非搁浅价格（按生效日升序）。
     * 用于补灌前的"同价假记录"清理——原实现每省都 `getAll()` 全表加载再内存过滤，
     * 31 省补灌时等于把 2000+ 行表反复读 31 遍；改 SQL 过滤后每省只取本省行。
     */
    @Query(
        "SELECT * FROM koil_prices WHERE province = :province AND isStranded = 0 " +
            "ORDER BY effectiveDate ASC"
    )
    suspend fun getNonStrandedByProvince(province: String): List<KOiLPriceEntity>

    @Query("SELECT COUNT(*) FROM koil_prices")
    suspend fun count(): Int

    /** 覆盖省份数（SQL 聚合，替代全表内存统计） */
    @Query("SELECT COUNT(DISTINCT province) FROM koil_prices")
    suspend fun countProvinces(): Int

    /** 指定省份覆盖的生效日期数（完整性补灌判定：覆盖窗口日 < 应有量则重灌该省） */
    @Query("SELECT COUNT(DISTINCT effectiveDate) FROM koil_prices WHERE province = :province")
    suspend fun countDatesForProvince(province: String): Int

    /** 覆盖油号数 */
    @Query("SELECT COUNT(DISTINCT grade) FROM koil_prices")
    suspend fun countGrades(): Int

    /** 调价次数（去重生效日期数） */
    @Query("SELECT COUNT(DISTINCT effectiveDate) FROM koil_prices")
    suspend fun countAdjustments(): Int

    /** 库内最新生效日期 */
    @Query("SELECT MAX(effectiveDate) FROM koil_prices")
    suspend fun maxDate(): String?

    /** 库内全部去重生效日期（一次性异常记录清理用） */
    @Query("SELECT DISTINCT effectiveDate FROM koil_prices")
    suspend fun distinctEffectiveDates(): List<String>

    /** 按主键（省+油号+生效日）删除单条（一次性异常记录清理用） */
    @Query("DELETE FROM koil_prices WHERE province = :province AND grade = :grade AND effectiveDate = :date")
    suspend fun deleteByKey(province: String, grade: String, date: String)

    /** 指定生效日期任一记录（窗口 W1 是否已调价的全国判定） */
    @Query("SELECT * FROM koil_prices WHERE effectiveDate = :date LIMIT 1")
    suspend fun getByDate(date: String): KOiLPriceEntity?

    @Query("SELECT * FROM koil_prices WHERE province = :province AND grade = :grade AND isStranded = 0 AND effectiveDate <= :date ORDER BY effectiveDate DESC LIMIT 1")
    suspend fun getLatestNonStranded(province: String, grade: String, date: String): KOiLPriceEntity?

    @Query("SELECT * FROM koil_prices WHERE province = :province AND grade = :grade ORDER BY effectiveDate ASC")
    fun observeByProvinceGrade(province: String, grade: String): Flow<List<KOiLPriceEntity>>

    /** 该省全部 省|油号|生效日 幂等键（重灌/补灌时不重复插入） */
    @Query("SELECT DISTINCT province || '|' || grade || '|' || effectiveDate FROM koil_prices WHERE province = :province")
    suspend fun keysForProvince(province: String): List<String>

    @Query("SELECT * FROM koil_prices WHERE province = :province AND grade = :grade ORDER BY effectiveDate ASC")
    suspend fun getByProvinceGrade(province: String, grade: String): List<KOiLPriceEntity>

    /** 指定省+油号+生效日期是否已有记录（该省该油号某窗口是否调价过） */
    @Query("SELECT * FROM koil_prices WHERE province = :province AND grade = :grade AND effectiveDate = :date LIMIT 1")
    suspend fun getByProvinceGradeDate(province: String, grade: String, date: String): KOiLPriceEntity?

    @Query("SELECT COUNT(*) FROM koil_prices WHERE province = :province AND grade = :grade")
    suspend fun countByProvinceGrade(province: String, grade: String): Int

    @Query("SELECT * FROM koil_prices WHERE province = :province AND grade = :grade ORDER BY effectiveDate DESC LIMIT 1")
    suspend fun getNewest(province: String, grade: String): KOiLPriceEntity?

    /** 响应式最新价：库内任何写入（联网灌库/调价更新）后自动重发，驱动首页四宫格/当前价实时刷新（根治冷启动后数据空白） */
    @Query("SELECT * FROM koil_prices WHERE province = :province AND grade = :grade ORDER BY effectiveDate DESC LIMIT 1")
    fun observeNewest(province: String, grade: String): Flow<KOiLPriceEntity?>

    /** v1.5.21 B7 展示层兜底：不晚于指定日期的最新生效价（响应式）——窗口日 24 点前不显示次日新价 */
    @Query(
        "SELECT * FROM koil_prices WHERE province = :province AND grade = :grade " +
            "AND effectiveDate <= :date ORDER BY effectiveDate DESC LIMIT 1"
    )
    fun observeNewestUpTo(province: String, grade: String, date: String): Flow<KOiLPriceEntity?>

    /** v1.5.21 B6 一次性清理：删除"未来生效日"脏记录（正常数据不存在 effectiveDate > 今天 的记录） */
    @Query("DELETE FROM koil_prices WHERE effectiveDate > :today")
    suspend fun deleteFutureRecords(today: String): Int

    /** 响应式行数：库从空→有数据（灌库完成）时触发首页整体刷新 */
    @Query("SELECT COUNT(*) FROM koil_prices")
    fun observeCount(): Flow<Int>

    /** 库内已覆盖省份（补全国幂等用：只拉缺失省） */
    @Query("SELECT DISTINCT province FROM koil_prices")
    suspend fun distinctProvinces(): List<String>

    /** 上一调价日价格（涨跌对比：最新价 vs 上一调价日价） */
    @Query(
        "SELECT * FROM koil_prices WHERE province = :province AND grade = :grade " +
            "AND effectiveDate < (SELECT MAX(effectiveDate) FROM koil_prices WHERE province = :province AND grade = :grade) " +
            "ORDER BY effectiveDate DESC LIMIT 1"
    )
    suspend fun getPrevious(province: String, grade: String): KOiLPriceEntity?

    /** 全部价格（CSV导出） */
    @Query("SELECT * FROM koil_prices ORDER BY province ASC, grade ASC, effectiveDate ASC")
    suspend fun getAll(): List<KOiLPriceEntity>
}
