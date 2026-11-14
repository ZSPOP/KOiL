package com.llpop.koil.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.llpop.koil.BuildConfig
import com.llpop.koil.R
import com.llpop.koil.data.settings.AppSettings
import com.llpop.koil.data.update.ApkUpdater
import com.llpop.koil.data.update.RefreshOutcome
import com.llpop.koil.data.update.UpdateChecker
import com.llpop.koil.data.update.UpdateDownloadState
import com.llpop.koil.data.update.UpdateFailure
import com.llpop.koil.data.update.blockedByWifiOnly
import com.llpop.koil.data.update.isCellularNetwork
import com.llpop.koil.data.update.isNewerRelease
import com.llpop.koil.data.update.needsCellularPrompt
import com.llpop.koil.data.update.normalizeVersion
import com.llpop.koil.data.update.releasePageUrl
import com.llpop.koil.data.update.shouldBadgeUpdate
import com.llpop.koil.ui.screen.CellularGate
import com.llpop.koil.ui.screen.openExternalUrl
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 检查更新的四个可见状态。Idle 时回落到「上次静默检查」的结论。 */
private sealed interface UpdateCheck {
    data object Idle : UpdateCheck
    data object Checking : UpdateCheck
    data object Latest : UpdateCheck
    data class Newer(val tag: String, val version: String, val url: String) : UpdateCheck
    data object Failed : UpdateCheck
}

@Composable
private fun checkedAgoText(at: Long): String? {
    if (at <= 0L) return null
    val diff = System.currentTimeMillis() - at
    if (diff < 0) return null
    return when {
        diff < 60_000L -> stringResource(R.string.koil_set_check_just_now)
        diff < 3_600_000L -> stringResource(R.string.koil_set_check_min_ago, (diff / 60_000L).toInt())
        diff < 86_400_000L -> stringResource(R.string.koil_set_check_hour_ago, (diff / 3_600_000L).toInt())
        else -> stringResource(R.string.koil_set_check_day_ago, (diff / 86_400_000L).toInt())
    }
}

/**
 * 检查更新 + 应用内下载，自成一段卡片。
 *
 * 状态与首页更新横幅同源（[UpdateChecker.updateTarget] 一次取出 tag 与直链），
 * 蜂窝门控与首页共用同一套策略（一次表态两处生效）。
 */
