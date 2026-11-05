package com.llpop.koil.data.sync

import com.llpop.koil.data.local.entity.KOiLRecordEntity
import com.llpop.koil.domain.logic.DerivedCalculator
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

/**
 * 云同步备份编解码（对齐 §7.10 手动备份 JSON 格式，version 1→2 向后兼容）：
 * - records：与 SettingsViewModel.exportBackup 同构（含派生字段，恢复后本地重算覆盖）
 * - meta：version 2 新增（appVersion/exportTime），旧文件（version 1）解析时自动忽略
 * - 同步范围：仅加油记录（v1.5.44 起设置不再随云同步备份/恢复；旧文件里的 settings 块
 *   恢复时忽略，不写回本地）
 *
 * 解析兼容性：org.json 的 opt* 系列——未知字段忽略、缺失字段给默认值，新旧互读。
 */
object BackupCodec {

    const val BACKUP_VERSION = 2
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
        )
    }

    // ===== 生成 =====

    /**
     * 生成完整备份 JSON（records 按 reoilDate 升序，含派生字段 distance/oilDays/oilConsumption/costPerKm）。
     * 仅含加油记录，不含设置（云同步范围 = 记录数据）。
     */
    fun buildBackup(records: List<KOiLRecordEntity>, appVersion: String): String {
        val dateFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val recordsArr = JSONArray()
        var prev: KOiLRecordEntity? = null
        for (r in records) {
            val distance = prev?.let { r.mileage - it.mileage }
            val oilDays = prev?.let {
                val d1 = LocalDate.parse(r.reoilDate, dateFmt)
                val d0 = LocalDate.parse(it.reoilDate, dateFmt)
                java.time.temporal.ChronoUnit.DAYS.between(d0, d1)
            }
            // 派生口径与 §7.10 手动备份一致：仅里程段 ≥100km 时计算油耗/每公里成本
            // v1.6.2：阈值统一取 DerivedCalculator.MIN_DISTANCE（原硬编码 100，与总览口径可能漂移）
            val valid = distance != null && distance >= DerivedCalculator.MIN_DISTANCE
            val consumption = if (valid) Math.round(r.oilAmount / distance!! * 100 * 100) / 100.0 else null
            val costPerKm = if (valid) Math.round(r.actualAmount / distance!! * 100) / 100.0 else null

            recordsArr.put(
                JSONObject()
                    .put("date", r.reoilDate)
                    .put("mileage", r.mileage)
                    .put("oilAmount", r.oilAmount)
                    .put("payableAmount", r.payableAmount)
                    .put("actualAmount", r.actualAmount)
                    .put("oilPrice", r.oilPrice ?: JSONObject.NULL)
                    .put("oilGrade", r.oilGrade ?: JSONObject.NULL)
                    .put("province", r.province ?: JSONObject.NULL)
                    .put("source", r.source ?: JSONObject.NULL)
                    .put("distance", distance ?: JSONObject.NULL)
                    .put("oilDays", oilDays ?: JSONObject.NULL)
                    .put("oilConsumption", consumption ?: JSONObject.NULL)
                    .put("costPerKm", costPerKm ?: JSONObject.NULL)
            )
            prev = r
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

    /** 解析结果：云端条数（含脏行）+ 通过校验的记录。v1.6.2：一次解析产出两者，避免大 JSON 解析两遍。 */
    data class ParseResult(val total: Int, val records: List<RecordData>)

    /** 解析备份 JSON 的 records 数组长度（云端条数，用于统计 errors = total - parsed） */
    fun countRecords(jsonText: String): Int {
        val root = runCatching { JSONObject(jsonText) }.getOrNull() ?: return 0
        return root.optJSONArray("records")?.length() ?: 0
    }

    /** 解析备份 JSON 的 records 数组；解析失败返回空列表（调用方按空库/格式错误处理） */
    fun parseRecords(jsonText: String): List<RecordData> = parse(jsonText).records

    /**
     * 单次解析：返回总条数与通过校验的记录。
     * 校验口径（v1.6.2 加严）：日期必须是真实存在的 yyyy-MM-dd、里程/油量为正、金额非负且有限，
     * 并设合理上界——云端文件可能被手工改坏或来自旧版本，脏值一旦入库会污染整条派生链
     * （油耗、每公里成本、异常判定全部基于相邻记录差值）。
     */
    fun parse(jsonText: String): ParseResult {
        val root = runCatching { JSONObject(jsonText) }.getOrNull() ?: return ParseResult(0, emptyList())
        val arr = root.optJSONArray("records") ?: return ParseResult(0, emptyList())
        val out = ArrayList<RecordData>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val date = o.optString("date")
            if (!DATE_REGEX.matches(date) || !isRealDate(date)) continue
            val mileage = o.optDouble("mileage", Double.NaN)
            val oilAmount = o.optDouble("oilAmount", Double.NaN)
            val actual = o.optDouble("actualAmount", Double.NaN)
            val payable = o.optDouble("payableAmount", actual)
            if (!mileage.inRange(1.0, MAX_MILEAGE)) continue
            if (!oilAmount.inRange(0.01, MAX_OIL_AMOUNT)) continue
            if (!actual.inRange(0.0, MAX_MONEY)) continue
            if (!payable.inRange(0.0, MAX_MONEY)) continue
            out.add(
                RecordData(
                    reoilDate = date,
                    mileage = mileage,
                    oilAmount = oilAmount,
                    payableAmount = payable,
                    actualAmount = actual,
                    oilPrice = if (o.isNull("oilPrice")) null else o.optDouble("oilPrice", Double.NaN).takeIf { it.isFinite() && it > 0 },
                    oilGrade = o.optString("oilGrade").takeIf { it.isNotEmpty() },
                    province = o.optString("province").takeIf { it.isNotEmpty() },
                    source = o.optString("source", "cloud_sync").takeIf { it.isNotEmpty() },
                )
            )
        }
        return ParseResult(arr.length(), out)
    }

    private fun Double.inRange(min: Double, max: Double): Boolean = isFinite() && this >= min && this <= max

    private fun isRealDate(date: String): Boolean =
        runCatching { LocalDate.parse(date, DateTimeFormatter.ISO_LOCAL_DATE) }.isSuccess

    private val DATE_REGEX = Regex("""\d{4}-\d{2}-\d{2}""")

    // 合理上界：超出即视为脏数据（真实车辆里程 < 1000 万 km，单次加油 < 1000 L，单次金额 < 10 万元）
    private const val MAX_MILEAGE = 10_000_000.0
    private const val MAX_OIL_AMOUNT = 1_000.0
    private const val MAX_MONEY = 100_000.0
}
