package com.llpop.koil.data.sync

import com.llpop.koil.data.local.entity.KOiLRecordEntity

/**
 * 云同步推送前的差集合并。
 *
 * 复合键 (reoilDate, mileage) 与 CSV 导入、恢复预览的口径一致。
 * [tombstones] 是本机主动删除、尚未推送到云端的记录键：合并时必须跳过，
 * 否则「A 设备删了记录，推送时被云端旧快照拉回来」，用户永远删不掉。
 * 云端快照内部重复的同键记录也只保留一条。
 */
object RecordMerger {

    fun keyOf(reoilDate: String, mileage: Double): String = "$reoilDate|$mileage"

    /** 该记录是否属于本机尚未推送成功的删除墓碑。恢复预览/提交都必须先过这一层。 */
    fun isTombstoned(reoilDate: String, mileage: Double, tombstones: Set<String>): Boolean =
        keyOf(reoilDate, mileage) in tombstones

    /** 过滤云端快照中的墓碑键；返回可参与恢复的记录。 */
    fun withoutTombstones(
        records: List<BackupCodec.RecordData>,
        tombstones: Set<String>,
    ): List<BackupCodec.RecordData> {
        if (records.isEmpty() || tombstones.isEmpty()) return records
        return records.filterNot { isTombstoned(it.reoilDate, it.mileage, tombstones) }
    }

    fun missing(
        cloud: List<BackupCodec.RecordData>,
        local: List<KOiLRecordEntity>,
        tombstones: Set<String> = emptySet(),
    ): List<KOiLRecordEntity> {
        if (cloud.isEmpty()) return emptyList()
        val known = HashSet<Pair<String, Double>>(local.size + cloud.size + tombstones.size)
        local.forEach { known.add(it.reoilDate to it.mileage) }
        return cloud.mapNotNull { r ->
            val key = r.reoilDate to r.mileage
            when {
                key in known -> null
                isTombstoned(r.reoilDate, r.mileage, tombstones) -> null
                else -> {
                    known.add(key)
                    r.toEntity()
                }
            }
        }
    }
}
