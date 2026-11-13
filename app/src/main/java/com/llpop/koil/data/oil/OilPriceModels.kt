package com.llpop.koil.data.oil

/**
 * 油价实时抓取模型（对齐 Web 端 oil-price.service.ts 的 OilPriceInfo/OilPriceResponse）。
 */

/** 单条油价信息（按油号） */
data class OilPriceInfo(
    val grade: String,          // "92" / "95" / "98" / "0"
    val gradeLabel: String,     // "92号汽油" 等
    val price: Double,          // 元/升
    val updateDate: String,     // yyyy-MM-dd
    val source: String,         // 来源名
    /**
     * 该油号本轮调价的变化量（元/升），仅实时源（xxapi / ruseo）提供，youjia 曲线无此字段。
     *
     * 有它就能直接判「这轮到底调没调」：窗口次日 [change] ≈ 0 即搁浅，
     * 否则即调价。没有它时只能拿「采样价 vs 库内最近价」的差值去猜，
     * 一旦源自身滞后未更新（youjia 每天晚 1~2 天把日期推到今天、价却没动），
     * 就会被误判成搁浅（V1.1.13 实测：官方 11-12 24 时降 645 元/吨，11-13 youjia 仍是旧价 8.91）。
     */
    val change: Double? = null,
)

/** 单省份抓取结果（success=false 时 error 非空） */
data class OilPriceResponse(
    val success: Boolean,
    val province: String? = null,
    val updateDate: String? = null,
    val nextAdjustment: String? = null,
    // forecast/nextAdjustDate/direction/confidence 随 apizero 移除而删除（预测专接口不可用），
    // 预测幅度恒走 EIA/腾讯本地估算（CrudeOilEstimator），窗口日恒走本地推算窗口日历。
    val error: String? = null,
    val data: List<OilPriceInfo> = emptyList(),
    /**
     * 全国各省的当日价（省简称 → 4 个油号），仅实时源（xxapi / ruseo）填充；youjia 为空。
     *
     * 用途：一次全国响应本来就含 31 省，跨天纠错（`reconcileLatestWindow`）需要按省对齐库里
     * 被误写成"搁浅"的记录，用它能做到零额外网络请求。
     */
    val siblings: Map<String, List<OilPriceInfo>> = emptyMap(),
)

/** 一次检查更新的汇总结果（对齐 Web OilPriceDbUpdateResponse） */
data class OilPriceSyncResult(
    val success: Boolean,
    val message: String,
    val newAdjustments: Int = 0,
    val newRecords: Int = 0,
    val latestDate: String? = null,
    val strandedWindowDate: String? = null,
    val nextAdjustment: String? = null,
)

/** 偏差预检结果（对齐 Web checkPriceDeviation 返回） */
data class DeviationCheckResult(
    val hasDeviation: Boolean,
    val actualUnitPrice: Double,
    val referencePrice: Double? = null,
    val referenceDate: String? = null,
    val deviationPercent: Double? = null,
    val thresholdPercent: Double,
    val pendingAfter: Boolean = false,
    val isLatestVerified: Boolean = false,
)
