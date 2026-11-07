package com.llpop.koil.data.update

import android.util.Log
import com.llpop.koil.data.settings.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * v1.6.5：应用更新检查。
 *
 * 冷启动静默检查（[checkIfDue]，8 小时节流）与设置页手动「检查更新」（[refresh]）共用同一条通道，
 * 结果落到 DataStore：`updateLatestTag` 供 UI 判断是否挂红点、`updateLastCheckAt` 作为节流依据。
 *
 * 设计取舍：
 * - **不引第三方库**（无 Retrofit/OkHttp）：一次 GET + 两个正则足够，6 秒超时；
 * - **失败绝不写假数据**：请求失败时保留上一次的 tag，只让调用方知道"这次没查到"（返回 null）；
 * - **8h 节流写在启动侧**：冷启动每次都调，但真正发请求的只有超出窗口的那一次，频繁启停几乎零流量。
 */
class UpdateChecker(private val settings: AppSettings) {

    /** 最近一次查到的 Release tag（原始形式，如 "V1.6.4"；空串=从未查到过） */
    fun latestTag(): Flow<String> = settings.updateLatestTag

    /** 最近一次**成功**检查的时间戳（0=从未查过），设置页据此显示「上次检查」 */
    fun lastCheckAt(): Flow<Long> = settings.updateLastCheckAt

    /**
     * 冷启动 / 回前台静默检查：距上次**成功**检查不足 [INTERVAL_MS] 时直接返回 null（不发请求、不写库）。
     * 时间戳取自机的墙上时钟；用户改系统时间导致差值异常时按"该查了"处理（宁多查一次，不漏提示）。
     *
     * v1.6.5 修正：原窗口 8 小时太长——刚装完包时那次冷启动会把当时的 release 记为"最新"，
     * 之后就算你发了新版，也要等满 8 小时才会再查，表现就是"好像从不自动检查"。
     * 缩短到 45 分钟（GitHub 匿名 API 限 60 次/小时，每分钟一次都够，不会触发限流），
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
     * @return 最新 tag（如 "V1.6.4"）；网络失败 / 非 2xx / 解析失败一律 null。
     */
    suspend fun refresh(): String? = withContext(Dispatchers.IO) {
        val tag = runCatching { fetchLatestTag() }
            .onFailure { Log.w("KOiL", "update check failed: ${it.message}") }
            .getOrNull()
            ?: return@withContext null
        // 先写 tag 再写时间戳：中途被杀也只会"多查一次"，不会出现"记了时间却没记 tag"的红点缺失
        runCatching {
            settings.setUpdateLatestTag(tag)
            settings.setUpdateLastCheckAt(System.currentTimeMillis())
        }.onFailure { Log.w("KOiL", "persist update tag failed: ${it.message}") }
        Log.i("KOiL", "update check ok: latest=$tag")
        tag
    }

    private fun fetchLatestTag(): String? {
        var conn: HttpURLConnection? = null
        try {
            conn = (URL(RELEASE_API).openConnection() as HttpURLConnection).apply {
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                setRequestProperty("User-Agent", "KOiL-Android")
                setRequestProperty("Accept", "application/vnd.github+json")
            }
            if (conn.responseCode !in 200..299) return null
            val json = conn.inputStream.bufferedReader().use { it.readText() }
            return Regex("\"tag_name\"\\s*:\\s*\"([^\"]+)\"")
                .find(json)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() }
        } finally {
            runCatching { conn?.disconnect() }
        }
    }

    companion object {
        /** 两次检查的最小间隔（v1.6.5：8 小时 → 45 分钟，见 [checkIfDue] 注释） */
        const val INTERVAL_MS = 45L * 60 * 1000
        const val RELEASE_API = "https://api.github.com/repos/ZSPOP/KOiL/releases/latest"
        private const val TIMEOUT_MS = 6000
    }
}

/** Release 页面地址（形如 https://github.com/ZSPOP/KOiL/releases/tag/V1.6.4） */
fun releasePageUrl(tag: String): String = "https://github.com/ZSPOP/KOiL/releases/tag/$tag"

/** 去掉 V/v 前缀，得到可比较的版本号（"V1.6.4" → "1.6.4"） */
fun normalizeVersion(tag: String): String = tag.trim().trimStart('V', 'v')

/**
 * 语义版本比较：只比数字段，忽略 V/v 前缀与非数字后缀（如 -beta）。返回 >0 表示 a 更新。
 */
fun compareVersionNames(a: String, b: String): Int {
    val pa = a.split(".", "-").mapNotNull { it.toIntOrNull() }
    val pb = b.split(".", "-").mapNotNull { it.toIntOrNull() }
    for (i in 0 until maxOf(pa.size, pb.size)) {
        val x = pa.getOrElse(i) { 0 }
        val y = pb.getOrElse(i) { 0 }
        if (x != y) return x.compareTo(y)
    }
    return 0
}
