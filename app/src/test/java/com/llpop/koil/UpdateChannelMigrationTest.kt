package com.llpop.koil

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.llpop.koil.data.update.UpdateNotifier
import com.llpop.koil.data.update.isUpdateChannelDisabled
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
            NotificationChannel("app_update_alert", "应用更新", NotificationManager.IMPORTANCE_HIGH)
        )
        assertEquals(
            NotificationManager.IMPORTANCE_HIGH,
            nm.getNotificationChannel("app_update_alert")!!.importance,
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
}
