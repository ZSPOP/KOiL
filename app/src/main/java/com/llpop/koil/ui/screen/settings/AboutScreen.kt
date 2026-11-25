package com.llpop.koil.ui.screen.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.delay
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.llpop.koil.BuildConfig
import com.llpop.koil.R
import com.llpop.koil.ui.component.KOiLGlassDialog
import top.yukonga.miuix.kmp.glass.GlassDialogButton
import top.yukonga.miuix.kmp.glass.GlassDialogButtonRole
import com.llpop.koil.ui.component.blur.ColorBlendToken
import com.llpop.koil.ui.component.pageScrollModifiers
import com.llpop.koil.ui.component.LocalPageBackdrop
import com.llpop.koil.ui.component.blur.rememberBlurBackdrop
import com.llpop.koil.ui.component.blur.overScrollVertical
import com.llpop.koil.ui.component.AdaptiveTopAppBar
import com.llpop.koil.ui.component.BackNavigationIcon
import com.llpop.koil.ui.component.BlurredBar
import com.llpop.koil.ui.component.blur.rememberBlurEnabled
import com.llpop.koil.ui.component.liquid.rememberCombinedBackdrop
import com.llpop.koil.ui.component.effect.BgEffectBackground
import com.llpop.koil.ui.theme.LocalKOiLDarkTheme
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.compose.ui.platform.LocalContext
import com.llpop.koil.data.update.AboutCapsuleState
import com.llpop.koil.data.update.AboutUpdateStatus
import com.llpop.koil.data.update.ApkUpdater
import com.llpop.koil.data.update.RefreshOutcome
import com.llpop.koil.data.update.UpdateChecker
import com.llpop.koil.data.update.UpdateDownloadState
import com.llpop.koil.data.update.UpdateNotifier
import com.llpop.koil.data.update.UpdateTarget
import com.llpop.koil.data.update.resolveCapsuleState
import com.llpop.koil.data.update.showsReadySlot
import com.llpop.koil.data.update.formatReleaseDate
import com.llpop.koil.data.update.isNewerRelease
import com.llpop.koil.data.update.normalizeVersion
import com.llpop.koil.ui.component.KoilAboutInfoCard
import com.llpop.koil.ui.component.KoilAboutUpdateButton
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurBlendMode
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.material.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import com.llpop.koil.ui.theme.KOiLType
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import androidx.compose.ui.text.style.TextOverflow
import com.llpop.koil.ui.component.ACTION_WIDTH_FRACTION
import androidx.compose.animation.core.animateFloatAsState

/**
 * 关于页：结构 / 尺寸 / 字号 / 间距 / 动效。
 * 模糊与背景特效组件位于：
 *  - com.llpop.koil.ui.component.blur.{BlurExt, ColorBlendToken}
 *  - com.llpop.koil.ui.component.effect.{BgEffectBackground ...}
 * 等价件：LocalAppDarkMode → LocalKOiLDarkTheme；
 * horizontalCutoutPadding → Scaffold contentWindowInsets。
 */
@Composable
fun AboutScreen(
    onBack: () -> Unit = {},
) {
    // 二级页：侧滑 / 系统返回键回到设置页，而不是被 Activity 直接 finish
    val scrollBehavior = MiuixScrollBehavior()
    val lazyListState = rememberLazyListState()

    // 滚动进度是帧率级 State：读点限制在 topBar 与各 graphicsLayer，
    // 组合期以值读取会让整个 AboutContent 每帧重组
    val scrollProgressState = remember {
        derivedStateOf {
            when {
                lazyListState.firstVisibleItemIndex > 0 -> 1f

                else -> {
                    val spacer = lazyListState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == "logoSpacer" }
                    if (spacer != null && spacer.size > 0) {
                        (lazyListState.firstVisibleItemScrollOffset.toFloat() / spacer.size).coerceIn(0f, 1f)
                    } else {
                        0f
                    }
                }
            }
        }
    }
    // 布尔/夹紧后再 derive，等值去重让顶栏只在阈值区间内重组
    val heroCollapsed by remember { derivedStateOf { scrollProgressState.value == 1f } }
    val titleAlpha by remember {
        derivedStateOf { ((scrollProgressState.value - 0.35f) / 0.65f).coerceIn(0f, 1f) }
    }

    val backdrop = rememberBlurBackdrop()
    // 纹理模糊的开关仍然跟 hero 收起走；标题的渐进模糊由 AdaptiveTopAppBar 内部按模糊开关决定
    val blurActive = backdrop != null && heroCollapsed
    // 顶栏单独录制一层、正文一层；弹层采样两者合并，避免采到自己的宿主或被遮罩
    val barBackdrop = if (backdrop != null) rememberLayerBackdrop() else null
    val overlayBackdrop = if (backdrop != null && barBackdrop != null) {
        rememberCombinedBackdrop(backdrop, barBackdrop)
    } else {
        null
    }

    Scaffold(
        topBar = {
            Box(modifier = if (barBackdrop != null) Modifier.layerBackdrop(barBackdrop) else Modifier) {
                // 顶栏底材只承担"收起后才敷纹理"这一半职责，标题的渐进模糊始终由模糊开关控制
                BlurredBar(backdrop = backdrop, blurActive = blurActive) {
                    AdaptiveTopAppBar(
                        title = stringResource(R.string.koil_set_about),
                        // 二级页不折叠大标题，只做「渐显小标题 + 玻璃按钮」的过渡
                        expandable = false,
                        titleColor = colorScheme.onSurface,
                        titleAlpha = { titleAlpha },
                        defaultWindowInsetsPadding = false,
                        scrollBehavior = scrollBehavior,
                        navigationIcon = { BackNavigationIcon(onClick = onBack) },
                    )
                }
            }
        },
        contentWindowInsets = WindowInsets.systemBars
            .add(WindowInsets.displayCutout)
            .only(WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        Box(modifier = if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier) {
            AboutContent(
                innerPadding = innerPadding,
                scrollBehavior = scrollBehavior,
                lazyListState = lazyListState,
                scrollProgress = { scrollProgressState.value },
                overlayBackdrop = overlayBackdrop,
            )
        }
    }
}

