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

        // 搁浅次数 = 今年已发生窗口数 − 上调次数 − 下调次数。
        // 说明：records 首条为「基準價」（无前一条可比较方向，不计上调/下调），但其窗口
        // 已发生且有记录（非搁浅）；若用「窗口+1 无记录」判定，首条窗口会两头不计，
        // 导致 上調+下調+擱淺 恒比窗口数少 1。改用差集口径后总数恒等于窗口数。
        val today = LocalDate.now().toString()
        val occurredWindows = windowDates
            .split(",").map { it.trim() }.filter { it.isNotBlank() && it <= today }
        val strandedCount = (occurredWindows.size - upCount - downCount).coerceAtLeast(0)

        // —— 展示层补全搁浅条目（窗口已过但该省该油号无生效记录 = 搁浅）——
        // 不灌库：库内记录/缺口统计/调价次数口径都不受影响，仅时间线显示补全，
        // 使「調價 20」与记录页条目完全对应（搁浅显示为灰色「擱淺 · 維持 X 價格」）。
        val effectiveDates = records.map { it.effectiveDate }.toSet()
        occurredWindows.forEach { w ->
            val eff = runCatching { LocalDate.parse(w).plusDays(1).toString() }.getOrNull() ?: return@forEach
            if (eff !in effectiveDates) {
                // 维持价 = 该搁浅窗口前最近一条记录（价格与生效日，用于「維持 09-25 價格」）
                val kept = records.lastOrNull { it.effectiveDate <= eff }
                items.add(
                    PriceAdjustItem(
                        date = eff,
                        label = eff.takeLast(5),
                        price = kept?.price ?: 0.0,
                        diff = 0.0,
                        isUp = null,
                        isStranded = true,
                        keptFrom = kept?.effectiveDate?.takeLast(5),
                    )
                )
            }
        }
        items.sortBy { it.date }

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
