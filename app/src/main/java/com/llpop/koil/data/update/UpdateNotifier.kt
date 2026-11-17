package com.llpop.koil.data.update

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.llpop.koil.MainActivity
import com.llpop.koil.R
import com.llpop.koil.data.settings.AppSettings
import com.llpop.koil.util.AppClock
import kotlinx.coroutines.flow.first

/**
 * 「发现新版本」的系统通知。
 *
 * 为什么需要它：首页更新横幅已撤掉（见 AddScreen 的历史），新版本改由系统通知告知；
 * 通知只负责**告知一次**，下载与安装全部留在关于页——通知栏是一条会被划掉的瞬时消息，
 * 而「有新版本」是持续到装完为止的状态，用通知当唯一入口等于把状态存进一个会过期的容器。
 *
 * 三条约束：
 * - **按 tag 去重**：冷启动与回前台都可能查到同一个版本，不去重会变成每隔 45 分钟一条；
 * - **不越过开关**：设置页「系统通知」总开关关掉时彻底不发（调价提醒与本通知共用这一个开关）；
 * - **没有权限就静默跳过**：API 33+ 未授权时 notify 会抛异常，必须先查。
 */
class UpdateNotifier(
    private val context: Context,
    private val settings: AppSettings,
) {

    /** 通道 ID：更新不是紧急事件，用 DEFAULT，不抢 heads-up */
    companion object {
        const val CHANNEL_ID = "app_update"
        private const val NOTIFICATION_ID = 1100

        /** 通知点击后要落到关于页：MainActivity 读这个 extra */
        const val EXTRA_OPEN_ABOUT = "com.llpop.koil.extra.OPEN_ABOUT"
        /** 通知对应的 tag：点开即视为已知晓，用来停掉同一版本的后续提醒 */
        const val EXTRA_TAG = "com.llpop.koil.extra.UPDATE_TAG"
    }

    private fun ensureChannel() {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.koil_noti_update_channel),
                // 更新提醒不该打断手上正在做的事：DEFAULT 出声但不弹横幅
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = context.getString(R.string.koil_noti_update_channel_desc)
            }
            nm.createNotificationChannel(channel)
        }
    }

    /** 用户可能在系统设置里关掉整个 App 的通知：通道在、权限在，也发不出去 */
    private fun channelEnabled(): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled()

    /**
     * 用户点开了这条通知（或从它进了关于页）：算已知晓，刷新时间戳。
     * 不写 tag 的话，7 天后还会为同一个版本再发一条 —— 用户明明已经看过。
     */
    suspend fun markAcknowledged(tag: String) {
        if (tag.isBlank()) return
        settings.setNotifyUpdateTag(tag)
        settings.setNotifyUpdateAt(AppClock.millis())
    }

    /**
     * 查到新版本时调用；只在「总开关开着 + 有权限 + 这个 tag 还没通知过」时发一条。
     *
     * @return 本次是否真的发出去（测试与调用方都靠它判断去重是否生效）
     */
    suspend fun notifyIfNeeded(target: UpdateTarget): Boolean {
        if (target.tag.isBlank()) return false
        if (!settings.notifyEnabled.first()) return false
        // 去重不只看 tag：同一版本会在 7 天后允许再提醒一次（见 shouldNotifyUpdate）
        if (!shouldNotifyUpdate(
                notifiedTag = settings.notifyUpdateTag.first(),
                notifiedAt = settings.notifyUpdateAt.first(),
                targetTag = target.tag,
                now = AppClock.millis(),
            )
        ) {
            return false
        }
        // 权限检查必须**写在这个函数里**（不能折进别的函数）：lint 只认同一函数体内的判断，
        // 拆出去会被判 MissingPermission，而它判的是真会崩的那一处
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        if (!channelEnabled()) return false

        ensureChannel()
        val launchIntent = Intent(context, MainActivity::class.java).apply {
            // SINGLE_TOP 与 CLEAR_TOP 必须成对：MainActivity 是默认 standard，
            // 只有 CLEAR_TOP 时它会走「销毁 + 重建」而不是 onNewIntent
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            // 点进来直接落在关于页：用户在这条通知上唯一的下一步就是「看看有什么 / 去更新」
            putExtra(EXTRA_OPEN_ABOUT, true)
            // 带上 tag：用户点开就算「已知晓」，据此刷新时间戳，7 天后不再追着提醒
            putExtra(EXTRA_TAG, target.tag)
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val title = context.getString(R.string.koil_noti_update_title, normalizeVersion(target.tag))
        val body = context.getString(R.string.koil_noti_update_body)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_noti_update)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(contentIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        // 发出去了才记账：上面任何一个跳过条件都不该消耗掉这一次提醒
        settings.setNotifyUpdateTag(target.tag)
        settings.setNotifyUpdateAt(AppClock.millis())
        return true
    }
}
