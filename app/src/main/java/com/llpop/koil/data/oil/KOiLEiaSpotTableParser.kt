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

    /** 顶层 `release_date` 是 `M/d/yyyy`，月日不补零（实测 `11/9/2026`，不是 `11/09/2026`） */
    private val RELEASE_DATE_FORMAT: DateTimeFormatter =
        DateTimeFormatter.ofPattern("M/d/yyyy", Locale.US)

    /**
     * 增量数据 + 官方发布日。
     *
     * [releaseDate] 是 EIA **发布这批数据**的日期，不是「数据截至日」——两者常差几天：
     * 11-09 那批发布的数据，最后一天是 11-06。分开给，UI 才能写「截至 11-06 · 源端 11-09 发布」，
     * 让用户分清「源端就是这么慢」和「App 卡住了」。
     */
    data class EiaSpotTable(
        val points: List<Pair<String, Double>>,
        val releaseDate: String? = null,
    )

    /**
     * 解析 JSON。任何结构异常都返回空表，不抛异常、不返回错位数据——
     * 返回错位的日期-价格对会让预测悄悄算错，比不补数据危险得多。
     */
    fun parse(body: String): List<Pair<String, Double>> = parseWithMeta(body).points

    /**
     * 同 [parse]，额外给出官方发布日。
     *
     * 发布日过不了下面三关就置 null —— 只丢这个字段，绝不因此丢数据：
     * 1. 字段缺失 / 不是合法 `M/d/yyyy`；
     * 2. 一个价格点都没解析出来（「这批数据」不存在，发布日没有指代对象）；
     * 3. 发布日早于数据最后一天（发布不可能早于数据日；出现即说明字段语义已被改掉，
     *    照原样显示等于把一个未知说成一个具体日期）。
     */
    fun parseWithMeta(body: String): EiaSpotTable {
        val root = runCatching { Json.parseToJsonElement(body) }.getOrNull() as? JsonObject
            ?: return EiaSpotTable(emptyList())
        val thead = (root["thead"] as? JsonArray)
            ?.mapNotNull { (it as? JsonPrimitive)?.content }
            .orEmpty()
        if (thead.isEmpty()) return EiaSpotTable(emptyList())

        // 按 key 找行，不按数组下标：EIA 调整 tbody 行序（按 section 分组）就会取到 WTI 那行
        val row = (root["tbody"] as? JsonArray)
            ?.mapNotNull { it as? JsonObject }
            ?.firstNotNullOfOrNull { (it["data"] as? JsonObject)?.get(SERIES_KEY) as? JsonArray }
            ?: return EiaSpotTable(emptyList())

        // 行首 DESC_COLUMNS 项固定是 [indent 类名, 展示名, 年份范围]，价格在其后，
        // 故价格列数必须与 thead 相等。
        // 结构判断只看「形状」，不看「值」——这是上一版的 bug 所在：
        // 曾用「尾部 N 个全部可解析」判断对齐，结果 6 天里只要有一天是 `n/a`，
        // 整条 RBRTE 行就被当成结构错位丢掉，App 仍然停在旧日期，比不修还差（少一天 ≠ 全丢）。
        if (row.size != DESC_COLUMNS + thead.size) return EiaSpotTable(emptyList())
        // 描述列必须都不是价格。若连「indent_0 / Brent - Europe / 1987 - 2026」都变成数字，
        // 说明列序已经变了，此时按位置取值必然错位，整条丢弃
        val descIsText = row.subList(0, DESC_COLUMNS).none { cell ->
            (cell as? JsonPrimitive)?.content?.toDoubleOrNull() != null
        }
        if (!descIsText) return EiaSpotTable(emptyList())
        val values = row.subList(DESC_COLUMNS, row.size)

        val points = thead.indices
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

        return EiaSpotTable(points, releaseDateOf(root, points))
    }

    /** 取顶层 `release_date`，按上面三条不变量校验；不满足返回 null */
    private fun releaseDateOf(
        root: JsonObject,
        points: List<Pair<String, Double>>,
    ): String? {
        val raw = (root["release_date"] as? JsonPrimitive)?.content?.trim().orEmpty()
        val released = runCatching { LocalDate.parse(raw, RELEASE_DATE_FORMAT).toString() }
            .getOrNull() ?: return null
        return resolveReleaseDate(released, points)
    }

    /**
     * 按**最终点集**校验发布日：发布日不可能早于该点集的最后一天，否则置 null。
     *
     * 为什么 [parseWithMeta] 里校验过还要再校验一次：那里校验的是增量表**自己**的 `points`，
     * 而调用方会把发布日挂到「v2 历史 + 增量」合并后的点集上。若 v2 反而更新
     * （增量最后一天 11-06、v2 已到 11-10、`release_date` 是 11-09），增量自查会通过，
     * 合并后却会出现「截至 11-10 · 源端 11-09 发布」这种自相矛盾的两个日期。
     * 不变量属于最终结果而不是中间结果，所以合并后必须再校验一次。
     *
     * 前置条件：[points] 按日期升序（[merge] 与 [parseWithMeta] 都保证）。
     */
    fun resolveReleaseDate(
        releaseDate: String?,
        points: List<Pair<String, Double>>,
    ): String? {
        if (releaseDate.isNullOrBlank() || points.isEmpty()) return null
        val released = runCatching { LocalDate.parse(releaseDate).toString() }.getOrNull()
            ?: return null
        return released.takeIf { it >= points.last().first }
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
