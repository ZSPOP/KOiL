package com.llpop.koil.data.local

import com.llpop.koil.data.local.dao.KOiLRecordDao
import com.llpop.koil.data.local.entity.KOiLRecordEntity
import com.llpop.koil.data.settings.AppSettings
import com.llpop.koil.data.sync.RecordMerger
import kotlinx.coroutines.flow.first

/**
 * **用户写入**的唯一入口（云同步写库不走这里，直接拿裸 DAO）。
 *
 * 分开的理由：墓碑/撤销这两套标记只该由「人」的动作触发。
 * - 用户新增/编辑命中墓碑 → 打撤销标记（撤销高于墓碑，「删错了重录」不能被当场删掉）；
 * - 用户删除 → 写墓碑 + 清该键的撤销标记；
 * - 同步插入 / 同步侧删除 → 一个标记都不许留（否则下次推送又把这些改动当成本机主张推上去）。
 *
 * 只接管写，不接管读：查询路径直连 DAO，多一层转发只会让调用点变多、没有收益。
 */
class RecordStore(
    private val dao: KOiLRecordDao,
    private val settings: AppSettings,
) {

    /** 用户新增一条。命中墓碑（删过又重录）时打撤销标记 */
    suspend fun insert(record: KOiLRecordEntity): Long {
        markRevivedIfTombstoned(listOf(record))
        return dao.insert(record)
    }

    /** 用户批量写入（CSV 导入、备份恢复）。同键重复按忽略处理 */
    suspend fun insertAll(entities: List<KOiLRecordEntity>): Int {
        if (entities.isEmpty()) return 0
        markRevivedIfTombstoned(entities)
        return dao.insertAllIgnore(entities).count { it != -1L }
    }

    /** 用户编辑。日期/里程可能被改，按「新键」判定撤销 */
    suspend fun update(record: KOiLRecordEntity) {
        markRevivedIfTombstoned(listOf(record))
        dao.update(record)
    }

    /** 用户删除。成功推送过云同步才留墓碑（否则无意义累积） */
    suspend fun delete(record: KOiLRecordEntity) {
        // 判定读 hasEverConfiguredSync 标记：「URL 非空 + 用户名非空」会被内置默认地址架空
        if (settings.syncEverConfigured.first()) {
            settings.markRecordDeleted(record.reoilDate, record.mileage)
        }
        dao.deleteById(record.id)
    }

    /**
     * 只对**命中墓碑**的记录打撤销标记。
     * 没删过的记录不该进这个集合——集合里每多一个键，推送时就多摘一条云端的删除，
     * 而那条删除是别的设备真实做过的动作。
     */
    private suspend fun markRevivedIfTombstoned(entities: List<KOiLRecordEntity>) {
        val tombstones = settings.syncDeletedKeys.first()
        if (tombstones.isEmpty()) return
        entities.forEach { r ->
            val key = RecordMerger.keyOf(r.reoilDate, r.mileage)
            if (key in tombstones) settings.markRecordRevived(key)
        }
    }
}
