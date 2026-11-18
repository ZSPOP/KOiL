package com.llpop.koil

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.llpop.koil.data.update.UpdateChannelIssue
import com.llpop.koil.data.update.UpdateNotifier
import com.llpop.koil.data.update.isUpdateChannelDisabled
import com.llpop.koil.data.update.updateChannelIssue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 通道重要性改成 HIGH 为什么必须换 ID。
 *
 * 一度以为「删掉同名通道再按 HIGH 重建」可以改掉重要性——实测不行：
 * 同名重建会沿用旧设置，importance 仍停在 DEFAULT，而 DEFAULT 永远不会顶部弹出。
 * 这两条用例把系统真实行为钉住，防止有人再把通道 ID 改回 `app_update`。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class UpdateChannelMigrationTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val nm: NotificationManager
        get() = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private fun createChannel(importance: Int) {
        nm.createNotificationChannel(
            NotificationChannel(UpdateNotifier.CHANNEL_ID, "应用更新", importance)
        )
    }

    @Test
    fun `删掉同名通道再按 HIGH 重建 —— 重要性仍然是旧的`() {
        // 这条用例记录的是**系统真实行为**，也是本次迁移方案必须换 ID 的原因：
        // 旧版本按 DEFAULT 建过 app_update，即便 delete 之后再用同一个 id 按 HIGH 重建，
        // 系统仍沿用旧设置，importance 停在 DEFAULT —— 也就是说「删同名再重建」这条路根本行不通。
        createChannel(NotificationManager.IMPORTANCE_DEFAULT)
        assertEquals(
            NotificationManager.IMPORTANCE_DEFAULT,
            nm.getNotificationChannel(UpdateNotifier.CHANNEL_ID)!!.importance,
        )

        nm.deleteNotificationChannel(UpdateNotifier.CHANNEL_ID)
        createChannel(NotificationManager.IMPORTANCE_HIGH)

        val rebuilt = nm.getNotificationChannel(UpdateNotifier.CHANNEL_ID)
        assertNotNull("重建后通道必须存在", rebuilt)
        assertEquals(
            "同名重建会沿用旧重要性，改不成 HIGH",
            NotificationManager.IMPORTANCE_DEFAULT,
            rebuilt!!.importance,
        )
    }

    @Test
    fun `换一个新的通道 ID 按 HIGH 建 —— 这才真的拿到 HIGH`() {
        nm.createNotificationChannel(
            NotificationChannel("KOiL_Update_probe", "应用更新", NotificationManager.IMPORTANCE_HIGH)
        )
        assertEquals(
            NotificationManager.IMPORTANCE_HIGH,
            nm.getNotificationChannel("KOiL_Update_probe")!!.importance,
        )
    }

    @Test
    fun `通道被用户关掉时能查出来`() {
        createChannel(NotificationManager.IMPORTANCE_NONE)
        assertTrue(isUpdateChannelDisabled(context))
    }

    @Test
    fun `通道正常时不报警`() {
        createChannel(NotificationManager.IMPORTANCE_HIGH)
        assertFalse(isUpdateChannelDisabled(context))
    }

    @Test
    fun `重要性只有 DEFAULT 时算静默 —— 通知栏看得见但永不弹横幅`() {
        // 这就是 1.2.7 在 HyperOS 上的实际状态：通知能进通知栏，横幅从不出现
        createChannel(NotificationManager.IMPORTANCE_DEFAULT)
        assertEquals(UpdateChannelIssue.Quiet, updateChannelIssue(context))
        assertFalse("静默不是「被关掉」，两者不能混为一谈", isUpdateChannelDisabled(context))
    }

    @Test
    fun `HIGH 但既没声音也不振动时同样算静默`() {
        // 横幅的真正条件是「通道会响」，不是 importance 的数字大小
        val silent = NotificationChannel(
            UpdateNotifier.CHANNEL_ID,
            "应用更新",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            setSound(null, null)
            enableVibration(false)
        }
        nm.createNotificationChannel(silent)
        assertEquals(UpdateChannelIssue.Quiet, updateChannelIssue(context))
    }

    @Test
    fun `HIGH 且有声有振动时才算正常`() {
        val alerting = NotificationChannel(
            UpdateNotifier.CHANNEL_ID,
            "应用更新",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            setSound(android.net.Uri.parse("content://settings/system/notification_sound"), null)
            enableVibration(true)
        }
        nm.createNotificationChannel(alerting)
        assertEquals(UpdateChannelIssue.None, updateChannelIssue(context))
    }

    @Test
    fun `通道尚未创建时不报警也不判静默`() {
        // ensureChannel() 紧接着就会建它，此时报「静默」会让首启的副标题说假话
        assertEquals(UpdateChannelIssue.None, updateChannelIssue(context))
    }
}
