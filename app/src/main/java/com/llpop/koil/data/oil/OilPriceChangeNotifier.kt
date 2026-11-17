package com.llpop.koil.data.oil

import android.content.Context
import com.llpop.koil.R
import com.llpop.koil.data.local.dao.KOiLPriceDao
import com.llpop.koil.data.settings.AppSettings
import com.llpop.koil.util.AppClock
import kotlinx.coroutines.flow.first
import java.time.LocalDate

/**
 * 油价库通知器（从 OilPriceDbSyncer 拆出，M-09，行为不变）。
 * Worker 与设置页「立即更新」共用；独立于同步成功与否——网络挂时窗口提醒也不丢。
 * 机制细节见 docs/KOiL油价数据库机制.md。
 */
internal class OilPriceChangeNotifier(
    private val settings: AppSettings,
    private val priceDao: KOiLPriceDao,
) {

    suspend fun notifyPriceChanges(context: Context) {
        if (!settings.notifyEnabled.first()) return

        // 1) 调价生效通知：默认省 + 默认油号的最新生效日变化。
        // 仅价格实际变化（prev==null 或 prev.price != newest.price）才推送——
        // 搁浅窗口（维持价）不误发"调价至同价"通知；价格未变只锚定 lastNotified，
        // 后续真调价仍正常推送（防旧锚点干扰）。
        val province = settings.koilDefaultProvince()
        val gradeCode = settings.defaultOilGrade.first()
            .takeIf { it in OilPriceNetwork.GRADE_LABELS } ?: "92"
        val grade = OilPriceNetwork.GRADE_LABELS[gradeCode] ?: gradeCode
        val newest = priceDao.getLatestPrice(province, grade, AppClock.today().format(KOIL_DATE_FMT))
        if (newest != null) {
            val lastNotified = settings.priceLastNotifiedDate.first()
            if (lastNotified.isBlank()) {
                // 首次启用：静默锚定当前生效日，不打扰
                settings.setPriceLastNotifiedDate(newest.effectiveDate)
            } else if (newest.effectiveDate != lastNotified) {
                val today = AppClock.today().format(KOIL_DATE_FMT)
                val prev = priceDao.getPrevious(province, grade, today)
                val priceChanged = prev == null || prev.price != newest.price
                if (shouldNotifyPriceChange(lastNotified, newest.effectiveDate, priceChanged)) {
                    val gradeLabel = OilPriceNetwork.GRADE_LABELS[grade] ?: grade
                    val title = context.getString(R.string.koil_noti_title_price_adjust)
                    val message = if (prev != null && prev.effectiveDate < newest.effectiveDate) {
                        context.getString(
                            R.string.koil_noti_msg_price_adjust,
                            province, gradeLabel, prev.price, newest.price,
                        )
                    } else {
                        // 没有可信的上一调价日时不能显示“¥8.63 → ¥8.63”，改为单边最新价
                        context.getString(
                            R.string.koil_noti_msg_price_latest,
                            province, gradeLabel, newest.price,
                        )
                    }
                    OilPriceNotifier.notify(context, title, message)
                }
                settings.setPriceLastNotifiedDate(newest.effectiveDate)
            }
        }

        // 2) 窗口日前一天提醒：下一个未过期窗口 == 明天
        val adjustDates = settings.priceDbAdjustDates.first()
            .split(",").map { it.trim() }.filter { it.isNotBlank() }
        if (adjustDates.isNotEmpty()) {
            val today = AppClock.today()
            val tomorrow = today.plusDays(1).toString()
            // 收成非空串再判：判据在纯函数里，这里不再依赖编译器对 String? 的智能转换
            val next = adjustDates.firstOrNull { it >= today.toString() }.orEmpty()
            val reminded = settings.priceLastWindowReminded.first()
            if (shouldRemindWindowTomorrow(next, tomorrow, reminded)) {
                OilPriceNotifier.notify(
                    context,
                    context.getString(R.string.koil_noti_title_window_tomorrow),
                    context.getString(R.string.koil_noti_msg_window_tomorrow, next),
                    OilPriceNotifier.NOTIFICATION_ID_WINDOW_REMINDER,   // 独立 ID 不覆盖调价通知
                )
                settings.setPriceLastWindowReminded(next)
            }

            // 3) 窗口日当天提醒：今天 == 窗口日 → "今晚 24 时生效"
            // 触发点：一次性 Worker（窗口日 00:05 精准）+ 启动/8h 周期兜底（notifyPriceChanges 幂等去重）
            val todayStr = today.toString()
            if (adjustDates.contains(todayStr)) {
                val remindedDay = settings.priceLastWindowDayReminded.first()
                if (shouldRemindWindowToday(adjustDates, todayStr, remindedDay)) {
                    OilPriceNotifier.notify(
                        context,
                        context.getString(R.string.koil_noti_title_window_today),
                        context.getString(R.string.koil_noti_msg_window_today, todayStr),
                        OilPriceNotifier.NOTIFICATION_ID_WINDOW_DAY,   // 独立 ID
                    )
                    settings.setPriceLastWindowDayReminded(todayStr)
                }
            }
        }
    }

    /**
     * 为**下一个尚未到来的**窗口日调度"当天 00:05"的一次性提醒 Worker（REPLACE：日历更新/重复调度覆盖旧任务）。
     * 当天若正是窗口日，`> today` 的筛选会让目标落到再下一个窗口——当天的"今晚24点生效"提醒
     * 由 [notifyPriceChanges] 负责（启动链与周期 Worker 都会调用，内部按 priceLastWindowDayReminded 幂等）。
     * 日历为空（从未同步成功）时跳过——首次同步成功后由启动链/周期 Worker 重新调度。
     */
    suspend fun scheduleWindowDayReminder(context: Context) {
        val adjustDates = settings.priceDbAdjustDates.first()
            .split(",").map { it.trim() }.filter { it.isNotBlank() }
        val next = adjustDates.firstOrNull { it > AppClock.today().format(KOIL_DATE_FMT) } ?: return
        val delayMs = runCatching {
            val target = LocalDate.parse(next).atTime(0, 5)
                .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
            (target - AppClock.millis()).coerceAtLeast(0L)
        }.getOrNull() ?: return
        val request = androidx.work.OneTimeWorkRequestBuilder<OilPriceWindowDayWorker>()
            .setInitialDelay(delayMs, java.util.concurrent.TimeUnit.MILLISECONDS)
            .build()
        androidx.work.WorkManager.getInstance(context).enqueueUniqueWork(
            "KOiL_window_day_reminder",
            androidx.work.ExistingWorkPolicy.REPLACE,
            request,
        )
    }
}

