package com.llpop.koil.ui.screen.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.llpop.koil.data.local.dao.FuelPriceDao
import com.llpop.koil.data.local.entity.FuelPriceEntity
import com.llpop.koil.data.oil.OilPriceDbSyncer
import com.llpop.koil.data.oil.OilPriceNetwork
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** 一条调价记录（含相对上一条的涨跌） */
data class PriceAdjustItem(
    val date: String,   // yyyy-MM-dd（生效日）
    val label: String,  // MM-dd 显示
    val price: Double,
    val diff: Double?,  // 相对上一条调价；null = 库内首条
    val isUp: Boolean?, // true=上调 / false=下调 / null=首条或持平
)

/** 油价明细页状态：省份/油号选择 + 调价时间线 + 涨跌汇总 */
data class OilPriceDetailUiState(
    val province: String = "广东",
    val grade: String = "92号汽油",
    val provinces: List<String> = OilPriceDbSyncer.PROVINCES,
    // 库内 grade 以完整标签存储（"92号汽油"），查询与显示一致
    val grades: List<String> = OilPriceNetwork.GRADE_LABELS.values.toList(),
    val items: List<PriceAdjustItem> = emptyList(),
    val riseTotal: Double = 0.0,   // 今年累计上调（元/L）
    val fallTotal: Double = 0.0,   // 今年累计下调（元/L）
    val latestPrice: Double? = null,
    val latestDate: String? = null,
)

class OilPriceDetailViewModel(
    private val priceDao: FuelPriceDao,
) : ViewModel() {

    private val provinceFlow = MutableStateFlow("广东")
    private val gradeFlow = MutableStateFlow("92号汽油")

    val uiState = combine(provinceFlow, gradeFlow) { p, g -> p to g }
        .flatMapLatest { (p, g) ->
            priceDao.observeByProvinceGrade(p, g).map { records ->
                buildUiState(p, g, records)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), OilPriceDetailUiState())

    fun selectProvince(province: String) {
        if (province in OilPriceDbSyncer.PROVINCES) provinceFlow.value = province
    }

    fun selectGrade(grade: String) {
        if (grade in OilPriceNetwork.GRADE_LABELS.values) gradeFlow.value = grade
    }

    private fun buildUiState(
        province: String,
        grade: String,
        records: List<FuelPriceEntity>,
    ): OilPriceDetailUiState {
        var rise = 0.0
        var fall = 0.0
        val items = mutableListOf<PriceAdjustItem>()
        records.forEachIndexed { i, r ->
            val prev = if (i > 0) records[i - 1].price else null
            val diff = if (prev != null) r.price - prev else null
            if (diff != null) {
                if (diff > 0.001) rise += diff
                else if (diff < -0.001) fall += -diff
            }
            items.add(
                PriceAdjustItem(
                    date = r.effectiveDate,
                    label = r.effectiveDate.takeLast(5),
                    price = r.price,
                    diff = diff,
                    isUp = diff?.let { it > 0.001 },
                )
            )
        }
        return OilPriceDetailUiState(
            province = province,
            grade = grade,
            items = items,
            riseTotal = rise,
            fallTotal = fall,
            latestPrice = items.lastOrNull()?.price,
            latestDate = items.lastOrNull()?.date,
        )
    }
}
