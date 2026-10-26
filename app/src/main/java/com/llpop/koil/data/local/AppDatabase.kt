package com.llpop.koil.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.llpop.koil.data.local.dao.KOiLPriceDao
import com.llpop.koil.data.local.dao.KOiLPricePendingDao
import com.llpop.koil.data.local.dao.KOiLRecordDao
import com.llpop.koil.data.local.entity.KOiLPriceEntity
import com.llpop.koil.data.local.entity.KOiLPricePendingEntity
import com.llpop.koil.data.local.entity.KOiLRecordEntity
import java.io.File

/**
 * KOiL 本地数据库（油价库）。
 *
 * —— 数据模型（koil_prices）——
 * 主键 (province, grade, effectiveDate)；effectiveDate = 发改委窗口日 + 1 天（窗口「24时」生效）；
 * price 为元/L，搁浅窗口存维持价（上一窗口价）；isStranded：0=调价 / 1=搁浅窗口（v1.4.181）。
 *
 * —— 一次性灌库 + 持续增量（v1.4.183）——
 * 2026 起点一次灌库（31省×4油号×已过窗口全量，含搁浅），之后新窗口日调价/搁浅增量累加；
 * 跨年不清库（purge 只清当年假日期，往年保留），历史持续累加。
 *
 * —— 迁移史（v1.5.0 重置）——
 * v1.5.0 数据库归 V1（App 未对外发布，作者自用）：删除 v2/v3 历史迁移（fuel_prices 改名、
 * isStranded 加列）与 schemas/2.json、3.json；全新安装直接按当前 entity 完整建表。
 * ⚠️ 已装旧版（库 version=3）覆盖升级会因 Room 禁止版本回退崩溃——必须卸载重装。
 * 禁止 fallbackToDestructiveMigration（清库式升级丢数据）。
 *
 * 完整机制见 docs/KOiL油价数据库机制.md（锚点链/推算/灌库/搁浅/清理/统计口径/已知边界）。
 */
@Database(
    entities = [
        KOiLRecordEntity::class,
        KOiLPriceEntity::class,
        KOiLPricePendingEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun KOiLRecordDao(): KOiLRecordDao
    abstract fun koilPriceDao(): KOiLPriceDao
    abstract fun KOiLPricePendingDao(): KOiLPricePendingDao

    companion object {
        const val DB_NAME = "KOiL.db"

        /**
         * 历史数据库文件名（迁移专用，勿改）：v1.4.50 及更早版本使用 koil.db，
         * 升级时先把旧文件改名到 DB_NAME，否则 Room 会当成全新空库。
         * 注意：表名 koil_record / koil_price_pending 因 SQLite 标识符大小写不敏感
         * 无法改为 KOiL_*（视为同名），故表名保留小写（见 entity 注释）。
         */
        private const val LEGACY_DB_NAME = "koil.db"

        @Volatile
        private var instance: AppDatabase? = null

        /** 历史数据库文件迁移：koil.db → KOiL.db（含 -wal/-shm），仅首次升级时执行一次 */
        private fun migrateDbFile(context: Context) {
            val oldBase = context.getDatabasePath(LEGACY_DB_NAME)
            if (!oldBase.exists()) return
            val newBase = context.getDatabasePath(DB_NAME)
            if (newBase.exists()) return
            listOf("", "-wal", "-shm").forEach { suffix ->
                val f = File(oldBase.path + suffix)
                if (f.exists()) f.renameTo(File(newBase.path + suffix))
            }
        }

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: run {
                    migrateDbFile(context)
                    Room.databaseBuilder(
                        context.applicationContext,
                        AppDatabase::class.java,
                        DB_NAME,
                    )
                        // WAL：灌库/调价大批量写入时首页查询读旧快照不阻塞，避免"写入期间查询排队变慢"
                        .setJournalMode(androidx.room.RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
                        .build()
                        .also { instance = it }
                }
            }
    }
}
