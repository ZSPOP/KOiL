package com.llpop.koil.data.oil

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.llpop.koil.R
import com.llpop.koil.data.local.dao.FuelPriceDao
import com.llpop.koil.data.settings.AppSettings
import kotlinx.coroutines.flow.first
import org.koin.core.context.GlobalContext
import java.time.LocalDate

/**
 * 油价库周期自动检测 Worker（对齐 Web 端 koil_price_auto_sync 定时任务）。
 * 每 8 小时执行一次；受自动更新开关 + 8 小时节流约束（checkAndUpdate(false) 内部处理）。
 * 同步成功后顺带推送：① 调价生效通知（新生效日 vs 上次已通知）② 窗口日前一天提醒。
 */
class OilPriceSyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val syncer = GlobalContext.get().get<OilPriceDbSyncer>()
            val result = syncer.checkAndUpdate(force = false)
            if (result.success) {
                notifyPriceChanges(applicationContext)
                Result.success()
            } else {
                Result.retry()
            }
        } catch (e: Exception) {
            Result.retry()
        }
    }

    /** 调价生效 + 窗口日前一天推送（受"調價推送通知"开关控制；未授权通知权限静默跳过） */
    private suspend fun notifyPriceChanges(context: Context) {
        val koin = GlobalContext.get()
        val settings = koin.get<AppSettings>()
        if (!settings.pricePush.first()) return
        val dao = koin.get<FuelPriceDao>()

        // 1) 调价生效通知：默认省 + 默认油号的最新生效日变化（库内 grade 为完整标签，如"92号汽油"）
        val province = settings.defaultProvince.first()
            .takeIf { it in OilPriceDbSyncer.PROVINCES } ?: OilPriceDbSyncer.PROVINCES[0]
        val gradeCode = settings.defaultOilGrade.first()
            .takeIf { it in OilPriceNetwork.GRADE_LABELS } ?: "92"
        val grade = OilPriceNetwork.GRADE_LABELS[gradeCode] ?: gradeCode
        val newest = dao.getNewest(province, grade)
        if (newest != null) {
            val lastNotified = settings.priceLastNotifiedDate.first()
            if (lastNotified.isBlank()) {
                // 首次启用：静默锚定当前生效日，不打扰
                settings.setPriceLastNotifiedDate(newest.effectiveDate)
            } else if (newest.effectiveDate != lastNotified) {
                val prev = dao.getPrevious(province, grade)
                val gradeLabel = OilPriceNetwork.GRADE_LABELS[grade] ?: grade
                val title = context.getString(R.string.noti_title_price_adjust)
                val message = if (prev != null && prev.effectiveDate < newest.effectiveDate) {
                    context.getString(
                        R.string.noti_msg_price_adjust,
                        province, gradeLabel, prev.price, newest.price,
                    )
                } else {
                    context.getString(
                        R.string.noti_msg_price_adjust,
                        province, gradeLabel, newest.price, newest.price,
                    )
                }
                OilPriceNotifier.notify(context, title, message)
                settings.setPriceLastNotifiedDate(newest.effectiveDate)
            }
        }

        // 2) 窗口日前一天提醒：下一个未过期窗口 == 明天
        val adjustDates = settings.priceDbAdjustDates.first()
            .split(",").map { it.trim() }.filter { it.isNotBlank() }
        if (adjustDates.isNotEmpty()) {
            val today = LocalDate.now()
            val tomorrow = today.plusDays(1).toString()
            val next = adjustDates.firstOrNull { it >= today.toString() }
            val reminded = settings.priceLastWindowReminded.first()
            if (next == tomorrow && reminded != next) {
                OilPriceNotifier.notify(
                    context,
                    context.getString(R.string.noti_title_window_tomorrow),
                    context.getString(R.string.noti_msg_window_tomorrow, next),
                )
                settings.setPriceLastWindowReminded(next)
            }
        }
    }
}
