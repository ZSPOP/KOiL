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

    /** 手动检查走 refresh()：它本身就是一次真实请求（45 分钟节流只在 checkIfDue 里） */
    fun check() {
        scope.launch {
            downloadFailed = false
            status = runCheck()
        }
    }

    // 进关于页自动查一次：这一页是唯一能发起检查的地方，进来就该给结论
    LaunchedEffect(Unit) { status = runCheck() }

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
    // 状态文字只在「本次运行查过」时出现：Idle（还没查 / 冷启动）什么都不显示
    val heroStatusLabel = when (status) {
        AboutUpdateStatus.Latest -> stringResource(R.string.koil_about_status_latest)
        AboutUpdateStatus.Failed -> stringResource(R.string.koil_about_status_failed)
        else -> null
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
            statusLabel = heroStatusLabel,
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
                            // 126dp 是 hero 下方的留白原值（hero 底边到下一元素的间距）。
                            // 这一项原先紧随「检查更新」分组标题，标题删掉（与卡内行同名）后由卡片接替，
                            // 留白沿用 126dp：间距关系不变，也释放掉原本给标题预留的那一段高度。
                            // 若改成 126 + 标题高（约 34dp = 160dp），卡片位置与改动前完全一致，
                            // 但会在 hero 与卡片之间留下一块原本属于标题的空白。
                            logoHeightDp + 52.dp + logoPadding.calculateTopPadding() -
                                scrollPadding.calculateTopPadding() + 126.dp,
                        ),
                    contentAlignment = Alignment.TopCenter,
                    content = { },
                )
            }

            item(key = "updateButton") {
                // 独立居中的胶囊：只在检测到新版本时出现，下载进度条挂在它下面
                KoilAboutUpdateButton(
                    status = status,
                    downloadState = downloadState,
                    readyTag = (downloadState as? UpdateDownloadState.Ready)?.tag,
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
                    modifier = Modifier.padding(bottom = 12.dp),
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
    /** 版本号右边的状态文案（已是最新版本 / 检查失败）；null 时整块不占位 */
    statusLabel: String?,
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    val p = versionCodeProgress()
                    alpha = 1 - p
                    scaleX = 1 - (p * 0.05f)
                    scaleY = 1 - (p * 0.05f)
                },
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                color = colorScheme.onSurfaceVariantSummary,
                text = versionLabel,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
            )
            if (statusLabel != null) {
                Spacer(Modifier.width(8.dp))
                Text(
                    // 检查结论跟在版本号右边：同一行读，不另起一行打断 hero 的居中关系
                    color = colorScheme.onSurfaceVariantSummary,
                    text = statusLabel,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
