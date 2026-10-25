package com.llpop.koil.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 待处理油价表（对应 Web 端 koil_price_pending，库内统一 KOiL 命名）
 * 双数据源价格不一致时入 pending，用户确认采纳/忽略。
 */
@Entity(
    tableName = "koil_price_pending",
    indices = [
        Index(value = ["province", "grade", "effectiveDate"], unique = true),
        Index("status"),
    ]
)
data class KOiLPricePendingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val province: String,
    val grade: String,
    val effectiveDate: String,
    val primaryPrice: Double?,
    val primarySource: String?,
    val secondaryPrice: Double?,
    val secondarySource: String?,
    val status: String,             // pending / accepted / ignored
    val resolvedPrice: Double?,
    val resolvedSource: String?,
    val note: String?,
    val createdAt: Long,
)
