package com.llpop.koil.data.sync

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.header
import io.ktor.client.request.put
import io.ktor.client.request.get
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.ContentType
import java.util.Base64

/** 云端快照在推送前被其它设备改写：调用方须重新拉取并合并后再试 */
class WebDavConflictException : IllegalStateException("WebDAV precondition failed (412): cloud snapshot changed")

/** 云端文件的内容与 ETag；content 为 null 表示云端尚无文件 */
data class CloudSnapshot(val content: String?, val etag: String?)

/**
 * WebDAV 传输客户端（坚果云 dav.jianguoyun.com 为标准 WebDAV；也可用于自建 Nextcloud 等兼容服务）。
 * - 认证：Basic Auth（用户名 + 应用密码，应用密码由用户在坚果云单独申请）
 * - 上传：MKCOL 建目录（已存在忽略 405；409=父目录缺失，逐级补建）→ 带前置条件的 PUT；有 ETag 时用 If-Match，
 *   云端尚无文件时用 If-None-Match: *，服务端返回 412 抛 [WebDavConflictException]
 * - 下载：GET，同时取回 ETag 供上传做前置条件；404 = 云端尚无备份
 * 超时 15s（油价格网络 8s，上传/下载大 JSON 放宽）。
 */
class WebDavClient(engine: HttpClientEngine? = null) {

    companion object {
        const val TIMEOUT_MS = 15000L
    }

    // engine 仅供单测注入 MockEngine；生产调用方一律用无参构造，行为不变
    private val client: HttpClient by lazy {
        val e = engine
        if (e == null) HttpClient(config()) else HttpClient(e, config())
    }

    private fun config(): HttpClientConfig<*>.() -> Unit = {
            install(HttpTimeout) {
                requestTimeoutMillis = TIMEOUT_MS
                connectTimeoutMillis = TIMEOUT_MS
                socketTimeoutMillis = TIMEOUT_MS
            }
        }

    /** 目录 URL（如 https://dav.jianguoyun.com/dav/KOiL/）与文件名拼接为完整目标 URL */
    private fun targetUrl(dirUrl: String, fileName: String): String {
        val base = dirUrl.trim().let {
            if (it.endsWith("/")) it else "$it/"
        }
        return base + fileName
    }

    private fun authHeader(username: String, password: String): String {
        val raw = "$username:$password".toByteArray(Charsets.UTF_8)
        val token = Base64.getEncoder().encodeToString(raw)
        return "Basic $token"
    }

    private suspend fun mkcol(url: String, auth: String): Int =
        client.request(url) {
            method = HttpMethod("MKCOL")
            header(HttpHeaders.Authorization, auth)
        }.status.value

    /** MKCOL 的「可接受」返回码：2xx=创建成功，405=已存在。409=父目录缺失，**不放行**，交给调用方逐级补建；3xx 不放行，应是错误地址或登录跳转 */
    private fun acceptableMkcol(code: Int): Boolean =
        code in 200..299 || code == 405

    /**
     * 确保目标目录存在。
     *
     * WebDAV 的 MKCOL 只创建**一层**目录，父目录缺失时返回 409。用户自填嵌套路径
     * （如 `.../dav/a/b/KOiL/`）时，只对最末级发一次 MKCOL 会失败，随后 PUT 404/409，
     * 用户只看到笼统的失败。这里在 409 时按层级逐级补齐祖先目录。
     */
    private suspend fun ensureCollection(dirUrl: String, auth: String) {
        val base = dirUrl.trim().let { if (it.endsWith("/")) it.dropLast(1) else it }
        val code = mkcol(base, auth)
        if (acceptableMkcol(code)) return
        if (code == 401 || code == 403) {
            throw IllegalStateException("WebDAV 目录创建被拒绝（HTTP $code）：认证失败或无写权限")
        }
        if (code != 409) throw IllegalStateException("WebDAV 目录创建失败（HTTP $code）：$base")

        // 409：父目录不存在。逐级 MKCOL（从根到目标），最后一层的状态码决定成败。
        val uri = runCatching { java.net.URI(base) }.getOrNull()
            ?: throw IllegalStateException("WebDAV 目录地址不合法：$base")
        val authority = uri.rawAuthority ?: throw IllegalStateException("WebDAV 目录地址缺少主机：$base")
        val segments = (uri.rawPath ?: "").split('/').filter { it.isNotBlank() }
        if (segments.isEmpty()) throw IllegalStateException("WebDAV 目录地址缺少路径：$base")
        var lastCode = code
        for (i in segments.indices) {
            val path = segments.take(i + 1).joinToString("/", prefix = "/")
            lastCode = mkcol("${uri.scheme}://$authority$path", auth)
            // 中间层认证/权限错误立即上报；最后一层由下面的统一判定处理
            if (i < segments.lastIndex && (lastCode == 401 || lastCode == 403)) {
                throw IllegalStateException("WebDAV 目录创建被拒绝（HTTP $lastCode）：$path")
            }
        }
        if (!acceptableMkcol(lastCode)) {
            throw IllegalStateException("WebDAV 目录创建失败（HTTP $lastCode）：$base")
        }
    }

