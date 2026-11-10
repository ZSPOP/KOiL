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
 * - 通道：oil_price_adjust_high（IMPORTANCE_HIGH——通道创建后重要性不可改，
 *   IMPORTANCE_HIGH 通道强制 heads-up 横幅弹窗）
 * - 权限：API 33+ 需 POST_NOTIFICATIONS（MainActivity 启动时请求；未授权静默跳过）
 * - 幂等：Worker 侧通过 AppSettings.priceLastNotifiedDate 去重
 */
object OilPriceNotifier {

    /** 通道 ID（IMPORTANCE_HIGH 强制弹横幅） */
    const val CHANNEL_ID = "oil_price_adjust_high"
    private const val NOTIFICATION_ID = 1001
    /** 窗口日前一天提醒独立 ID——与调价生效通知互不覆盖 */
    const val NOTIFICATION_ID_WINDOW_REMINDER = 1002
    /** 窗口日当天"今晚24点生效"提醒独立 ID */
    const val NOTIFICATION_ID_WINDOW_DAY = 1003

    fun ensureChannel(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.koil_set_price_push),
                NotificationManager.IMPORTANCE_HIGH,   // HIGH → heads-up 横幅弹窗
            ).apply {
                description = context.getString(R.string.koil_set_price_push_sub)
            }
            nm.createNotificationChannel(channel)
        }
    }

    /** 发通知；未授权通知权限时静默跳过（不 crash）；notificationId 区分不同通知。
     *  BigTextStyle → 正文完整显示、横幅更高、可展开。 */
    fun notify(context: Context, title: String, message: String, notificationId: Int = NOTIFICATION_ID) {
        // minSdk 33 起 POST_NOTIFICATIONS 恒存在（无需兼容低版本分支）
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        ensureChannel(context)
        // 点击通知进入 App 首页（FLAG_IMMUTABLE 兼容 API 31+ 强制要求）
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
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))   // 完整正文 + 可展开
            .setContentIntent(contentIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)   // 配合 HIGH 通道触发横幅
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(notificationId, notification)
    }
}
