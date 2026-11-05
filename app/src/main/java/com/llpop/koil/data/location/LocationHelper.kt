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
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.nio.charset.Charset
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * 定位助手：获取当前位置并逆地理编码为省份。
 * 三级降级链（对应 Web 端 use-location-permission）：
 *   1. GPS/网络定位 → Geocoder 反查
 *   2. IP 归属地（whois.pconline.com.cn，HTTPS）
 *   3. null（调用方用默认省份）
 */
class LocationHelper(private val context: Context) {

    private val httpClient = HttpClient { }

    // v1.5.23 A3：IP 定位节流（进程内 30 分钟一次）——归属地接口按 IP 限流，启动+手动叠加易被打回
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

    /** IP 归属地：走 HTTPS 的 whois.pconline.com.cn（v1.6.3 起，此前用 ip-api.com 的 http 接口）。
     *
     *  v1.6.2 曾为 ip-api.com 单独放行明文流量，属于"能用但破口"。本版换成 HTTPS 源，全局重新禁止明文：
     *  - 该接口返回 GBK（Response Header: charset=GBK），必须按 GBK 显式解码，否则省份是乱码 → 白名单直接判非法；
     *  - 返回值就是中文省名（"广东省"），比原 ip-api 的英文 regionName 更稳，normalizeProvince 去后缀后即入白名单；
     *  - 仍是 30 分钟节流：接口按 IP 限流，启动 + 手动叠加容易被打回。
     */
    private suspend fun getProvinceByIp(): String? {
        val now = System.currentTimeMillis()
        if (now - lastIpLookupAt < 30 * 60 * 1000L) return null
        lastIpLookupAt = now
        return try {
            val response = httpClient.get("https://whois.pconline.com.cn/ipJson.jsp?json=true")
            val text = String(response.body<ByteArray>(), Charset.forName("GBK")).trim()
            // 接口在 JSON 前后可能夹空白/BOM，JSONObject 自己能吃空白，这里再兜一层空串
            if (text.isEmpty()) return null
            val json = JSONObject(text.trim())
            val province = json.optString("pro")
            if (province.isNotBlank() && province != "内网IP") normalizeProvince(province) else null
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
