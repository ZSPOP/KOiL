package com.llpop.koil

import com.llpop.koil.data.sync.SyncConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 云同步「有效配置」口径回归。
 *
 * 这里钉的是 PR ① 的核心承诺：**默认值只在计算时出现，不进存储**。
 * 所以关键断言不是「读出来等于默认值」（兜底写入回退时照样通过），
 * 而是「存储为空时，有效值才等于默认值」——见 [默认值不参与存储_]。
 */
class SyncConfigTest {

    @Test
    fun `地址为空才回落默认`() {
        assertEquals(SyncConfig.DEFAULT_URL, SyncConfig.effectiveUrl(""))
        assertEquals("https://example.com/dav/", SyncConfig.effectiveUrl("https://example.com/dav/"))
    }

    @Test
    fun `账号为空才回落默认`() {
        assertEquals(SyncConfig.DEFAULT_USERNAME, SyncConfig.effectiveUsername(""))
        assertEquals("someone@example.com", SyncConfig.effectiveUsername("someone@example.com"))
    }

    @Test
    fun `从没设置过密码时回落内置默认`() {
        assertEquals(SyncConfig.DEFAULT_APP_PASSWORD, SyncConfig.effectivePassword("", everSet = false))
    }

    @Test
    fun `设置过又清空不得回落默认`() {
        // 用户点「清空密码」= 主动停用：此处必须为空，否则会偷偷用默认密码连上去
        assertEquals("", SyncConfig.effectivePassword("", everSet = true))
    }

    @Test
    fun `存储有密码时永远用用户的`() {
        assertEquals("my-own-pwd", SyncConfig.effectivePassword("my-own-pwd", everSet = false))
        assertEquals("my-own-pwd", SyncConfig.effectivePassword("my-own-pwd", everSet = true))
    }

    @Test
    fun `三项齐全才算配置完成`() {
        assertTrue(
            SyncConfig.isComplete(SyncConfig.DEFAULT_URL, SyncConfig.DEFAULT_USERNAME, SyncConfig.DEFAULT_APP_PASSWORD)
        )
        assertFalse(SyncConfig.isComplete("", SyncConfig.DEFAULT_USERNAME, SyncConfig.DEFAULT_APP_PASSWORD))
        assertFalse(SyncConfig.isComplete(SyncConfig.DEFAULT_URL, "", SyncConfig.DEFAULT_APP_PASSWORD))
        assertFalse(SyncConfig.isComplete(SyncConfig.DEFAULT_URL, SyncConfig.DEFAULT_USERNAME, ""))
    }

    @Test
    fun `落盘守卫认得出三个内置默认值`() {
        // 这一组是落盘路径的判据：等于默认的一律不写进存储。
        // 原来的 `assertNotEquals(stored, effective)` 恒真，钉不住任何东西，已删除。
        assertTrue(SyncConfig.isBuiltInUrl(SyncConfig.DEFAULT_URL))
        assertTrue(SyncConfig.isBuiltInUsername(SyncConfig.DEFAULT_USERNAME))
        assertTrue(SyncConfig.isBuiltInPassword(SyncConfig.DEFAULT_APP_PASSWORD))

        // 带空白也要认出来（输入框可能带首尾空格）
        assertTrue(SyncConfig.isBuiltInUrl("  ${SyncConfig.DEFAULT_URL}  "))
        assertTrue(SyncConfig.isBuiltInUsername(" ${SyncConfig.DEFAULT_USERNAME} "))

        // 用户自建的值不能被误拦
        assertFalse(SyncConfig.isBuiltInUrl("https://example.com/dav/"))
        assertFalse(SyncConfig.isBuiltInUsername("someone@example.com"))
        assertFalse(SyncConfig.isBuiltInPassword("my-own-pwd"))
        // 空串也不等于默认：空串代表「用户清空了」，该照写（清空地址后 Flow 自己回落默认）
        assertFalse(SyncConfig.isBuiltInUrl(""))
        assertFalse(SyncConfig.isBuiltInUsername(""))
    }
}
