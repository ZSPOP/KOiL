package com.llpop.koil.data.sync

import com.llpop.koil.data.local.entity.KOiLRecordEntity
import com.llpop.koil.domain.logic.DerivedCalculator
import com.llpop.koil.domain.logic.RecordValidator
import com.llpop.koil.util.AppClock
import com.llpop.koil.util.TimeFormats
import org.json.JSONArray
import org.json.JSONObject

/**
 * 云同步备份编解码（对齐 §7.10 手动备份 JSON 格式）：
 * - records：与 SettingsViewModel.exportBackup 同构（含派生字段，恢复后本地重算覆盖）
 * - records 含 isFullTank（满箱标记）；更早格式缺该字段按 true（历史口径=全部视为加满）
 * - meta：写入 appVersion/exportTime；不含 meta 的文件解析时自动忽略
 * - 同步范围：仅加油记录（设置不随云同步备份/恢复；云端文件里的 settings 块
 *   恢复时忽略，不写回本地）
 *
 * 解析兼容性：org.json 的 opt* 系列——未知字段忽略、缺失字段给默认值，不同格式版本均可解析。
 */
object BackupCodec {

    /**
     * 当前写入的备份格式版本。
     * v1：只有 records。v2：+ `deleted: [{key, at}]`（删除扩散）。
     * 升版本的理由不是「加了字段」，而是云同步的读路径要靠它判断「能不能无损往返」——
     * 本版读不出 deleted 的 v2 快照必须判读不懂，否则写回时会把删除静默抹掉。
     */
    const val BACKUP_VERSION = 2
    /** 手动备份（含 settings 块）的格式版本；文件结构含 settings 块，与云快照按结构分流 */
    const val MANUAL_BACKUP_VERSION = 1

    /**
     * 版本分流依据：显式写入的 version 必须能被读出来校验；缺 version 的旧格式按宽松口径解析。
     * 兼容集 1..3：更早的快照缺 isFullTank 字段，按缺省 true 解析，保证历史备份仍可恢复。
     */
    fun isKnownBackupVersion(v: Int): Boolean = v in 1..3

    /** 云端固定文件名（跨设备定位"最新备份"用；手动备份仍用时间戳文件名） */
    const val CLOUD_FILE_NAME = "koil_backup.json"

    /** 墓碑窗口：只写最近 180 天、最多 1000 条（坚果云单文件 500MB 够大，但 deleted 会无限增长） */
    private const val TOMBSTONE_WINDOW_MS = 180L * 24 * 60 * 60 * 1000
    private const val TOMBSTONE_MAX = 1000
    private const val ONE_DAY_MS = 24L * 60 * 60 * 1000

    /**
     * 云端删除标记。`at` 用于「同键多次删除取最晚」以及窗口裁剪。
     * 写入前必须过 [clampAt]：一台时间设到未来的设备否则会永久赢下所有合并。
     */
    data class DeletedEntry(val key: String, val at: Long)

    /** `at` 钳制：不得晚于 now+1d（容忍时钟小幅超前的设备，不给它永久优势） */
    fun clampAt(at: Long, now: Long = AppClock.millis()): Long =
        if (at > now + ONE_DAY_MS) now + ONE_DAY_MS else if (at < 0) now else at

    /**
     * 删除标记的裁剪口径：**入参已是去重后的 Map**（同键取 max 在 [parse] 的 deletedMap 里做）
     * → 逐条钳制 at → 去掉超窗口的 → 超量只留最近的 N 条。
     *
     * 写出去的 JSON 与本地缓存必须走**同一个**函数：只裁 JSON 不裁缓存，缓存里
     * 超窗口的键每次都被裁掉、下次又原样进来，永远循环、永远写不出去、永远占着。
     */
    fun pruneTombstones(
        deleted: Map<String, Long>,
        now: Long = AppClock.millis(),
    ): Map<String, Long> =
        deleted.entries
            .associate { it.key to clampAt(it.value, now) }
            .filterValues { it >= now - TOMBSTONE_WINDOW_MS }
            .entries
            .sortedByDescending { it.value }
            .take(TOMBSTONE_MAX)
            .associateTo(LinkedHashMap()) { it.key to it.value }

    /** 解析后的单条记录（与 KOiLRecordEntity 字段一一对应，id 不跨设备传递） */
    data class RecordData(
        val reoilDate: String,
        val mileage: Double,
        val oilAmount: Double,
        val payableAmount: Double,
        val actualAmount: Double,
        val oilPrice: Double? = null,
        val oilGrade: String? = null,
        val province: String? = null,
        val source: String? = null,
        /** 满箱标记：更早格式的备份无此字段，缺省 true */
        val isFullTank: Boolean = true,
    ) {
        fun toEntity(): KOiLRecordEntity = KOiLRecordEntity(
            reoilDate = reoilDate,
            mileage = mileage,
            oilAmount = oilAmount,
            payableAmount = payableAmount,
            actualAmount = actualAmount,
            oilPrice = oilPrice,
            oilGrade = oilGrade,
            province = province,
            source = source,
            isFullTank = isFullTank,
        )
    }

