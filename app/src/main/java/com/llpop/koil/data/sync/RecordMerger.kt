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

    // ===== 删除扩散（快照 v2） =====

    /**
     * 合并三处删除来源：本地墓碑（无时间，按 [now] 计）、云端 `deleted`、本地缓存。
     * **同键取最晚 `at`** —— 早的那条已被晚的覆盖，留着只会在摘除时摘一条漏一条。
     */
    fun mergeTombstones(
        local: Set<String>,
        cloud: List<BackupCodec.DeletedEntry>,
        cache: Map<String, Long>,
        now: Long,
    ): Map<String, Long> {
        val out = HashMap<String, Long>(local.size + cloud.size + cache.size)
        cache.forEach { (key, at) -> out[key] = at }
        cloud.forEach { e -> out[e.key] = maxOf(out[e.key] ?: Long.MIN_VALUE, e.at) }
        local.forEach { key -> if (key !in out) out[key] = now }
        return out
    }

    /**
     * 有效墓碑 = (本地 ∪ 云端 ∪ 缓存) − 撤销。顺序固定：撤销永远高于墓碑。
     * 只有这一处过滤，读路径（合并/恢复预览）与写路径（推送）都从这里取，
     * 它们一旦各算各的，就会出现「拉的时候跳过了、推的时候又写回去」。
     */
    fun effectiveTombstones(merged: Map<String, Long>, revived: Set<String>): Set<String> =
        merged.keys - revived

    /** 云端 `deleted` 命中的键里，本地仍有记录的：数据赢过删除，登记为撤销（复活能再删，丢记录找不回） */
    fun dataWinsOverDeletion(merged: Map<String, Long>, localKeys: Set<String>, revived: Set<String>): Set<String> =
        merged.keys.filter { it in localKeys && it !in revived }.toSet()

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
