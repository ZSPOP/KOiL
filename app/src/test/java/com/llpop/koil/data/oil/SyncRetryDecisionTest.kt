package com.llpop.koil.data.oil

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Worker 失败后的重试判定。
 *
 * `runAttemptCount` 是本次任务的第几次尝试（0 起）：达到 [MAX_SYNC_RETRY] 就不再 retry。
 * 判错的两个方向都是真故障：太早放弃 → 一次网络抖动就等 8 小时；
 * 永不放弃 → 无网时反复唤醒耗电（指数退避也压不住长时间失败）。
 */
class SyncRetryDecisionTest {

    @Test
    fun `前三次失败继续重试`() {
        assertTrue(shouldRetrySync(0))
        assertTrue(shouldRetrySync(1))
        assertTrue(shouldRetrySync(2))
    }

    @Test
    fun `达到上限后收尾等下个周期`() {
        assertFalse(shouldRetrySync(MAX_SYNC_RETRY))
        assertFalse(shouldRetrySync(MAX_SYNC_RETRY + 1))
        assertFalse(shouldRetrySync(99))
    }

    @Test
    fun `上限是三次`() {
        // 这个数字写进 KDoc 与 PR 描述，改动必须是有意的
        assertTrue(MAX_SYNC_RETRY == 3)
    }
}
