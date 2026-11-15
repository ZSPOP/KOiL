package com.llpop.koil

import com.llpop.koil.data.update.AboutCapsuleState
import com.llpop.koil.data.update.AboutCapsuleState.Downloading
import com.llpop.koil.data.update.AboutCapsuleState.Failed
import com.llpop.koil.data.update.AboutCapsuleState.Hidden
import com.llpop.koil.data.update.AboutCapsuleState.Install
import com.llpop.koil.data.update.AboutCapsuleState.Newer
import com.llpop.koil.data.update.AboutUpdateStatus
import com.llpop.koil.data.update.UpdateDownloadState
import com.llpop.koil.data.update.UpdateFailure
import com.llpop.koil.data.update.resolveCapsuleState
import com.llpop.koil.data.update.shouldDiscardReady
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 关于页胶囊的形态推导。
 *
 * 这段判断决定了「点一下会发生什么」：判错一格就是「明明下好了却重下 6.7MB」，
 * 或「包已被系统清掉却还写着安装最新版」。六种形态逐格钉住。
 */
class AboutCapsuleStateTest {

    private fun newer(tag: String = "V1.1.18") =
        AboutUpdateStatus.Newer(tag = tag, version = "1.1.18", apkUrl = "https://example/a.apk")

    @Test
    fun `没有新版且不在失败态 —— 整块不显示`() {
        assertEquals(Hidden, resolveCapsuleState(AboutUpdateStatus.Idle, UpdateDownloadState.Idle, null))
        assertEquals(Hidden, resolveCapsuleState(AboutUpdateStatus.Latest, UpdateDownloadState.Idle, null))
        assertEquals(Hidden, resolveCapsuleState(AboutUpdateStatus.Failed, UpdateDownloadState.Idle, null))
    }

    @Test
    fun `有新版未下载 —— 显示有新版本`() {
        assertEquals(Newer, resolveCapsuleState(newer(), UpdateDownloadState.Idle, null))
    }

    @Test
    fun `下载中 —— 仍按有新版本展示，形态是 Downloading（点击取消）`() {
        assertEquals(
            Downloading,
            resolveCapsuleState(newer(), UpdateDownloadState.Downloading(received = 1024, total = 0), null),
        )
        // 有总大小时同样
        assertEquals(
            Downloading,
            resolveCapsuleState(newer(), UpdateDownloadState.Downloading(received = 50, total = 100), null),
        )
    }

    @Test
    fun `包已落地且 tag 一致 —— 安装最新版`() {
        assertEquals(Install, resolveCapsuleState(newer(), UpdateDownloadState.Idle, "V1.1.18"))
        assertEquals(Install, resolveCapsuleState(newer(), UpdateDownloadState.Ready("V1.1.18"), "V1.1.18"))
    }

    @Test
    fun `包被系统清掉 —— 退回有新版本`() {
        // readyTag 有值但文件没了：调用方传下来的 readyTag 是 null
        assertEquals(Newer, resolveCapsuleState(newer(), UpdateDownloadState.Idle, null))
    }

    @Test
    fun `又出了更新的版本 —— 旧包对不上新 tag，退回有新版本`() {
        assertEquals(Newer, resolveCapsuleState(newer("V1.1.19"), UpdateDownloadState.Ready("V1.1.18"), "V1.1.18"))
    }

    @Test
    fun `下载失败 —— 显示下载失败`() {
        assertEquals(
            Failed,
            resolveCapsuleState(
                newer(),
                UpdateDownloadState.Failed(UpdateFailure.DOWNLOAD, "timeout"),
                null,
            ),
        )
    }

    @Test
    fun `失败但包已落地 —— 显示安装最新版，不能报下载失败`() {
        // 下载完自动拉起安装器、用户没装就返回：state 停在失败态，但盘上的包是好的。
        // 报「下载失败」会让用户以为要重下 6.7MB，而点下去其实是直接进安装器。
        assertEquals(
            Install,
            resolveCapsuleState(
                newer(),
                UpdateDownloadState.Failed(UpdateFailure.LAUNCH, "installer"),
                "V1.1.18",
            ),
        )
    }

    @Test
    fun `重查仍拿不到直链 —— 版本确实存在但胶囊进失败态，不能被藏掉`() {
        assertEquals(Failed, resolveCapsuleState(newer(), UpdateDownloadState.Idle, null, extraFailed = true))
        // 极端场景：状态还没回来的那一帧也不能藏（否则按钮一隐一现）
        assertEquals(Failed, resolveCapsuleState(AboutUpdateStatus.Idle, UpdateDownloadState.Idle, null, true))
    }

    @Test
    fun `失败优先于下载中 —— 两次独立状态更新不靠互斥假设兜底`() {
        assertEquals(
            Failed,
            resolveCapsuleState(
                newer(),
                UpdateDownloadState.Downloading(received = 10, total = 100),
                null,
                extraFailed = true,
            ),
        )
    }

    @Test
    fun `旧包在盘上但正在下载新包 —— 仍显示下载中，不能提前写安装`() {
        assertEquals(
            Downloading,
            resolveCapsuleState(newer("V1.1.19"), UpdateDownloadState.Downloading(1, 100), "V1.1.18"),
        )
    }

    @Test
    fun `检测到新版本时丢旧包 —— 同 tag 不丢，异 tag 丢，没有包不用丢`() {
        assertFalse(shouldDiscardReady(null, "V1.1.18"))
        assertFalse(shouldDiscardReady("V1.1.18", "V1.1.18"))
        assertTrue(shouldDiscardReady("V1.1.17", "V1.1.18"))
    }

    @Test
    fun `同一份状态下 readyTag 决定 Newer 还是 Install —— 一次判断只差一个参数`() {
        // 这条替代了原先「同样的输入给同样的形态」那类恒真断言（f(x) == f(x) 永远不会红）。
        // 现在两个调用只差 readyTag，形态必须不同，改坏任一分支都会失败。
        val status = newer("V1.1.18")
        assertEquals(Newer, resolveCapsuleState(status, UpdateDownloadState.Idle, null))
        assertEquals(Install, resolveCapsuleState(status, UpdateDownloadState.Idle, "V1.1.18"))
    }
}
