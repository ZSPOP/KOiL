package com.llpop.koil

import com.llpop.koil.data.update.fetchBothSources
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 双源并行请求的调度验证。
 *
 * 串行时两源都超时要 12 秒（各 6 秒），关于页进页自动查那段全是转圈 —— 这是复审提的 P3。
 * 这里用 CompletableDeferred 让两源互相等待：串行实现会卡在第一个源上超时，并行实现则
 * 两源同时到达、彼此放行。不依赖墙上时钟，共享 runner 的调度抖动也不会误报。
 * 顺带验证一个源失败不影响另一个。
 */
class UpdateParallelFetchTest {

    @Test
    fun `两源并行时彼此到达而非串行阻塞`() = runBlocking {
        val giteeArrived = CompletableDeferred<Unit>()
        val githubArrived = CompletableDeferred<Unit>()
        val result = withTimeout(1000) {
            fetchBothSources(
                gitee = { giteeArrived.complete(Unit); githubArrived.await(); "gitee" },
                github = { githubArrived.complete(Unit); giteeArrived.await(); "github" },
                pick = { g, h -> "$g+$h" },
            )
        }
        // 两源必须同时在运行才能互相放行：串行实现会卡在第一个 lambda 的 await 上超时。
        // 不依赖墙上时钟，2 核共享 runner 的调度抖动也不会误报。
        assertEquals("gitee+github", result)
    }

    @Test
    fun `一个源返回 null 时仍取另一个`() = runBlocking {
        val result = fetchBothSources(
            gitee = { null },
            github = { delay(20); "github-new" },
            pick = { g, h -> h ?: g },
        )
        assertEquals("github-new", result)
    }

    @Test
    fun `两源都失败时返回 null`() = runBlocking {
        val result = fetchBothSources<String>(
            gitee = { null },
            github = { null },
            pick = { g, h -> h ?: g },
        )
        assertEquals(null, result)
    }
}
