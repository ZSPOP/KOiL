package com.llpop.koil.data.oil

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import org.koin.core.context.GlobalContext

/**
 * 油价库周期自动检测 Worker（对齐 Web 端 koil_price_auto_sync 定时任务）。
 * 每 8 小时执行一次；受自动更新开关 + 8 小时节流约束（checkAndUpdate(false) 内部处理）。
 */
class OilPriceSyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val syncer = GlobalContext.get().get<OilPriceDbSyncer>()
            val result = syncer.checkAndUpdate(force = false)
            if (result.success) Result.success() else Result.retry()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
