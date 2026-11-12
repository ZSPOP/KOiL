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
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL

/**
 * 一次检查的结果。tag 与直链**必须来自同一个快照**：
 * 分开两个变量传，就会出现「tag 是 V1.1.10、链接还是 V1.1.9」的错位下载，
 * 而且下的是个真 APK，三条校验全过，用户装完仍是旧版还把新版标成已读。
 */
data class UpdateTarget(val tag: String, val apkUrl: String)

@Serializable
private data class GiteeAsset(
    @SerialName("browser_download_url") val url: String = "",
    val name: String = "",
)

@Serializable
private data class GiteeRelease(
    @SerialName("tag_name") val tagName: String = "",
    val prerelease: Boolean = false,
    val assets: List<GiteeAsset> = emptyList(),
)

/**
 * 应用更新检查。
 *
 * 冷启动 / 回前台静默检查（[checkIfDue]，45 分钟节流）与设置页手动「检查更新」（[refresh]）共用同一条通道，
 * 结果落到 DataStore：`updateLatestTag` / `updateApkUrl` 供 UI 判断是否挂红点与发起下载、
 * `updateLastCheckAt` 作为节流依据。
 *
 * 设计取舍：
 * - **不引第三方 HTTP 库**：一次 GET 足够，6 秒超时；JSON 用工程已有的 kotlinx.serialization；
 * - **失败绝不写假数据**：请求失败时保留上一次的 tag + 直链，只让调用方知道"这次没查到"（返回 null）；
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

    /** 最新版 APK 直链（Gitee 资产 URL，空串=没拿到直链）。下载器只认这一处，UI 不自己拼。 */
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
     *
     * 口径由用户明确指定：**成功拉起系统安装器**即视为已知晓（不是"装完"）——
     * 用户明确拒绝过"红点直到装上才灭"（那会让取消安装的人被反复打扰）。
     * 因此这里写的是"已进入安装流程"，对应动作必须真的把安装器拉起来了；
     * 下载进行中、下载失败、用户取消下载都不写。
     *
     * 写入跑在应用级 scope：用户点开安装器后立刻切走，页面 scope 会被取消，
     * `DataStore.edit` 有概率没落库——红点不灭，再点还是同一个症状。
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
     * 取 45 分钟（Gitee 匿名读取的额度远高于这个频率，不会触发限流），
     * 并让「回前台」也走同一条通道，日常开 App 基本不会错过新版本。
     */
    suspend fun checkIfDue(): UpdateTarget? {
        val last = settings.updateLastCheckAt.first()
        val elapsed = System.currentTimeMillis() - last
        if (last > 0 && elapsed in 0 until INTERVAL_MS) return null
        return refresh()
    }

    /**
     * 真实请求一次 Gitee Releases。
     * @return 最新版本与它的 APK 直链；网络失败 / 非 2xx / 解析失败一律 null。
     */
    suspend fun refresh(): UpdateTarget? = withContext(Dispatchers.IO) {
        val target = runCatching { fetchLatestTarget() }
            .onFailure { Log.w("KOiL", "update check failed: ${it.message}") }
            .getOrNull()
            ?: return@withContext null

        // 只认严格比本机更新的 Release：不更新的 tag 一律不落库，
        // 设置页因此不存在任何指向更低版本的提示/跳转入口
        val newer = isNewerRelease(target.tag, BuildConfig.VERSION_NAME)
        // tag 与直链在**同一次 edit** 里写入：两次独立写入会留下「tag 已更新、链接还是旧版」的持久错位
        runCatching {
            settings.setUpdateTarget(
                tag = if (newer) target.tag else "",
                apkUrl = if (newer) target.apkUrl else "",
            )
            settings.setUpdateLastCheckAt(System.currentTimeMillis())
        }.onFailure { Log.w("KOiL", "persist update target failed: ${it.message}") }
        Log.i("KOiL", "update check ok: latest=${target.tag} apk=${target.apkUrl.ifEmpty { "-" }}")
        target
    }

    /**
     * 读 Gitee 的 release **列表**，自己挑版本号最大的那条。
     *
     * 为什么不用 `/releases/latest`：它的「最新」是「最新创建」，不是「版本号最大」。
     * 补传一个旧版本（本仓库真发生过：1.1.7 之后决定"不补传 1.1.5 了"，但补传随时可能发生）
     * 会拿到新 id + 新 created_at，于是 latest 返回 V1.1.5 →
     * 与本机比较后 not newer → 清空落库 → **红点静默熄灭**，用户再也收不到提示。
     * 列表接口一页 20 条（keep=5）足够覆盖，几 KB 的响应换掉一整类静默失败。
     */
    private fun fetchLatestTarget(): UpdateTarget? {
        var conn: HttpURLConnection? = null
        try {
            conn = (URL(RELEASE_LIST_API).openConnection() as HttpURLConnection).apply {
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                setRequestProperty("User-Agent", "KOiL-Android")
            }
            if (conn.responseCode !in 200..299) return null
            val json = conn.inputStream.bufferedReader().use { it.readText() }
            return selectLatestRelease(json)
        } finally {
            runCatching { conn?.disconnect() }
        }
    }

    companion object {
        /** 两次检查的最小间隔（45 分钟，见 [checkIfDue] 注释） */
        const val INTERVAL_MS = 45L * 60 * 1000

        /** 列表接口（不是 /latest，原因见 [fetchLatestTarget]） */
        const val RELEASE_LIST_API =
            "https://gitee.com/api/v5/repos/ZSPOP/KOiL/releases?per_page=20"
        private const val TIMEOUT_MS = 6000
    }
}

/**
 * 从 Gitee release **列表** JSON 里挑出版本号最大的一条，返回 tag 与 APK 直链。
 *
 * 抽成顶层纯函数是为了能单测：线上只跑"正常响应"一条路径，
 * 而真正会翻车的是补传旧版、混进 prerelease、assets 里只有源码包这几种。
 *
 * 用 kotlinx.serialization 而不是正则扫全文：release 的 `body` 里若贴了下载链接，
 * 正则会把正文里的 URL 当成 asset；按结构解析则只认 assets 数组，不受正文影响。
 */
internal fun selectLatestRelease(json: String): UpdateTarget? {
    val releases = runCatching {
        Json { ignoreUnknownKeys = true }.decodeFromString<List<GiteeRelease>>(json)
    }.getOrNull() ?: return null

    // 跳过 prerelease 与无 tag 的脏数据：预发布不该覆盖正式版的提示
    val best = releases
        .filter { !it.prerelease && it.tagName.isNotBlank() }
        .maxWithOrNull { a, b -> compareVersionNames(a.tagName, b.tagName) }
        ?: return null

    // assets 里除 APK 还有源码包（V1.x.zip / .tar.gz），只认 .apk；找不到直链时留空，
    // 让 UI 回退浏览器，而不是让整次检查失败（否则用户永远看不到"有新版本"）
    val apkUrl = best.assets.firstOrNull {
        it.name.endsWith(".apk") || it.url.endsWith(".apk")
    }?.url.orEmpty()
    return UpdateTarget(best.tagName, apkUrl)
}

/** 版本页地址。用 Gitee 而不是 GitHub：GitHub 在国内经常打不开，而这条只在「Gitee 没镜像好」时才走 */
fun releasePageUrl(tag: String): String = "https://gitee.com/ZSPOP/KOiL/releases/tag/$tag"

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
