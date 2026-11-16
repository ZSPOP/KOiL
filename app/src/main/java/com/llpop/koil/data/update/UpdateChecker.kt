package com.llpop.koil.data.update

import android.util.Log
import com.llpop.koil.BuildConfig
import com.llpop.koil.data.settings.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
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

/**
 * 一次检查结果对已落库状态的处置（[UpdateChecker.refresh] 的唯一口径）。
 *
 * 三态而不是「有/无 target」两态：两态会把「确认没有新版」和「新版直链还没就绪」压成同一个 null，
 * 调用方只能二选一——要么照清空处理，把用户手里可用的直链抹掉；
 * 要么照写入处理，把 (tag, "") 存进库，于是 1.1.15 的实测故障（显示可更新、怎么点都跳浏览器、
 * 45 分钟节流内不重查）有了复现路径。
 */
sealed interface UpdatePersistAction {
    /** 确认更新且直链可用：写入 (tag, apkUrl)。 */
    data class Write(val target: UpdateTarget) : UpdatePersistAction

    /** 确认不比本机新：清空旧值，库里不留任何指向更低版本的入口。 */
    data object Clear : UpdatePersistAction

    /** 确认更新但直链未就绪（Gitee「Release 已建、apk 附件还在传」的镜像窗口）：不写也不清。 */
    data object Keep : UpdatePersistAction
}

/**
 * 判定一次检查该落到哪个处置。
 *
 * 抽成纯函数是为了单测：它决定上面那种卡死会不会复现，不该只靠实机复现验证。
 */
fun resolvePersistAction(tag: String, apkUrl: String, newer: Boolean): UpdatePersistAction = when {
    !newer -> UpdatePersistAction.Clear
    apkUrl.isBlank() -> UpdatePersistAction.Keep
    else -> UpdatePersistAction.Write(UpdateTarget(tag, apkUrl))
}

/**
 * 这次处置是否算「一次成功的检查」。
 *
 * Keep 不算：空直链若也推进节流时间戳，下次冷启动本该重查补直链，却被 45 分钟窗口直接跳过。
 */
val UpdatePersistAction.advancesLastCheckAt: Boolean
    get() = this !is UpdatePersistAction.Keep

/**
 * 一次检查的三态结果。
 * 不能只用「有/无 target」表达：那样「确认没新版」和「网络失败」会被压成同一个 null，
 * 设置页就分不出该显示「已是最新」还是「检查失败」。
 */
sealed interface RefreshOutcome {
    data class Newer(val target: UpdateTarget) : RefreshOutcome
    data object Latest : RefreshOutcome
    data object Failed : RefreshOutcome
}

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
 * 结果落到 DataStore：`updateTargetPair` 供 UI 判断是否挂红点与发起下载、
 * `updateLastCheckAt` 作为节流依据。
 *
 * 设计取舍：
 * - **不引第三方 HTTP 库**：一次 GET 足够，6 秒超时；JSON 用工程已有的 kotlinx.serialization；
 * - **失败绝不写假数据**：请求失败时保留上一次的 tag + 直链，只让调用方知道"这次没查到"（返回 null）；
 * - **45min 节流写在启动侧**：冷启动 / 回前台每次都调，但真正发请求的只有超出窗口的那一次，频繁启停几乎零流量。
 */
class UpdateChecker(private val settings: AppSettings) {

    /**
     * 用户已知晓的版本 tag，**只在本次运行内有效**（冷启动清零，不落库）。
     * 产品口径：横幅的 × 只收起本次运行，冷启动后新版本横幅必须重新出现 ——
     * 所以这个值不能再写 DataStore，否则「× 掉之后永远不再提醒」。
     */
    private val sessionSeenTag = MutableStateFlow("")

    /** 最近一次**成功**检查的时间戳（0=从未查过）。 */
    fun lastCheckAt(): Flow<Long> = settings.updateLastCheckAt

    /**
     * 最近一次**失败**检查的时间戳（0=当前没有未恢复的失败）。
     * 后台静默检查失败时用户什么都看不到（不弹、不亮红点），Gitee 长期不可用就会表现为
     * 「永远没有新版本」。设置页用这个值提示一句，成功一次即清零。
     */
    fun lastFailedAt(): Flow<Long> = settings.updateLastFailedAt

    /**
     * 用户已知晓的版本 tag（原始形式）。与 [updateTarget] 配合判定红点：
     * 两者不等 = 有新版本且用户还没被告知过。写入时机见 [markSeen]。
     */
    fun seenTag(): Flow<String> = sessionSeenTag

    /**
     * tag 与直链的**单一快照**。UI 一律读这个，不分别订阅 latestTag / latestApkUrl：
     * 两条 collect 是两帧，中间可能取到「新 tag + 旧 url」→ 下上一版的包、校验全过、
     * 装完仍是旧版，还把新版标成已读。DataStore 一次 map 出来，就不存在这个窗口。
     */
    fun updateTarget(): Flow<UpdateTarget?> =
        settings.updateTargetPair.map { (tag, url) ->
            if (tag.isBlank()) null else UpdateTarget(tag, url)
        }

    /**
     * 记录「用户已知晓此版本」，**只影响本次运行**（冷启动清零）。
     * 调用时机必须是用户主动表达知悉的动作：**关闭首页更新横幅**（唯一调用点）。
     * **下载进行中、下载失败、拉起安装器但未装成都不要调用**
     * ——那会让红点与横幅同时消失，用户再也不会被提醒。
     * （装成功后 `isNewerRelease(latest, 当前版本)` 自然为 false，横幅自己就消失了，
     * 不需要任何人提前写 seenTag 去兜。）
     */
    fun markSeen(tag: String) {
        if (tag.isBlank()) return
        sessionSeenTag.value = tag
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
        return (refresh() as? RefreshOutcome.Newer)?.target
    }

