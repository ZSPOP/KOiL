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

    companion object {
        /**
         * 通道 ID。
         *
         * 为什么不能继续叫 `app_update`：Android 的通道重要性**创建后不可修改**，
         * 用同一个 id 删掉再按 HIGH 重建也没用——系统沿用旧设置（有单测钉住这一条）。
         * 旧通道是按 IMPORTANCE_DEFAULT 建的，而 DEFAULT 永远不会顶部弹出，
         * 所以「通知从来没弹出来」有一半原因在这里。
         *
         * 不叫 `app_update` 的另一个后果是：升级机子上会残留一条旧通道，
         * 由 [ensureChannel] 顺手清掉（全新安装本来就不存在它）。
         */
        const val CHANNEL_ID = "app_update_alert"
        private const val LEGACY_CHANNEL_ID = "app_update"
        private const val NOTIFICATION_ID = 1100

        /** 通知点击后要落到关于页：MainActivity 读这个 extra */
        const val EXTRA_OPEN_ABOUT = "com.llpop.koil.extra.OPEN_ABOUT"
        /** 通知对应的 tag：点开即视为已知晓，用来停掉同一版本的后续提醒 */
        const val EXTRA_TAG = "com.llpop.koil.extra.UPDATE_TAG"
    }

    /**
     * 建通道。ID 始终是 [CHANNEL_ID]，不换名。
     *
     * 难点是「重要性一经创建不可修改」：旧版按 DEFAULT 建过这条通道，
     * 现在要顶部弹出就必须是 HIGH。做法是**只做一次**「删掉旧通道再按 HIGH 重建」，
     * 并用一个落盘标记记住已迁移过——否则每次冷启动都会把用户在系统设置里手改的
     * 重要性再顶回 HIGH，等于把用户的选择反复抹掉。
     */
    private fun ensureChannel() {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        // 升级机子上会残留旧 ID 的通道（全新安装没有）。留着只会在系统设置里多一条删不掉的僵尸项
        if (nm.getNotificationChannel(LEGACY_CHANNEL_ID) != null) {
            nm.deleteNotificationChannel(LEGACY_CHANNEL_ID)
        }
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.koil_noti_update_channel),
                // IMPORTANCE_HIGH：顶部弹出的横幅 + 提示音，同时保留在通知栏里。
                // 「有新版本」是用户需要当场决定装不装的事，值得打断一次；点开或划掉就没了。
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = context.getString(R.string.koil_noti_update_channel_desc)
                // 不开「长按可改锁屏可见性」之外的额外开关：横幅 + 声音由 HIGH 自带
                lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
            }
            nm.createNotificationChannel(channel)
        }
    }

    /** 用户可能在系统设置里关掉整个 App 的通知：通道在、权限在，也发不出去 */
    /**
     * App 级总开关**与**通道本身都开着，这条通知才发得出去。
     *
     * `areNotificationsEnabled()` 只查 App 级：用户在系统设置里把「应用更新」这一个通道单独关掉时，
     * 它照样返回 true，于是我们照发、系统直接丢弃、App 完全无感——
     * 通道重要性一旦被手改成 NONE，就再也收不到更新通知，却看不出任何异常。
     * 通道尚未创建时返回 true（`ensureChannel()` 会紧接着建它）。
     */
    private fun channelEnabled(): Boolean {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = nm.getNotificationChannel(CHANNEL_ID)
        return channel == null || channel.importance != NotificationManager.IMPORTANCE_NONE
    }

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
            // API 26+ 由通道 IMPORTANCE_HIGH 决定 heads-up，这行是给旧版本兜底；
            // 显式 PRIORITY_HIGH 保证「顶部弹出 + 通知栏常驻」两件事同时成立
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        // 发出去了才记账：上面任何一个跳过条件都不该消耗掉这一次提醒
        settings.setNotifyUpdateTag(target.tag)
        settings.setNotifyUpdateAt(AppClock.millis())
        return true
    }
}

/**
 * 用户是否在系统设置里单独关掉了「应用更新」这一个渠道。
 *
 * App 总开关与运行时权限都可能还是好的，但渠道 importance 为 NONE 时系统会直接丢弃通知。
 * 设置页据此在副标题里点明，避免「开关开着却一条都收不到」的无声失败。
 */
fun isUpdateChannelDisabled(context: Context): Boolean = runCatching {
    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    nm.getNotificationChannel(UpdateNotifier.CHANNEL_ID)?.importance ==
        NotificationManager.IMPORTANCE_NONE
}.getOrDefault(false)