    // ===== 生成 =====

    /**
     * org.json 对 NaN/Infinity 会抛 JSONException，一条脏记录就能让云端推送永久失败。
     * 非有限值写 JSONObject.NULL，恢复端会按 RecordValidator 跳过该行，而不是让整包备份失败。
     */
    private fun JSONObject.putFinite(name: String, value: Double?): JSONObject {
        put(name, value?.takeIf { it.isFinite() } ?: JSONObject.NULL)
        return this
    }

    /**
     * 生成完整备份 JSON（records 按 reoilDate 升序，含派生字段 distance/oilDays/oilConsumption/costPerKm/isFullTank）。
     * 仅含加油记录，不含设置（云同步范围 = 记录数据）。
     * 派生口径统一走 [DerivedCalculator]（满箱区间口径，与总览页一致）；
     * distance/oilDays 仍为相邻段口径；派生依赖升序：函数内部强制排序，调用方无需自行保证（M-21）。
     */
    fun buildBackup(
        records: List<KOiLRecordEntity>,
        appVersion: String,
        deleted: List<DeletedEntry> = emptyList(),
    ): String {
        val now = AppClock.millis()
        // 按键去重取 max → 裁剪窗口 → 才能在 push 时「一次摘净」，也让文件不无限增长
        val deduped = pruneTombstones(deleted.associate { it.key to it.at }, now)
            .map { (key, at) -> DeletedEntry(key, at) }
        val deletedArr = JSONArray()
        deduped.forEach { deletedArr.put(JSONObject().put("key", it.key).put("at", it.at)) }
        val recordsArr = JSONArray()
        // 与总览/CSV 导出同一计算器：油耗/每公里成本只在满箱闭合区间产出（半箱为 null），
        // abnormalPct 取 0（仅影响状态判定，不影响派生数值本身）
        val derived = DerivedCalculator.compute(records, 0.0)
        for (d in derived) {
            val r = d.record
            val distance = d.distance
            recordsArr.put(
                JSONObject()
                    .put("date", r.reoilDate)
                    .putFinite("mileage", r.mileage)
                    .putFinite("oilAmount", r.oilAmount)
                    .putFinite("payableAmount", r.payableAmount)
                    .putFinite("actualAmount", r.actualAmount)
                    .putFinite("oilPrice", r.oilPrice)
                    .put("oilGrade", r.oilGrade ?: JSONObject.NULL)
                    .put("province", r.province ?: JSONObject.NULL)
                    .put("source", r.source ?: JSONObject.NULL)
                    .putFinite("distance", distance)
                    .put("oilDays", d.oilDays ?: JSONObject.NULL)
                    .putFinite("oilConsumption", d.oilConsumption)
                    .putFinite("costPerKm", d.costPerKm)
                    .put("isFullTank", r.isFullTank)
            )
        }

        val backup = JSONObject()
            .put("version", BACKUP_VERSION)
            .put("exportTime", TimeFormats.second())
            .put("recordCount", records.size)
            .put("records", recordsArr)
            .put("deleted", deletedArr)
            .put(
                "meta", JSONObject()
                    .put("appVersion", appVersion)
                    .put("app", "KOiL")
            )
        return backup.toString()
    }

    // ===== 解析 =====

    /**
     * Android org.json 的 optString() 对显式 JSONObject.NULL 返回字符串 "null"，
     * 必须先用 isNull() 区分“缺失/显式 null/真实字符串”。source 缺失时才使用 fallback。
     */
    private fun JSONObject.optNullableString(name: String, fallback: String? = null): String? {
        // Android org.json 的 isNull() 对“字段缺失”也返回 true，必须先用 has() 区分
        if (!has(name)) return fallback
        if (isNull(name)) return null
        // 被写成字符串 "null" 的备份也要归一（手拼 JSON 会产生这种值）
        return optString(name, fallback ?: "").takeIf { it.isNotEmpty() && it != "null" }
    }

    /**
     * 可空 Double 字段：键缺失/显式 null/非有限值一律返回 null。
     * 原先 oilPrice 直接用 isNull() 判空——行为碰巧正确（isNull 对缺失也返回 true），
     * 但与 optNullableString 的 has() 纪律不一致；统一走 helper，调用点只保留领域校验（> 0）。
     */
    private fun JSONObject.optNullableDouble(name: String): Double? {
        if (!has(name)) return null
        if (isNull(name)) return null
        return optDouble(name, Double.NaN).takeIf { it.isFinite() }
    }

