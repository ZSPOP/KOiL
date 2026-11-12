package com.llpop.koil.data.update

import android.util.Log
import com.llpop.koil.BuildConfig
import com.llpop.koil.data.settings.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * 应用更新检查。
 *
 * 冷启动 / 回前台静默检查（[checkIfDue]，45 分钟节流）与设置页手动「检查更新」（[refresh]）共用同一条通道，
 * 结果落到 DataStore：`updateLatestTag` 供 UI 判断是否挂红点、`updateLastCheckAt` 作为节流依据。
 *
 * 设计取舍：
 * - **不引第三方库**（无 Retrofit/OkHttp）：一次 GET + 两个正则足够，6 秒超时；
 * - **失败绝不写假数据**：请求失败时保留上一次的 tag，只让调用方知道"这次没查到"（返回 null）；
 * - **45min 节流写在启动侧**：冷启动 / 回前台每次都调，但真正发请求的只有超出窗口的那一次，频繁启停几乎零流量。
 */
class UpdateChecker(private val settings: AppSettings) {

    /** 最近一次查到的 Release tag（原始形式，如 "V1.0.0"；空串=从未查到过） */
    fun latestTag(): Flow<String> = settings.updateLatestTag

    /** 最近一次**成功**检查的时间戳（0=从未查过），设置页据此显示「上次检查」 */
    fun lastCheckAt(): Flow<Long> = settings.updateLastCheckAt

    /**
     * 用户已知晓的版本 tag（原始形式）。与 [latestTag] 配合判定红点：
     * 两者不等 = 有新版本且用户还没被告知过。写入时机见 AppSettings.updateSeenTag。
     */
    fun seenTag(): Flow<String> = settings.updateSeenTag

    /** 最新版 APK 直链（Gitee 资产 URL，空串=没查到）。下载器只认这一处，UI 不自己拼。 */
    fun latestApkUrl(): Flow<String> = settings.updateApkUrl

    /**
     * 应用级 scope：由 Application 在 onCreate 注入（与 CloudSyncManager 同一先例）。
     * [markSeen] 用它落库，不依赖页面生命周期。
     */
    private var appScope: CoroutineScope? = null
    fun init(scope: CoroutineScope) {
        appScope = scope
    }

    /**
     * 记录「用户已知晓此版本」。
     * 调用时机必须是用户主动表达知悉的动作：打开 Release 页、关闭更新横幅、成功拉起安装器。
     * **下载进行中、下载失败、用户取消都不要调用**——那会让红点与横幅同时消失，用户再也不会被提醒。
     *
     * 写入跑在应用级 scope：用户点开链接后立刻连续返回退出设置页时，页面 scope 会被取消，
     * `DataStore.edit` 有概率没落库——红点不灭，再点还是同一个症状。
     * ④ 的「成功拉起安装器后再标记」正好落在用户切走的时刻，风险更高。
     */
    fun markSeen(tag: String) {
        if (tag.isBlank()) return
        val scope = appScope
        if (scope == null) {
            // 未注入就静默丢弃会让红点永远不灭，这里明确告警（Application.onCreate 已注入）
            Log.w("KOiL", "markSeen before init, tag=$tag dropped")
            return
        }
        scope.launch {
            runCatching { settings.setUpdateSeenTag(tag) }
                .onFailure { Log.w("KOiL", "persist seen tag failed: ${it.message}") }
        }
    }

    /**
     * 冷启动 / 回前台静默检查：距上次**成功**检查不足 [INTERVAL_MS] 时直接返回 null（不发请求、不写库）。
     * 时间戳取自机的墙上时钟；用户改系统时间导致差值异常时按"该查了"处理（宁多查一次，不漏提示）。
     *
     * 检查间隔 45 分钟：窗口过长会让刚装完包时那次冷启动把当时的 release 记为最新，
     * 之后发新版也要等满窗口才再查，表现为『好像从不自动检查』。
     * 取 45 分钟（GitHub 匿名 API 限 60 次/小时，每分钟一次都够，不会触发限流），
     * 并让「回前台」也走同一条通道，日常开 App 基本不会错过新版本。
     */
    suspend fun checkIfDue(): String? {
        val last = settings.updateLastCheckAt.first()
        val elapsed = System.currentTimeMillis() - last
        if (last > 0 && elapsed in 0 until INTERVAL_MS) return null
        return refresh()
    }

    /**
     * 真实请求一次 GitHub Releases API。
     * @return 最新 tag（如 "V1.0.0"）；网络失败 / 非 2xx / 解析失败一律 null。
     */
    suspend fun refresh(): String? = withContext(Dispatchers.IO) {
        // APK 名带 versionCode（KOiL-V1.1.9-vc151-release.apk），App 无法预知，
        // 只能从 Release 的 assets 里取直链；tag 与链接必须来自同一次响应，
        // 否则会出现「红点说 1.1.9、下载链接却指向 1.1.8」。
        val release = runCatching { fetchLatestRelease() }
            .onFailure { Log.w("KOiL", "update check failed: ${it.message}") }
            .getOrNull()
            ?: return@withContext null
        val tag = release.first
        // 只认严格比本机更新的 Release：不更新的 tag 一律不落库，
        // 设置页因此不存在任何指向更低版本的提示/跳转入口
        val newer = compareVersionNames(normalizeVersion(tag), BuildConfig.VERSION_NAME) > 0
        // 先写 tag 再写时间戳：中途被杀也只会"多查一次"，不会出现"记了时间却没记 tag"的红点缺失
        runCatching {
            settings.setUpdateLatestTag(if (newer) tag else "")
            settings.setUpdateApkUrl(if (newer) release.second else "")
            settings.setUpdateLastCheckAt(System.currentTimeMillis())
        }.onFailure { Log.w("KOiL", "persist update tag failed: ${it.message}") }
        Log.i("KOiL", "update check ok: latest=$tag")
        tag
    }

