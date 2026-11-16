package com.llpop.koil.data.sync

import com.llpop.koil.data.local.entity.KOiLRecordEntity

/**
 * 首次配对对账：**只算，不写**。
 *
 * 非空库第一次打开同步时，旧实现直接静默推上去——用户既不知道发生了什么，
 * 也不知道云端那些本机没有的记录会被覆盖掉。这里把两边的差集算清楚，交给用户选。
 *
 * 四个数（面板直接展示）：
 * - `both`   两边都有同键记录
 * - `cloudOnly` 云端独有（本机没有）——**必须减墓碑**，否则「合并」就成了删除复活的另一个入口
 * - `localOnly` 本机独有（云端没有）
 * - `conflict` 同键但内容不同（⊆ both）
 * 另加 `tombstoneSkipped`：云端有、但本机已删（墓碑命中）的键。
 *
 * 为什么必须多这一类：K 减掉墓碑之后，`both + cloudOnly + localOnly` 不可能等于键并集
 * （被墓碑滤掉的键既不属于任一方）。把跳过数单独列出来，等式 `both+K+T+S = 并集` 才成立，
 * 面板也能对用户说清「有 N 条云端记录因为你本机删过而不会被拉回来」。
 */
object SyncReconcile {

    /** 用户在面板上的三选一 */
    enum class Mode { MERGE, LOCAL_WINS, CLOUD_WINS }

    data class Stats(
        val localCount: Int,
        val cloudCount: Int,
        val bothCount: Int,
        val cloudOnlyCount: Int,
        val localOnlyCount: Int,
        val conflictCount: Int,
        val tombstoneSkippedCount: Int,
        val unionCount: Int,
    ) {
        /** 分区自检：四类 + 墓碑跳过 = 键并集。这条等式就是「没有键被算两遍或漏掉」的证明 */
        val partitioned: Boolean
            get() = bothCount + cloudOnlyCount + localOnlyCount + tombstoneSkippedCount == unionCount

        /** 两边完全一致：不需要做任何合并动作 */
        val identical: Boolean
            get() = cloudOnlyCount == 0 && localOnlyCount == 0 && conflictCount == 0

        /** 合并会不会动数据（只增不减：只插云端独有） */
        val mergeInserts: Int get() = cloudOnlyCount
    }

    fun compute(
        local: List<KOiLRecordEntity>,
        cloud: List<BackupCodec.RecordData>,
        tombstones: Set<String>,
    ): Stats {
        // 同键多条：只认第一条（建表有 reoilDate+mileage 唯一索引，正常情况下不会重复）
        val localByKey = LinkedHashMap<String, KOiLRecordEntity>(local.size)
        local.forEach { localByKey.putIfAbsent(RecordMerger.keyOf(it.reoilDate, it.mileage), it) }
        val cloudByKey = LinkedHashMap<String, BackupCodec.RecordData>(cloud.size)
        cloud.forEach {
            val key = RecordMerger.keyOf(it.reoilDate, it.mileage)
            cloudByKey.putIfAbsent(key, it)
        }

        val bothKeys = localByKey.keys.intersect(cloudByKey.keys)
        val localOnlyKeys = localByKey.keys - cloudByKey.keys
        val cloudOnlyRaw = cloudByKey.keys - localByKey.keys
        // 墓碑优先于「云端独有」：本机删掉、还没推上去的键，不能被算成待拉回的记录
        val skipped = cloudOnlyRaw.filter { it in tombstones }.toSet()
        val cloudOnlyKeys = cloudOnlyRaw - skipped

        val conflicts = bothKeys.count { key ->
            val l = localByKey.getValue(key)
            val c = cloudByKey.getValue(key)
            !sameContent(l, c)
        }

        return Stats(
            localCount = localByKey.size,
            cloudCount = cloudByKey.size,
            bothCount = bothKeys.size,
            cloudOnlyCount = cloudOnlyKeys.size,
            localOnlyCount = localOnlyKeys.size,
            conflictCount = conflicts,
            tombstoneSkippedCount = skipped.size,
            unionCount = (localByKey.keys + cloudByKey.keys).size,
        )
    }

    /**
     * 同键（日期+里程）后的内容比较。
     * 只比落库的 9 个基础字段：云端快照里的 distance/oilConsumption 等派生字段按本机重算，
     * 拿来比会把「派生精度差异」误报成冲突。
     */
    fun sameContent(local: KOiLRecordEntity, cloud: BackupCodec.RecordData): Boolean =
        local.mileage == cloud.mileage &&
            local.oilAmount == cloud.oilAmount &&
            local.payableAmount == cloud.payableAmount &&
            local.actualAmount == cloud.actualAmount &&
            local.oilPrice == cloud.oilPrice &&
            local.oilGrade == cloud.oilGrade &&
            local.province == cloud.province &&
            local.source == cloud.source &&
            local.isFullTank == cloud.isFullTank
}
