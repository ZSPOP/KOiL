package com.llpop.koil.data.update

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
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
         * 这条通道被换过两次，原因都是同一个：**通道的任何属性一经创建就不可修改**，
         * 用同一个 id 删掉再按新参数重建也没用——系统沿用首次创建的那份设置
         * （`UpdateChannelMigrationTest` 钉住了这条实测行为）。
         * 所以「把已经落地的通道改成会弹横幅」在 Android 上没有别的办法，只能换 ID。
         *
         * 两次换名的经过（不要重复踩）：
         * - `app_update`：最早按 IMPORTANCE_DEFAULT 建，DEFAULT 永远不会顶部弹出；
         * - `app_update_alert`：只把重要性抬到 HIGH，忽略了**通道是否静音**这个真正的开关，
         *   HyperOS 上落地成了「声音：无 / 悬浮通知：关」，横幅依旧不弹；
         * - `KOiL_Update`：现用。建通道时显式给默认提示音 + 振动 + 锁屏可见，
         *   重要性只是「允许弹」的前提，**有声才是弹的条件**。
         *
         * 历史 ID 由 [ensureChannel] 顺手删掉（全新安装本来就不存在它们）。
         */
        const val CHANNEL_ID = "KOiL_Update"
        private val LEGACY_CHANNEL_IDS = listOf("app_update_alert", "app_update")
        private const val NOTIFICATION_ID = 1100

        /** 通知点击后要落到关于页：MainActivity 读这个 extra */
        const val EXTRA_OPEN_ABOUT = "com.llpop.koil.extra.OPEN_ABOUT"
        /** 通知对应的 tag：点开即视为已知晓，用来停掉同一版本的后续提醒 */
        const val EXTRA_TAG = "com.llpop.koil.extra.UPDATE_TAG"
    }

    /**
     * 建通道，并清掉历史 ID 的残留。
     *
     * 通道属性一经创建不可修改，**同名重建也改不掉**：删掉之后再用同一个 id 按新参数重建，
     * 系统仍沿用首次那份（`UpdateChannelMigrationTest` 钉住了这条实测行为）。
     * 所以「让更新通知弹横幅」的唯一办法是换 ID：[CHANNEL_ID] = `KOiL_Update`。
     *
     * **光有 IMPORTANCE_HIGH 不够。** 横幅真正的条件是这个通道「会响」：
     * 只写 IMPORTANCE_HIGH、不给声音时，部分 ROM（HyperOS 实测）会把通道落地成
     * 「声音：无 / 振动：关 / 悬浮通知：关」，于是在通知栏里躺着、永远不弹。
     * 这里所以要显式给默认提示音与振动，重要性只是「允许弹」的前提。
     *
     * [LEGACY_CHANNEL_IDS] 只会出现在升级机子上（全新安装没有），顺手删掉，
     * 免得系统设置里堆着几条删不掉的僵尸通道。
     */
    private fun ensureChannel() {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        LEGACY_CHANNEL_IDS.forEach { legacy ->
            if (nm.getNotificationChannel(legacy) != null) nm.deleteNotificationChannel(legacy)
        }
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.koil_noti_update_channel),
                // IMPORTANCE_HIGH：允许顶部弹出的横幅，同时保留在通知栏里。
                // 「有新版本」是用户需要当场决定装不装的事，值得打断一次；点开或划掉就没了。
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = context.getString(R.string.koil_noti_update_channel_desc)
                // 必须显式给声音与振动：这是横幅的触发条件，不是锦上添花。
                // 用系统默认通知音而不是自带音频资源——跟随用户的铃声与静音策略，也不额外占包体。
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .build(),
                )
                enableVibration(true)
                // 锁屏上显示内容：更新提醒不含隐私信息，藏起来反而让人以为没收到
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
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
            // 默认提示音 + 振动 + 灯效一起要：与通道属性同向，避免某一路被系统判成静默
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            // 用「状态」类而不是「推荐」类：部分 ROM 会把「推荐」折叠进汇总，状态类不会。
            // Android 没有「软件更新」这个 category 常量（CATEGORY_UPDATE 两边都不存在），
            // 更新通知的本质也确实是「一个持续到你装完为止的状态」，与 STATUS 语义一致
            .setCategory(android.app.Notification.CATEGORY_STATUS)
            // 锁屏可见：与通道的 VISIBILITY_PUBLIC 保持一致
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        // 发出去了才记账：上面任何一个跳过条件都不该消耗掉这一次提醒
        settings.setNotifyUpdateTag(target.tag)
        settings.setNotifyUpdateAt(AppClock.millis())
        return true
    }
}

/** 更新通知这条通道在系统侧的三态：正常 / 被关掉 / 在但不会响 */
enum class UpdateChannelIssue {
    /** 通道不在或一切正常（通道尚未创建时也按正常处理，`ensureChannel()` 紧接着就建） */
    None,

    /** 用户在系统设置里把这条通道整个关掉了：重要性 NONE，系统直接丢弃 */
    Disabled,

    /**
     * 通道还在、但会被判成静默：重要性低于 HIGH，或既没有声音也不会振动。
     * 这种通道**永远不会弹出横幅**，通知只安静地躺在通知栏里 —— 正是 1.2.7 之前的样子。
     */
    Quiet,
}

/**
 * 读出更新通道在系统侧的实际状态，供设置页副标题提示「开关开着却收不到」的原因。
 *
 * 光查 `areNotificationsEnabled()` 不够：App 级开关、运行时权限、通道级开关是三件事，
 * 任何一层关掉都发不出去，而 App 自己毫无感知。这里把通道层的两种失败分开报，
 * 一条提示「被关掉」、一条提示「不响」——后者才是最容易被忽略的：通知能看见，就是不弹。
 */
fun updateChannelIssue(context: Context): UpdateChannelIssue = runCatching {
    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val channel = nm.getNotificationChannel(UpdateNotifier.CHANNEL_ID)
        ?: return@runCatching UpdateChannelIssue.None
    when {
        channel.importance == NotificationManager.IMPORTANCE_NONE -> UpdateChannelIssue.Disabled
        // 重要性不足：系统根本不会考虑横幅
        channel.importance < NotificationManager.IMPORTANCE_HIGH -> UpdateChannelIssue.Quiet
        // 重要性够了但通道静默：横幅同样不弹（HyperOS 上「声音：无」时就是这一档）
        channel.sound == null && !channel.shouldVibrate() -> UpdateChannelIssue.Quiet
        else -> UpdateChannelIssue.None
    }
}.getOrDefault(UpdateChannelIssue.None)

/** 用户是否在系统设置里单独关掉了「应用更新」这一个渠道。 */
fun isUpdateChannelDisabled(context: Context): Boolean =
    updateChannelIssue(context) == UpdateChannelIssue.Disabled