@Composable
private fun AboutContent(
    innerPadding: PaddingValues,
    scrollBehavior: ScrollBehavior,
    lazyListState: LazyListState,
    scrollProgress: () -> Float,
    overlayBackdrop: Backdrop?,
) {
    val layoutDirection = LocalLayoutDirection.current
    val density = LocalDensity.current
    val uriHandler = LocalUriHandler.current

    // —— 更新状态：只在本次运行内有效（冷启动回到 Idle）——
    val updateChecker = remember { GlobalContext.get().get<UpdateChecker>() }
    val apkUpdater = remember { GlobalContext.get().get<ApkUpdater>() }
    val updateNotifier = remember { GlobalContext.get().get<UpdateNotifier>() }
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<AboutUpdateStatus>(AboutUpdateStatus.Idle) }
    // 「直链还没就绪、重查一次也没拿到」的下载侧失败：版本确实存在，胶囊不能消失
    var downloadFailed by remember { mutableStateOf(false) }
    // 请求在途：挡连点（进页自动查 + 用户立刻点「应用版本」行会变成两次真请求）
    var isChecking by remember { mutableStateOf(false) }
    val downloadState by apkUpdater.state.collectAsStateWithLifecycle()
    // 状态行这一格三态：
    //   下载中   → 细条按真实百分比走 + 下面一行「45% (3.4MB/6.4MB)」
    //   刚下完   → 条走满 + 「100% (6.4MB/6.4MB)」定格 1.5 秒（ApkUpdater 按 1% 步进发状态，
    //              最后一格常停在 99%，不补这一下就永远看不到「走完」）
    //   待安装   → 换成一句话「安装包已就绪 · 6.4MB」，不含进度条
    // 为什么待安装不留满格条：进度条表达的是「正在走的过程」，下好一周后冷启动还看到同一条满格条，
    // 读起来就是「卡在 100%」。稳态该用结论，不该用过程。
    var statusSlot by remember { mutableStateOf<StatusSlot?>(null) }
    val statusSlotAlpha = remember { Animatable(0f) }
    // 「当前新版」的 tag 必须进 key：Ready 由 ApkUpdater 在下载完成时发，Newer 由进页那次
    // check 回来才有，两条线不同步。只把 downloadState 当 key 时，冷启动/重进关于页会命中
    // 「Ready 先到、status 还是 Idle」——守卫判成不匹配直接淡出，之后 status 变成 Newer
    // 又不会重启这个 effect，「安装包已就绪」就再也不出现。
    val newerTag = (status as? AboutUpdateStatus.Newer)?.tag
    LaunchedEffect(downloadState, newerTag) {
        when (val d = downloadState) {
            is UpdateDownloadState.Downloading -> {
                statusSlot = StatusSlot.Progress(
                    DownloadSample(d.percent, d.received, d.total, finished = false)
                )
                statusSlotAlpha.animateTo(1f, tween(180))
            }
            is UpdateDownloadState.Ready -> {
                // Ready 只在「这份包就是当前新版」时才有意义：盘上留着旧版包、界面已经提示有新版本时，
                // 说「安装包已就绪」会让人以为能直接装（胶囊其实写着「有新版本」）
                if (!showsReadySlot(newerTag, apkUpdater.readyTagSnapshot)) {
                    statusSlotAlpha.animateTo(0f, tween(250))
                    statusSlot = null
                    return@LaunchedEffect
                }
                // 本次刚下完：先把条补满定格 1.5 秒，让「走完」这件事被看见
                (statusSlot as? StatusSlot.Progress)?.let {
                    statusSlot = StatusSlot.Progress(it.sample.copy(finished = true))
                    delay(1500)
                }
                statusSlot = StatusSlot.Ready(
                    sizeMb = apkUpdater.readyPackageBytes()?.let { "%.1f".format(java.util.Locale.ROOT, it / 1048576f) }
                )
                statusSlotAlpha.animateTo(1f, tween(200))
            }
            // 失败 / 取消 / 回 Idle：包不在盘上、或说的不是当前新版，才淡出。
            // 包还在（例如拉起安装器失败、或刚点安装去系统授权页）就保留结论——
            // 这条状态说的是磁盘上的事实，不该被一次流程中断擦掉。
            else -> if (showsReadySlot(newerTag, apkUpdater.readyTagSnapshot)) {
                statusSlot = StatusSlot.Ready(
                    sizeMb = apkUpdater.readyPackageBytes()
                        ?.let { "%.1f".format(java.util.Locale.ROOT, it / 1048576f) }
                )
                statusSlotAlpha.animateTo(1f, tween(200))
            } else if (statusSlot != null) {
                statusSlotAlpha.animateTo(0f, tween(250))
                statusSlot = null
            }
        }
    }
    val storedTarget by updateChecker.updateTarget().collectAsStateWithLifecycle(initialValue = null)

    // 安装授权：点「安装最新版」时未开「安装未知应用」→ 系统跳到授权页，
    // 用户授权完回到本页（ON_RESUME）就自动续上，不用再点一次
    val context = LocalContext.current
    LifecycleResumeEffect(Unit) {
        // 用户可能在文件管理器里把包删了、或系统清理了外部存储：先用磁盘事实校正一遍，
        // 否则页面会一直写着「安装包已就绪」，点「安装最新版」又毫无反应。
        apkUpdater.verifyReady()
        val tag = (status as? AboutUpdateStatus.Newer)?.tag
        // 点过安装、被「安装未知应用」授权页打断：拿到授权就自动续装，不用再点一次。
        // 不再依赖 Failed 状态——缺授权已经被判定为「流程中断」，不是下载/安装失败。
        if (tag != null &&
            apkUpdater.pendingPermission.value &&
            context.packageManager.canRequestPackageInstalls()
        ) {
            apkUpdater.installReady(tag)
        }
        onPauseOrDispose { }
    }

    fun applyOutcome(outcome: RefreshOutcome): AboutUpdateStatus = when (outcome) {
        is RefreshOutcome.Newer -> AboutUpdateStatus.Newer(
            outcome.target.tag,
            normalizeVersion(outcome.target.tag),
            outcome.target.apkUrl,
        )
        RefreshOutcome.Latest -> AboutUpdateStatus.Latest
        RefreshOutcome.Failed -> AboutUpdateStatus.Failed
    }

    /**
     * 手动检查与进页检查共用：真实请求一次，并把「检测到更新的版本」这件事
     * 顺手落到 ApkUpdater（旧包的清理在那里做，磁盘内容只由它管）。
     */
    suspend fun runCheck(): AboutUpdateStatus {
        val next = applyOutcome(updateChecker.refresh())
        // 手动检查与进页检查同样是「发现新版本」的出口：通知不能只挂在后台那条受 45 分钟
        // 节流约束的路上，否则会出现「胶囊/红点已经在说有新版，通知栏却一条都没有」。
        // 走同一个 notifyIfNeeded，自带 tag 去重与「系统通知」总开关判断，不会变成两条。
        if (next is AboutUpdateStatus.Newer) {
            runCatching { updateNotifier.notifyIfNeeded(UpdateTarget(next.tag, next.apkUrl)) }
                .onFailure { android.util.Log.w("KOiL", "notify on manual check failed: ${it.message}") }
        }
        // 这里**不再**丢掉旧包：删除的时机推到「新包真正落地」（ApkUpdater 用临时文件下载、
        // 校验通过才原子替换）。否则「下好 V1.2.4 没装 → 出了 V1.2.5 → 下载失败」会两空。
        return next
    }

    /**
     * 手动检查走 refresh()：它本身就是一次真实请求（45 分钟节流只在 checkIfDue 里）。
     *
     * [isChecking] 挡连点：进页自动查 + 用户立刻点「应用版本」行，会变成两次真请求；
     * 快速来回切页同理。旧版靠一个 Checking 状态挡住，这里保留同样的语义。
     */
    fun check() {
        if (isChecking) return
        scope.launch {
            isChecking = true
            downloadFailed = false
            try {
                status = runCheck()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                // 以前这里没有 try/finally：任意一次异常都会让 isChecking 永久停在 true，
                // 之后点 hero 区（检查）与进页自动检查都会在第一行静默 return，表现得「点了没反应」。
                android.util.Log.w("KOiL", "manual update check failed: ${e.message}")
                status = AboutUpdateStatus.Failed
            } finally {
                isChecking = false
            }
        }
    }

    // 进关于页自动查一次：这一页是唯一能发起检查的地方，进来就该给结论
    LaunchedEffect(Unit) {
        isChecking = true
        try {
            status = runCheck()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.w("KOiL", "auto update check failed: ${e.message}")
            status = AboutUpdateStatus.Failed
        } finally {
            isChecking = false
        }
    }

    // 冷启动静默检查已经发现的版本：先用库里的值把胶囊撑起来，不用等这次请求回来
    LaunchedEffect(storedTarget) {
        val target = storedTarget ?: return@LaunchedEffect
        if (status == AboutUpdateStatus.Idle && isNewerRelease(target.tag, BuildConfig.VERSION_NAME)) {
            status = AboutUpdateStatus.Newer(
                target.tag,
                normalizeVersion(target.tag),
                target.apkUrl,
            )
        }
    }

    /** 点胶囊：直链就绪就下；空直链（镜像窗口：附件还没传完）先就地重查一次，拿到才下 */
    fun downloadOrRecheck() {
        val target = (status as? AboutUpdateStatus.Newer) ?: return
        if (target.apkUrl.isNotBlank()) {
            downloadFailed = false
            apkUpdater.download(target.tag, target.apkUrl)
            return
        }
        scope.launch {
            val next = applyOutcome(updateChecker.refresh())
            status = next
            val target = next as? AboutUpdateStatus.Newer
            val url = target?.apkUrl.orEmpty()
            if (target != null && url.isNotBlank()) {
                downloadFailed = false
                apkUpdater.download(target.tag, url)
            } else {
                // 重查还是拿不到：这时候报「下载失败」才不算误导（网络没问题，只是镜像还没同步完）
                downloadFailed = true
            }
        }
    }

    // hero 区是「logo → 版本号 → 状态行 → 胶囊槽位」，都在 hero 内部、跟 hero 一起淡出。
    // 段内比例沿用参考图口径：状态区固定 26dp（6dp 间距 + 20dp 行高，有没有文案都占位，胶囊不跳）；
    // 状态区到胶囊槽位、槽位到卡片都取 50dp，槽位正好一个胶囊高（52dp）。
    // 下载进度显示在**状态行**里（替掉「正在检查… / 已是最新版本」那行），所以槽位不再为进度预留
    // 37dp —— 50 / 52 / 50 三段围着胶囊对称，下载前后 hero 高度都不变。
    val statusGapTop = 6.dp
    val statusAreaHeight = 26.dp
    // 状态区底到胶囊槽位顶：与「槽位底 → 信息卡」取同一个 50dp，胶囊整块上下等距。
    val statusToCapsule = 50.dp
    // 槽位就是一个胶囊的高度。下载进度已挪到状态行，槽位不再需要为它预留空间。
    val capsuleSlotHeight = 52.dp
    val heroToCard = 126.dp

    val backdrop = rememberLayerBackdrop()

    val isDark = LocalKOiLDarkTheme.current
    val blurEnabled by rememberBlurEnabled()
    val effectBackground = isRuntimeShaderSupported() && blurEnabled

    // 官方 AboutPage 是成套的另一组参数，故意不接入 KOiLGlassSpec：
    // 60f 半径 / RoundedCornerShape(16.dp) / Overlay_Thin_Light（暗）· Pured_Regular_Light（亮）
    // / brightness 0 · contrast 1 · saturation 1，与 example/shared/.../AboutPage.kt 逐项一致。
    // 接进全站 spec 会变成 BlurDemo 那套（半径减半、混色换档、叠加亮度对比度饱和度），
    // 等于把「官方关于页」改成「demo 面板」，因此这里保持原样。
    val cardBlendColors = remember(isDark) {
        if (isDark) ColorBlendToken.Overlay_Thin_Light
        else ColorBlendToken.Pured_Regular_Light
    }
    val logoBlend = remember(isDark) {
        if (isDark) {
            listOf(
                BlendColorEntry(Color(0xe6a1a1a1.toInt()), BlurBlendMode.ColorDodge),
                BlendColorEntry(Color(0x4de6e6e6), BlurBlendMode.LinearLight),
                BlendColorEntry(Color(0xff1af500.toInt()), BlurBlendMode.Lab),
            )
        } else {
            listOf(
                BlendColorEntry(Color(0xcc4a4a4a.toInt()), BlurBlendMode.ColorBurn),
                BlendColorEntry(Color(0xff4f4f4f.toInt()), BlurBlendMode.LinearLight),
                BlendColorEntry(Color(0xff1af200.toInt()), BlurBlendMode.Lab),
            )
        }
    }

    var logoHeightDp by remember { mutableStateOf(300.dp) }
    var sheet by remember { mutableStateOf("") }

    // 各段淡出进度在 graphicsLayer 内计算，读点全部落在绘制阶段
    val versionCodeProgress = { ((scrollProgress() - 0.05f) / 0.15f).coerceIn(0f, 1f) }
    val iconProgress = { ((scrollProgress() - 0.35f) / 0.15f).coerceIn(0f, 1f) }
    // 胶囊单独一条更早的曲线：它和信息卡之间只有 heroToCard 这一段间距。
    // 照 logo 那条 0.35→0.5 的曲线，卡片压上来时胶囊还完全不透明，就会变成
    // 「悬在卡片上的胶囊」；这条曲线在 p ≈ 0.11 处收干净，卡片碰到之前已经化掉。
    val capsuleProgress = { ((scrollProgress() - 0.02f) / 0.09f).coerceIn(0f, 1f) }

    // logo 下方：本机版本；有新版本时追加「→ 新版本」
    val newerVersion = (status as? AboutUpdateStatus.Newer)?.version
    val heroVersionLabel = if (newerVersion != null) {
        stringResource(R.string.koil_about_version_arrow, BuildConfig.VERSION_NAME, newerVersion)
    } else {
        stringResource(R.string.koil_about_version_plain, BuildConfig.VERSION_NAME)
    }
    // 状态文字三态：检查中即时出现、出结论后停 5 秒再淡出、发现新版本时让位给版本号箭头。
    // 「检查中」必须立刻显示：从进页到结果回来这段原本是空白，用户会以为点了没反应。
    val checkingLabel = stringResource(R.string.koil_about_status_checking)
    val latestLabel = stringResource(R.string.koil_about_status_latest)
    val failedLabel = stringResource(R.string.koil_about_status_failed)
    val pendingStatusLabel = when {
        isChecking -> checkingLabel
        status == AboutUpdateStatus.Latest -> latestLabel
        status == AboutUpdateStatus.Failed -> failedLabel
        else -> null
    }
    // 文案与 alpha 分开存：淡出期间文案仍在，靠 alpha 收掉，硬置 null 会闪一下
    var shownStatusLabel by remember { mutableStateOf<String?>(null) }
    val statusAlpha = remember { Animatable(0f) }
    LaunchedEffect(pendingStatusLabel, isChecking) {
        if (pendingStatusLabel == null) {
            statusAlpha.animateTo(0f, tween(200))
            shownStatusLabel = null
        } else {
            shownStatusLabel = pendingStatusLabel
            statusAlpha.animateTo(1f, tween(200))
            // 检查中不进入停留倒计时：它本身是个会被下一次结果替换的中间态
            if (!isChecking) {
                delay(5_000)
                statusAlpha.animateTo(0f, tween(400))
                shownStatusLabel = null
            }
        }
    }

    val scrollPadding = PaddingValues(
        top = innerPadding.calculateTopPadding(),
        start = innerPadding.calculateStartPadding(layoutDirection),
        end = innerPadding.calculateEndPadding(layoutDirection),
    )
    val logoPadding = PaddingValues(
        top = innerPadding.calculateTopPadding() + 40.dp,
        start = innerPadding.calculateStartPadding(layoutDirection),
        end = innerPadding.calculateEndPadding(layoutDirection),
    )

    // logo 在 progress 到 0.5 时完全透明（alpha = (progress - 0.35) / 0.15），此后 hero 不再接收点击：
    // 它压在上层，继续接收等于让一块「看不见的区域」吃掉卡片的点击。
    // 阈值必须跟着淡出完成点走，写 1f 会留出半屏幽灵可点区（#81 一轮评审指出的就是这一处）。
    // derivedStateOf 只在跨阈值时翻转，不会跟着每一帧滚动重组。
    val heroInteractive by remember { derivedStateOf { scrollProgress() < 0.5f } }

    // 胶囊淡完（p ≈ 0.11）就不再组合：ghost 按钮会让一块看不见的胶囊吃掉滑上来的卡片点击
    val capsuleVisible by remember { derivedStateOf { scrollProgress() < 0.11f } }

    // hero 占位高度：与列表里 logoSpacer 的 Spacer 高度同源，只算一次。
    // logoHeightDp 由 onSizeChanged 量的是 Column 全高，已含胶囊槽（52dp），这里不能再补一次。
    val heroSpacerHeight = logoHeightDp +
        logoPadding.calculateTopPadding() - scrollPadding.calculateTopPadding() + heroToCard

    // 列表尾部补量：滚到底时不再白给一整屏空白（原先的 fillParentMaxHeight 会撑满一屏）。
    // 注意补的是「Box(Spacer, Column)」的高度，Box 取子级最大而非相加：
    // 尾项内容高 A 小于补量时仍会差 A 才收满，故不能写成「必定收满」；
    // 当前尾项（开源/条款多张卡）远超半屏，手机上触发不了，大屏或大字号折叠时才可能遇到。
    var viewportHeightDp by remember { mutableStateOf(0.dp) }
    var infoItemHeightDp by remember { mutableStateOf(0.dp) }
    var aboutContentHeightDp by remember { mutableStateOf(0.dp) }
    // 未测到内容高度前先不补，避免首帧白占一整屏再回缩（首帧后补上，滚动停在顶部，用户看不到跳动）
    val tailFillHeight = if (infoItemHeightDp <= 0.dp || aboutContentHeightDp <= 0.dp) {
        0.dp
    } else {
        (viewportHeightDp - infoItemHeightDp - aboutContentHeightDp).coerceAtLeast(0.dp)
    }

    BgEffectBackground(
        dynamicBackground = effectBackground,
        modifier = Modifier.fillMaxSize(),
        bgModifier = Modifier.layerBackdrop(backdrop),
        isFullSize = true,
        effectBackground = effectBackground,
        alpha = { 1f - scrollProgress() },
    ) {
        LazyColumn(
            state = lazyListState,
            modifier = Modifier
                .fillMaxSize()
                // 视口高 = LazyColumn 的实际高度（Scaffold 内容区），尾部补量按它算
                .onSizeChanged { viewportHeightDp = with(density) { it.height.toDp() } }
                .pageScrollModifiers(
                    enableScrollEndHaptic = true,
                    showTopAppBar = true,
                    topAppBarScrollBehavior = scrollBehavior,
                ),
            contentPadding = PaddingValues(
                top = scrollPadding.calculateTopPadding(),
                start = scrollPadding.calculateStartPadding(layoutDirection),
                end = scrollPadding.calculateEndPadding(layoutDirection),
            ),
        ) {
            item(key = "logoSpacer") {
                // hero 的占位：hero 与列表是同一个 Box 的兄弟节点，两者必须严格重合，
                // 否则卡片会压到胶囊上、或者中间留一条缝。这里只做占位、不接点击（点击由 hero 自己接）。
                // 高度 = hero 内容高 + hero 顶部内边距 + 胶囊到卡片。两个易错点：
                // 1) onSizeChanged 挂在 padding 之内、量的是 Column 全部内容，所以 logoHeightDp 已经含
                //    logo + 版本号 + 状态区 + 状态区到胶囊 + 胶囊本身 —— 这里不能再补 statusToCapsule
                //    和胶囊高度，补一次就多出一整段空白（#81 一轮评审抓到的 114dp 错位就是这个）；
                // 2) hero 从页面顶端起算，而这一项从 contentPadding 之后起算，两者差 scrollPadding.top，必须抵掉。
                Spacer(Modifier.height(heroSpacerHeight))
            }


            item(key = "updateInfo") {
                Box(
                    modifier = Modifier
                        // onSizeChanged 放在 padding 外，量到的才是这一项的真实占位（含底距）
                        .onSizeChanged { infoItemHeightDp = with(density) { it.height.toDp() } }
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 12.dp),
                ) {
                    KoilAboutInfoCard(
                        versionName = BuildConfig.VERSION_NAME,
                        versionCode = BuildConfig.VERSION_CODE,
                        releaseDate = formatReleaseDate(BuildConfig.KOIL_RELEASE_DATE),
                        // 「应用版本」行点了就地检查，不跳转
                        onCheck = { check() },
                        blurBackdrop = if (blurEnabled) backdrop else null,
                        blurBlendColors = cardBlendColors,
                    )
                }
            }

            item(key = "about") {
                Box {
                    // 尾部补量：只补到「视口 + heroSpacer」所需的差值（≈ 屏高 − 信息卡 − 本项内容），
                    // 不再用 fillParentMaxHeight 白给一整屏。Box 高度取两者较大值，所以这里补的是
                    // 「内容不够一屏视口」的那一段：滚到底时不再空出一整屏（补量见 tailFillHeight 注释）。
                    Spacer(Modifier.height(tailFillHeight))
                    Column(
                        modifier = Modifier
                            // 量本项内容高（含 padding bottom 与 systemBars spacer），喂给补量公式
                            .onSizeChanged { aboutContentHeightDp = with(density) { it.height.toDp() } }
                            .padding(bottom = 12.dp),
                    ) {
                        SmallTitle(text = stringResource(R.string.koil_about_open_source))
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp)
                                .padding(bottom = 12.dp)
                                .then(
                                    if (blurEnabled) {
                                        Modifier.textureBlur(
                                            backdrop = backdrop,
                                            shape = RoundedCornerShape(16.dp),
                                            blurRadius = 60f,
                                            colors = BlurColors(blendColors = cardBlendColors),
                                            enabled = true,
                                        )
                                    } else Modifier
                                ),
                            // 关键：blur 生效时底色必须透明，否则 Card 自绘的实色会把采样结果整块盖掉。
                            // 内容色不能给透明：Card 会把 LocalContentColor 设成它，内部谁没显式指定字色就整块看不见
                            colors = CardDefaults.defaultColors(
                                if (blurEnabled) Color.Transparent else colorScheme.surfaceContainer,
                                colorScheme.onSurfaceContainer,
                            ),
                        ) {
                            val ossProjects = remember {
                                listOf(
                                    "KOiL" to "https://github.com/ZSPOP/KOiL",
                                    "Miuix" to "https://github.com/compose-miuix-ui/miuix",
                                    "AndroidX" to "https://github.com/androidx/androidx",
                                    "Kotlin" to "https://github.com/JetBrains/kotlin",
                                    "Ktor" to "https://github.com/ktorio/ktor",
                                    "Koin" to "https://github.com/InsertKoinIO/koin",
                                )
                            }
                            ossProjects.forEach { (name, url) ->
                                ArrowPreference(
                                    title = name,
                                    summary = url.removePrefix("https://"),
                                    onClick = { runCatching { uriHandler.openUri(url) } },
                                )
                            }
                            // 条款/隐私正文放这张卡尾部：与开源项目同属"资料与声明"性质，
                            // 不再单独占一张卡
                            ArrowPreference(
                                title = stringResource(R.string.koil_set_about_terms),
                                onClick = { sheet = "terms" },
                                holdDownState = sheet == "terms",
                            )
                            ArrowPreference(
                                title = stringResource(R.string.koil_set_about_privacy),
                                onClick = { sheet = "privacy" },
                                holdDownState = sheet == "privacy",
                            )
                        }

                        Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.systemBars))
                    }
                }
            }
        }
        if (sheet.isNotEmpty()) {
            // 关于页只有一个原生 Scaffold，没有页面级 backdrop 作用域，
            // 这里用本页录制的 backdrop 显式补上，弹窗才有玻璃
            CompositionLocalProvider(LocalPageBackdrop provides (overlayBackdrop ?: backdrop)) {
            val titleRes = if (sheet == "terms") R.string.koil_set_about_terms
            else R.string.koil_set_about_privacy
            val bodyRes = if (sheet == "terms") R.string.koil_set_about_terms_body
            else R.string.koil_set_about_privacy_body
            KOiLGlassDialog(
                show = true,
                onDismissRequest = { sheet = "" },
                contentPadding = 16.dp,
                scrollable = true,
                title = stringResource(titleRes),
            ) {
                Column {
                    Text(
                        text = stringResource(bodyRes),
                        fontSize = KOiLType.BodySmall,
                        lineHeight = 19.sp,
                        color = colorScheme.onBackgroundVariant,
                    )
                    Spacer(Modifier.height(14.dp))
                    GlassDialogButton(
                        onClick = { sheet = "" },
                        modifier = Modifier.fillMaxWidth(),
                        role = GlassDialogButtonRole.Normal,
                    ) {
                        Text(
                            text = stringResource(R.string.koil_common_close),
                            style = MiuixTheme.textStyles.button,
                        )
                    }
                }
            }
            }
        }
        // hero 放在列表之后：绘制与命中都在上层，胶囊才像真的嵌在 hero 里、点得到。
        // 滚动淡出到底后同时停止接收点击，卡片照常点、照常滚。
        AboutHero(
            logoPadding = logoPadding,
            layoutDirection = layoutDirection,
            density = density,
            blurEnabled = blurEnabled,
            backdrop = backdrop,
            logoBlend = logoBlend,
            iconProgress = iconProgress,
            capsuleProgress = capsuleProgress,
            capsuleVisible = capsuleVisible,
            versionCodeProgress = versionCodeProgress,
            versionLabel = heroVersionLabel,
            statusLabel = shownStatusLabel,
            statusAlpha = { statusAlpha.value },
            statusGapTop = statusGapTop,
            statusAreaHeight = statusAreaHeight,
            statusToCapsule = statusToCapsule,
            capsuleSlotHeight = capsuleSlotHeight,
            downloadSlot = statusSlot,
            downloadSlotAlpha = { statusSlotAlpha.value },
            // 与胶囊自身的渲染条件同源：非 Hidden 就等于「这一块会显示」。
            // 单写 status is Newer 靠的是「失败态下 status 不再是 Newer」这条跨文件不变量，改起来容易静默破坏。
            hasCapsule = resolveCapsuleState(
                status = status,
                downloadState = downloadState,
                readyTag = apkUpdater.readyTagSnapshot,
                extraFailed = downloadFailed,
            ) != AboutCapsuleState.Hidden,
            capsule = {
                KoilAboutUpdateButton(
                    status = status,
                    downloadState = downloadState,
                    // 用 ApkUpdater 的快照而不是从 state 推：失败态下 state 里没有 tag，
                    // 但盘上的包可能仍然可用（下完没装成）
                    readyTag = apkUpdater.readyTagSnapshot,
                    extraFailed = downloadFailed,
                    onDownload = { downloadOrRecheck() },
                    onCancel = { apkUpdater.cancel() },
                    onInstall = {
                        (status as? AboutUpdateStatus.Newer)?.let { apkUpdater.installReady(it.tag) }
                    },
                    onRetry = {
                        downloadFailed = false
                        downloadOrRecheck()
                    },
                    blurBackdrop = if (blurEnabled) backdrop else null,
                    blurBlendColors = cardBlendColors,
                )
            },
            interactive = heroInteractive,
            onClick = { check() },
            onMeasured = { logoHeightDp = it },
        )

    }
}

