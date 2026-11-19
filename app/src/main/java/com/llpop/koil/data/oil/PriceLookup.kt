package com.llpop.koil.data.oil

import com.llpop.koil.data.local.dao.KOiLPriceDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 「当日参考价」的唯一取价口径。
 *
 * 录入卡片上显示的单价、应付金额、偏差校验三处都必须从这里取，否则同一场景会出现两个数
 * （实付为空时先按一个价显示、填了实付又按另一个价重算）。
 *
 * 规则：
 * - 只取「≤ 指定日期」已生效的最新一条（窗口日白天不提前用次日新价）；
 * - 价格必须 > 0 且有限，脏行一律当没有；
 * - 油号缺失时统一回落 92#，与界面默认保持一致（此前这里用原始油号查，界面用 92#，两边会分叉）。
 */
object PriceLookup {

    /** 未知油号统一回落到 92#，避免「界面按 92# 算、校验按原始字符串查不到」的分叉 */
    const val DEFAULT_GRADE: String = "92号汽油"

    fun gradeLabel(grade: String): String = OilPriceNetwork.GRADE_LABELS[grade] ?: DEFAULT_GRADE

    /**
     * 已知油号才返回标签，未知返回 null。
     * 入库写库路径必须用它：未知油号若回落到 92#，会把别的油品价格冒充 92 号写进库里，
     * 之后被当成真实价展示、参与涨跌对比。那条路径宁可跳过这条数据。
     */
    fun gradeLabelOrNull(grade: String): String? = OilPriceNetwork.GRADE_LABELS[grade]

    private fun Double.validPrice(): Double? = takeIf { it > 0 && it.isFinite() }

    /** 一次性取价（校验/兜底路径）；无记录或价格无效返回 null */
    suspend fun referencePrice(
        dao: KOiLPriceDao,
        province: String,
        grade: String,
        date: String,
    ): Double? {
        if (province.isBlank() || date.isBlank()) return null
        return dao.getLatestPrice(province, gradeLabel(grade), date)?.price?.validPrice()
    }

    /** 响应式取价（界面显示路径）：库内写入（灌库/调价更新）后自动刷新 */
    fun observeReferencePrice(
        dao: KOiLPriceDao,
        province: String,
        grade: String,
        date: String,
    ): Flow<Double?> =
        dao.observeNewestUpTo(province, gradeLabel(grade), date)
            .map { it?.price?.validPrice() }
}