    /**
     * 真实请求一次 Gitee Releases。
     *
     * 返回值已经把「是否比本机新」算完了，UI 直接按三态分支，不再自己 `compareVersionNames` 第二遍
     * （[isNewerRelease] 的文档要求判据只此一处，两处各算一遍迟早分叉）。
     */
    suspend fun refresh(): RefreshOutcome = withContext(Dispatchers.IO) {
        val target = runCatching { fetchLatestTarget() }
            .onFailure { Log.w("KOiL", "update check failed: ${it.message}") }
            .getOrNull()
            ?: run {
                // 失败时留时间戳：后台检查全程静默，不留记录就等于「什么都没发生」
                runCatching { settings.setUpdateLastFailedAt(System.currentTimeMillis()) }
                return@withContext RefreshOutcome.Failed
            }
        // 请求成功即说明源可用，把失败标记清掉。
        // 放在这里而不是 persist 之后：即使这一轮是 Keep（直链没就绪），源本身也是通的。
        runCatching { settings.setUpdateLastFailedAt(0L) }

        // 只认严格比本机更新的 Release：不更新的 tag 一律不落库，
        // 设置页因此不存在任何指向更低版本的提示/跳转入口
        val newer = isNewerRelease(target.tag, BuildConfig.VERSION_NAME)
        // 落库口径见 resolvePersistAction：直链没就绪时这轮既不写也不清（Keep）
        val action = resolvePersistAction(target.tag, target.apkUrl, newer)
        // tag 与直链在**同一次 edit** 里写入：两次独立写入会留下「tag 已更新、链接还是旧版」的持久错位
        runCatching {
            when (action) {
                is UpdatePersistAction.Write ->
                    settings.setUpdateTarget(tag = action.target.tag, apkUrl = action.target.apkUrl)

                UpdatePersistAction.Clear ->
                    settings.setUpdateTarget(tag = "", apkUrl = "")

                // 查到新版但直链未就绪：上一次可用的直链继续留在库里，
                // 横幅与红点不会因为这一轮撞窗口而消失
                UpdatePersistAction.Keep -> Unit
            }
            // 只有 Write / Clear 才算一次成功检查并推进节流时间戳；Keep 不推进
            if (action.advancesLastCheckAt) settings.setUpdateLastCheckAt(System.currentTimeMillis())
        }.onFailure { Log.w("KOiL", "persist update target failed: ${it.message}") }
        // 带上 newer：否则「确认没新版」和「查到新版」打出一模一样的日志，排查时分不清
        Log.i(
            "KOiL",
            "update check ok: latest=${target.tag} apk=${target.apkUrl.ifEmpty { "-" }} newer=$newer",
        )
        if (newer) RefreshOutcome.Newer(target) else RefreshOutcome.Latest
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
/**
 * Release JSON 解析器。Json 是线程安全的不可变配置，建一次复用即可；
 * 每次解析都 new 一个属于无谓开销（K2: Redundant creation of Json format）。
 */
private val releaseJson = Json { ignoreUnknownKeys = true }

internal fun selectLatestRelease(json: String): UpdateTarget? {
    val releases = runCatching {
        releaseJson.decodeFromString<List<GiteeRelease>>(json)
    }.getOrNull() ?: return null

    // 跳过 prerelease 与无 tag 的脏数据：预发布不该覆盖正式版的提示
    val best = releases
        .filter { !it.prerelease && it.tagName.isNotBlank() }
        .maxWithOrNull { a, b -> compareVersionNames(a.tagName, b.tagName) }
        ?: return null

    // assets 里除 APK 还有源码包（V1.x.zip / .tar.gz），只认 .apk；找不到直链时留空，
    // 交给 UI 就地重查，而不是让整次检查失败（否则用户永远看不到"有新版本"）
    // 多个 .apk 时优先 KOiL- 开头的那个（本项目的产物命名），避免源码包/附带包被选走；
    // 顺序由服务端决定，不能靠"第一个"
    val apkAssets = best.assets.filter { it.name.endsWith(".apk") || it.url.endsWith(".apk") }
    val apkUrl = (apkAssets.firstOrNull { it.name.startsWith("KOiL-") } ?: apkAssets.firstOrNull())
        ?.url.orEmpty()
    return UpdateTarget(best.tagName, apkUrl)
}

/** 版本页地址。用 Gitee 而不是 GitHub：GitHub 在国内经常打不开。当前无生产调用方（预留），仅测试覆盖格式 */
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
fun shouldShowBanner(latestTag: String, seenTag: String, currentVersion: String): Boolean =
    isNewerRelease(latestTag, currentVersion) && latestTag != seenTag

/**
 * 「设置页红点要不要亮」：只看有没有新版本，**不受横幅 × 影响**。
 *
 * × 收起的只是首页那条横幅（「这条提醒我看过了」），不等于「我不想知道有更新」；
 * 红点要一直亮到装上最新版为止 —— 装成功后版本号不再比本机新，红点自然灭。
 */
fun shouldBadgeUpdate(latestTag: String, currentVersion: String): Boolean =
    isNewerRelease(latestTag, currentVersion)
