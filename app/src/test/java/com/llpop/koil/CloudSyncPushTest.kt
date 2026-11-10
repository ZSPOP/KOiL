package com.llpop.koil

import com.llpop.koil.data.sync.CloudSyncManager
import org.junit.Assert.assertEquals
import org.junit.Test

/** 云同步推送调度的纯逻辑回归（不依赖 Room/WebDAV）。 */
class CloudSyncPushTest {

    @Test
    fun `推送节流期内要等待而不是丢弃变更`() {
        // 上次推送后只过了 20s：还需等待 10s，不能直接 return 掉本次变更
        assertEquals(10_000L, CloudSyncManager.pushWaitMs(lastPushAt = 0L, now = 20_000L))
        // 窗口已过：立即允许推送
        assertEquals(0L, CloudSyncManager.pushWaitMs(lastPushAt = 0L, now = 40_000L))
        assertEquals(0L, CloudSyncManager.pushWaitMs(lastPushAt = 1_000L, now = 31_000L))
    }
}
