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

@Database(
    entities = [
        KOiLRecordEntity::class,
        KOiLPriceEntity::class,
        KOiLPricePendingEntity::class,
    ],
    version = 2,
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

        /**
         * 数据库迁移集合（规范，禁止清库式升级）：
         * 1) 修改 entity/DAO 后，把 @Database version 升为 n+1；
         * 2) 对照 schemas/<n>/xxx.json 与 schemas/<n+1>/xxx.json 编写 Migration(n, n+1)
         *    （ALTER TABLE / CREATE TABLE / 数据搬移），写入本数组；
         * 3) 禁止 fallbackToDestructiveMigration —— 会清空用户全部数据。
         *
         * 1 → 2（v1.4.159 命名统一）：fuel_prices 表重命名为 koil_prices，
         * 旧版本（version=1）已建表用户升级时重命名，数据保留；全新安装直接建新表。
         */
        val MIGRATIONS: Array<Migration> = arrayOf(
            object : Migration(1, 2) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE fuel_prices RENAME TO koil_prices")
                }
            },
        )

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
                        // 显式注册迁移（无 fallback 回退）：版本不符且无迁移时直接崩溃而非清空数据
                        .addMigrations(*MIGRATIONS)
                        .build()
                        .also { instance = it }
                }
            }
    }
}