/** Hero：图标 100dp（原图 250dp）+ 名称 35sp Bold + 版本 14sp，随滚动淡出 */
@Composable
private fun BoxScope.AboutHero(
    logoPadding: PaddingValues,
    layoutDirection: androidx.compose.ui.unit.LayoutDirection,
    density: androidx.compose.ui.unit.Density,
    blurEnabled: Boolean,
    backdrop: LayerBackdrop?,
    logoBlend: List<BlendColorEntry>,
    iconProgress: () -> Float,
    /** 胶囊单独一条更早的淡出曲线：卡片碰上来之前必须已经化掉 */
    capsuleProgress: () -> Float,
    /** 胶囊是否仍然组合：淡完之后不再组合，避免留下看不见却能点的幽灵按钮 */
    capsuleVisible: Boolean,
    versionCodeProgress: () -> Float,
    /** hero 整体向上位移的像素（与列表滚动同速）：胶囊因此不会停在原地被卡片穿过 */
    /** logo 下方的版本号文案：`1.1.17`，有新版本时是 `1.1.17 → 1.1.18` */
    versionLabel: String,
    /** 版本号下方的状态文案（正在检查… / 已是最新版本 / 检查失败）：无文案时留空位，胶囊不上下跳 */
    statusLabel: String?,
    /** 状态文案的淡入淡出进度（0f 隐藏 / 1f 全显）：出结论 5 秒后由调用方收掉 */
    statusAlpha: () -> Float,
    /** 状态区距版本号 / 状态区固定高度 / 状态区到胶囊顶 */
    statusGapTop: Dp,
    statusAreaHeight: Dp,
    statusToCapsule: Dp,
    /** 胶囊槽位高度：正好一个胶囊高（52dp），恒定 */
    capsuleSlotHeight: Dp,
    /** 状态行里的下载进度 / 待安装文案：null = 不显示 */
    downloadSlot: StatusSlot?,
    /** 上面那块的 alpha：出现时淡入，取消/失败时淡出 */
    downloadSlotAlpha: () -> Float,
    /** 胶囊与下载进度条：属于 hero 内容，跟着 hero 一起淡出、不上滑 */
    capsule: @Composable () -> Unit,
    /**
     * 当前是否真有胶囊：与 [resolveCapsuleState] 同源（非 Hidden 即有）。
     * 它只决定「要不要组合胶囊」；槽位恒为 [capsuleSlotHeight]（52dp）、与它无关——
     * hero 总高固定，信息卡不因版本状态上下跳。
     */
    hasCapsule: Boolean,
    /**
     * 这一整块是否接收点击。列表滚动后 hero 已经淡出，这时它仍压在上层，
     * 若继续接收点击会把卡片与滚动一起挡掉。
     */
    interactive: Boolean,
    /** 点 hero 区（胶囊之外的地方）= 检查更新；胶囊自己有点击，内层优先 */
    onClick: () -> Unit,
    onMeasured: (androidx.compose.ui.unit.Dp) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (interactive) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        // indication = null：这块面积很大，默认的水波是一块亮白矩形，太抢眼
                        indication = null,
                        onClick = onClick,
                    )
                } else Modifier,
            )
            .padding(
                top = logoPadding.calculateTopPadding() + 52.dp,
                start = logoPadding.calculateStartPadding(layoutDirection),
                end = logoPadding.calculateEndPadding(layoutDirection),
            )
            .onSizeChanged { size ->
                with(density) { onMeasured(size.height.toDp()) }
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painter = painterResource(R.drawable.koil_logo),
            contentDescription = null,
            colorFilter = if (blurEnabled && backdrop != null) null
            else ColorFilter.tint(colorScheme.onBackground),
            modifier = Modifier
                .padding(top = 8.dp, bottom = 12.dp)
                .width(200.dp)
                .height(68.dp)
                .graphicsLayer {
                    val p = iconProgress()
                    alpha = 1 - p
                    scaleX = 1 - (p * 0.05f)
                    scaleY = 1 - (p * 0.05f)
                }
                .then(
                    if (blurEnabled && backdrop != null) {
                        Modifier.textureBlur(
                            backdrop = backdrop,
                            shape = RectangleShape,
                            blurRadius = 150f,
                            colors = BlurColors(blendColors = logoBlend),
                            contentBlendMode = BlendMode.DstIn,
                            enabled = true,
                        )
                    } else Modifier
                ),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    val p = versionCodeProgress()
                    alpha = 1 - p
                    scaleX = 1 - (p * 0.05f)
                    scaleY = 1 - (p * 0.05f)
                },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                color = colorScheme.onSurfaceVariantSummary,
                text = versionLabel,
                fontSize = KOiLType.BodyLarge,
                textAlign = TextAlign.Center,
            )
            // 状态区高度固定：三种文案（正在检查… / 已是最新版本 / 检查失败）与无文案时都占同一份高度，
            // 结论换来换去胶囊不会上下跳。文案本身靠 alpha 淡入淡出，不走「有就撑开、没有就塌陷」。
            // 下载进度占用同一格：下载时这行换成长条 + 一行百分比，高度仍封在 26dp 里。
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    // 状态行按最小高度占位：三种文案在 1.0 倍下等高、胶囊不跳；
                    // 字号放大后允许撑高（hero 高度是量出来的，列表占位会跟着重算）
                    .heightIn(min = statusAreaHeight),
                contentAlignment = Alignment.Center,
            ) {
                val slot = downloadSlot
                if (slot is StatusSlot.Progress) {
                    DownloadProgressLine(
                        progress = slot.sample,
                        alpha = downloadSlotAlpha,
                        // 状态区 26dp 里给文案留的 6dp 上间距，进度块只用 2dp：
                        // 4dp 条 + 3dp 间隔 + 一行 11sp 文本 ≈ 23dp，加起来仍 ≤ 26dp，hero 不长高
                        modifier = Modifier.padding(top = 2.dp),
                    )
                } else if (slot is StatusSlot.Ready) {
                    // 待安装：只有一个结论，没有条。与「已是最新版本」用同一档字号，换词不换版式
                    Text(
                        color = colorScheme.onSurfaceVariantSummary,
                        text = slot.sizeMb?.let {
                            stringResource(R.string.koil_about_package_ready, it)
                        } ?: stringResource(R.string.koil_about_package_ready_plain),
                        fontSize = KOiLType.BodyLarge,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .padding(top = statusGapTop)
                            .graphicsLayer { alpha = downloadSlotAlpha() },
                    )
                } else if (statusLabel != null) {
                    Text(
                        color = colorScheme.onSurfaceVariantSummary,
                        text = statusLabel,
                        fontSize = KOiLType.BodyLarge,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .padding(top = statusGapTop)
                            .graphicsLayer { alpha = statusAlpha() },
                    )
                }
            }
        }
        // 状态区到胶囊顶：胶囊属于 hero，只淡出不位移
        Spacer(Modifier.height(statusToCapsule))
        // 槽位恒为一个胶囊高（52dp），内容在槽位里居中。无论有没有新版、是否正在下载、是否滚动，
        // 槽位都是同一个高度——hero 总高不跳，信息卡不上下移动。
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(capsuleSlotHeight)
                // 胶囊只淡出、不位移：曲线比 logo 更早（卡片碰上来之前就已化掉），
                // 否则它就是一粒悬在信息卡上方的胶囊。
                .graphicsLayer { alpha = 1f - capsuleProgress() },
            contentAlignment = Alignment.Center,
        ) {
            if (hasCapsule && interactive && capsuleVisible) {
                capsule()
            } else {
                // interactive == false 时不组合胶囊：alpha 已经到 0，留着会变成看不见却能点的幽灵按钮，
                // 挡住滑上来的卡片。
                // 这里的 Spacer 不决定槽位高度（高度由外层 height(capsuleSlotHeight) 定死），
                // 只是标记「这里原本要放胶囊」，同时让居中逻辑在空档期也有内容可居中。
                Spacer(Modifier.height(52.dp))
            }
        }
    }
}

