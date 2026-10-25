package com.llpop.koil.ui.screen.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.llpop.koil.data.local.dao.KOiLPriceDao
import com.llpop.koil.data.local.entity.KOiLPriceEntity
import com.llpop.koil.data.oil.OilPriceDbSyncer
import com.llpop.koil.data.oil.OilPriceNetwork
import com.llpop.koil.data.settings.AppSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

/** 一条调价记录（含相对上一条的涨跌） */
data class PriceAdjustItem(
    val date: String,   // yyyy-MM-dd（生效日）
    val label: String,  // MM-dd 显示
    val price: Double,
    val diff: Double?,  // 相对上一条调价；null = 库内首条
    val isUp: Boolean?, // true=上调 / false=下调 / null=首条或持平
)

/** 油价明细页状态：省份/油号选择 + 调价时间线 + 汇总统计 */
data class OilPriceDetailUiState(
    val province: String = "广东",
    val grade: String = "92号汽油",
    val provinces: List<String> = OilPriceDbSyncer.PROVINCES,
    // 库内 grade 以完整标签存储（"92号汽油"），查询与显示一致
    val grades: List<String> = OilPriceNetwork.GRADE_LABELS.values.toList(),
    val items: List<PriceAdjustItem> = emptyList(),   // 升序（旧→新），走势图用
    val upCount: Int = 0,           // 今年上调次数
    val downCount: Int = 0,         // 今年下调次数
    val strandedCount: Int = 0,     // 今年搁浅次数（窗口日历已发生但库内无生效记录）
    val riseTotal: Double = 0.0,    // 累计上调金额（元/L）
    val fallTotal: Double = 0.0,    // 累计下调金额（元/L）
    val netChange: Double = 0.0,    // 净涨跌 = 累计上调 − 累计下调
    val latestPrice: Double? = null,
    val latestDate: String? = null,
)

class OilPriceDetailViewModel(
    private val priceDao: KOiLPriceDao,
    private val settings: AppSettings,
) : ViewModel() {

    private val provinceFlow = MutableStateFlow("广东")
    private val gradeFlow = MutableStateFlow("92号汽油")

    val uiState = combine(
        provinceFlow,
        gradeFlow,
        settings.priceDbAdjustDates,
    ) { p, g, dates -> Triple(p, g, dates) }
        .flatMapLatest { (p, g, dates) ->
            priceDao.observeByProvinceGrade(p, g).map { records ->
                buildUiState(p, g, records, dates)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), OilPriceDetailUiState())

    fun selectProvince(province: String) {
        if (province in OilPriceDbSyncer.PROVINCES) provinceFlow.value = province
    }

    fun selectGrade(grade: String) {
        if (grade in OilPriceNetwork.GRADE_LABELS.values) gradeFlow.value = grade
    }

    private suspend fun buildUiState(
        province: String,
        grade: String,
        records: List<KOiLPriceEntity>,
        windowDates: String,
    ): OilPriceDetailUiState {
        var rise = 0.0
        var fall = 0.0
        var upCount = 0
        var downCount = 0
        val items = mutableListOf<PriceAdjustItem>()
        records.forEachIndexed { i, r ->
            val prev = if (i > 0) records[i - 1].price else null
            val diff = if (prev != null) r.price - prev else null
            if (diff != null) {
                if (diff > 0.001) { rise += diff; upCount++ }
                else if (diff < -0.001) { fall += -diff; downCount++ }
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

        // 搁浅次数：权威窗口日历「今年已发生窗口」中，**该省该油号**在窗口日+1 无生效记录的数量。
        // 判定与 upCount/downCount 同源（records），保证：上調+下調+擱淺 = 今年已发生窗口数。
        val today = LocalDate.now().toString()
        val occurredWindows = windowDates
            .split(",").map { it.trim() }.filter { it.isNotBlank() && it <= today }
        val strandedCount = occurredWindows.count { w ->
            runCatching {
                priceDao.getByProvinceGradeDate(
                    province,
                    grade,
                    LocalDate.parse(w).plusDays(1).toString(),
                ) == null
            }.getOrDefault(false)
        }

        return OilPriceDetailUiState(
            province = province,
            grade = grade,
            items = items,
            upCount = upCount,
            downCount = downCount,
            strandedCount = strandedCount,
            riseTotal = rise,
            fallTotal = fall,
            netChange = rise - fall,
            latestPrice = items.lastOrNull()?.price,
            latestDate = items.lastOrNull()?.date,
        )
    }
}
