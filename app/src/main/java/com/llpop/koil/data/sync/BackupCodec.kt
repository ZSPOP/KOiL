package com.llpop.koil.data.sync

import com.llpop.koil.data.local.entity.KOiLRecordEntity
import com.llpop.koil.domain.logic.DerivedCalculator
import com.llpop.koil.domain.logic.RecordValidator
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

    /** 当前写入的备份格式版本 */
    const val BACKUP_VERSION = 1
    /** 手动备份（含 settings 块）的格式版本；文件结构含 settings 块，与云快照按结构分流 */
    const val MANUAL_BACKUP_VERSION = 1

    /**
     * 版本分流依据：显式写入的 version 必须能被读出来校验；缺 version 的旧格式按宽松口径解析。
     * 兼容集 1..3：更早的快照缺 isFullTank 字段，按缺省 true 解析，保证历史备份仍可恢复。
     */
    fun isKnownBackupVersion(v: Int): Boolean = v in 1..3

    /** 云端固定文件名（跨设备定位"最新备份"用；手动备份仍用时间戳文件名） */
    const val CLOUD_FILE_NAME = "koil_backup.json"

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
    fun buildBackup(records: List<KOiLRecordEntity>, appVersion: String): String {
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
            .put("exportTime", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()))
            .put("recordCount", records.size)
            .put("records", recordsArr)
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
    data class ParseResult(val total: Int, val records: List<RecordData>, val version: Int? = null)

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
        return ParseResult(arr.length(), out, version)
    }

    // 校验口径统一由 domain.logic.RecordValidator 提供（CSV/手动备份/云备份三处共用）
}
