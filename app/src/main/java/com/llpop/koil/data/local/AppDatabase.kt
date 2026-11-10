package com.llpop.koil.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.llpop.koil.data.local.dao.KOiLPriceDao
import com.llpop.koil.data.local.dao.KOiLRecordDao
import com.llpop.koil.data.local.entity.KOiLPriceEntity
import com.llpop.koil.data.local.entity.KOiLRecordEntity

/**
 * KOiL 本地数据库（应用唯一库）。
 *
 * —— 数据模型（koil_prices）——
 * 主键 (province, grade, effectiveDate)；effectiveDate = 发改委窗口日 + 1 天（窗口「24时」生效）；
 * price 为元/L，搁浅窗口存维持价（上一窗口价）；isStranded：0=调价 / 1=搁浅窗口。
 *
 * —— 一次性灌库 + 持续增量 ——
 * 2026 起点一次灌库（31省×4油号×已过窗口全量，含搁浅），之后新窗口日调价/搁浅增量累加；
 * 跨年不清库（purge 只清当年假日期，往年保留），历史持续累加。
 *
 * —— schema 与版本 ——
 * 库文件与设置文件使用独立命名，互不干扰。
 * 两张表（koil_record / koil_prices）即完整结构：安装后按当前 entity 建表；
 * exportSchema 产出的 1.json 是 schema 基准。
 * 禁止 fallbackToDestructiveMigration（清库式升级会丢数据）。
 *
 * 【版本规则】Room 的 version 存在库文件头，只增不减。
 * 今后任何 schema 改动都必须 version+1 并写迁移与迁移单测；
 * 已随对外版本发布过的 version 不可再降——降级会让 Room 走 onUpgrade(大→小)，
 * 找不到迁移路径直接抛 IllegalStateException 闪退。
 *
 * 完整机制见 docs/KOiL油价数据库机制.md（锚点链/推算/灌库/搁浅/清理/统计口径/已知边界）。
 */
@Database(
    entities = [
        KOiLRecordEntity::class,
        KOiLPriceEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun KOiLRecordDao(): KOiLRecordDao
    abstract fun koilPriceDao(): KOiLPriceDao

    companion object {
        /** 独立库名：设备上无同名文件，安装即建空库；不读取、不改名任何既有数据文件 */
        const val DB_NAME = "KOiL.db"

        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DB_NAME,
                )
                    // WAL：灌库/调价大批量写入时首页查询读旧快照不阻塞，避免"写入期间查询排队变慢"
                    .setJournalMode(androidx.room.RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
                    // schema 迁移链：新版本只增不减，迁移逐段注册（schema 改动属高风险变更，见 docs/COLLABORATION.md 第十二节）
                    .build()
                    .also { instance = it }
            }
    }
}
