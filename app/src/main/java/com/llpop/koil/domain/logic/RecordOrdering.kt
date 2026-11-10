package com.llpop.koil.domain.logic

import com.llpop.koil.data.local.entity.KOiLRecordEntity

/**
 * 加油记录的**唯一时间序口径**：(reoilDate, id) 升序。
 *
 * 为什么必须带 id：DAO 的 `observeAll()` 是 `ORDER BY reoilDate DESC, id DESC`，
 * 直接 `sortedBy { reoilDate }` 是稳定排序，同日多条会保持 id **降序**；
 * 而 `DerivedCalculator` 用列表相邻前一条算里程段，顺序反了会把正常的同日两次加油
 * 算成负里程（假「里程表回拨」）并丢掉后录入那条记录的油耗。
 * 同一天多次加油、补录、CSV/备份导入同日多条都会命中。
 *
 * 所有「按时间顺序读记录」的地方（总览派生、CSV 导出、手动备份、云同步推送）
 * 都必须走这里，不要再各自写 sortedBy。
 */
fun Iterable<KOiLRecordEntity>.inChronologicalOrder(): List<KOiLRecordEntity> =
    sortedWith(compareBy<KOiLRecordEntity> { it.reoilDate }.thenBy { it.id })
