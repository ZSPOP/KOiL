package com.llpop.koil.ui.screen.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.llpop.koil.data.settings.AppSettings
import com.llpop.koil.data.sync.SyncConfig
import com.llpop.koil.data.sync.SyncDraftWriter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 落盘路径的回归（PR #85 第一轮评审 P0-1 / P0-2 / P1-3 / P2-5）。
 *
 * 被打回的原因：口径统一到了数据层，但 UI 仍把「有效值」当「用户输入」往存储里写——
 * 全新安装什么都不填、直接点「保存并测试」，默认地址/账号/密码三件套就全进了存储；
 * 清空密码后再点「立即备份」，默认密码又被写回去，用户的停用被静默撤销。
 *
 * 这里跑的是保存路径唯一的落盘实现 `SyncDraftWriter.persist`，断言方向刻意选
 * 「存储里该键仍为空」而不是「读出来等于默认值」。
 *
 * `setEnabled(on)` 已改为单参、内部不再调用 persistDrafts，开关这条路径结构性上不可能写配置。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class SyncViewModelConfigTest {

    private val settings = AppSettings(ApplicationProvider.getApplicationContext<Context>())

    @Test
    fun `三项默认值都传下去_存储里仍然没有值`() = runBlocking {
        // 界面草稿的初值就是有效值（含默认兜底），原样传下去也不许落盘
        SyncDraftWriter.persist(
            settings,
            SyncConfig.DEFAULT_URL,
            SyncConfig.DEFAULT_USERNAME,
            SyncConfig.DEFAULT_APP_PASSWORD,
        )
        assertEquals("", settings.syncUsername.first())
        assertEquals("", settings.syncAppPassword.first())
        assertFalse("默认账号/密码不得被当成用户填过", settings.syncPasswordEverSet.first())
    }

    @Test
    fun `清空密码后的空草稿不会把默认密码写回去`() = runBlocking {
        // 模拟「设置过又清空」：everSet=true、存储为空
        settings.setSyncPasswordEverSet(true)
        settings.setSyncAppPassword("")

        SyncDraftWriter.persist(settings, null, null, null)   // 清空后点按钮，草稿为 null
        SyncDraftWriter.persist(settings, null, null, "")     // 空串同样不该写

        assertEquals("", settings.syncAppPassword.first())
        assertEquals(
            "",
            SyncConfig.effectivePassword(settings.syncAppPassword.first(), settings.syncPasswordEverSet.first()),
        )
    }

    @Test
    fun `用户自己填的值照常落盘并且记为设置过`() = runBlocking {
        SyncDraftWriter.persist(settings, null, null, "my-own-pwd")

        assertEquals("my-own-pwd", settings.syncAppPassword.first())
        assertTrue("保存过密码必须记为设置过，否则换机解密失败会静默回落默认", settings.syncPasswordEverSet.first())
    }

    @Test
    fun `用户自建地址与账号照常落盘`() = runBlocking {
        SyncDraftWriter.persist(settings, "https://dav.example.com/dav/KOiL/", "me@example.com", null)

        assertEquals("https://dav.example.com/dav/KOiL/", settings.syncUrl.first())
        assertEquals("me@example.com", settings.syncUsername.first())
    }
}
