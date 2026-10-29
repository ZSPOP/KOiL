package com.llpop.koil.data.local.seed

import android.content.Context
import android.util.Log
import com.llpop.koil.data.local.AppDatabase
import com.llpop.koil.data.local.entity.KOiLRecordEntity
import com.llpop.koil.data.settings.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * 首次启动预置数据灌入：
 * - koil_record：用户提供的 16 条历史加油记录（保留，测试用）
 * - koil_prices：已改为联网灌库（方案X，见 OilPriceDbSyncer.initPriceDb），不再内置油价快照
 * v1.5.23：灌入条件从「表为空」改为「一次性标记 KOiL-record-seeded」——
 * 用户主动删光全部记录后重启不再「复活」种子数据（此前 count==0 会重灌，删除形同虚设）。
 * 失败静默（离线可用不依赖种子）。
 */
object SeedInitializer {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun seedIfEmpty(context: Context, db: AppDatabase, settings: AppSettings) = withContext(Dispatchers.IO) {
        try {
            // v1.5.23：一次性标记优先——已灌过（含用户删光后）不再重灌
            if (settings.recordSeeded.first()) return@withContext
            if (db.KOiLRecordDao().count() == 0) {
                val records = loadRecords(context)
                if (records.isNotEmpty()) {
                    db.KOiLRecordDao().insertAll(records)
                    settings.setRecordSeeded(true)
                    Log.i("KOiL", "Seed: seeded ${records.size} records (one-time marker set)")
                }
            }
        } catch (t: Throwable) {
            Log.e("KOiL", "Seed seedIfEmpty failed", t)
        }
    }

    private fun loadRecords(context: Context): List<KOiLRecordEntity> {
        val raw = readAsset(context, "seed_records.json") ?: return emptyList()
        return json.decodeFromString<List<SeedRecord>>(raw).map {
            KOiLRecordEntity(
                reoilDate = it.reoilDate,
                mileage = it.mileage,
                oilAmount = it.oilAmount,
                payableAmount = it.payableAmount,
                actualAmount = it.actualAmount,
                oilPrice = it.oilPrice,
                oilGrade = it.oilGrade,
                province = it.province,
                source = it.source,
            )
        }
    }

    private fun readAsset(context: Context, name: String): String? =
        context.assets.open(name).bufferedReader().use { it.readText() }
}
