package com.llpop.koil.data.local.seed

import kotlinx.serialization.Serializable

/** 预置加油记录种子（来自 koil_database koil_record.csv，16 条） */
@Serializable
data class SeedRecord(
    val reoilDate: String,
    val mileage: Double,
    val oilAmount: Double,
    val payableAmount: Double,
    val actualAmount: Double,
    val oilPrice: Double? = null,
    val oilGrade: String? = null,
    val province: String? = null,
    val source: String? = null,
)
