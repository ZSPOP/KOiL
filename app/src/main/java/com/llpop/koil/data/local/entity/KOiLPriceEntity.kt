package com.llpop.koil.data.local.entity

import androidx.room.Entity

/**
 * 本地油价库（替代服务端 oil_price_db / koil_price_history）
 * 支持按 省份 + 油号 + 日期 查询，用于价格偏差与展示。
 * 预置快照 + 公开数据源可选更新（默认关闭）。
 */
@Entity(
    tableName = "koil_prices",
    primaryKeys = ["province", "grade", "effectiveDate"],
)
data class KOiLPriceEntity(
    val province: String,           // 广东
    val grade: String,              // 92号汽油 / 95号汽油 / 98号汽油 / 0号柴油
    val effectiveDate: String,      // yyyy-MM-dd
    val price: Double,              // 元/L
    val source: String?,            // 来源 URL
    val isVerified: Boolean,        // 官方已核验
)
