package com.llpop.koil.data.oil

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException
import org.koin.core.context.GlobalContext

/**
 * 油价库周期自动检测 Worker（对齐 Web 端 koil_price_auto_sync 定时任务）。
 * 每 8 小时执行一次；受自动更新开关 + 8 小时节流约束（checkAndUpdate(false) 内部处理）。
 * 通知（调价生效 / 窗口日前一天提醒）由 OilPriceDbSyncer.notifyPriceChanges 处理，
 * 独立于同步结果执行——同步失败（网络挂）时窗口提醒也不丢；设置页「立即更新」共用同一入口。
 */
class OilPriceSyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val syncer = GlobalContext.get().get<OilPriceDbSyncer>()
            val result = syncer.checkAndUpdate(force = false)
            // 通知不依赖本次同步成功（逻辑幂等：价格未变不推、已提醒不重复）
            syncer.notifyPriceChanges(applicationContext)
            // 每次周期检测后重新排期窗口日当天提醒（App 持续运行跨窗口日也能覆盖）
            syncer.scheduleWindowDayReminder(applicationContext)
            if (result.success) {
                Result.success()
            } else {
                retryOrGiveUp()
            }
        } catch (e: CancellationException) {
            // 任务被系统取消/被新任务替换时不要转成 retry，否则会重复排队唤醒
            throw e
        } catch (e: Exception) {
            retryOrGiveUp()
        }
    }

    /**
     * 重试上限保护：避免失败即 Result.retry() 叠加指数退避导致长时间反复唤醒；
     * 周期任务（8 小时）本来就会再来一次，连续失败 3 次后直接收尾，等下个周期，避免无谓耗电。
     */
    private fun retryOrGiveUp(): Result =
        if (shouldRetrySync(runAttemptCount)) Result.retry() else Result.success()
}

/** 连续失败达到这个次数就收尾等下个周期（周期任务 8 小时一次，不必在一轮里耗电） */
internal const val MAX_SYNC_RETRY = 3

/**
 * 失败后要不要 Result.retry()。
 *
 * 抽成顶层函数：Worker 本体要 WorkManager 环境，跑不进 JVM 单测；而"重试几次才放弃"
 * 是这里唯一会算错的东西 —— 判错的表现是「反复唤醒耗电」或「一次失败就放弃」。
 * `runAttemptCount` 是本次任务的第几次尝试（0 起），达到 [MAX_SYNC_RETRY] 就不再重试。
 */
internal fun shouldRetrySync(runAttemptCount: Int): Boolean = runAttemptCount < MAX_SYNC_RETRY
