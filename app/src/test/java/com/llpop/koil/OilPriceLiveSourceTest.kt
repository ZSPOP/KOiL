package com.llpop.koil

import com.llpop.koil.data.oil.hasTodayAdjustment
import com.llpop.koil.data.oil.normalizeRegionName
import com.llpop.koil.data.oil.priceUnchanged
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 实时价源接入的两个不可见风险点。
 *
 * 一、省份名映射：xxapi 返回「广东省 / 内蒙古自治区 / 新疆维吾尔自治区」，库主键是「广东 / 内蒙古 / 新疆」。
 *    映射错了不会抛异常，只会让该省永远查不到价格（四宫格全空）——所以每种后缀形态都要钉住。
 *
 * 二、搁浅判据：V1.1.13 线上事故的根因就是拿「价格差」判搁浅。官方 11-12 24 时降 645 元/吨，
 *    youjia 把日期推到 11-13、价仍是 8.91，于是「采样 8.91 = 库内 8.91 → 无变化 → 搁浅」。
 *    实时源的 change 字段就是用来堵这个洞的：有 change 时价格差再小也不算搁浅。
 */
class OilPriceLiveSourceTest {

    // ===== 一、源端行政区名 → App 省份主键 =====

    @Test
    fun `省与直辖市去掉后缀`() {
        assertEquals("广东", normalizeRegionName("广东省"))
        assertEquals("北京", normalizeRegionName("北京市"))
        assertEquals("上海", normalizeRegionName("上海市"))
        assertEquals("黑龙江", normalizeRegionName("黑龙江省"))
        assertEquals("重庆", normalizeRegionName("重庆市"))
    }

    @Test
    fun `五个自治区各自的后缀形态`() {
        assertEquals("内蒙古", normalizeRegionName("内蒙古自治区"))
        assertEquals("广西", normalizeRegionName("广西壮族自治区"))
        assertEquals("新疆", normalizeRegionName("新疆维吾尔自治区"))
        assertEquals("宁夏", normalizeRegionName("宁夏回族自治区"))
        assertEquals("西藏", normalizeRegionName("西藏自治区"))
    }

    @Test
    fun `港澳台不映射到任何省份`() {
        // 31 省列表不含港澳台：返回 null 而不是原名，避免写进一个永远查不到的省份
        assertNull(normalizeRegionName("香港特别行政区"))
        assertNull(normalizeRegionName("澳门特别行政区"))
        assertNull(normalizeRegionName("台湾省"))
    }

    @Test
    fun `空值与未知地区一律返回 null`() {
        assertNull(normalizeRegionName(null))
        assertNull(normalizeRegionName(""))
        assertNull(normalizeRegionName("   "))
        assertNull(normalizeRegionName("某某地区"))
    }

    @Test
    fun `前后空白不影响映射`() {
        assertEquals("广东", normalizeRegionName(" 广东省 "))
    }

    // ===== 二、搁浅判据：change 优先，价格差兜底 =====

    @Test
    fun `实时源报出降幅时即使价格与库内相同也不算搁浅`() {
        // 事故场景的反面：xxapi date=11-13、n92=8.39、change=-0.52
        assertFalse(priceUnchanged(change = -0.52, sampledPrice = 8.39, latestPrice = 8.91))
    }

    @Test
    fun `实时源报出零变化才判搁浅`() {
        assertTrue(priceUnchanged(change = 0.0, sampledPrice = 8.91, latestPrice = 8.91))
        assertTrue(priceUnchanged(change = 0.002, sampledPrice = 8.91, latestPrice = 8.91))
        assertTrue(priceUnchanged(change = -0.002, sampledPrice = 8.91, latestPrice = 8.91))
        // 越过分毫阈值就是真调价
        assertFalse(priceUnchanged(change = 0.006, sampledPrice = 8.91, latestPrice = 8.91))
    }

    @Test
    fun `无 change 时退回价格差判断`() {
        // youjia 兜底：价格没变看起来像搁浅，但调用方据此不会下搁浅结论（见 syncer 分支）
        assertTrue(priceUnchanged(change = null, sampledPrice = 8.91, latestPrice = 8.91))
        assertFalse(priceUnchanged(change = null, sampledPrice = 8.39, latestPrice = 8.91))
        // 库内还没有价：不能判"无变化"
        assertFalse(priceUnchanged(change = null, sampledPrice = 8.91, latestPrice = null))
    }

    // ===== 三、今日已有记录的跳过判定（决定错的搁浅记录能不能被纠错） =====

    @Test
    fun `今日已有真实调价记录就跳过`() {
        assertTrue(hasTodayAdjustment("2026-11-13", false, "2026-11-13"))
    }

    @Test
    fun `今日只是搁浅记录不算已有调价，必须放行纠错`() {
        // V1.1.13 写进库的「11-13 搁浅 8.91」必须被实时源的 8.39 覆盖掉，否则价格一直卡旧值
        assertFalse(hasTodayAdjustment("2026-11-13", true, "2026-11-13"))
    }

    @Test
    fun `库里最新是往日或空记录时不跳过`() {
        assertFalse(hasTodayAdjustment("2026-10-31", false, "2026-11-13"))
        assertFalse(hasTodayAdjustment(null, null, "2026-11-13"))
    }
}
