package com.llpop.koil.data.sync

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * `ensureCollection` 的目录创建行为（M-03）：
 * - 409 = 父目录缺失，必须逐级补建祖先目录后再上传；
 * - 405 = 已存在，直接上传；
 * - 认证失败（401）立即抛明确异常，不进入补建循环；
 * - 补建后最终仍失败（409）抛出带状态码的异常，不执行 PUT。
 */
class WebDavClientTest {

    private class Recorder {
        val methods = mutableListOf<String>()
        val paths = mutableListOf<String>()
        val counts = mutableMapOf<String, Int>()

        fun mkcolCount(): List<String> =
            paths.filterIndexed { i, _ -> methods[i] == "MKCOL" }
    }

    /** 构造注入 MockEngine 的客户端；mkcolHandler 按 (路径, 该路径第几次 MKCOL) 返回状态码 */
    private fun makeClient(
        mkcolHandler: (path: String, nth: Int) -> HttpStatusCode,
        putStatus: HttpStatusCode = HttpStatusCode.Created,
        onPut: (headers: Map<String, String>) -> Unit = {},
    ): Pair<WebDavClient, Recorder> {
        val rec = Recorder()
        val engine = MockEngine { request ->
            rec.methods.add(request.method.value)
            rec.paths.add(request.url.encodedPath)
            when {
                request.method.value == "MKCOL" -> {
                    val nth = rec.counts.merge(request.url.encodedPath, 1, Int::plus)!!
                    respond("", mkcolHandler(request.url.encodedPath, nth))
                }
                request.method == HttpMethod.Put -> {
                    onPut(request.headers.entries().associate { it.key to (it.value.firstOrNull() ?: "") })
                    respond("", putStatus)
                }
                else -> respond("", HttpStatusCode.NotFound)
            }
        }
        return WebDavClient(engine) to rec
    }

    private val dirUrl = "https://dav.example.com/dav/a/b/"
    private val fileName = "backup.json"

    /** 嵌套路径：首层 MKCOL 409 → 必须对 /dav、/dav/a、/dav/a/b 各发一次补建 MKCOL，随后 PUT 成功 */
    @Test
    fun `嵌套路径 409 时逐级补建父目录后上传`() = runBlocking {
        val (client, rec) = makeClient(mkcolHandler = { path, nth ->
            when {
                // 首次对全路径 MKCOL：父目录缺失返回 409
                path == "/dav/a/b" && nth == 1 -> HttpStatusCode.Conflict
                else -> HttpStatusCode.Created
            }
        })
        client.upload(dirUrl, fileName, "{}", "u", "p", cloudExists = false)

        // 首次全路径 + 逐级补建（/dav、/dav/a、/dav/a/b）
        assertEquals(listOf("/dav/a/b", "/dav", "/dav/a", "/dav/a/b"), rec.mkcolCount())
        assertEquals(1, rec.methods.count { it == "PUT" })
    }

    /** 目录已存在（405）→ 只发一次 MKCOL，直接上传，不发补建请求 */
    @Test
    fun `目录已存在 405 时直接上传`() = runBlocking {
        val (client, rec) = makeClient(mkcolHandler = { _, _ -> HttpStatusCode.MethodNotAllowed })
        client.upload(dirUrl, fileName, "{}", "u", "p", cloudExists = false)

        assertEquals(listOf("/dav/a/b"), rec.mkcolCount())
        assertEquals(1, rec.methods.count { it == "PUT" })
    }

    /** 补建后最终层仍 409 → 抛出带状态码的异常，不执行 PUT */
    @Test
    fun `补建后最终仍 409 时抛异常`() = runBlocking {
        val (client, rec) = makeClient(mkcolHandler = { _, _ -> HttpStatusCode.Conflict })
        try {
            client.upload(dirUrl, fileName, "{}", "u", "p", cloudExists = false)
            fail("应抛出 IllegalStateException")
        } catch (e: IllegalStateException) {
            assertTrue("异常应包含状态码: ${e.message}", e.message!!.contains("409"))
        }
        assertEquals(0, rec.methods.count { it == "PUT" })
    }

    /** 认证失败（401）→ 立即抛明确异常，不进入补建循环 */
    @Test
    fun `认证失败 401 时立即抛异常`() = runBlocking {
        val (client, rec) = makeClient(mkcolHandler = { _, _ -> HttpStatusCode.Unauthorized })
        try {
            client.upload(dirUrl, fileName, "{}", "u", "p", cloudExists = false)
            fail("应抛出 IllegalStateException")
        } catch (e: IllegalStateException) {
            assertTrue("异常应包含认证提示: ${e.message}", e.message!!.contains("认证"))
        }
        // 只有首次 MKCOL，没有逐级补建请求
        assertEquals(1, rec.methods.count { it == "MKCOL" })
        assertEquals(0, rec.methods.count { it == "PUT" })
    }

    /** 上传成功时带 Basic 认证头与 If-None-Match（云端无文件场景） */
    @Test
    fun `上传携带认证与 If-None-Match 头`() = runBlocking {
        var sawAuth = false
        var sawIfNoneMatch = false
        val (client, _) = makeClient(
            mkcolHandler = { _, _ -> HttpStatusCode.MethodNotAllowed },
            onPut = { headers ->
                sawAuth = headers["Authorization"]?.startsWith("Basic ") == true
                sawIfNoneMatch = headers["If-None-Match"] == "*"
            },
        )
        client.upload(dirUrl, fileName, "{}", "u", "p", cloudExists = false)
        assertTrue("PUT 应带 Basic 认证头", sawAuth)
        assertTrue("首传应带 If-None-Match: *", sawIfNoneMatch)
    }
}
