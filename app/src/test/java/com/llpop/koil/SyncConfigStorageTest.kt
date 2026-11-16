package com.llpop.koil

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.llpop.koil.data.settings.AppSettings
import com.llpop.koil.data.sync.SyncConfig
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 「默认值不落盘」的存储侧钉子（PR ① 的验收口径）。
 *
 * 断言必须写成「写入空输入后，存储里该键仍为空」——
 * 写成「读出来等于默认值」的话，一旦有人把兜底改成写存储，这条照样通过，钉不住。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class SyncConfigStorageTest {

    private val settings = AppSettings(ApplicationProvider.getApplicationContext<Context>())

    @Test
    fun `写入空账号后存储仍为空_默认值不落盘`() = runBlocking {
        settings.setSyncUsername("")
        assertEquals("", settings.syncUsername.first())
        // 有效值有默认，但那是算出来的，不是存下来的
        assertEquals(SyncConfig.DEFAULT_USERNAME, SyncConfig.effectiveUsername(settings.syncUsername.first()))
    }

    @Test
    fun `写入空密码后存储仍为空`() = runBlocking {
        settings.setSyncAppPassword("")
        assertEquals("", settings.syncAppPassword.first())
    }

    @Test
    fun `清空密码后有效密码为空_不是默认密码`() = runBlocking {
        settings.setSyncPasswordEverSet(true)
        settings.setSyncAppPassword("")
        val effective = SyncConfig.effectivePassword(
            settings.syncAppPassword.first(),
            settings.syncPasswordEverSet.first(),
        )
        assertEquals("", effective)
    }

    @Test
    fun `从未设置过时有效密码才回落默认`() = runBlocking {
        settings.setSyncPasswordEverSet(false)
        settings.setSyncAppPassword("")
        val effective = SyncConfig.effectivePassword(
            settings.syncAppPassword.first(),
            settings.syncPasswordEverSet.first(),
        )
        assertEquals(SyncConfig.DEFAULT_APP_PASSWORD, effective)
    }
}
