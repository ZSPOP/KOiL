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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 落盘路径的回归（PR #85 评审 P0-1 / P0-2 / P1-3 / P2-5 / P2-6 / P3-2）。
 *
 * 第一轮被打回的原因：口径统一到了数据层，但 UI 仍把「有效值」当「用户输入」往存储里写——
 * 全新安装什么都不填、直接点「保存并测试」，默认地址/账号/密码三件套就全进了存储；
 * 清空密码后再点「立即备份」，默认密码又被写回去，用户的停用被静默撤销。
 *
 * 断言方向刻意选「键的原始值」，不是「读出来等于默认值」：
 * 后者在兜底被改成写存储时照样通过，钉不住任何东西（`syncUrl` 那条 Flow 读出来永远非空）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class SyncViewModelConfigTest {

    private val settings = AppSettings(ApplicationProvider.getApplicationContext<Context>())

    @Before
    fun clearStore() = runBlocking { settings.clearSyncSecretForTest() }

    @Test
    fun `三项默认值都传下去_存储里仍然没有值`() = runBlocking {
        // 界面草稿的初值就是有效值（含默认兜底），原样传下去也不许落盘
        SyncDraftWriter.persist(
            settings,
            SyncConfig.DEFAULT_URL,
            SyncConfig.DEFAULT_USERNAME,
            SyncConfig.DEFAULT_APP_PASSWORD,
        )
        val (rawUrl, rawUser, rawPwd) = settings.rawSyncConfig()
        // 地址/账号被写成空串（语义＝回到默认），密码干脆不写
        assertEquals("", rawUrl)
        assertEquals("", rawUser)
        assertNull("默认密码不得写进存储", rawPwd)
        assertFalse("默认账号/密码不得被当成用户填过", settings.syncPasswordEverSet.first())
    }

    @Test
    fun `全新安装时三个键不存在`() = runBlocking {
        val (rawUrl, rawUser, rawPwd) = settings.rawSyncConfig()
        assertNull(rawUrl)
        assertNull(rawUser)
        assertNull(rawPwd)
        // 有效值仍然齐：默认值只在计算时出现
        assertEquals(SyncConfig.DEFAULT_URL, settings.syncUrl.first())
        assertEquals(SyncConfig.DEFAULT_USERNAME, SyncConfig.effectiveUsername(settings.syncUsername.first()))
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

    @Test
    fun `把地址账号改回内置默认_存储被清空而不是弹回旧值`() = runBlocking {
        // P3-2：先填自定义值，再改回默认
        SyncDraftWriter.persist(settings, "https://dav.example.com/dav/KOiL/", "me@example.com", null)
        SyncDraftWriter.persist(settings, SyncConfig.DEFAULT_URL, SyncConfig.DEFAULT_USERNAME, null)

        val (rawUrl, rawUser, _) = settings.rawSyncConfig()
        assertEquals("", rawUrl)
        assertEquals("", rawUser)
        // 读出来仍是默认值，但这来自兜底计算，不是存储里存的
        assertEquals(SyncConfig.DEFAULT_URL, settings.syncUrl.first())
    }

    @Test
    fun `旧版存量用户_有密码但没有标记时读作已设置过`() = runBlocking {
        // P2-6 升级路径：旧版没有 everSet 这个键，但存着非空密码
        settings.setSyncAppPassword("legacy-pwd")
        settings.setSyncPasswordEncrypted(false)

        assertTrue(
            "有非空密码就一定是用户设置过的，不能回落默认密码",
            settings.syncPasswordEverSet.first(),
        )
        assertEquals("legacy-pwd", settings.syncAppPassword.first())
    }

    @Test
    fun `存储为空且无标记时仍按从未设置`() = runBlocking {
        // 全新安装：可以回落内置默认密码，这是「打开开关就能连」的前提
        assertFalse(settings.syncPasswordEverSet.first())
        assertEquals(
            SyncConfig.DEFAULT_APP_PASSWORD,
            SyncConfig.effectivePassword("", settings.syncPasswordEverSet.first()),
        )
    }
}
