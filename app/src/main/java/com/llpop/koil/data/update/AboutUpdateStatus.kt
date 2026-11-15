package com.llpop.koil.data.update

/**
 * 关于页的更新状态。
 *
 * 只在**本次运行内**有效（冷启动回到 [Idle]）——「已是最新版本 / 检查失败」这类反馈
 * 是「刚才那一次手动检查」的结论，不该跨启动挂着。
 *
 * 与 [RefreshOutcome] 的区别：那个是数据层口径（只有 Newer/Latest/Failed），
 * 这里多一个 [Idle] 表示「本次启动还没查过」，UI 据此决定不显示任何状态文字。
 */
sealed interface AboutUpdateStatus {
    /** 本次运行还没检查过：胶囊可能已由冷启动静默检查的结论撑起，但状态文字不显示 */
    data object Idle : AboutUpdateStatus

    /** 查过确认已是最新 */
    data object Latest : AboutUpdateStatus

    /** 查过但请求失败（离线 / 限流 / 超时） */
    data object Failed : AboutUpdateStatus

    /** 有比本机新的版本。[apkUrl] 可能为空（镜像窗口：附件还没传完） */
    data class Newer(
        val tag: String,
        val version: String,
        val apkUrl: String,
    ) : AboutUpdateStatus
}
