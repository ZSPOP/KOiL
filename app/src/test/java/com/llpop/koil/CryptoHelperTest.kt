package com.llpop.koil

import android.util.Base64
import com.llpop.koil.data.sync.CryptoHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assume.assumeNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 云同步密码的加解密边界。
 *
 * 契约只有一条：**任何失败都必须返回 null，不能抛**。调用方据此回退明文存储并打标记；
 * 一旦这里抛异常，设置页保存密码就会崩，而且用户完全不知道发生了什么。
 * 密文来自 DataStore，损坏的路径真实存在：手工改过备份、换过设备、密钥被系统清掉。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class CryptoHelperTest {

    @Test
    fun `非法 Base64 返回 null 不抛`() {
        assertNull(CryptoHelper.decrypt("not-base64!!"))
    }

    @Test
    fun `空串返回 null`() {
        // Base64 解出 0 字节 → 取 IV 时越界，必须被吞成 null
        assertNull(CryptoHelper.decrypt(""))
    }

    @Test
    fun `截断的密文返回 null`() {
        val tooShort = Base64.encodeToString(ByteArray(4), Base64.NO_WRAP)
        assertNull(CryptoHelper.decrypt(tooShort))
    }

    @Test
    fun `只有 IV 没有密文也返回 null`() {
        // 12 字节 IV + 0 字节正文：GCM 校验必然失败
        val ivOnly = Base64.encodeToString(ByteArray(12), Base64.NO_WRAP)
        assertNull(CryptoHelper.decrypt(ivOnly))
    }

    @Test
    fun `Keystore 可用时加解密互逆且密文不含明文`() {
        val plain = "app-password-测试"
        val encrypted = CryptoHelper.encrypt(plain)
        // Robolectric 不保证提供 AndroidKeyStore：不可用时应返回 null（这正是降级契约），
        // 此时本用例按"环境不支持"跳过，而不是伪装成通过
        assumeNotNull(encrypted)

        assertNotEquals(plain, encrypted)
        assertEquals(plain, CryptoHelper.decrypt(encrypted!!))
    }

    @Test
    fun `同一明文两次加密密文不同（随机 IV）`() {
        val first = CryptoHelper.encrypt("same") ?: return
        val second = CryptoHelper.encrypt("same") ?: return
        assertNotEquals("IV 必须随机，否则相同密码产生相同密文", first, second)
    }
}
