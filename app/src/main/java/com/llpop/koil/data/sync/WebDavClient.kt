package com.llpop.koil.data.sync

import io.ktor.client.HttpClient
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

/**
 * WebDAV 传输客户端（坚果云 dav.jianguoyun.com 为标准 WebDAV；也可用于自建 Nextcloud 等兼容服务）。
 * - 认证：Basic Auth（用户名 + 应用密码，应用密码由用户在坚果云单独申请）
 * - 上传：MKCOL 建目录（已存在忽略 409）→ PUT 覆盖目标文件（幂等）
 * - 下载：GET；404 = 云端尚无备份（返回 null）
 * 超时 15s（油价格网络 8s，上传/下载大 JSON 放宽）。
 */
class WebDavClient {

    companion object {
        const val TIMEOUT_MS = 15000L
    }

    private val client: HttpClient by lazy {
        HttpClient {
            install(HttpTimeout) {
                requestTimeoutMillis = TIMEOUT_MS
                connectTimeoutMillis = TIMEOUT_MS
                socketTimeoutMillis = TIMEOUT_MS
            }
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

    /** 上传全量快照（幂等覆盖）。成功返回 true；认证/网络失败抛异常由调用方统一处理 */
    suspend fun upload(dirUrl: String, fileName: String, content: String, username: String, password: String) {
        val auth = authHeader(username, password)
        // 先确保目录存在（已存在时服务器返回 409 Conflict，忽略）
        runCatching {
            client.request(targetUrl(dirUrl, "").trimEnd('/')) {
                method = HttpMethod("MKCOL")
                header(HttpHeaders.Authorization, auth)
            }
        }
        val resp = client.put(targetUrl(dirUrl, fileName)) {
            header(HttpHeaders.Authorization, auth)
            contentType(ContentType.Application.Json)
            setBody(content)
        }
        // 2xx（200/201/204）视为成功；认证失败（401/403）抛明确异常
        if (resp.status.value !in 200..299) {
            error("WebDAV upload failed: HTTP ${resp.status.value}")
        }
    }

    /** 下载快照。云端无文件（404）返回 null；认证/网络失败抛异常 */
    suspend fun download(dirUrl: String, fileName: String, username: String, password: String): String? {
        val auth = authHeader(username, password)
        val resp = client.get(targetUrl(dirUrl, fileName)) {
            header(HttpHeaders.Authorization, auth)
        }
        return when {
            resp.status == HttpStatusCode.NotFound -> null
            resp.status.value in 200..299 -> resp.bodyAsText()
            else -> error("WebDAV download failed: HTTP ${resp.status.value}")
        }
    }

    /**
     * 连接测试（设置页「保存并测试连接」）：
     * 对目录根发 PROPFIND（Depth: 0），认证通过返回 207（或 404=目录尚不存在但认证 OK，备份时会自动建），
     * 401/403 = 账号或应用密码错误；其余异常抛给调用方。
     *
     * v1.6.2：判定收紧。原实现是「非 401/403 就算成功」，于是一个普通网页地址（405/500/502）
     * 也会显示「连接成功」，用户要到真正备份失败才发现配错了 URL。
     * 现在只认：2xx（含 207）、3xx（地址可达且未拒绝认证）、404/409（目录不存在/已存在）。
     */
    suspend fun checkConnection(dirUrl: String, username: String, password: String): Boolean {
        val auth = authHeader(username, password)
        val resp = client.request(targetUrl(dirUrl, "").trimEnd('/')) {
            method = HttpMethod("PROPFIND")
            header(HttpHeaders.Authorization, auth)
            header("Depth", "0")
        }
        val code = resp.status.value
        return code in 200..399 || code == 404 || code == 409
    }
}
