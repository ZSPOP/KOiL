package com.llpop.koil.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.llpop.koil.R
import com.llpop.koil.data.update.AboutCapsuleState
import com.llpop.koil.data.update.AboutUpdateStatus
import com.llpop.koil.data.update.UpdateDownloadState
import com.llpop.koil.data.update.resolveCapsuleState
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 胶囊宽度占屏宽的比例（图二实测 63%，用比例而不是写死 dp：小屏不溢出、大屏不变形） */
private const val ACTION_WIDTH_FRACTION = 0.63f

/** 胶囊高度 52dp，圆角取它的一半 → 左右两端是完整半圆（纯胶囊形） */
private val ACTION_HEIGHT = 52.dp
private val ACTION_CORNER = ACTION_HEIGHT / 2

/** 进度条粗细：比分割线（1dp）厚，比文字笔画细，"看得见但不抢戏" */
private val PROGRESS_HEIGHT = 3.dp

/**
 * 关于页的胶囊按钮 + 下载进度条。
 *
 * 五形态 / 六条触发路径（全部由外部状态推导，组件本身不持有业务状态）：
 * - 没有比本机新的版本 → 不显示（[status] 不是 Newer 时直接 return）
 * - 有新版未下载 → 「有新版本」，点击开始下载
 * - 下载中 → 写「取消下载」+ 进度条 + 百分比，点下去即取消（服务端不支持 Range，取消=重下）
 * - 下载完成（[readyTag] 与当前 tag 相同）→ 「安装最新版」，点击拉起安装器
 * - 已下载但包被删 / 又出新版 → [readyTag] 对不上，退回「有新版本」
 * - 下载失败 → 「下载失败」，点击重试
 */
@Composable
fun KoilAboutUpdateButton(
    status: AboutUpdateStatus,
    downloadState: UpdateDownloadState,
    /** 已落地的包对应的 tag（[com.llpop.koil.data.update.ApkUpdater.readyTag] 的快照） */
    readyTag: String?,
    /**
     * 额外视为「下载失败」。用于「直链还没就绪 → 就地重查一次 → 仍拿不到」这条路径：
     * 那是下载侧的失败，但 [status] 仍是 Newer（版本确实存在），不能因此把胶囊整个藏掉。
     */
    extraFailed: Boolean,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onInstall: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    /** 与下方信息卡同一套毛玻璃：不传则退回实心 surfaceContainer */
    blurBackdrop: LayerBackdrop? = null,
    blurBlendColors: List<BlendColorEntry>? = null,
) {
    val capsule = resolveCapsuleState(status, downloadState, readyTag, extraFailed)
    if (capsule == AboutCapsuleState.Hidden) return
    val downloading = downloadState as? UpdateDownloadState.Downloading

    val label = when (capsule) {
        AboutCapsuleState.Failed -> stringResource(R.string.koil_about_action_failed)
        AboutCapsuleState.Install -> stringResource(R.string.koil_about_action_install)
        // 下载中写「取消下载」：这一格是可点的，文案必须说清点下去会发生什么。
        // 不写「下载中」是因为进度条与百分比已经说了这件事，重复一次只占位置。
        AboutCapsuleState.Downloading -> stringResource(R.string.koil_about_action_cancel_download)
        else -> stringResource(R.string.koil_about_action_newer)
    }
    val onClick = when (capsule) {
        AboutCapsuleState.Downloading -> onCancel
        AboutCapsuleState.Failed -> onRetry
        AboutCapsuleState.Install -> onInstall
        else -> onDownload
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Button(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth(ACTION_WIDTH_FRACTION)
                .then(
                    if (blurBackdrop != null) {
                        // 和信息卡同一套采样：形状跟随胶囊圆角，底色必须透明，
                        // 否则 Button 自绘的实色会把采样结果整块盖掉（信息卡踩过同一个坑）
                        Modifier.textureBlur(
                            backdrop = blurBackdrop,
                            shape = RoundedCornerShape(ACTION_CORNER),
                            blurRadius = 60f,
                            colors = BlurColors(blendColors = blurBlendColors.orEmpty()),
                            enabled = true,
                        )
                    } else Modifier
                ),
            cornerRadius = ACTION_CORNER,
            minHeight = ACTION_HEIGHT,
            colors = ButtonDefaults.buttonColors(
                color = if (blurBackdrop != null) Color.Transparent
                else MiuixTheme.colorScheme.surfaceContainer,
                contentColor = MiuixTheme.colorScheme.onSurfaceContainer,
            ),
        ) {
            Text(
                text = label,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
            )
        }
        if (downloading != null) {
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                // percent < 0 = 服务端没给 Content-Length，交给组件跑不确定态动画
                progress = if (downloading.percent >= 0) downloading.percent / 100f else null,
                height = PROGRESS_HEIGHT,
                modifier = Modifier.fillMaxWidth(ACTION_WIDTH_FRACTION),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = if (downloading.percent >= 0) {
                    // 「45% (3.4MB/6.4MB)」：括号里只放数字与单位，帮用户判断这包到底多大；
                    // 拿不到总大小时（percent < 0）不加括号，否则分母是假的
                    stringResource(
                        R.string.koil_about_percent_with_size,
                        downloading.percent,
                        "%.1f".format(java.util.Locale.ROOT, downloading.received / 1048576f),
                        "%.1f".format(java.util.Locale.ROOT, downloading.total / 1048576f),
                    )
                } else {
                    // 拿不到总大小：只报已下载量，不画百分比（分子有了、分母没有，百分比是假的）
                    stringResource(
                        R.string.koil_about_downloaded,
                        // Locale.ROOT：format 默认跟随系统语言，德语/法语会把小数点写成逗号，
                        // 拼进「已下载 %1$s MB」就变成「已下载 3,4 MB」
                        "%.1f".format(java.util.Locale.ROOT, downloading.received / 1048576f),
                    )
                },
                fontSize = 12.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
    }
}

/**
 * 关于页的两行信息卡：「应用版本」「发布日期」。
 *
 * 都是 `showArrow = false`：这两行不是"跳出去"的入口，
 * 右箭头会让人以为点了要打开别处（「应用版本」点了只是就地检查更新）。
 */
@Composable
fun KoilAboutInfoCard(
    versionName: String,
    versionCode: Int,
    releaseDate: String,
    onCheck: () -> Unit,
    modifier: Modifier = Modifier,
    blurBackdrop: LayerBackdrop? = null,
    blurBlendColors: List<BlendColorEntry>? = null,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
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
            MiuixTheme.colorScheme.onSurfaceContainer,
        ),
    ) {
        // 用 BasicComponent 而不是 ArrowPreference：后者把右箭头画死在 endActions 里，
        // 关不掉；这两行不是「跳出去」的入口（「应用版本」点了只是就地检查），不能带箭头。
        BasicComponent(
            title = stringResource(R.string.koil_about_app_version),
            endActions = {
                Text(
                    text = stringResource(
                        R.string.koil_set_about_version,
                        versionName,
                        versionCode,
                    ),
                    fontSize = 14.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            },
            onClick = onCheck,
        )
        BasicComponent(
            title = stringResource(R.string.koil_about_release_date),
            endActions = {
                Text(
                    text = releaseDate,
                    fontSize = 14.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            },
            // 纯展示行：不传 onClick（源码签名是 click 可空的 `(() -> Unit)? = null`），
            // 而不是塞一个空 lambda —— 后者会让 TalkBack 把它读成一个点了没反应的按钮
        )
    }
}
