package com.llpop.koil.data.update

import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** 失败原因按类型给出，文案由 UI 用 string 资源渲染：这里不能写死中文，工程有四套语言。 */
enum class UpdateFailure { DOWNLOAD, SIGNATURE, NO_PERMISSION, LAUNCH }

/** 更新包下载 / 安装的状态机。UI 只读这一个 Flow，不自己判断「能不能装」。 */
sealed interface UpdateDownloadState {
    data object Idle : UpdateDownloadState

    data class Downloading(val received: Long, val total: Long) : UpdateDownloadState {
        /** total 未知（没有 Content-Length）时返回 -1，UI 用不确定进度 */
        val percent: Int
            get() = if (total > 0) ((received * 100) / total).toInt().coerceIn(0, 100) else -1
    }

    data class Ready(val tag: String) : UpdateDownloadState

    /** [detail] 只进日志/排查，展示给用户的一律是 [reason] 对应的本地化文案 */
    data class Failed(val reason: UpdateFailure, val detail: String? = null) : UpdateDownloadState
}

private sealed interface FetchResult {
    class Ok(val file: File) : FetchResult
    class Bad(val reason: UpdateFailure, val detail: String) : FetchResult
}

/**
 * 应用内下载 APK 并拉起系统安装器（替代「跳浏览器」）。
 *
 * 为什么不把 URL 直接丢给浏览器：
 * - 浏览器下载落到公共目录，系统安装器拿不到我们私有目录的读权限，还得再走一次「选择文件」；
 * - 无法在安装前做签名校验 —— 签名不一致时系统只甩一句「应用未安装」，用户以为包坏了。
 *
 * 三条校验都要过，缺一条就不拉起安装器（宁可让用户重下）：
 * 1. 字节数：有 Content-Length 时必须与实际写盘字节一致（截断的 APK 装上会 INVALID_APK）；
 * 2. PK 魔数：前两字节必须是 0x50 0x4B —— 被代理/运营商插了错误页时通常仍返回 200；
 * 3. 签名：与本机同签名才装。GitHub 与 Gitee 发的是同一个 keystore 签的包，
 *    不一致只可能是包被替换，或本机装的是另一个签名的旧包。
 *
 * 下载跑在应用级 scope：用户点了横幅又切走时页面 scope 会被取消，几十秒的下载会静默死掉。
 */
