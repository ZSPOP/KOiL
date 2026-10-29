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
)

/** 单省份抓取结果（success=false 时 error 非空） */
data class OilPriceResponse(
    val success: Boolean,
    val province: String? = null,
    val updateDate: String? = null,
    val nextAdjustment: String? = null,
    // v1.5.21：forecast/nextAdjustDate/direction/confidence 已随 apizero 移除全部删除（预测专接口不再存在），
    // 预测幅度恒走 EIA/腾讯本地估算（CrudeOilEstimator），窗口日恒走本地推算窗口日历。
    val error: String? = null,
    val data: List<OilPriceInfo> = emptyList(),
)

/** 一次检查更新的汇总结果（对齐 Web OilPriceDbUpdateResponse） */
data class OilPriceSyncResult(
    val success: Boolean,
    val message: String,
    val newAdjustments: Int = 0,
    val newRecords: Int = 0,
    val pendingCount: Int = 0,
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
