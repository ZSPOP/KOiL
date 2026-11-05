package com.llpop.koil.data.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import com.llpop.koil.data.oil.Provinces
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * 定位助手：获取当前位置并逆地理编码为省份。
 * 三级降级链（对应 Web 端 use-location-permission）：
 *   1. GPS/网络定位 → Geocoder 反查
 *   2. IP 归属地（ip-api.com）
 *   3. null（调用方用默认省份）
 */
class LocationHelper(private val context: Context) {

    private val httpClient = HttpClient { }

    // v1.5.23 A3：IP 定位节流（进程内 30 分钟一次）——ip-api.com 免费版 45 次/分钟/IP 限流，启动+手动叠加易触发
    private var lastIpLookupAt = 0L

    /** 检查定位权限是否已授予 */
    fun hasPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
        return fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED
    }

    /**
     * 获取当前省份（suspend，三级降级）。
     */
    suspend fun getCurrentProvince(): String? = withContext(Dispatchers.IO) {
        // 第一级：GPS + Geocoder
        if (hasPermission()) {
            try {
                val location = getLastKnownLocation()
                if (location != null) {
                    val province = geocodeProvince(location.latitude, location.longitude)
                    if (Provinces.isValid(province)) return@withContext province
                }
            } catch (_: Exception) { }
        }
        // 第二级：IP 归属地
        try {
            val province = getProvinceByIp()
            if (Provinces.isValid(province)) return@withContext province
        } catch (_: Exception) { }
        // 第三级：null（调用方回退默认）
        null
    }

    /** IP 归属地：请求 ip-api.com 获取省份（免费版仅 http 明文可用，https 需付费 Key；
     *  明文只含省份信息、敏感度低，配合 30 分钟节流缓解限流与暴露面）。
     *  v1.6.2：① 该域名已在 network_security_config 中单独放行明文（否则 targetSdk 28+ 直接抛
     *  UnknownServiceException，整条 IP 降级链静默失效）；② 加 lang=zh-CN，让 regionName 直接返回
     *  "广东省"而不是"Guangdong"，否则拿到的英文省名不在省份白名单里，四宫格会全部空白。 */
    private suspend fun getProvinceByIp(): String? {
        val now = System.currentTimeMillis()
        if (now - lastIpLookupAt < 30 * 60 * 1000L) return null
        lastIpLookupAt = now
        return try {
            val response = httpClient.get("http://ip-api.com/json/?fields=status,regionName,country&lang=zh-CN")
            val json = JSONObject(response.bodyAsText())
            if (json.optString("status") == "success") {
                val region = json.optString("regionName")
                if (region.isNotBlank()) normalizeProvince(region) else null
            } else null
        } catch (_: Exception) {
            null
        }
    }

    /** 最近 30 分钟内的定位缓存才采用（v1.5.23 A4：避免数月前的旧定位误报省份） */
    private fun getLastKnownLocation(): Location? {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        val providers = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER,
        )
        val cutoff = System.currentTimeMillis() - 30 * 60 * 1000L
        var best: Location? = null
        for (provider in providers) {
            try {
                val loc = lm.getLastKnownLocation(provider) ?: continue
                if (loc.time < cutoff) continue   // v1.5.23：过期缓存丢弃
                if (best == null || loc.time > best.time) best = loc
            } catch (_: SecurityException) { }
        }
        return best
    }

    private fun geocodeProvince(lat: Double, lng: Double): String? {
        return try {
            val geocoder = Geocoder(context, Locale.SIMPLIFIED_CHINESE)
            // minSdk 33：统一走异步回调接口；v1.5.23 A2：CountDownLatch 等待至多 2s
            // （此前 Thread.sleep(200) 依赖回调在主线程同步执行，超时即静默降级 IP，偶尔定位失败）
            val latch = CountDownLatch(1)
            var result: String? = null
            geocoder.getFromLocation(lat, lng, 1) { addresses ->
                result = addresses.firstOrNull()?.adminArea
                latch.countDown()
            }
            latch.await(2, TimeUnit.SECONDS)
            result?.let { normalizeProvince(it) }
        } catch (_: Exception) {
            null
        }
    }

    /** Geocoder 返回的 adminArea 可能是"广东省"，统一为"广东"。
     * 注意 removeSuffix 必须按后缀长度从长到短匹配：
     * 若先匹配"自治区"，"广西壮族自治区"会被误删成"广西壮族"（丢前缀"壮族"），
     * 必须让"壮族自治区/回族自治区/维吾尔自治区"优先于"自治区"匹配。 */
    private fun normalizeProvince(adminArea: String): String {
        val suffixes = listOf(
            "维吾尔自治区",
            "壮族自治区",
            "回族自治区",
            "自治区",
            "特别行政区",
            "省",
            "市",
        )
        var result = adminArea.trim()
        suffixes.firstOrNull { result.endsWith(it) }?.let { result = result.removeSuffix(it) }
        return result
    }
}
