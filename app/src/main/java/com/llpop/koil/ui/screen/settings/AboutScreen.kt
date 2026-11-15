@file:OptIn(top.yukonga.miuix.kmp.interfaces.ExperimentalScrollBarApi::class)

package com.llpop.koil.ui.screen.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import top.yukonga.miuix.kmp.basic.rememberScrollBarAdapter
import top.yukonga.miuix.kmp.basic.VerticalScrollBar
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
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
import com.llpop.koil.ui.component.BlurredBar
import com.llpop.koil.ui.component.KOiLGlassDialog
import top.yukonga.miuix.kmp.basic.Button
import com.llpop.koil.ui.component.blur.ColorBlendToken
import com.llpop.koil.ui.component.blur.rememberBlurBackdrop
import com.llpop.koil.ui.component.blur.rememberBlurEnabled
import com.llpop.koil.ui.component.effect.BgEffectBackground
import com.llpop.koil.ui.theme.LocalKOiLDarkTheme
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.compose.ui.platform.LocalContext
import com.llpop.koil.data.update.AboutUpdateStatus
import com.llpop.koil.data.update.ApkUpdater
import com.llpop.koil.data.update.RefreshOutcome
import com.llpop.koil.data.update.UpdateChecker
import com.llpop.koil.data.update.UpdateDownloadState
import com.llpop.koil.data.update.UpdateFailure
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
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

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
    BackHandler(onBack = onBack)
    val scrollBehavior = MiuixScrollBehavior()
    val lazyListState = rememberLazyListState()

    // 滚动进度是帧率级 State：读点限制在 topBar 与各 graphicsLayer，
    // 组合期以值读取会让整个 AboutContent 每帧重组
    val scrollProgressState = remember {
        derivedStateOf {
            when {
                lazyListState.firstVisibleItemIndex > 0 -> 1f

                // 滚到底：底部不再留一整屏兜底，靠这条把 hero 收完
                // （viewportSize 为 0 说明还没测量，不能当"到底"，否则首帧会闪一下已收起状态）
                lazyListState.layoutInfo.viewportSize.height > 0 &&
                    !lazyListState.canScrollForward -> 1f

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
    val blurActive = backdrop != null && heroCollapsed
    val barColor = if (heroCollapsed && !blurActive) colorScheme.surface else Color.Transparent

    Scaffold(
        topBar = {
            BlurredBar(backdrop = backdrop, blurActive = blurActive) {
                SmallTopAppBar(
                    title = stringResource(R.string.koil_set_about),
                    scrollBehavior = scrollBehavior,
                    color = barColor,
                    titleColor = colorScheme.onSurface.copy(alpha = titleAlpha),
                    defaultWindowInsetsPadding = false,
                    navigationIcon = {
                        val layoutDirection = LocalLayoutDirection.current
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = MiuixIcons.Back,
                                contentDescription = stringResource(R.string.koil_back),
                                tint = colorScheme.onSurface,
                                modifier = Modifier.graphicsLayer {
                                    // RTL 语言下整组镜像，箭头指向阅读方向的反向
                                    scaleX = if (layoutDirection == LayoutDirection.Rtl) -1f else 1f
                                },
                            )
                        }
                    },
                )
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
) {
    val layoutDirection = LocalLayoutDirection.current
    val density = LocalDensity.current
    val uriHandler = LocalUriHandler.current

    // —— 更新状态：只在本次运行内有效（冷启动回到 Idle）——
    val updateChecker = remember { GlobalContext.get().get<UpdateChecker>() }
    val apkUpdater = remember { GlobalContext.get().get<ApkUpdater>() }
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<AboutUpdateStatus>(AboutUpdateStatus.Idle) }
    // 「直链还没就绪、重查一次也没拿到」的下载侧失败：版本确实存在，胶囊不能消失
    var downloadFailed by remember { mutableStateOf(false) }
    // 请求在途：挡连点（进页自动查 + 用户立刻点「应用版本」行会变成两次真请求）
    var isChecking by remember { mutableStateOf(false) }
    val downloadState by apkUpdater.state.collectAsStateWithLifecycle()
    val storedTarget by updateChecker.updateTarget().collectAsStateWithLifecycle(initialValue = null)

    // 安装授权：点「安装最新版」时未开「安装未知应用」→ 系统跳到授权页，
    // 用户授权完回到本页（ON_RESUME）就自动续上，不用再点一次
    val context = LocalContext.current
    LifecycleResumeEffect(Unit) {
        val st = apkUpdater.state.value
        val tag = (status as? AboutUpdateStatus.Newer)?.tag
        if (st is UpdateDownloadState.Failed &&
            st.reason == UpdateFailure.NO_PERMISSION &&
            tag != null &&
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
        (next as? AboutUpdateStatus.Newer)?.let { apkUpdater.discardReadyExcept(it.tag) }
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
            status = runCheck()
            isChecking = false
        }
    }

    // 进关于页自动查一次：这一页是唯一能发起检查的地方，进来就该给结论
    LaunchedEffect(Unit) {
        isChecking = true
        status = runCheck()
        isChecking = false
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

    // hero 区改成「logo → 版本号 → 状态文案 → 胶囊」一整块，胶囊不再挂在下方列表里。
    // 段内比例沿用参考图口径：版本号底边到卡片顶边原为 102dp + 胶囊 52dp + 36dp。
    // 状态区固定 26dp（6dp 间距 + 20dp 行高：有没有文案都占位，胶囊不会上下跳），
    // 状态区到胶囊取 62dp（比原 76dp 少 14dp，胶囊随之上移 14dp）；
    // hero 底边到卡片补到 50dp（36 + 14）把上移量还回去 —— 卡片位置一丝不动。
    val statusGapTop = 6.dp
    val statusAreaHeight = 26.dp
    val statusToCapsule = 62.dp
    val heroToCard = 50.dp

    val backdrop = rememberLayerBackdrop()

    val isDark = LocalKOiLDarkTheme.current
    val blurEnabled by rememberBlurEnabled()
    val effectBackground = isRuntimeShaderSupported() && blurEnabled

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

    BgEffectBackground(
        dynamicBackground = effectBackground,
        modifier = Modifier.fillMaxSize(),
        bgModifier = Modifier.layerBackdrop(backdrop),
        isFullSize = true,
        effectBackground = effectBackground,
        alpha = { 1f - scrollProgress() },
    ) {
        AboutHero(
            logoPadding = logoPadding,
            layoutDirection = layoutDirection,
            density = density,
            blurEnabled = blurEnabled,
            backdrop = backdrop,
            logoBlend = logoBlend,
            iconProgress = iconProgress,
            versionCodeProgress = versionCodeProgress,
            versionLabel = heroVersionLabel,
            statusLabel = shownStatusLabel,
            statusAlpha = { statusAlpha.value },
            statusGapTop = statusGapTop,
            statusAreaHeight = statusAreaHeight,
            statusToCapsule = statusToCapsule,
            // 胶囊与进度条现在属于 hero：只在检测到新版本时出现，随 hero 一起淡出
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
            // 整块可点：这是关于页的第二个检查入口（第一个是「应用版本」行）
            onClick = { check() },
            onMeasured = { logoHeightDp = it },
        )

        LazyColumn(
            state = lazyListState,
            modifier = Modifier
                .fillMaxSize()
                .scrollEndHaptic()
                .overScrollVertical()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(
                top = scrollPadding.calculateTopPadding(),
                start = scrollPadding.calculateStartPadding(layoutDirection),
                end = scrollPadding.calculateEndPadding(layoutDirection),
            ),
        ) {
            item(key = "logoSpacer") {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(
                            // hero 现在自带胶囊，实测高度已经包含它。这里有三个易错点：
                            // 1) onSizeChanged 挂在 padding 之内，量到的是内容高、不含 padding，所以要补回
                            //    logoPadding.top + 52dp 才是 hero 底边的绝对位置；
                            // 2) hero 从页面顶端起算，而这一项从 LazyColumn 的 contentPadding 之后起算，
                            //    两者差 scrollPadding.top，必须抵掉；
                            // 3) 最后补的 heroToCard 就是「hero 底边（= 胶囊底）到卡片顶」的距离。
                            logoHeightDp + 52.dp + logoPadding.calculateTopPadding() -
                                scrollPadding.calculateTopPadding() + heroToCard,
                        ),
                    contentAlignment = Alignment.TopCenter,
                    content = { },
                )
            }

            item(key = "updateInfo") {
                Box(modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp)) {
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
                    Column(
                        modifier = Modifier.padding(bottom = 12.dp),
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
                            )
                            ArrowPreference(
                                title = stringResource(R.string.koil_set_about_privacy),
                                onClick = { sheet = "privacy" },
                            )
                        }

                        Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.systemBars))
                    }
                }
            }
        }
        if (sheet.isNotEmpty()) {
            val titleRes = if (sheet == "terms") R.string.koil_set_about_terms
            else R.string.koil_set_about_privacy
            val bodyRes = if (sheet == "terms") R.string.koil_set_about_terms_body
            else R.string.koil_set_about_privacy_body
            KOiLGlassDialog(
                show = true,
                onDismissRequest = { sheet = "" },
                contentPadding = 16.dp,
                scrollable = true,
                widthFraction = 0.90f,
            ) {
                Column {
                    Text(
                        text = stringResource(titleRes),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onBackground,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = stringResource(bodyRes),
                        fontSize = 12.sp,
                        lineHeight = 19.sp,
                        color = colorScheme.onBackgroundVariant,
                    )
                    Spacer(Modifier.height(14.dp))
                    Button(onClick = { sheet = "" }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.koil_common_close), fontSize = 13.sp)
                    }
                }
            }
        }
        VerticalScrollBar(
            adapter = rememberScrollBarAdapter(lazyListState),
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
            trackPadding = scrollPadding,
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
    backdrop: top.yukonga.miuix.kmp.blur.LayerBackdrop?,
    logoBlend: List<BlendColorEntry>,
    iconProgress: () -> Float,
    versionCodeProgress: () -> Float,
    /** logo 下方的版本号文案：`1.1.17`，有新版本时是 `1.1.17 → 1.1.18` */
    versionLabel: String,
    /** 版本号下方的状态文案（正在检查… / 已是最新版本 / 检查失败）：无文案时留空位，胶囊不上下跳 */
    statusLabel: String?,
    /** 状态文案的淡入淡出进度（0f 隐藏 / 1f 全显）：出结论 5 秒后由调用方收掉 */
    statusAlpha: () -> Float,
    /** 状态区距版本号 / 状态区固定高度 / 状态区到胶囊 */
    statusGapTop: Dp,
    statusAreaHeight: Dp,
    statusToCapsule: Dp,
    /** 胶囊与下载进度条：属于 hero 内容，随 hero 一起淡出 */
    capsule: @Composable () -> Unit,
    /** 整块点击 = 触发一次检查更新 */
    onClick: () -> Unit,
    onMeasured: (androidx.compose.ui.unit.Dp) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
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
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
            )
            // 状态区高度固定：三种文案（正在检查… / 已是最新版本 / 检查失败）与无文案时都占同一份高度，
            // 结论换来换去胶囊不会上下跳。文案本身靠 alpha 淡入淡出，不走「有就撑开、没有就塌陷」。
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(statusAreaHeight)
                    .padding(top = statusGapTop),
                contentAlignment = Alignment.Center,
            ) {
                if (statusLabel != null) {
                    Text(
                        color = colorScheme.onSurfaceVariantSummary,
                        text = statusLabel,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.graphicsLayer { alpha = statusAlpha() },
                    )
                }
            }
        }
        // 状态区到胶囊的距离：胶囊已经不再靠下方列表的留白顶开，由这一处直接给
        Spacer(Modifier.height(statusToCapsule))
        Box(
            modifier = Modifier.graphicsLayer {
                // 跟 logo 同一套淡出：滚动时整块一起收，不让胶囊孤零零留在原位被卡片盖过
                val p = iconProgress()
                alpha = 1 - p
                scaleX = 1 - (p * 0.05f)
                scaleY = 1 - (p * 0.05f)
            },
        ) {
            capsule()
        }
    }
}