/*
 * 下面三个判定抽成顶层函数是为了能单测：通知去重一旦判错，用户要么被同一个窗口反复打扰，
 * 要么永远收不到那次提醒 —— 两种都是"只有真机跑过一整天才会发现"的类型。
 * 判定只依赖字符串/布尔参数，不碰 DataStore 与通知通道，所以可以在 JVM 里跑满所有组合。
 */

/**
 * 调价生效通知要不要发。
 *
 * @param lastNotified 上次已通知的生效日（空串 = 首次启用，只锚定不打扰）
 * @param newestDate 库里最新的生效日
 * @param priceChanged 与上一条价格相比是否真的变了（搁浅窗口的维持价不算）
 */
internal fun shouldNotifyPriceChange(
    lastNotified: String,
    newestDate: String,
    priceChanged: Boolean,
): Boolean = lastNotified.isNotBlank() && newestDate != lastNotified && priceChanged

/** 窗口日前一天提醒要不要发：下一个未过期窗口正好是明天，且这个窗口没提醒过 */
internal fun shouldRemindWindowTomorrow(
    nextWindow: String,
    tomorrow: String,
    reminded: String,
): Boolean = nextWindow == tomorrow && reminded != nextWindow

/** 窗口日当天提醒要不要发：今天本身就是窗口日，且当天没提醒过 */
internal fun shouldRemindWindowToday(
    adjustDates: List<String>,
    today: String,
    remindedDay: String,
): Boolean = adjustDates.contains(today) && remindedDay != today