    /**
     * 上传全量快照。
     * [cloudExists] / [expectedEtag] 描述本次推送前读到的云端状态：
     * - 云端尚无文件 → If-None-Match: *，防两台设备同时首传互相覆盖；
     * - 云端有文件且给了 ETag → If-Match，防两台设备先后推送时后写者整包覆盖；
     * - 云端有文件但服务端不给 ETag → 不带前置条件（部分 WebDAV 实现如此），
     *   此时由调用方的「推送前合并」保证不丢记录。
     * 服务端返回 412 抛 [WebDavConflictException]，调用方重新拉取合并后重试。
     */
    suspend fun upload(
        dirUrl: String,
        fileName: String,
        content: String,
        username: String,
        password: String,
        expectedEtag: String? = null,
        cloudExists: Boolean = true,
    ) {
        val auth = authHeader(username, password)
        // 先确保目录存在：MKCOL 只建单层，409 时逐级补建父目录（失败会把状态码透传到异常消息）
        ensureCollection(dirUrl, auth)
        val resp = client.put(targetUrl(dirUrl, fileName)) {
            header(HttpHeaders.Authorization, auth)
            contentType(ContentType.Application.Json)
            when {
                !cloudExists -> header(HttpHeaders.IfNoneMatch, "*")
                !expectedEtag.isNullOrBlank() -> header(HttpHeaders.IfMatch, expectedEtag)
            }
            setBody(content)
        }
        if (resp.status == HttpStatusCode.PreconditionFailed) throw WebDavConflictException()
        // 2xx（200/201/204）视为成功；认证失败（401/403）抛明确异常
        if (resp.status.value !in 200..299) {
            error("WebDAV upload failed: HTTP ${resp.status.value}")
        }
    }

    /** 下载快照。云端无文件（404）返回 null；认证/网络失败抛异常 */
    suspend fun download(dirUrl: String, fileName: String, username: String, password: String): String? =
        downloadWithMeta(dirUrl, fileName, username, password).content

    /** 下载快照并带回 ETag，供上传做 If-Match 前置条件 */
    suspend fun downloadWithMeta(
        dirUrl: String,
        fileName: String,
        username: String,
        password: String,
    ): CloudSnapshot {
        val auth = authHeader(username, password)
        val resp = client.get(targetUrl(dirUrl, fileName)) {
            header(HttpHeaders.Authorization, auth)
        }
        return when {
            resp.status == HttpStatusCode.NotFound -> CloudSnapshot(null, null)
            resp.status.value in 200..299 -> CloudSnapshot(
                resp.bodyAsText(),
                resp.headers[HttpHeaders.ETag]?.takeIf { it.isNotBlank() },
            )
            else -> error("WebDAV download failed: HTTP ${resp.status.value}")
        }
    }

    /**
     * 连接测试（设置页「保存并测试连接」）：
     * 对目录根发 PROPFIND（Depth: 0），认证通过返回 207（或 404=目录尚不存在但认证 OK，备份时会自动建），
     * 401/403 = 账号或应用密码错误；其余异常抛给调用方。
     *
     * 判定口径：只认 2xx（含 207）、404/409（目录不存在/已存在）。
     * 3xx 可能是跳登录页或普通网页，不能当连接成功；宽松判定（如「非 401/403 就算成功」）
     * 会让普通网页地址（405/500/502）也显示「连接成功」，用户要到真正备份失败才发现配错了 URL。
     */
    suspend fun checkConnection(dirUrl: String, username: String, password: String): Boolean {
        val auth = authHeader(username, password)
        val resp = client.request(targetUrl(dirUrl, "").trimEnd('/')) {
            method = HttpMethod("PROPFIND")
            header(HttpHeaders.Authorization, auth)
            header("Depth", "0")
        }
        val code = resp.status.value
        // 只认明确的成功/目录状态；3xx 可能是跳登录页或普通网页，不能当连接成功
        return code in 200..299 || code == 404 || code == 409
    }
}