class ApkUpdater(
    private val app: Application,
    private val checker: UpdateChecker,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow<UpdateDownloadState>(UpdateDownloadState.Idle)
    val state: StateFlow<UpdateDownloadState> = _state.asStateFlow()

    /**
     * 失败是**一次性事件**，不是状态：用户从首页切到设置页时，两个页面各自的
     * `LaunchedEffect` 首次组合都会读到同一个状态 → 明明这次什么都没做，却弹出上次的失败。
     * 状态留给需要展示当前进度的地方，提示走这条流。
     */
    private val _failures = MutableSharedFlow<UpdateFailure>(extraBufferCapacity = 1)
    val failures: SharedFlow<UpdateFailure> = _failures.asSharedFlow()

    private var job: Job? = null

    /** 已下好的 tag（用于「已下好就再拉一次安装器」，避免重复下载几 MB） */
    private var readyTag: String? = null

    /**
     * 判据看**文件**，不看 state：首次安装的用户必然经历
     * 「下载完 → 没授权 → 跳设置页 → 授予 → 回来再点」，
     * 此时 state 已被改成 Failed(NO_PERMISSION)，若拿 state 判断就会再下一遍 6.6MB。
     */
    private fun isReady(tag: String): Boolean =
        readyTag == tag && apkFile()?.exists() == true

    /**
     * 开始下载。[url] 是 UpdateChecker 落库的 Gitee 资产直链。
     * 重复点击安全：同一轮下载中直接返回，不重复建连接。
     */
    fun download(tag: String, url: String) {
        if (tag.isBlank() || url.isBlank()) return
        // 已下好则直接拉起安装器；文件若被系统清理掉了（外部存储 evict），
        // installReady 返回 false 时必须继续走下载，否则用户点第二次没反应。
        if (isReady(tag) && installReady(tag)) return
        if (job?.isActive == true) return

        job = scope.launch {
            val result = runCatching { fetch(url) }
                .onFailure { Log.w("KOiL", "update download failed: ${it.message}") }
                .getOrNull() ?: FetchResult.Bad(UpdateFailure.DOWNLOAD, "下载异常中断")
            when (result) {
                is FetchResult.Bad -> {
                    Log.w("KOiL", "update download aborted: ${result.detail}")
                    fail(result.reason, result.detail)
                }
                is FetchResult.Ok -> {
                    readyTag = tag
                    _state.value = UpdateDownloadState.Ready(tag)
                    installReady(tag)
                }
            }
        }
    }

    /**
     * 统一失败出口：改状态 + 发一次性事件。
     * NO_PERMISSION 不清 readyTag（包已下好，授权回来应直接安装）；
     * 其它失败清掉，下次点击从头下载。
     */
    private fun fail(reason: UpdateFailure, detail: String? = null) {
        if (reason != UpdateFailure.NO_PERMISSION) readyTag = null
        _state.value = UpdateDownloadState.Failed(reason, detail)
        _failures.tryEmit(reason)
    }

    /** 已下好则直接拉起安装器；返回 false 表示还没下好或没拉起来 */
    fun installReady(tag: String): Boolean {
        val file = apkFile() ?: return false
        if (!file.exists()) return false
        return install(file, tag)
    }

    private fun install(file: File, tag: String): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !app.packageManager.canRequestPackageInstalls()
        ) {
            // 没有「安装未知应用」授权时系统安装器直接拒，用户看不出原因。
            // 先跳到本应用的授权页，回来后再点一次；不做静默重试。
            // 保留 readyTag：文件已经下好了，用户授权回来再点就应直接进安装器
            fail(UpdateFailure.NO_PERMISSION)
            runCatching {
                app.startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:${app.packageName}")
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }.onFailure { Log.w("KOiL", "open unknown-source settings failed: ${it.message}") }
            return false
        }

        val uri = FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, APK_MIME)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching {
            app.startActivity(intent)
            // seenTag 的写入时机是「成功拉起安装器」，不是「下载完成」：
            // 卡在系统确认页、用户取消安装后，提醒仍应存在。
            checker.markSeen(tag)
            true
        }.onFailure {
            Log.w("KOiL", "launch installer failed: ${it.message}")
            fail(UpdateFailure.LAUNCH, it.message)
        }.getOrDefault(false)
    }

    private fun apkFile(): File? {
        val base = app.getExternalFilesDir(null) ?: app.cacheDir
        return File(base, "updates/$APK_NAME")
    }

    private fun fetch(url: String): FetchResult {
        val dest = apkFile() ?: return FetchResult.Bad(UpdateFailure.DOWNLOAD, "无法创建缓存目录")
        dest.parentFile?.mkdirs()

        var current = url
        var hops = 0
        while (true) {
            val conn = (URL(current).openConnection() as HttpURLConnection).apply {
                // Gitee 的下载链接实测 302 → 302 → CDN，必须手动跟：
                // 自动跟随时拿不到中间跳的 Location，出问题无法定位是哪一跳断的。
                instanceFollowRedirects = false
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                setRequestProperty("User-Agent", "KOiL-Android")
            }
            try {
                val code = conn.responseCode
                if (code in 300..399) {
                    val location = conn.getHeaderField("Location")
                        ?: return FetchResult.Bad(UpdateFailure.DOWNLOAD, "跳转缺少 Location（HTTP $code）")
                    if (++hops > MAX_REDIRECT) {
                        return FetchResult.Bad(UpdateFailure.DOWNLOAD, "跳转超过 $MAX_REDIRECT 次")
                    }
                    current = URL(URL(current), location).toString()
                    continue
                }
                if (code !in 200..299) {
                    return FetchResult.Bad(UpdateFailure.DOWNLOAD, "HTTP $code")
                }

                val total = conn.contentLengthLong
                var received = 0L
                conn.inputStream.use { input ->
                    dest.outputStream().use { out ->
                        val buf = ByteArray(16 * 1024)
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            received += n
                            _state.value = UpdateDownloadState.Downloading(received, total)
                        }
                    }
                }

                // ① 长度
                if (total > 0 && received != total) {
                    dest.delete()
                    return FetchResult.Bad(UpdateFailure.DOWNLOAD, "不完整：$received/$total 字节")
                }
                if (received <= 0) {
                    dest.delete()
                    return FetchResult.Bad(UpdateFailure.DOWNLOAD, "下载文件为空")
                }
                // ② PK 魔数
                if (!hasZipMagic(dest)) {
                    dest.delete()
                    return FetchResult.Bad(UpdateFailure.DOWNLOAD, "内容不是 APK（可能被代理插入错误页）")
                }
                // ③ 签名
                signatureProblem(dest)?.let {
                    dest.delete()
                    return it
                }
                return FetchResult.Ok(dest)
            } finally {
                runCatching { conn.disconnect() }
            }
        }
    }

    private fun hasZipMagic(file: File): Boolean = runCatching {
        file.inputStream().use { input ->
            val b = ByteArray(2)
            input.read(b) == 2 && b[0] == 0x50.toByte() && b[1] == 0x4B.toByte()
        }
    }.getOrDefault(false)

    /** 返回 null 表示签名一致；否则给出失败原因与排查细节 */
    private fun signatureProblem(file: File): FetchResult.Bad? = runCatching {
        val pm = app.packageManager
        @Suppress("DEPRECATION")
        val archive = pm.getPackageArchiveInfo(file.absolutePath, PackageManager.GET_SIGNING_CERTIFICATES)
            ?: return FetchResult.Bad(UpdateFailure.SIGNATURE, "无法解析 APK（可能已损坏）")

        // 两边都取 apkContentsSigners，不用 signingCertificateHistory：
        // 后者含「历史上用过的证书」，新包用曾经那套旧签名也会被判为一致，
        // 但系统安装时装的是新证书 —— 提前校验就失去意义。
        val archiveSigners = archive.signingInfo?.apkContentsSigners
            ?.takeIf { it.isNotEmpty() }
            ?: return FetchResult.Bad(UpdateFailure.SIGNATURE, "APK 没有签名信息")

        @Suppress("DEPRECATION")
        val selfInfo = pm.getPackageInfo(app.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        val selfSigners = selfInfo.signingInfo?.apkContentsSigners
            ?.takeIf { it.isNotEmpty() }
            ?: return FetchResult.Bad(UpdateFailure.SIGNATURE, "读取本机签名失败")

        val same = archiveSigners.any { a -> selfSigners.any { b -> a == b } }
        if (same) null else FetchResult.Bad(UpdateFailure.SIGNATURE, "签名与本机不一致")
    }.getOrElse { FetchResult.Bad(UpdateFailure.SIGNATURE, "签名校验异常：${it.message}") }

    private companion object {
        const val APK_MIME = "application/vnd.android.package-archive"
        const val APK_NAME = "KOiL-update.apk"
        const val MAX_REDIRECT = 5
        const val CONNECT_TIMEOUT_MS = 10_000
        const val READ_TIMEOUT_MS = 30_000
    }
}
