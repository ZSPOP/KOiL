package com.llpop.koil.data.oil

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import org.koin.core.context.GlobalContext

/**
 * v1.5.27：窗口日当天"今晚24点生效"提醒 Worker。
 * 由 OilPriceDbSyncer.scheduleWindowDayReminder 在窗口日 00:05 精准调度（一次性）；
 * 执行时复用 notifyPriceChanges（幂等：当天已提醒则跳过，不会重复打扰）。
 */
class OilPriceWindowDayWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val syncer = GlobalContext.get().get<OilPriceDbSyncer>()
            syncer.notifyPriceChanges(applicationContext)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
