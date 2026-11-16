package com.llpop.koil.data.oil

/**
 * 「上次核对结论」的结构化形式。
 *
 * 历史实现把一句中文句子（25 处硬编码）写进 DataStore，再读出来塞进 `lastUpdateMessage`，
 * 而设置页对那个字段一个引用都没有：用户只看得到「上次检查 21:44」，看不到那次是成功、
 * 搁浅、还是失败——失败原因只留在日志里。要把它显示出来就必须能翻译成三种语言，
 * 所以改成「机器码 + 参数」：文案全部交给 `strings.xml`，持久化只存 [encode] 的结果。
 *
 * 编码格式：`code` 与各参数用 U+001F（单元分隔符）连接，见 [encode] / [decodePriceDbOutcome]。
 * 不用 JSON 是因为这里只有一个判别码和最多两个短参数，序列化框架反而要先配多态注册。
 *
 * 落盘范围：`Busy` / `AutoUpdateOff` / `Throttled` 只出现在 `OilPriceDbSyncer` 的早退路径
 * （在写缓存**之前** return），因此永远不会被 [encode] 存下来，设置页也永远不显示它们。
 * 其中只有 `Busy` 有 UI 出口（手动「立即更新」被互斥挡下时提示一句）；另两个目前没有任何
 * 消费者——`OilPriceSyncWorker` 只读 `result.success`，不读 outcome。保留这两个值是为了
 * 语义清楚，不是为了被读取。
 */
sealed interface PriceDbOutcome {

    /** 上一轮还没跑完（并发互斥挡下） */
    data object Busy : PriceDbOutcome

    /** 自动更新开关关着，本次跳过 */
    data object AutoUpdateOff : PriceDbOutcome

    /** 距上次成功检测不足 [hours] 小时 */
    data class Throttled(val hours: Int) : PriceDbOutcome

    data object NoProvince : PriceDbOutcome

    /** 两个实时源都失败；[detail] 是上游原始错误，无法本地化，原样展示 */
    data class FetchFailed(val detail: String) : PriceDbOutcome

    /** 数据源日期滞后到 [date]，跳过本次核验；[repaired] 是顺带按实时源纠正的条数 */
    data class SourceLagging(val date: String, val repaired: Int) : PriceDbOutcome

    /** [date] 是调价窗口日，价格 24 点后才生效 */
    data class WindowDay(val date: String) : PriceDbOutcome

    /** 价格未变，但没有实时源背书，不能判搁浅（[date] 为核对日） */
    data class UnchangedUnconfirmed(val date: String) : PriceDbOutcome

    /** 实时源确认本轮无变化 = 真搁浅；[inserted] 是当天补写的维持价条数 */
    data class Stranded(val date: String, val inserted: Int) : PriceDbOutcome

    /** 非窗口日的正常维持（[date] 为核对日） */
    data class Unchanged(val date: String) : PriceDbOutcome

    /** 今日已有调价记录，只做了跨天纠错 [repaired] 条 */
    data class RepairedOnly(val repaired: Int) : PriceDbOutcome

    data object AlreadyAdjusted : PriceDbOutcome

    /** 本次实际入库 [records] 条；[failedProvinces] > 0 表示部分省份核验失败 */
    data class Updated(val records: Int, val failedProvinces: Int) : PriceDbOutcome

    data object NoNewRecords : PriceDbOutcome

    /** 检测过程抛异常；[detail] 是异常信息，原样展示 */
    data class Failed(val detail: String) : PriceDbOutcome

