package com.llpop.koil.data.oil

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException
import org.koin.core.context.GlobalContext

/**
 * 窗口日当天"今晚24点生效"提醒 Worker。
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
        } catch (e: CancellationException) {
            // 任务被系统取消/被新任务替换时不要转成 retry，否则会重复排队唤醒（与 OilPriceSyncWorker 同口径）
            throw e
        } catch (e: Exception) {
            // 重试上限保护：一次性任务失败即 retry 会叠加指数退避无限唤醒；
            // 这是"今晚24点生效"提醒，错过窗口后重试已无意义，连续失败 3 次直接收尾，
            // 下个窗口日会重新精准调度。与 OilPriceSyncWorker 的 retryOrGiveUp 同口径。
            if (runAttemptCount >= MAX_RETRY) Result.success() else Result.retry()
        }
    }

    private companion object {
        const val MAX_RETRY = 3
    }
}