@Composable
fun KOiLUpdateSection(
    modifier: Modifier = Modifier,
    /** 传入页面级 backdrop 时卡片走背景采样毛玻璃，颜色随底层动态背景走。 */
    blurBackdrop: LayerBackdrop? = null,
    blurBlendColors: List<BlendColorEntry>? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 冷启动静默检查的结果落在 DataStore，进页面即可判红点（不用先点一次）
    val updateChecker = remember { GlobalContext.get().get<UpdateChecker>() }
    // 与首页同源：tag + 直链一次取出，不分别订阅（两帧之间会错位）
    val updateTarget by updateChecker.updateTarget().collectAsStateWithLifecycle(initialValue = null)
    val storedTag = updateTarget?.tag.orEmpty()
    // 用户已知晓的 tag：与 storedTag 不等才挂红点。点过一次同一版本就不再提醒，发新版时 tag 变化自动重新亮
    val seenTag by updateChecker.seenTag().collectAsStateWithLifecycle(initialValue = "")
    // 显示「上次检查」——静默检查没有可见反馈，用户会以为它从没跑过
    val lastCheckAt by updateChecker.lastCheckAt().collectAsStateWithLifecycle(initialValue = 0L)
    // 应用内更新：直链与 tag 同源（refresh 一次响应里取），这里只搬运
    val apkUpdater = remember { GlobalContext.get().get<ApkUpdater>() }
    val apkUrl = updateTarget?.apkUrl.orEmpty()
    val downloadState by apkUpdater.state.collectAsStateWithLifecycle()

    val checkedAgo = checkedAgoText(lastCheckAt)
    val storedVersion = remember(storedTag) { normalizeVersion(storedTag) }
    // 下载中显示百分比、失败显示原因；只有这块有内容时才覆盖「可更新 Vx.x.x」文案
    val downloadLabel = when (val st = downloadState) {
        is UpdateDownloadState.Downloading ->
            if (st.percent >= 0) {
                // 这一行的点击在下载中是「取消」，必须写出来：
                // 否则用户看到百分比想点进去看详情，一点下载就没了
                stringResource(R.string.koil_update_progress_tap_cancel, storedVersion, st.percent)
            } else {
                stringResource(R.string.koil_update_progress_unknown, storedVersion)
            }
        // 失败原因不放在 value：那是一整句话，会把左边标题列压到零宽
        // （实机表现为标题四字竖排）。原因走副标题，value 只留短动作词。
        else -> null
    }
    // 与红点判定共用同一个判据（isNewerRelease）：两处各算一遍的话，将来只改一处就会分叉
    // 成「首页横幅亮、红点不亮」这类最难排查的不一致
    val storedHasNewer = isNewerRelease(storedTag, BuildConfig.VERSION_NAME)
    var update by remember { mutableStateOf<UpdateCheck>(UpdateCheck.Idle) }

    // 蜂窝门控：与首页共用同一套策略（一次表态，两处都生效）
    val settings = remember { GlobalContext.get().get<AppSettings>() }
    val cellularDecided by settings.updateCellularDecided.collectAsStateWithLifecycle(initialValue = false)
    val wifiOnlyPref by settings.updateWifiOnly.collectAsStateWithLifecycle(initialValue = false)
    var cellularGate by remember { mutableStateOf<CellularGate?>(null) }
    var cellularPending by remember { mutableStateOf<Pair<String, String>?>(null) }

    fun applyNetworkGate(tag: String, url: String) {
        // 缺安装授权时不会下载，只拉起安装器 / 跳授权页 —— 别先弹蜂窝流量确认
        if ((downloadState as? UpdateDownloadState.Failed)?.reason == UpdateFailure.NO_PERMISSION &&
            // 同上：包已被系统清掉时不能短路，否则绕过蜂窝确认直接下几 MB
            apkUpdater.hasReady(tag)
        ) {
            apkUpdater.download(tag, url)
            return
        }
        val cellular = isCellularNetwork(context)
        cellularGate = when {
            needsCellularPrompt(cellular, cellularDecided) -> CellularGate.Ask
            blockedByWifiOnly(cellular, cellularDecided, wifiOnlyPref) -> CellularGate.Blocked
            else -> null.also { apkUpdater.download(tag, url) }
        }
        if (cellularGate != null) cellularPending = tag to url
    }

    val failReason = (downloadState as? UpdateDownloadState.Failed)?.reason
    val failTag = (update as? UpdateCheck.Newer)?.tag ?: storedTag
    val failActionLabel: String? = failReason?.let {
        stringResource(
            if (it == UpdateFailure.NO_PERMISSION && apkUpdater.hasReady(failTag)) {
                R.string.koil_update_action_grant
            } else {
                R.string.koil_update_action_retry
            }
        )
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(bottom = 12.dp)
            .then(
                if (blurBackdrop != null) {
                    Modifier.textureBlur(
                        backdrop = blurBackdrop,
                        shape = RoundedCornerShape(16.dp),
                        blurRadius = 60f,
                        colors = BlurColors(blendColors = blurBlendColors.orEmpty()),
                        enabled = true,
                    )
                } else Modifier
            ),
        // 毛玻璃生效时底色必须透明，否则 Card 自绘的实色会把采样结果整块盖掉
        colors = CardDefaults.defaultColors(
            if (blurBackdrop != null) Color.Transparent else MiuixTheme.colorScheme.surfaceContainer,
            Color.Transparent,
        ),
    ) {
        ArrowPreference(
            title = stringResource(R.string.koil_set_about_check_update),
            summary = failReason?.let { stringResource(it.messageRes()) }
                ?: checkedAgo?.let { stringResource(R.string.koil_set_about_checked_ago, it) }
                ?: stringResource(R.string.koil_set_about_check_update_sub),
            onClick = {
                val u = update
                when {
                    // 下载中再点这一行 = 取消，不是重下
                    downloadState is UpdateDownloadState.Downloading -> apkUpdater.cancel()
                    u is UpdateCheck.Newer -> {
                        // 应用内下载全程不写 seenTag（下载/安装失败、用户在系统确认页取消，红点与横幅都必须还在）。
                        // 只有回退浏览器这条路径会在成功打开版本页后标记已知晓 —— 那是用户主动表达知悉的动作。
                        // 用 Newer 自带的 tag，不读 storedTag：refresh() 刚返回时 DataStore 的 Flow
                        // 可能还没 emit 到 Compose，此时读 storedTag 会把上一个版本记成已读。
                        // 直链只认 Newer 自带的那一个：它与 tag 出自同一次 refresh()。
                        if (u.url.isNotBlank()) {
                            applyNetworkGate(u.tag, u.url)
                        } else if (openExternalUrl(context, releasePageUrl(u.tag))) {
                            updateChecker.markSeen(u.tag)
                        }
                    }
                    u == UpdateCheck.Checking -> Unit
                    // 冷启动静默检查早就发现了新版本：显示侧读 DataStore、点击侧读内存 update，
                    // 两者不同步会让第一次点击落进下面的「重新检查」分支空转一次请求（最长 6 秒），
                    // 用户看到的就是「点第一次没反应」。这里直接用已知 tag 跳转。
                    // 必须用原始 storedTag（V1.1.3），不能用 normalizeVersion 后的 storedVersion：
                    // releasePageUrl 拼的是 releases/tag/$tag，GitHub 上没有去掉 V 的 tag，会 404。
                    u == UpdateCheck.Idle && storedHasNewer -> {
                        if (apkUrl.isNotBlank()) {
                            apkUpdater.download(storedTag, apkUrl)
                        } else if (openExternalUrl(context, releasePageUrl(storedTag))) {
                            updateChecker.markSeen(storedTag)
                        }
                    }
                    else -> {
                        update = UpdateCheck.Checking
                        scope.launch {
                            // refresh 已经把「是否比本机新」算完了（判据只在 UpdateChecker 一处），
                            // 这里按三态分支即可
                            update = when (val outcome = updateChecker.refresh()) {
                                is RefreshOutcome.Newer -> UpdateCheck.Newer(
                                    outcome.target.tag,
                                    normalizeVersion(outcome.target.tag),
                                    outcome.target.apkUrl,
                                  )
                                RefreshOutcome.Latest -> UpdateCheck.Latest
                                RefreshOutcome.Failed -> UpdateCheck.Failed
                            }
                        }
                    }
                }
            },
            endActions = {
                // 红点：有新版本，且用户还没知晓过。判定抽到 shouldBadgeUpdate 以便单测
                if (shouldBadgeUpdate(storedTag, seenTag, BuildConfig.VERSION_NAME)) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFE53935)),
                    )
                    Spacer(Modifier.width(6.dp))
                }
                val value = downloadLabel ?: failActionLabel ?: when (val u = update) {
                    // Idle 时回落到"上次静默检查"的结论：冷启动查到新版本，这一行直接显示「发现新版本」
                    UpdateCheck.Idle ->
                        if (storedHasNewer) stringResource(R.string.koil_set_about_newer, storedVersion) else ""
                    UpdateCheck.Checking -> stringResource(R.string.koil_set_about_checking)
                    UpdateCheck.Latest -> stringResource(R.string.koil_set_about_latest)
                    is UpdateCheck.Newer -> stringResource(R.string.koil_set_about_newer, u.version)
                    UpdateCheck.Failed -> stringResource(R.string.koil_set_about_check_failed)
                }
                if (value.isNotEmpty()) {
                    Text(
                        text = value,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            },
        )
    }

    cellularGate?.let { gate ->
        val pending = cellularPending
        UpdateCellularDialog(
            blocked = gate == CellularGate.Blocked,
            onDownload = {
                cellularGate = null
                if (gate == CellularGate.Ask) {
                    scope.launch { settings.setUpdateCellularPolicy(wifiOnly = false) }
                }
                pending?.let { apkUpdater.download(it.first, it.second) }
                cellularPending = null
            },
            onWaitWifi = {
                cellularGate = null
                if (gate == CellularGate.Ask) {
                    scope.launch { settings.setUpdateCellularPolicy(wifiOnly = true) }
                }
                cellularPending = null
            },
            // 误触外部只关弹窗，不写偏好、不消费「只问一次」
            onDismiss = {
                cellularGate = null
                cellularPending = null
            },
        )
    }
}