/**
 * 状态行里那格下载进度的一次采样。
 *
 * - [percent] < 0：服务端没给 `Content-Length`，分母未知 → 条走不确定态动画、文字只报已下载量
 * - [finished]：下载已结束、这块正在「定格 100%」收尾（此时条一定填满）
 */
/** 状态行这一格要显示什么：下载中（含刚下完的那一下）/ 待安装结论 / 不显示（null） */
private sealed interface StatusSlot {
    data class Progress(val sample: DownloadSample) : StatusSlot
    data class Ready(val sizeMb: String?) : StatusSlot
}

private data class DownloadSample(
    val percent: Int,
    val received: Long,
    val total: Long,
    val finished: Boolean,
) {
    val fraction: Float? get() = if (percent >= 0) percent / 100f else null
    fun receivedMb(): String = "%.1f".format(java.util.Locale.ROOT, received / 1048576f)
    fun totalMb(): String = "%.1f".format(java.util.Locale.ROOT, total / 1048576f)
}

/**
 * 状态行里的下载进度：上面一条细进度条（与胶囊同宽），下面一行百分比。
 *
 * 宽度直接复用 [ACTION_WIDTH_FRACTION]：和下方胶囊同一个比例，视觉上是一条竖直对齐的轴；
 * 两处各写一个 0.63f 迟早会漂。文字单独占一行（不并排）——并排时条只能缩到很窄，进度感就没了。
 */
