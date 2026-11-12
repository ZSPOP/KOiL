package com.llpop.koil

import com.llpop.koil.data.update.UpdateFailure
import com.llpop.koil.data.update.isReadyFor
import com.llpop.koil.data.update.nextReadyTag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * 「包是否已下好」的判据与失败后 readyTag 的去留。
 *
 * 这条逻辑是「下完 → 没授权 → 去设置页授予 → 回来再点」不重下 6.6MB 的全部依据，
 * 之前没有任何测试守着：谁把它改回读 state，CI 依然全绿。
 */
class ApkReadyStateTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `包已在且 tag 一致才算 ready`() {
        val f = tmp.newFile("KOiL-update.apk")
        assertTrue(isReadyFor("V1.1.10", "V1.1.10", f))
    }

    @Test
    fun `文件被系统清掉就不 ready —— 判据是文件不是 state`() {
        val f = tmp.newFile("KOiL-update.apk")
        assertTrue(f.delete())
        assertFalse(isReadyFor("V1.1.10", "V1.1.10", f))
    }

    @Test
    fun `tag 不一致不 ready`() {
        val f = tmp.newFile("KOiL-update.apk")
        assertFalse(isReadyFor("V1.1.10", "V1.1.9", f))
    }

    @Test
    fun `缺授权保留 readyTag —— 授权回来直接进安装器`() {
        assertEquals("V1.1.10", nextReadyTag("V1.1.10", UpdateFailure.NO_PERMISSION))
    }

    @Test
    fun `其它失败一律清空 readyTag —— 下次点击从头下载`() {
        listOf(UpdateFailure.DOWNLOAD, UpdateFailure.SIGNATURE, UpdateFailure.LAUNCH).forEach {
            assertNull(nextReadyTag("V1.1.10", it))
        }
    }

    @Test
    fun `进程重启 —— 落盘 tag 与文件都在，恢复后不该重下`() {
        // readyTag 只有内存副本时，「下完没装、退出应用、再进来」会丢掉唯一凭据：
        val f = tmp.newFile("KOiL-update.apk")
        val saved = "V1.1.10"
        // 恢复就是拿落盘 tag 当内存 tag 再判一次
        assertTrue(isReadyFor(saved, saved, f))
    }

    @Test
    fun `进程重启 —— 文件已被系统清掉，落盘 tag 不能当已下好`() {
        val f = tmp.newFile("KOiL-update.apk")
        assertTrue(f.delete())
        val saved = "V1.1.10"
        assertFalse(isReadyFor(saved, saved, f))
    }
}