    /** 解析结果：云端条数（含脏行）+ 通过校验的记录 + 备份 version（缺省/非法为 null）。一次解析产出，避免大 JSON 解析两遍。 */
    data class ParseResult(
        val total: Int,
        val records: List<RecordData>,
        val version: Int? = null,
        /** v1 快照读作空集；同键多条取最晚 at */
        val deleted: List<DeletedEntry> = emptyList(),
    )

    /** 解析备份 JSON 的 records 数组长度（云端条数，用于统计 errors = total - parsed） */
    fun countRecords(jsonText: String): Int {
        val root = runCatching { JSONObject(jsonText) }.getOrNull() ?: return 0
        return root.optJSONArray("records")?.length() ?: 0
    }

    /** 解析备份 JSON 的 records 数组；解析失败返回空列表（调用方按空库/格式错误处理） */
    fun parseRecords(jsonText: String): List<RecordData> = parse(jsonText).records

    /**
     * 单次解析：返回总条数与通过校验的记录。
     * 校验口径：日期必须是真实存在的 yyyy-MM-dd、里程/油量为正、金额非负且有限，
     * 并设合理上界——云端文件可能被手工改坏或由不同版本生成，脏值一旦入库会污染整条派生链
     * （油耗、每公里成本、异常判定全部基于相邻记录差值）。
     */
    /**
     * 结构自检：这段文本能不能被认作备份文件（不看记录是否有效）。
     *
     * 为什么必须单独有它：[parse] 是「永不抛异常」的宽松解析（坏 JSON / 少字段都退化成 0 条），
     * 但「0 条」和「读不懂」在同步里是两件相反的事——前者是空备份，可以覆盖；后者是别人的
     * 数据（错误页、截断的文件、别的格式），覆盖了就没了。把两者都当 0 条处理，就是
     * 「未知错误当成空集」。
     */
    fun looksLikeBackup(jsonText: String): Boolean {
        val root = runCatching { JSONObject(jsonText) }.getOrNull() ?: return false
        // 至少要有一个 records 数组：合法空备份也是 records: []，而不是连这个键都没有
        return root.has("records") && root.optJSONArray("records") != null
    }

    fun parse(jsonText: String): ParseResult {
        val root = runCatching { JSONObject(jsonText) }.getOrNull() ?: return ParseResult(0, emptyList())
        val arr = root.optJSONArray("records") ?: return ParseResult(0, emptyList())
        // version 写得进也要读得出（M-05）：暴露给调用方做格式分流；缺失或非法按 null（宽松口径）
        val version = root.takeIf { it.has("version") }?.optInt("version", -1)?.takeIf { it >= 0 }
        val out = ArrayList<RecordData>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val date = o.optString("date")
            val mileage = o.optDouble("mileage", Double.NaN)
            val oilAmount = o.optDouble("oilAmount", Double.NaN)
            val actual = o.optDouble("actualAmount", Double.NaN)
            val payable = o.optDouble("payableAmount", actual)
            if (!RecordValidator.isValidRecord(date, mileage, oilAmount, payable, actual)) continue
            out.add(
                RecordData(
                    reoilDate = date,
                    mileage = mileage,
                    oilAmount = oilAmount,
                    payableAmount = payable,
                    actualAmount = actual,
                    oilPrice = o.optNullableDouble("oilPrice")?.takeIf { it > 0 },
                    oilGrade = o.optNullableString("oilGrade"),
                    province = o.optNullableString("province"),
                    source = o.optNullableString("source", "cloud_sync"),
                    // 更早格式无此字段：缺省 true；显式 false/"否" 才视为未加满，非法值同样按缺省
                    isFullTank = if (o.has("isFullTank") && !o.isNull("isFullTank")) o.optBoolean("isFullTank", true) else true,
                )
            )
        }
        // 按键去重取 max：同键出现两次（at=100、at=200）解析后只留一条且 at=200，
        // 摘除时才能一次摘净，不会摘了一条又漏出一条
        val deletedMap = HashMap<String, Long>()
        root.optJSONArray("deleted")?.let { dArr ->
            for (i in 0 until dArr.length()) {
                val o = dArr.optJSONObject(i) ?: continue
                val key = o.optString("key").takeIf { it.isNotBlank() } ?: continue
                // 缺 at（手改过、恢复工具产出）以前会读成 0 → 下一秒被窗口裁掉 → 删除静默消失。
                // 缺省按 now：宁可用现在的时间扩散，也不能把这条删除丢掉。
                val rawAt = o.optLong("at", 0L)
                val at = clampAt(if (rawAt <= 0L) AppClock.millis() else rawAt)
                val prev = deletedMap[key]
                if (prev == null || at > prev) deletedMap[key] = at
            }
        }
        return ParseResult(arr.length(), out, version, deletedMap.map { DeletedEntry(it.key, it.value) })
    }

    // 校验口径统一由 domain.logic.RecordValidator 提供（CSV/手动备份/云备份三处共用）
}
