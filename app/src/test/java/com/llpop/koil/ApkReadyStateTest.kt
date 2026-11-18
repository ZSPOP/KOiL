package com.llpop.koil

import com.llpop.koil.data.update.UpdateFailure
import com.llpop.koil.data.update.isReadyFor
import com.llpop.koil.data.update.shouldDropReadyPackage
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
        f.writeBytes(ByteArray(8))   // 非空：0 字节不算下完，见下一条
        assertTrue(isReadyFor("V1.1.10", "V1.1.10", f))
    }

    @Test
    fun `0 字节空壳不算 ready —— exists 骗得过人，骗不过安装器`() {
        // 下载中断留下的空文件：只判 exists 会显示「安装包已就绪」，
        // 点安装必被系统以「解析包时出现问题」拒掉
        val f = tmp.newFile("KOiL-update.apk")
        assertFalse(isReadyFor("V1.1.10", "V1.1.10", f))
        f.writeBytes(ByteArray(8))
        assertTrue(isReadyFor("V1.1.10", "V1.1.10", f))
    }

    @Test
    fun `文件被系统清掉就不 ready —— 判据是文件不是 state`() {
        val f = tmp.newFile("KOiL-update.apk")
        assertTrue(f.delete())
        assertFalse(isReadyFor("V1.1.10", "V1.1.10", f))
    }

    @Test
    fun `已装上的那一版包在启动时该被清掉`() {
        // 覆盖安装后：包里是 V1.2.9，App 自己已经是 1.2.9 —— 这 6.8MB 留着没用
        val f = tmp.newFile("KOiL-V1.2.9.apk")
        f.writeBytes(ByteArray(8))
        assertTrue(shouldDropReadyPackage("V1.2.9", "1.2.9", f))
    }

    @Test
    fun `还没装的新版包不能被清掉`() {
        // 下完还没来得及装，App 还是旧版本 —— 删了就得重下 6.8MB
        val f = tmp.newFile("KOiL-V1.2.9.apk")
        f.writeBytes(ByteArray(8))
        assertFalse(shouldDropReadyPackage("V1.2.9", "1.2.8", f))
    }

    @Test
    fun `文件不在或 tag 为空时不需要清`() {
        assertFalse(shouldDropReadyPackage("", "1.2.9", null))
        val gone = tmp.newFile("KOiL-V1.2.9.apk")
        assertTrue(gone.delete())
        assertFalse(shouldDropReadyPackage("V1.2.9", "1.2.9", gone))
    }

    @Test
    fun `tag 不一致不 ready`() {
        val f = tmp.newFile("KOiL-update.apk")
        assertFalse(isReadyFor("V1.1.10", "V1.1.9", f))
    }

    @Test
    fun `进程重启 —— 落盘 tag 与文件都在，恢复后不该重下`() {
        // readyTag 只有内存副本时，「下完没装、退出应用、再进来」会丢掉唯一凭据：
        val f = tmp.newFile("KOiL-update.apk")
        f.writeBytes(ByteArray(8))
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
