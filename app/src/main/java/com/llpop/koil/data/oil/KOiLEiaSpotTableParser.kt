package com.llpop.koil.data.oil

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * EIA「WPSR 现货价格表」JSON 端点的解析与合并（纯函数，无网络无状态，便于单测）。
 *
 * 为什么会有这个类：EIA 同一份布伦特现货数据有两条发布链路，**节奏不同步**。
 * - `api.eia.gov/v2/petroleum/pri/spt`（App 原来用的）：实测停在 2026-11-03；
 * - `www.eia.gov/petroleum/prices/data/12_series/D/PET_PRI_SPT_S1_D.json`（WPSR 页面自己 XHR 的）：
 *   同期已到 2026-11-06，且每个工作日发布。
 *
 * 两条链路 series 同为 `RBRTE`、单位同为 USD/bbl，**没有口径差**，只是 v2 慢。
 * 所以这里只做两件事：解析出最近 6 个交易日，按日期覆盖到 v2 历史之上。
 *
 * 不做 forward-fill：EIA 休市就该没有记录，拿旧收盘价重复填充会人为压低波动。
 */
internal object KOiLEiaSpotTableParser {

    /** 页面内部端点：约 2 KB、application/json、无需 key、无 WAF（实测直连 200） */
    const val URL = "https://www.eia.gov/petroleum/prices/data/12_series/D/PET_PRI_SPT_S1_D.json"

    private const val SERIES_KEY = "RBRTE"

    /** 价格列之前的固定描述列：indent 类名、展示名、年份范围 */
    private const val DESC_COLUMNS = 3

    /** thead 是 `MM/dd/yy`；两位年份按 2000 起算（EIA 这份数据从 1987 起，不会撞到 19xx） */
    private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("MM/dd/yy", Locale.US)

    /**
     * 解析 JSON。任何结构异常都返回空表，不抛异常、不返回错位数据——
     * 返回错位的日期-价格对会让预测悄悄算错，比不补数据危险得多。
     */
    fun parse(body: String): List<Pair<String, Double>> {
        val root = runCatching { Json.parseToJsonElement(body) }.getOrNull() as? JsonObject
            ?: return emptyList()
        val thead = (root["thead"] as? JsonArray)
            ?.mapNotNull { (it as? JsonPrimitive)?.content }
            .orEmpty()
        if (thead.isEmpty()) return emptyList()

        // 按 key 找行，不按数组下标：EIA 调整 tbody 行序（按 section 分组）就会取到 WTI 那行
        val row = (root["tbody"] as? JsonArray)
            ?.mapNotNull { it as? JsonObject }
            ?.firstNotNullOfOrNull { (it["data"] as? JsonObject)?.get(SERIES_KEY) as? JsonArray }
            ?: return emptyList()

        // 行首 3 项是 [indent 类名, 展示名, 年份范围]，价格在其后。
        // 不硬编码 3，改成「右侧对齐」：取尾部 thead.size 个元素。
        // 对齐后必须**全是数字或空串**——描述列（indent 类名、年份范围）一旦被算进价格区，
        // 结构判断只看「形状」，不看「值」——这是上一版的 bug 所在：
        // 曾用「尾部 N 个全部可解析」判断对齐，结果 6 天里只要有一天是 `n/a`，
        // 整条 RBRTE 行就被当成结构错位丢掉，App 仍然停在旧日期，比不修还差（少一天 ≠ 全丢）。
        // 现在改成：描述列固定 3 列（indent 类名 / 展示名 / 年份范围），价格列数必须与 thead 相等。
        if (row.size != DESC_COLUMNS + thead.size) return emptyList()
        // 前 3 列必须都不是价格。若连「indent_0 / Brent - Europe / 1987 - 2026」都变成数字，
        // 说明列序已经变了，此时按位置取值必然错位，整条丢弃
        val descIsText = row.subList(0, DESC_COLUMNS).none { cell ->
            (cell as? JsonPrimitive)?.content?.toDoubleOrNull() != null
        }
        if (!descIsText) return emptyList()
        val values = row.subList(DESC_COLUMNS, row.size)

        return thead.indices
            .mapNotNull { i ->
                val date = runCatching { LocalDate.parse(thead[i].trim(), DATE_FORMAT).toString() }
                    .getOrNull() ?: return@mapNotNull null
                // 空串 / 非数字 / 非有限 / ≤0 只跳过当天，不连坐整条：单日缺数据不该让整段增量失效
                val value = (values[i] as? JsonPrimitive)?.content?.toDoubleOrNull()
                    ?: return@mapNotNull null
                if (!value.isFinite() || value <= 0) null else date to value
            }
            .distinctBy { it.first }
            .sortedBy { it.first }
    }

    /**
     * 把增量按日期覆盖到历史上。
     *
     * 只在 JSON 自带的那几天覆盖，**不改写 v2 的整段历史**：EIA 修订旧值时，
     * 回测（读固定 CSV）与线上会悄悄分叉，越多覆盖越难解释差异。
     * 增量里出现 v2 没有的日期则直接补上——那正是这条链路存在的意义。
     */
    fun merge(
        base: List<Pair<String, Double>>,
        overrides: List<Pair<String, Double>>,
    ): List<Pair<String, Double>> {
        if (overrides.isEmpty()) return base.sortedBy { it.first }
        val byDate = LinkedHashMap<String, Double>(base.size + overrides.size)
        base.forEach { byDate[it.first] = it.second }
        overrides.forEach { byDate[it.first] = it.second }
        return byDate.entries.sortedBy { it.key }.map { it.key to it.value }
    }
}