    fun encode(): String = when (this) {
        Busy -> CODE_BUSY
        AutoUpdateOff -> CODE_AUTO_OFF
        is Throttled -> listOf(CODE_THROTTLED, hours.toString()).joinToString(SEP)
        NoProvince -> CODE_NO_PROVINCE
        is FetchFailed -> listOf(CODE_FETCH_FAILED, detail.clean()).joinToString(SEP)
        is SourceLagging -> listOf(CODE_SOURCE_LAGGING, date.clean(), repaired.toString()).joinToString(SEP)
        is WindowDay -> listOf(CODE_WINDOW_DAY, date.clean()).joinToString(SEP)
        is UnchangedUnconfirmed -> listOf(CODE_UNCHANGED_UNCONFIRMED, date.clean()).joinToString(SEP)
        is Stranded -> listOf(CODE_STRANDED, date.clean(), inserted.toString()).joinToString(SEP)
        is Unchanged -> listOf(CODE_UNCHANGED, date.clean()).joinToString(SEP)
        is RepairedOnly -> listOf(CODE_REPAIRED_ONLY, repaired.toString()).joinToString(SEP)
        AlreadyAdjusted -> CODE_ALREADY_ADJUSTED
        is Updated -> listOf(CODE_UPDATED, records.toString(), failedProvinces.toString()).joinToString(SEP)
        NoNewRecords -> CODE_NO_NEW_RECORDS
        is Failed -> listOf(CODE_FAILED, detail.clean()).joinToString(SEP)
    }

    companion object {
        /** 参数分隔符：U+001F 不会出现在日期、数字或网络错误串里 */
        private const val SEP = "\u001F"

        private const val CODE_BUSY = "busy"
        private const val CODE_AUTO_OFF = "auto_off"
        private const val CODE_THROTTLED = "throttled"
        private const val CODE_NO_PROVINCE = "no_province"
        private const val CODE_FETCH_FAILED = "fetch_failed"
        private const val CODE_SOURCE_LAGGING = "source_lagging"
        private const val CODE_WINDOW_DAY = "window_day"
        private const val CODE_UNCHANGED_UNCONFIRMED = "unchanged_unconfirmed"
        private const val CODE_STRANDED = "stranded"
        private const val CODE_UNCHANGED = "unchanged"
        private const val CODE_REPAIRED_ONLY = "repaired_only"
        private const val CODE_ALREADY_ADJUSTED = "already_adjusted"
        private const val CODE_UPDATED = "updated"
        private const val CODE_NO_NEW_RECORDS = "no_new_records"
        private const val CODE_FAILED = "failed"

        /** 参数里绝不能带分隔符，否则解码会把一条结论切成两条 */
        private fun String.clean(): String = replace(SEP, " ").take(200)
    }
}

/**
 * 与 [PriceDbOutcome.encode] 配对。读不懂就返回 null —— 这里必须宽容：
 * DataStore 里可能还躺着老版本写入的中文句子（如「已核对至 2026-11-16，油价无变化（搁浅）」），
 * 或者被清空过；解析失败只表现为「不显示上次结论」，绝不能因此抛异常。
 */
fun decodePriceDbOutcome(raw: String): PriceDbOutcome? {
    if (raw.isBlank()) return null
    val parts = raw.split('\u001F')
    val code = parts[0]
    fun str(i: Int): String = parts.getOrNull(i).orEmpty()
    fun int(i: Int): Int = parts.getOrNull(i)?.toIntOrNull() ?: 0
    return when (code) {
        "busy" -> PriceDbOutcome.Busy
        "auto_off" -> PriceDbOutcome.AutoUpdateOff
        "throttled" -> PriceDbOutcome.Throttled(int(1))
        "no_province" -> PriceDbOutcome.NoProvince
        "fetch_failed" -> PriceDbOutcome.FetchFailed(str(1))
        "source_lagging" -> PriceDbOutcome.SourceLagging(str(1), int(2))
        "window_day" -> PriceDbOutcome.WindowDay(str(1))
        "unchanged_unconfirmed" -> PriceDbOutcome.UnchangedUnconfirmed(str(1))
        "stranded" -> PriceDbOutcome.Stranded(str(1), int(2))
        "unchanged" -> PriceDbOutcome.Unchanged(str(1))
        "repaired_only" -> PriceDbOutcome.RepairedOnly(int(1))
        "already_adjusted" -> PriceDbOutcome.AlreadyAdjusted
        "updated" -> PriceDbOutcome.Updated(int(1), int(2))
        "no_new_records" -> PriceDbOutcome.NoNewRecords
        "failed" -> PriceDbOutcome.Failed(str(1))
        // 老版本写的中文句子 / 未知编码：不显示，不报错
        else -> null
    }
}
