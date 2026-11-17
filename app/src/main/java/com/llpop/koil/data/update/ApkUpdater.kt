package com.llpop.koil.data.update

import android.app.Application
import com.llpop.koil.data.settings.AppSettings
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.net.toUri
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext
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
    private val settings: AppSettings,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow<UpdateDownloadState>(UpdateDownloadState.Idle)
    val state: StateFlow<UpdateDownloadState> = _state.asStateFlow()

    private var job: Job? = null

    /** 已下好的 tag（用于「已下好就再拉一次安装器」，避免重复下载几 MB）
     *  `@Volatile`：写盘恢复跑在 IO 上，下载判断在主线程读。 */
    @Volatile
    private var readyTag: String? = null

    /** 已落地包的 tag 快照。UI 不能只从 [state] 推断：失败态里包仍可能是完好的，
     *  而 state 是 Failed 时读不到 tag，会把「下好了没装成」误报成「下载失败」。 */
    val readyTagSnapshot: String?
        get() = readyTag

    /**
     * 进程重启后的恢复。
     *
     * `readyTag` 只有内存副本时，「下完没装、退出应用、再进来」会丢掉唯一凭据：
     * `updates/KOiL-update.apk` 明明还在，但 readyTag 是 null，
     * 点横幅 → `isReadyFor` 为 false → `fetch()` 先 `dest.delete()` 再从头下 6.7MB。
     * 文件名是常量（`KOiL-update.apk`），盘上没有任何信息能说明它对应哪个 tag，
     * 所以 tag 必须跟着落盘。
     */
    /** 恢复是否已完成。`download()` 必须等它，否则恢复窗口内 `readyTag` 还是 null，
     *  会把盘上已下好的包删掉重下（这正是 ① 要修的同一个后果，只是窗口短得多）。 */
    private val restored = kotlinx.coroutines.CompletableDeferred<Unit>()

    init {
        scope.launch {
            try {
                val saved = settings.updateReadyTag.first()
                if (saved.isNotBlank()) {
                    if (isReadyFor(saved, saved, apkFile())) {
                        readyTag = saved
                        _state.value = UpdateDownloadState.Ready(saved)
                    } else {
                        // 文件已被系统清掉（外部存储 evict / 清数据）→ 落盘 tag 一并失效
                        settings.setUpdateReadyTag(null)
                    }
                }
            } finally {
                // 无论成功失败都要放行 download()：卡住它等于「点了更新没反应」
                restored.complete(Unit)
            }
        }
    }

    /** readyTag 落盘：只在内存改完之后调用，保证本进程立刻生效 */
    private fun persistReadyTag(tag: String?) {
        scope.launch { settings.setUpdateReadyTag(tag) }
    }

    private fun isReady(tag: String): Boolean = isReadyFor(tag, readyTag, apkFile())

    /**
     * 开始下载。[url] 是 UpdateChecker 落库的 Gitee 资产直链。
     * 重复点击安全：同一轮下载中直接返回，不重复建连接。
     */
    fun download(tag: String, url: String) {
        if (tag.isBlank() || url.isBlank()) return
        if (job?.isActive == true) return

        job = scope.launch {
            // 先等落盘恢复：恢复窗口内 readyTag 尚为 null，直接判断会把好包删掉重下
            restored.await()
            // 已下好就**只**拉起安装器，绝不重新下载。
            // 缺授权时 install() 会 fail(NO_PERMISSION) 并返回 false，
            // 若这里继续往下走，就会为「没有安装权限」这件与包无关的事重下 6.7MB，
            // 而 fetch() 还会先 `dest.delete()` 把已下好的包删掉。
            if (isReady(tag)) {
                if (!installReady(tag) &&
                    (_state.value as? UpdateDownloadState.Failed)?.reason == UpdateFailure.LAUNCH
                ) {
                    // 只剩「文件在但拉不起安装器」：包不可用，删掉让下一次点击重下
                    apkFile()?.delete()
                    readyTag = null
                    persistReadyTag(null)
                }
                return@launch
            }
            // 不能用 runCatching 包 fetch：它会把 CancellationException 也吞成 null，
            // 于是「用户取消」被报成 DOWNLOAD 失败，横幅从「发现新版本」变成「下载失败」。
            val result = try {
                fetch(url)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("KOiL", "update download failed: ${e.message}")
                FetchResult.Bad(UpdateFailure.DOWNLOAD, e.message ?: "下载异常中断")
            }
            when (result) {
                is FetchResult.Bad -> {
                    Log.w("KOiL", "update download aborted: ${result.detail}")
                    fail(result.reason, result.detail)
                }
                is FetchResult.Ok -> {
                    readyTag = tag
                    persistReadyTag(tag)
                    _state.value = UpdateDownloadState.Ready(tag)
                    installReady(tag)
                }
            }
        }
    }

    /**
     * 取消正在进行的下载。
     *
     * 没有这个入口时，用户点错/后悔只能等几十秒或杀 App——`scope` 是应用级的，
     * `job` 只会自然结束。取消后必须让 state 离开 `Downloading`：否则横幅停在
     * 一个死掉的百分比上，看起来像「下载卡住了」，正是 ② 修掉的那个症状。
     *
     * `readyTag` 一并清空：半截包没通过校验，不能当作「已下好」。
     */
    fun cancel() {
        val running = job ?: return
        running.cancel()
        job = null
        // 只删**这次下载**写的临时文件。正式包不在这次下载的写入路径上（见 partFile 的说明），
        // 所以取消不该动它：用户可能刚下好一版还没装，取消另一次下载不该把那份也弄没。
        // readyTag 同理保留——它指向的是正式包，与这次被取消的下载无关。
        if (_state.value is UpdateDownloadState.Downloading) {
            partFile()?.delete()
        }
        _state.value = UpdateDownloadState.Idle
    }

    /**
     * 统一失败出口：只改状态，不发事件。
     *
     * 失败是「有更新但这次没装成」的持续状态，不是一次性通知：
     * - 用 Toast/SharedFlow 发送 → 首页与设置页同时组合时各收一次（双弹）；
     *   无人订阅时 `tryEmit` 直接丢弃 → 用户切走后失败完全无提示。
     * 展示交给 UI 就地读 state：横幅显示「· 失败」并把动作换成「重试」，设置页显示原因。
     *
     * NO_PERMISSION 不清 readyTag（包已下好，授权回来应直接安装）；其它失败清掉，下次从头下载。
     */
    private fun fail(reason: UpdateFailure, detail: String? = null) {
        readyTag = nextReadyTag(readyTag, reason)
        persistReadyTag(readyTag)
        _state.value = UpdateDownloadState.Failed(reason, detail)
    }

    /**
     * 检测到更新的版本时，把**旧版本**的包丢掉。
     *
     * 不丢的后果：盘上留着一个能通过签名校验的旧包，用户点「安装最新版」时
     * 若 tag 判断出现偏差就得装回旧版；而且 6.7MB 会一直压在更新目录里。
     * 只丢「与 [tag] 不同」的那一个：同 tag 的包正在装，不能删。
     */
    fun discardReadyExcept(tag: String) {
        if (!shouldDiscardReady(readyTag, tag)) return
        apkFile()?.delete()
        readyTag = null
        persistReadyTag(null)
        if (_state.value is UpdateDownloadState.Ready) {
            _state.value = UpdateDownloadState.Idle
        }
    }

    /**
     * 盘上正式包的字节数；没有包返回 null。
     * 只给 UI 显示体积用（冷启动恢复态没有「本次下载」的采样，体积只能从文件读）。
     */
    fun readyPackageBytes(): Long? =
        apkFile()?.takeIf { it.exists() }?.length()?.takeIf { it > 0 }

    /** 已下好则直接拉起安装器；返回 false 表示还没下好或没拉起来 */
    fun installReady(tag: String): Boolean {
        // tag 对不上就不能装：正式路径上躺着的可能是**上一个**版本的包（新的还没下好），
        // 拿它去装等于把用户装回旧版
        if (!isReady(tag)) return false
        val file = apkFile() ?: return false
        if (!file.exists()) return false
        return install(file)
    }

    private fun install(file: File): Boolean {
        // minSdk 33 远高于 O：canRequestPackageInstalls 一定存在，不再做版本判断
        if (!app.packageManager.canRequestPackageInstalls()) {
            // 没有「安装未知应用」授权时系统安装器直接拒，用户看不出原因。
            // 先跳到本应用的授权页，回来后再点一次；不做静默重试。
            // 保留 readyTag：文件已经下好了，用户授权回来再点就应直接进安装器
            fail(UpdateFailure.NO_PERMISSION)
            runCatching {
                app.startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        "package:${app.packageName}".toUri()
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
            // 这里**不写 seenTag**。startActivity 返回只代表安装器弹出来了，用户可能点取消；
            // 提前标记会让横幅与红点一起消失，他留在旧版且再也不会被提示。
            // 装成功后 isNewerRelease(latest, 当前版本) 自然为 false，横幅自己消失——
            // 不需要任何人提前写入来兜这个场景。
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

    /**
     * 下载中的临时文件。新包**先写这里**，校验（长度 / PK 魔数 / 签名）全过之后才原子替换正式包。
     *
     * 原来直接往正式路径写、开头先 `delete()`，等于「一开始下载新版就把上一个下好的包毁掉」：
     * 已有 V1.2.4 待装、下 V1.2.5 中途失败或取消，盘上只剩半截文件，两个都装不了。
     * 走临时文件后，正式包在整个下载过程中保持完好，替换那一瞬间才是「新包已落地」。
     */
    private fun partFile(): File? {
        val base = app.getExternalFilesDir(null) ?: app.cacheDir
        return File(base, "updates/$APK_PART_NAME")
    }

    private suspend fun fetch(url: String): FetchResult {
        val dest = partFile() ?: return FetchResult.Bad(UpdateFailure.DOWNLOAD, "无法创建缓存目录")
        dest.parentFile?.mkdirs()
        // 只清临时文件：这次下载不继承上一轮的半截字节。**正式包不动**，它可能正等着安装
        dest.delete()

        var current = url
        var hops = 0
        while (true) {
            // 取消是在阻塞读里生效的：每跳、每块都检查一次，否则要等 readTimeout(30s) 才停
            coroutineContext.ensureActive()
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
                // 6.6MB / 16KB ≈ 400 次读，逐块发 StateFlow 会推 400 次重组，
                // 而 UI 只用 percent 一个整数：按 1% 步进即可，首块必须发一次（percent 可能恒为 0）
                var lastPercent = Int.MIN_VALUE
                conn.inputStream.use { input ->
                    dest.outputStream().use { out ->
                        val buf = ByteArray(16 * 1024)
                        while (true) {
                            coroutineContext.ensureActive()
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            received += n
                            val percent = UpdateDownloadState.Downloading(received, total).percent
                            if (percent != lastPercent) {
                                lastPercent = percent
                                _state.value = UpdateDownloadState.Downloading(received, total)
                            }
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
                // 三项校验都过了才替换正式包：替换后 readyTag 才跟着换，
                // 之前「下好没装」的旧包一直是磁盘上唯一可安装的那份
                val final = apkFile() ?: return FetchResult.Bad(UpdateFailure.DOWNLOAD, "无法创建缓存目录")
                val moved = runCatching {
                    java.nio.file.Files.move(
                        dest.toPath(),
                        final.toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    )
                    true
                }.getOrElse { false }
                if (!moved) {
                    dest.delete()
                    return FetchResult.Bad(UpdateFailure.DOWNLOAD, "无法替换更新包")
                }
                return FetchResult.Ok(final)
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
        const val APK_PART_NAME = "KOiL-update.part"
        const val MAX_REDIRECT = 5
        const val CONNECT_TIMEOUT_MS = 10_000
        const val READ_TIMEOUT_MS = 30_000
    }
}

/**
 * 「这个 tag 的包是否已经下好」。
 * 判据是**文件在不在**，不是 state：首次安装必然经历
 * 「下载完 → 没授权 → 跳设置页 → 授予 → 回来再点」，那时 state 已被改成
 * Failed(NO_PERMISSION)，若拿 state 判断就会再下一遍 6.6MB。
 * 抽成顶层纯函数是为了能单测（这条逻辑没有别的地方守着）。
 */
internal fun isReadyFor(tag: String, readyTag: String?, file: File?): Boolean =
    readyTag == tag && file?.exists() == true

/**
 * 失败后 readyTag 的去留：只有「缺授权」保留（包已下好，授权回来应直接进安装器），
 * 其它失败一律清空，让下次点击从头下载。
 */
internal fun nextReadyTag(current: String?, reason: UpdateFailure): String? =
    if (reason == UpdateFailure.NO_PERMISSION) current else null