    /**
     * 读 Gitee 的 latest release，返回 (tag, APK 直链)。
     *
     * 为什么换到 Gitee：① 国内直连比 GitHub 稳，更新检查是冷启动路径，超时就等于永不提示；
     * ② APK 只镜像在 Gitee（GitHub 那份是主源但国内下载慢），下载直链本来就该来自 Gitee。
     *
     * 不用列表接口取第一条：Gitee 的 /releases 返回顺序实测是旧→新（第一条是 V1.1.7），
     * 拿它当「最新」会把用户引导到旧版本。
     */
    private fun fetchLatestRelease(): Pair<String, String>? {
        var conn: HttpURLConnection? = null
        try {
            conn = (URL(RELEASE_API).openConnection() as HttpURLConnection).apply {
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                setRequestProperty("User-Agent", "KOiL-Android")
            }
            if (conn.responseCode !in 200..299) return null
            val json = conn.inputStream.bufferedReader().use { it.readText() }
            return parseGiteeLatestRelease(json)
        } finally {
            runCatching { conn?.disconnect() }
        }
    }

    companion object {
        /** 两次检查的最小间隔（45 分钟，见 [checkIfDue] 注释） */
        const val INTERVAL_MS = 45L * 60 * 1000
        const val RELEASE_API = "https://gitee.com/api/v5/repos/ZSPOP/KOiL/releases/latest"
        private const val TIMEOUT_MS = 6000
    }
}

/**
 * 从 Gitee `releases/latest` 的 JSON 里取 (tag, APK 直链)。
 * 抽成顶层纯函数是为了能单测：线上只跑"正常响应"一条路径，字段缺失/源码包混进来时才最容易出错。
 *
 * 不用 [org.json] 解析：这是单文件、结构固定的响应，两个正则足够；
 * 引第三方 JSON 只会让冷启动路径多一个可能抛异常的点。
 * assets 里除 APK 还有源码包（V1.x.zip / .tar.gz），必须按 `.apk` 过滤，否则会去下 zip。
 *
 * 找不到 APK 时返回空串而不是 null：镜像链可能只传上源码包（APK 上传失败），
 * 这时仍应提示"有新版本"（UI 回退浏览器），而不是让整个检查失败、用户永远看不到更新。
 */
internal fun parseGiteeLatestRelease(json: String): Pair<String, String>? {
    val tag = Regex("\"tag_name\"\\s*:\\s*\"([^\"]+)\"")
        .find(json)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() }
        ?: return null
    val apkUrl = Regex("\"browser_download_url\"\\s*:\\s*\"([^\"]+)\"")
        .findAll(json)
        .map { it.groupValues[1] }
        .firstOrNull { it.endsWith(".apk") }
        .orEmpty()
    return tag to apkUrl
}

/** Release 页面地址（形如 https://github.com/ZSPOP/KOiL/releases/tag/V1.0.0） */
fun releasePageUrl(tag: String): String = "https://github.com/ZSPOP/KOiL/releases/tag/$tag"

/** 去掉 V/v 前缀，得到可比较的版本号（"V1.0.0" → "1.0.0"） */
fun normalizeVersion(tag: String): String = tag.trim().trimStart('V', 'v')

/**
 * 语义版本比较：只比数字段，忽略 V/v 前缀与非数字后缀（如 -beta）。返回 >0 表示 a 更新。
 *
 * 两侧都在内部做 normalize，不依赖调用方先规范化：调用方常常只规范化了一侧
 * （isNewerRelease 传进来的 latestTag 已 normalize，currentVersion 是原始的 VERSION_NAME），
 * 若这里不再兜一次，"V1.1.3" 的首段 "V1" 会被 toIntOrNull 静默丢弃 → 得到 [1,3] 而不是 [1,1,3]，
 * 表现为「V1.1.4 不比 V1.1.3 新」：红点永不亮、refresh() 永不落库，且不报错不崩溃。
 */
fun compareVersionNames(a: String, b: String): Int {
    val pa = normalizeVersion(a).split(".", "-").mapNotNull { it.toIntOrNull() }
    val pb = normalizeVersion(b).split(".", "-").mapNotNull { it.toIntOrNull() }
    for (i in 0 until maxOf(pa.size, pb.size)) {
        val x = pa.getOrElse(i) { 0 }
        val y = pb.getOrElse(i) { 0 }
        if (x != y) return x.compareTo(y)
    }
    return 0
}

/**
 * 是否比本机版本严格更新（不看用户是否已读）。
 * 红点、首页横幅、设置页文字都必须走这一处判据——否则多处各算一遍，
 * 将来只改一处就会分叉成「横幅亮、红点不亮」这类最难排查的不一致。
 */
fun isNewerRelease(latestTag: String, currentVersion: String): Boolean =
    latestTag.isNotEmpty() &&
        compareVersionNames(normalizeVersion(latestTag), currentVersion) > 0

/**
 * 红点判定：存在严格更新的正式 release，且用户还没知晓过这个版本。
 *
 * 抽成纯函数而不是留在 Composable 里的表达式——这段逻辑决定「要不要提醒」，
 * 判错的后果是用户永远看不到新版本或每次都被重复提示，必须能单测（见 UpdateVersionTest）。
 *
 * @param latestTag DataStore 里最近一次查到的 tag（空串=从未查到）
 * @param seenTag 用户已知晓的 tag（空串=从未知晓）
 * @param currentVersion 本机 versionName，用于挡住「本地已是最新」的情况
 */
fun shouldBadgeUpdate(latestTag: String, seenTag: String, currentVersion: String): Boolean =
    isNewerRelease(latestTag, currentVersion) && latestTag != seenTag
