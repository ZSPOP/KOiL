package com.llpop.koil

import com.llpop.koil.data.update.fetchBothSources
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.system.measureTimeMillis

/**
 * 双源并行请求的调度验证。
 *
 * 串行时两源都超时要 12 秒（各 6 秒），关于页进页自动查那段全是转圈 —— 这是复审提的 P3。
 * 这里用 delay 模拟两源各自的耗时，断言总耗时接近单源而非两倍；顺带验证一个源失败不影响另一个。
 */
class UpdateParallelFetchTest {

    @Test
    fun `两源并行时总耗时接近单源而非两倍`() = runBlocking {
        val elapsed = measureTimeMillis {
            fetchBothSources(
                gitee = { delay(300); "gitee" },
                github = { delay(300); "github" },
                pick = { g, h -> "$g+$h" },
            )
        }
        // 并行 ≈ 300ms，串行 ≈ 600ms。留足调度余量，但明显小于两倍耗时。
        assertTrue("并行耗时 $elapsed ms 应明显小于串行 600ms", elapsed < 500)
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
