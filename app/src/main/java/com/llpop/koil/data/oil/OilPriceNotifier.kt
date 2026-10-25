package com.llpop.koil.data.oil

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.llpop.koil.R

/**
 * 调价推送通知：油价调整生效 / 窗口日前一天提醒。
 * - 通道：oil_price_adjust（默认重要级）
 * - 权限：API 33+ 需 POST_NOTIFICATIONS（MainActivity 启动时请求；未授权静默跳过）
 * - 幂等：Worker 侧通过 AppSettings.priceLastNotifiedDate 去重
 */
object OilPriceNotifier {

    const val CHANNEL_ID = "oil_price_adjust"
    private const val NOTIFICATION_ID = 1001

    fun ensureChannel(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.koil_set_price_push),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = context.getString(R.string.koil_set_price_push_sub)
            }
            nm.createNotificationChannel(channel)
        }
    }

    /** 发通知；未授权通知权限时静默跳过（不 crash） */
    fun notify(context: Context, title: String, message: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        ensureChannel(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_noti_price)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }
}