@Composable
private fun DownloadProgressLine(
    progress: DownloadSample,
    alpha: () -> Float,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { this.alpha = alpha() },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 目标值：正常进度用它、收尾定格给 1f；拿不到总大小（fraction == null）时不画确定进度，
        // 交给组件跑不确定态动画
        val target = if (progress.finished) 1f else progress.fraction
        val animated by animateFloatAsState(
            targetValue = target ?: 0f,
            animationSpec = tween(220),
            label = "downloadFraction",
        )
        LinearProgressIndicator(
            progress = if (target == null) null else animated,
            height = 4.dp,
            modifier = Modifier.fillMaxWidth(ACTION_WIDTH_FRACTION),
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = if (progress.finished) {
                // 收尾：有总大小就报满格；没有总大小（percent < 0）就维持「已下载 x MB」
                if (progress.percent >= 0) stringResource(
                    R.string.koil_about_percent_with_size,
                    100,
                    "%.1f".format(java.util.Locale.ROOT, progress.total / 1048576f),
                    "%.1f".format(java.util.Locale.ROOT, progress.total / 1048576f),
                ) else stringResource(R.string.koil_about_downloaded, progress.receivedMb())
            } else if (progress.percent >= 0) stringResource(
                R.string.koil_about_percent_with_size,
                progress.percent,
                progress.receivedMb(),
                progress.totalMb(),
            ) else stringResource(R.string.koil_about_downloaded, progress.receivedMb()),
            fontSize = KOiLType.CaptionLarge,
            color = colorScheme.onSurfaceVariantSummary,
            textAlign = TextAlign.Center,
            maxLines = 1,
            // 大字号下这一行放不下时省略号收尾，别把字形硬切一半（也保证不因换行撑高状态区）
            overflow = TextOverflow.Ellipsis,
        )
    }
}
