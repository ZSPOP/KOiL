package com.llpop.koil.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 加油记录表（对应 Web 端 koil_record，库内统一 KOiL 命名）
 * 仅存基础 10 字段；派生字段（distance/oilConsumption/priceDeviation 等）
 * 查询时按技术文档第 8 章算法计算，不落库。
 */
@Entity(
    tableName = "koil_record",
    indices = [
        Index(value = ["reoilDate", "mileage"], unique = true),
        Index("reoilDate"),
    ]
)
data class KOiLRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val reoilDate: String,          // yyyy-MM-dd
    val mileage: Double,            // 本次里程表读数 km
    val oilAmount: Double,          // 加油量 L
    val payableAmount: Double,      // 应付金额 元
    val actualAmount: Double,       // 实付金额 元
    val oilPrice: Double?,          // 油价 元/L
    val oilGrade: String?,          // 油号 92/95/98/0
    val province: String?,          // 省份
    val source: String?,            // entry_page / backfill_modal
)
