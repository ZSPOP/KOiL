package com.llpop.koil.data.update

/**
 * 关于页胶囊按钮的五种形态（对应六条触发路径：`Newer` 一条承担「未下载 / 包被删 / 又出新版」）。
 *
 * 抽成纯枚举而不是留在 composable 里，是因为这段 `when` 决定了「点一下会发生什么」：
 * 判断错一格就是「明明下好了却重新下 6.7MB」或「包没了还写着安装最新版」。
 * 放在这里才能被单元测试逐格钉住。
 */
enum class AboutCapsuleState {
    /** 没有比本机新的版本：整块不显示 */
    Hidden,

    /** 有新版、尚未下好：点一下开始下载 */
    Newer,

    /** 有新版、正在下载：文案是「取消下载」+ 百分比，点一下取消（服务端不支持 Range，取消即重下） */
    Downloading,

    /** 有新版、包已落地且 tag 对得上：点一下拉起安装器 */
    Install,

    /** 下载/重查失败：点一下重试 */
    Failed,
}

/**
 * 由「检查结论 + 下载状态 + 已落地的包」推导胶囊形态。
 *
 * 判定顺序有讲究：
 * 1. **先判失败，但「包还在」时让位给安装**：失败是终止态，可它不只代表下载出错 ——
 *    「已下好却没装成」（用户从安装器返回没点安装、首次安装被未知来源拦下）也落在这里。
 *    此时 `readyTag` 仍等于当前 tag，包是完好的，应显示「安装最新版」而**不是**「下载失败」，
 *    否则用户以为要重下 6.6MB，点下去却直接进了安装器。
 * 2. `extraFailed` 优先于一切：那是「直链还没就绪、重查仍拿不到」的下载侧失败，
 *    与包在不在无关。它与 `Downloading` 理论上互斥，但调用方是两次独立状态更新，
 *    不能靠互斥假设兜底。
 * 3. 再判下载中。
 * 4. 再判包是否真的可用 —— 只看 `readyTag` 与当前 tag 是否相等，**不看** state 是否 `Ready`：
 *    进程重启后 state 回到 Idle，但包还在，此时仍应显示「安装最新版」。
 *
 * @param readyTag 已落地的包对应的 tag（`ApkUpdater.readyTag` 的快照），可能为 null
 * @param extraFailed 「直链还没就绪 → 就地重查 → 仍拿不到」这类下载侧失败
 */
fun resolveCapsuleState(
    status: AboutUpdateStatus,
    downloadState: UpdateDownloadState,
    readyTag: String?,
    extraFailed: Boolean = false,
): AboutCapsuleState {
    val newer = status as? AboutUpdateStatus.Newer
    // 没有新版也不在失败态 → 整块不显示（失败态要显示「下载失败」，不能藏）
    if (newer == null && !extraFailed) return AboutCapsuleState.Hidden

    if (extraFailed) return AboutCapsuleState.Failed
    // 失败态要先让位给「包其实还在」：下载完自动拉起安装器、用户没装就返回，
    // 或首次安装被「未知来源」拦下，都会停在这个失败态上；此时盘上的包是完好的，
    // 再报「下载失败」等于告诉用户要重下 6.7MB，而点下去其实直接进安装器。
    if (downloadState is UpdateDownloadState.Failed) {
        if (newer != null && readyTag == newer.tag) return AboutCapsuleState.Install
        return AboutCapsuleState.Failed
    }
    if (downloadState is UpdateDownloadState.Downloading) return AboutCapsuleState.Downloading

    // readyTag 对不上当前 tag = 包被系统清掉，或又出了更新的版本 → 退回「有新版本」
    if (newer != null && readyTag == newer.tag) return AboutCapsuleState.Install
    return AboutCapsuleState.Newer
}
