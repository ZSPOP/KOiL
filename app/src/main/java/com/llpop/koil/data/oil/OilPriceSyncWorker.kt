package com.llpop.koil.data.oil

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import org.koin.core.context.GlobalContext

/**
 * 油价库周期自动检测 Worker（对齐 Web 端 koil_price_auto_sync 定时任务）。
 * 每 8 小时执行一次；受自动更新开关 + 8 小时节流约束（checkAndUpdate(false) 内部处理）。
 * 通知（调价生效 / 窗口日前一天提醒）v1.5.22 起抽到 OilPriceDbSyncer.notifyPriceChanges，
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
            // v1.5.22 D：通知不依赖本次同步成功（逻辑幂等：价格未变不推、已提醒不重复）
            syncer.notifyPriceChanges(applicationContext)
            // v1.5.27：每次周期检测后重新排期窗口日当天提醒（App 持续运行跨窗口日也能覆盖）
            syncer.scheduleWindowDayReminder(applicationContext)
            if (result.success) {
                Result.success()
            } else {
                Result.retry()
            }
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
