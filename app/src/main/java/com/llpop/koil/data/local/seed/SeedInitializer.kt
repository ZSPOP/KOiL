package com.llpop.koil.data.local.seed

import android.content.Context
import android.util.Log
import com.llpop.koil.data.local.AppDatabase
import com.llpop.koil.data.local.entity.KOiLRecordEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * 首次启动预置数据灌入：
 * - koil_record：用户提供的 16 条历史加油记录（保留，测试用）
 * - fuel_prices：已改为联网灌库（方案X，见 OilPriceDbSyncer.initPriceDb），不再内置油价快照
 * 仅在对应表为空时写入；失败静默（离线可用不依赖种子）。
 */
object SeedInitializer {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun seedIfEmpty(context: Context, db: AppDatabase) = withContext(Dispatchers.IO) {
        try {
            if (db.KOiLRecordDao().count() == 0) {
                val records = loadRecords(context)
                if (records.isNotEmpty()) {
                    db.KOiLRecordDao().insertAll(records)
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
