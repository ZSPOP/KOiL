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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

/** 一条调价记录（含相对上一条的涨跌）；搁浅条目为展示层补全（窗口已过但价格未变） */
data class PriceAdjustItem(
    val date: String,   // yyyy-MM-dd（生效日）
    val label: String,  // MM-dd 显示
    val price: Double,
    val diff: Double?,  // 相对上一条调价；null = 库内首条
    val isUp: Boolean?, // true=上调 / false=下调 / null=首条或持平
    val isStranded: Boolean = false, // true=搁浅（窗口日已过、价格未变）
    val keptFrom: String? = null,    // 搁浅条目：维持自哪个生效日（MM-dd）
)

/** 油价明细页状态：省份/油号/年份选择 + 调价时间线 + 汇总统计 */
data class OilPriceDetailUiState(
    val province: String = "广东",
    val grade: String = "92号汽油",
    val year: String = java.time.LocalDate.now().year.toString(),
    val years: List<String> = emptyList(),   // 库内该省油号记录存在的年份（降序），跨年后可切回看往年
    val provinces: List<String> = OilPriceDbSyncer.PROVINCES,
    // 库内 grade 以完整标签存储（"92号汽油"），查询与显示一致
    val grades: List<String> = OilPriceNetwork.GRADE_LABELS.values.toList(),
    val items: List<PriceAdjustItem> = emptyList(),   // 升序（旧→新），走势图用
    val upCount: Int = 0,           // 所选年份上调次数
    val downCount: Int = 0,         // 所选年份下调次数
    val strandedCount: Int = 0,     // 所选年份搁浅次数（窗口日历已发生但库内无生效记录）
    val riseTotal: Double = 0.0,    // 所选年份累计上调金额（元/L）
    val fallTotal: Double = 0.0,    // 所选年份累计下调金额（元/L）
    val netChange: Double = 0.0,    // 净涨跌 = 累计上调 − 累计下调
    val latestPrice: Double? = null,
    val latestDate: String? = null,
)

/** 明细页参数（省份 + 油号 + 年份 + 窗口日历） */
data class OilPriceDetailParams(
    val province: String,
    val grade: String,
    val year: String,
    val dates: String,
)

class OilPriceDetailViewModel(
    private val priceDao: KOiLPriceDao,
    private val settings: AppSettings,
) : ViewModel() {

    private val provinceFlow = MutableStateFlow("广东")
    private val gradeFlow = MutableStateFlow("92号汽油")
    private val yearFlow = MutableStateFlow(java.time.LocalDate.now().year.toString())

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState = combine(
        provinceFlow,
        gradeFlow,
        yearFlow,
        settings.priceDbAdjustDates,
    ) { p, g, y, d -> OilPriceDetailParams(p, g, y, d) }
        .flatMapLatest { params ->
            priceDao.observeByProvinceGrade(params.province, params.grade).map { records ->
                buildUiState(params, records)            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), OilPriceDetailUiState())

    fun selectProvince(province: String) {
        if (province in OilPriceDbSyncer.PROVINCES) provinceFlow.value = province
    }

    fun selectGrade(grade: String) {
        if (grade in OilPriceNetwork.GRADE_LABELS.values) gradeFlow.value = grade
    }

    /** 切换查看年份（默认当年，跨年后可切回看往年） */
    fun selectYear(year: String) {
        yearFlow.value = year
    }

    /** 上一省份（供顶部左右箭头点击切换；边界返回 false 不动作） */
    fun selectPrevProvince() {
        val idx = OilPriceDbSyncer.PROVINCES.indexOf(provinceFlow.value)
        if (idx > 0) provinceFlow.value = OilPriceDbSyncer.PROVINCES[idx - 1]
    }

    /** 下一省份（供顶部左右箭头点击切换；边界返回 false 不动作） */
    fun selectNextProvince() {
        val idx = OilPriceDbSyncer.PROVINCES.indexOf(provinceFlow.value)
        if (idx >= 0 && idx < OilPriceDbSyncer.PROVINCES.size - 1) {
            provinceFlow.value = OilPriceDbSyncer.PROVINCES[idx + 1]
        }
    }

    private suspend fun buildUiState(
        params: OilPriceDetailParams,
        records: List<KOiLPriceEntity>,
    ): OilPriceDetailUiState {
        // 年份过滤（v1.4.191 跨年维度）：默认显示所选年份的记录，跨年后当年从零开始、可切回看往年
        val yearRecords = records.filter { it.effectiveDate.startsWith(params.year) }
        // 可选年份：库内该省油号记录实际存在的年份（降序）
        val years = records.map { it.effectiveDate.take(4) }.distinct().sortedDescending()
        var rise = 0.0
        var fall = 0.0
        var upCount = 0
        var downCount = 0
        val items = mutableListOf<PriceAdjustItem>()
        // 上一条非搁浅记录（搁浅条目「維持 X 價格」的维持源日期）
        var lastAdjustLabel: String? = null
        yearRecords.forEachIndexed { i, r ->
            // v1.5.23：prev 用「全量记录」的上一条而非年过滤后的上一条——年初第一条调价
            // 相对上年末的涨跌不再丢失（此前 i==0 时 prev=null，当年累计涨跌漏计年初调价）
            val prev = records.takeWhile { it.effectiveDate < r.effectiveDate }.lastOrNull()?.price
            val diff = if (prev != null) r.price - prev else null
            // 涨跌统计只算非搁浅调价（搁浅 diff=0 天然不进，显式排除更清晰）
            if (diff != null && !r.isStranded) {
                if (diff > 0.001) { rise += diff; upCount++ }
                else if (diff < -0.001) { fall += -diff; downCount++ }
            }
            if (!r.isStranded) lastAdjustLabel = r.effectiveDate.takeLast(5)
            items.add(
                PriceAdjustItem(
                    date = r.effectiveDate,
                    // 调价记录显示生效日；搁浅显示窗口日（= 生效日−1，与数据库卡「調整日 10-15」一致）
                    label = if (r.isStranded) {
                        runCatching { LocalDate.parse(r.effectiveDate).minusDays(1).toString().takeLast(5) }
                            .getOrDefault(r.effectiveDate.takeLast(5))
                    } else {
                        r.effectiveDate.takeLast(5)
                    },
                    price = r.price,
                    // 搁浅 = 维持价（与上一窗口同价）→ 显示 0 持平；
                    // 首条搁浅（1-06，库内首条）prev=null → null（无前值可比较）
                    diff = if (r.isStranded) (diff?.let { 0.0 }) else diff,
                    isUp = if (r.isStranded) null else diff?.let { it > 0.001 },
                    isStranded = r.isStranded,
                    keptFrom = if (r.isStranded) lastAdjustLabel else null,
                )
            )
        }

        // 搁浅次数 = 所选年份库内 isStranded 记录数（v1.4.181 搁浅入库后直接统计，无需窗口差集/判定）
        val strandedCount = yearRecords.count { it.isStranded }

        return OilPriceDetailUiState(
            province = params.province,
            grade = params.grade,
            year = params.year,
            years = years,
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
