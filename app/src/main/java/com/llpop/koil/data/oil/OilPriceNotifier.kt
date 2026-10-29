package com.llpop.koil.data.oil

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.llpop.koil.MainActivity
import com.llpop.koil.R

/**
 * 调价推送通知：油价调整生效 / 窗口日前一天提醒 / 窗口日当天提醒。
 * - 通道：oil_price_adjust_high（v1.5.28：由 DEFAULT 升 HIGH——原通道创建后重要性不可改，
 *   换新通道 ID 强制 heads-up 横幅弹窗；旧通道删除清理）
 * - 权限：API 33+ 需 POST_NOTIFICATIONS（MainActivity 启动时请求；未授权静默跳过）
 * - 幂等：Worker 侧通过 AppSettings.priceLastNotifiedDate 去重
 */
object OilPriceNotifier {

    /** v1.5.28：新通道 ID（IMPORTANCE_HIGH 强制弹横幅）；旧通道 oil_price_adjust 删除清理 */
    const val CHANNEL_ID = "oil_price_adjust_high"
    private const val CHANNEL_ID_LEGACY = "oil_price_adjust"
    private const val NOTIFICATION_ID = 1001
    /** v1.5.23 D3：窗口日前一天提醒独立 ID——与调价生效通知不再互相覆盖 */
    const val NOTIFICATION_ID_WINDOW_REMINDER = 1002
    /** v1.5.27：窗口日当天"今晚24点生效"提醒独立 ID */
    const val NOTIFICATION_ID_WINDOW_DAY = 1003

    fun ensureChannel(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        // v1.5.28：删除旧 DEFAULT 通道（避免同 ID 重建在部分机型保留旧重要性；通知均已 autoCancel，无残留干扰）
        nm.deleteNotificationChannel(CHANNEL_ID_LEGACY)
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.koil_set_price_push),
                NotificationManager.IMPORTANCE_HIGH,   // v1.5.28：HIGH → heads-up 横幅弹窗
            ).apply {
                description = context.getString(R.string.koil_set_price_push_sub)
            }
            nm.createNotificationChannel(channel)
        }
    }

    /** 发通知；未授权通知权限时静默跳过（不 crash）；notificationId 区分不同通知（v1.5.23 D3）。
     *  v1.5.28：BigTextStyle → 正文完整显示、横幅更高、可展开。 */
    fun notify(context: Context, title: String, message: String, notificationId: Int = NOTIFICATION_ID) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        ensureChannel(context)
        // v1.5.22 C：点击通知进入 App 首页（FLAG_IMMUTABLE 兼容 API 31+ 强制要求）
        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_noti_price)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))   // v1.5.28：完整正文 + 可展开
            .setContentIntent(contentIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)   // v1.5.28：配合 HIGH 通道触发横幅
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(notificationId, notification)
    }
}
