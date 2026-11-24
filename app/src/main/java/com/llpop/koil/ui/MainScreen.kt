package com.llpop.koil.ui

import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import com.llpop.koil.ui.navigation.Route
import top.yukonga.miuix.kmp.nav.core.NavController
import top.yukonga.miuix.kmp.nav.core.NavDisplay
import top.yukonga.miuix.kmp.nav.core.NavKey
import top.yukonga.miuix.kmp.nav.transition.NavSwipeDirection
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.llpop.koil.R
import com.llpop.koil.data.settings.AppSettings
import com.llpop.koil.ui.component.BlurredBar
import com.llpop.koil.ui.component.blur.rememberBlurBackdrop
import com.llpop.koil.ui.component.rememberAppPageInsets
import com.llpop.koil.ui.component.effect.BgEffectBackground
import com.llpop.koil.ui.component.liquid.rememberCombinedBackdrop
import com.llpop.koil.ui.component.liquid.IosLiquidGlassNavigationBar
import com.llpop.koil.ui.screen.HomeScreen
import com.llpop.koil.ui.screen.OverviewScreen
import com.llpop.koil.ui.screen.SettingsScreen
import com.llpop.koil.ui.screen.settings.OilPriceDetailScreen
import com.llpop.koil.ui.theme.FloatingBottomBarStyle
import com.llpop.koil.ui.theme.KOiLTheme
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.glass.GlassNavigationBar
import top.yukonga.miuix.kmp.glass.GlassNavigationItem
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.glass.Data
import top.yukonga.miuix.kmp.icon.glass.HomeOutline
import top.yukonga.miuix.kmp.icon.glass.SettingsOutline
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.compose.foundation.gestures.Orientation
import com.llpop.koil.ui.component.blur.LocalBlurEnabled
import top.yukonga.miuix.kmp.utils.PagerGestureNestedScrollConnection
import top.yukonga.miuix.kmp.utils.PagerInterceptionMode
import top.yukonga.miuix.kmp.utils.pagerGestureOverride
import top.yukonga.miuix.kmp.utils.PagerNavigationSpringSpec
import top.yukonga.miuix.kmp.utils.springAnimateToPage
import top.yukonga.miuix.kmp.material.isRuntimeShaderSupported

/**
 * 主界面：三 Tab（录入 / 总览 / 设置）+ 底部导航 + 主题三档。
 *
 * 底部导航条采用悬浮导航体系：
 * - 底栏固定悬浮（不提供开关），只可选悬浮样式
 * - 悬浮样式：Miuix（官方玻璃胶囊 GlassNavigationBar） / iOS 液态玻璃（IosLiquidGlassNavigationBar）
 * 非悬浮时保留普通 NavigationBar + BlurredBar 模糊。
 */
@Composable
fun MainScreen(
    /** 从「新版本提醒」通知点进来：首帧就落在关于页，不用用户再找一层 */
    openAboutOnLaunch: Boolean = false,
    /** 消费掉上面那个请求，避免返回首页后又被它弹回关于页 */
    onOpenAboutHandled: () -> Unit = {},
    viewModel: MainViewModel = koinViewModel(),
) {
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val language by viewModel.language.collectAsStateWithLifecycle()
    val floatingStyle by viewModel.floatingBottomBarStyle.collectAsStateWithLifecycle()
    val blurEnabled by viewModel.blurEnabled.collectAsStateWithLifecycle()
    val wallpaperEnabled by viewModel.wallpaperEnabled.collectAsStateWithLifecycle()
    val pagerGestureMode by viewModel.pagerGestureMode.collectAsStateWithLifecycle()
    val swipeDismissEnabled by viewModel.swipeDismissEnabled.collectAsStateWithLifecycle()
    // 页面切换手势：与 miuix PagerInterceptionMode 一一对应（cross_axis = 跨轴拦截）
    val pagerInterceptionMode = if (pagerGestureMode == "native") {
        PagerInterceptionMode.Native
    } else {
        PagerInterceptionMode.CrossAxis
    }
    val interceptPagerGestures = pagerInterceptionMode == PagerInterceptionMode.CrossAxis

    val darkTheme = when (themeMode) {
        AppSettings.ThemeMode.SYSTEM -> isSystemInDarkTheme()
        AppSettings.ThemeMode.LIGHT -> false
        AppSettings.ThemeMode.DARK -> true
    }

    // 运行时语言切换：按所选 Language 覆盖 Configuration + LocalContext，
    // 使 stringResource 立即按目标 locale 取资源（无需重启 Activity）。
    val baseConfig = androidx.compose.ui.platform.LocalConfiguration.current
    val baseContext = androidx.compose.ui.platform.LocalContext.current
    // 「跟随系统」时取窗口当前配置里的 locale，而不是 Locale.getDefault()：
    // 系统语言变化（清单已声明 locale|layoutDirection 不重建 Activity）时前者一定是最新值，
    // 后者依赖框架更新进程默认 Locale，时序上可能慢一拍。同时把它作为 remember key，
    // 否则系统语言变了、language 仍是 SYSTEM，remember 命中旧值 → 界面停在上一种语言。
    val systemLocales = baseConfig.locales
    val locale = remember(language, systemLocales) {
        if (language == AppSettings.Language.SYSTEM) systemLocales[0]
        else com.llpop.koil.util.LocaleManager.resolve(language)
    }
    val overrideConfig = remember(locale, baseConfig) {
        android.content.res.Configuration(baseConfig).apply { setLocale(locale) }
    }
    val localizedContext = remember(overrideConfig, baseContext) {
        baseContext.createConfigurationContext(overrideConfig)
    }
    // 覆盖 LocalContext 后，activity-compose 1.9.3 的
    // LocalActivityResultRegistryOwner 兜底解析（沿 LocalContext.base 链查找
    // ActivityResultRegistryOwner）会因 createConfigurationContext 的 wrapper
    // 链不含 Activity 而失败，导致首页定位权限 launcher 抛
    // No ActivityResultRegistryOwner。故从原始 baseContext 解析出 owner 显式补回。
    val registryOwner = remember(baseContext) {
        var cur: android.content.Context? = baseContext
        while (cur is android.content.ContextWrapper && cur !is androidx.activity.result.ActivityResultRegistryOwner) {
            cur = cur.baseContext
        }
        cur as? androidx.activity.result.ActivityResultRegistryOwner
    }
    check(registryOwner != null) { "KOiL: 无法从 baseContext 解析 ActivityResultRegistryOwner" }

    // 切到别的 App / 回桌面时主动清焦点。
    // 系统在窗口重新拿到焦点时会把焦点还给 ComposeView 上最后聚焦的那个节点 —— 首页录入卡的
    // 输入框正好就是它，于是回到 App 会「白弹一次键盘」；更糟的是首页那个
    // 「键盘可见 && 首页输入框有焦点 → formBringIntoView」的副作用会顺着 Pager 的滚动链
    // 把整页拽回首页，看起来像「自动跳到首页并把光标放进输入框」。
    // force = true 连 focus 组一起清，避免有节点残留可被系统恢复。
    val windowInfo = androidx.compose.ui.platform.LocalWindowInfo.current
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    LaunchedEffect(windowInfo) {
        androidx.compose.runtime.snapshotFlow { windowInfo.isWindowFocused }
            .collect { isFocused -> if (!isFocused) focusManager.clearFocus(force = true) }
    }

    // 官方 AppHost 的做法：只有宿主把平台安全区、外部 padding 和导航 chrome 合并一次，
    // 页面用 LocalAppPageInsets 读。我们外层没有 rail / 搜索胶囊，两者恒为 0。
    val appPageInsets = rememberAppPageInsets(
        hostPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
        startAlreadyReserved = false,
        overlayStart = { 0.dp },
        searchTop = { 0.dp },
    )
    androidx.compose.runtime.CompositionLocalProvider(
        com.llpop.koil.ui.component.LocalAppPageInsets provides appPageInsets,
        // 把"应用语言"显式注入组合。Dialog/Popup 是独立组合根，会用窗口自己的
        // configuration 覆盖 LocalConfiguration/LocalContext（MainScreen 的覆盖到不了），
        // 而自定义 CompositionLocal 会随父组合继承 —— 弹窗据此把语言重新盖回去。
        com.llpop.koil.ui.component.LocalAppLocale provides locale,
        androidx.compose.ui.platform.LocalConfiguration provides overrideConfig,
        androidx.compose.ui.platform.LocalContext provides localizedContext,
        androidx.activity.compose.LocalActivityResultRegistryOwner provides registryOwner,
        // 模糊效果总开关：一路往下传给页面 Scaffold / 弹层 / 底栏，关掉即全部回落不透明
        LocalBlurEnabled provides blurEnabled,
        // 动态壁纸：开关开启时页面底层跑 BgEffectBackground 流动色，关闭时为 surface
        com.llpop.koil.ui.theme.LocalWallpaperEnabled provides wallpaperEnabled,
    ) {
        KOiLTheme(darkTheme = darkTheme) {
        val surfaceColor = MiuixTheme.colorScheme.surface
        // 录制层与官方 GlassPage 一一对应：
        //  - wallpaperBackdrop：只录「固定壁纸」这一层（壁纸钉在屏幕上，翻页只移动其上的内容）
        //  - contentBackdrop：录 Pager 里所有页面的内容（官方页内的 `backdrop` 那一层）
        //  - pageBackdrop：录整个 Scaffold（底栏 + 内容 + 各页顶栏），供弹层/浮层采样，
        //    对应官方 `Scaffold(modifier = Modifier.layerBackdrop(pageBackdrop))`
        val wallpaperBackdrop = rememberLayerBackdrop()
        val contentBackdrop = rememberBlurBackdrop()
        val pageBackdrop = rememberLayerBackdrop()
        // 官方底栏是 `GlassNavigationBar(backdrop = backdrop)` —— 采「壁纸 + 内容」那一层，
        // 内容从底栏下面滚过时底栏会有真实折射。这里用同一语义：壁纸层 + Pager 内容层。
        val navBarBackdrop = if (blurEnabled && isRuntimeShaderSupported()) {
            if (contentBackdrop != null) rememberCombinedBackdrop(wallpaperBackdrop, contentBackdrop) else wallpaperBackdrop
        } else {
            null
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                // 最底层填充：Scaffold 已透明，若不铺背景会露出窗口底色
                .background(surfaceColor),
        ) {
            // 顺序与官方 GlassPage 一致：先 layerBackdrop 再 background。
            // .background() 写在 layerBackdrop 之前的话，底色在录制层**外面**，
            // 录制层里只剩动态底；一旦关掉动态壁纸，层就是全透明的，所有玻璃
            // （卡片 / 顶栏 / 底栏）采样到空层 → 提亮成一片白灰块。
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .layerBackdrop(wallpaperBackdrop)
                    .background(surfaceColor),
            ) {
                // 唯一一份动态底，常驻播放：壁纸不随页面平移，色阶也不会因翻页重新计时。
                if (wallpaperEnabled) {
                    BgEffectBackground(
                        dynamicBackground = true,
                        modifier = Modifier.fillMaxSize(),
                        isFullSize = true,
                    ) {
                    }
                }
            }
            // 三页用 HorizontalPager 承载：
            // 页面常驻 + 滑动过渡动画，避免 when 切换整树销毁重建导致的卡顿
            val pagerState = rememberPagerState(pageCount = { 3 })
            val scope = rememberCoroutineScope()
            // 全局提示宿主：所有页面/弹窗共用一条 Snackbar 通道（替代散落的 Toast）
            val snackbarHostState = remember { SnackbarHostState() }
            val selectedTab by remember { derivedStateOf { pagerState.currentPage } }
            // 二级页（关于 / 调价明细）改由导航层承载：back stack 里 Main 常驻，
            // 二级页作为 entry 压栈，转场与交互式侧滑返回由 NavDisplay 负责
            val backStack = remember { mutableStateListOf<NavKey>(Route.Main) }
            val nav = remember(backStack) { NavController(backStack) }
            // 通知点进来：状态变化才生效（App 已在前台时点通知走 onNewIntent，不重组就不会读到新值）
            LaunchedEffect(openAboutOnLaunch) {
                if (openAboutOnLaunch) {
                    if (backStack.last() != Route.About) nav.push(Route.About)
                    onOpenAboutHandled()
                }
            }

            // 一级页的返回手势：数据总览 / 设定不是首页，按返回先回首页，不退出应用；
            // 只在首页按返回才交给系统（Activity finish）。
            // 二级页（明细页 / 关于页）自己有 BackHandler，且比这里更内层注册、先消费事件；
            // enabled 里再排除一次，不依赖注册顺序兜底。
            BackHandler(enabled = selectedTab != 0 && backStack.last() == Route.Main) {
                scope.launch { pagerState.springAnimateToPage(0) }
            }

            // 导航项列表按标签缓存，避免每次重组都重新 listOf 三个 NavigationItem
            // （含 stringResource 解析）在 Pager 滑动引发的高频重组下造成纯浪费。
            val navAddLabel = stringResource(R.string.koil_nav_add)
            val navOverviewLabel = stringResource(R.string.koil_nav_overview)
            val navSettingsLabel = stringResource(R.string.koil_nav_settings)
            val navigationItems = remember(navAddLabel, navOverviewLabel, navSettingsLabel) {
                listOf(
                    NavigationItem(label = navAddLabel, icon = MiuixIcons.Glass.HomeOutline),
                    NavigationItem(label = navOverviewLabel, icon = MiuixIcons.Glass.Data),
                    NavigationItem(label = navSettingsLabel, icon = MiuixIcons.Glass.SettingsOutline),
                )
            }
            // 官方 Glass 导航条的 items：与 basic NavigationItem 分开构造，两者类型不同。
            // 底栏固定「图标 + 文字」——Glass 组件没有 showLabel 开关，item 的 label 直接上屏。
            val glassNavigationItems = remember(navAddLabel, navOverviewLabel, navSettingsLabel) {
                listOf(
                    GlassNavigationItem(icon = MiuixIcons.Glass.HomeOutline, label = navAddLabel, contentDescription = navAddLabel),
                    GlassNavigationItem(icon = MiuixIcons.Glass.Data, label = navOverviewLabel, contentDescription = navOverviewLabel),
                    GlassNavigationItem(icon = MiuixIcons.Glass.SettingsOutline, label = navSettingsLabel, contentDescription = navSettingsLabel),
                )
            }
            val isFloatingStyleIos = floatingStyle == FloatingBottomBarStyle.IosLike.storageValue
            val isDark = darkTheme

            // 横移返回：方向取物理方向（不随布局镜像），RTL 下反过来
            val swipeBackDirection = if (LocalLayoutDirection.current == LayoutDirection.Rtl) {
                NavSwipeDirection.RightToLeft
            } else {
                NavSwipeDirection.LeftToRight
            }
            val swipeDismiss = if (swipeDismissEnabled) swipeBackDirection else null

            androidx.compose.runtime.CompositionLocalProvider(
                com.llpop.koil.ui.component.LocalWallpaperBackdrop provides wallpaperBackdrop,
                // 弹层/浮层采样「整页」（对应官方 GlassDialog/GlassBottomSheet 的 backdrop = pageBackdrop）
                com.llpop.koil.ui.component.LocalPageBackdrop provides pageBackdrop,
                com.llpop.koil.ui.component.LocalSnackbarHostState provides snackbarHostState,
            ) {
                // 根弹层宿主：官方 example 的 GlassPageHost 就是这个角色。它只负责
                // popupHost（承载 GlassDialog / GlassBottomSheet / 下拉面板），**不挂
                // layerBackdrop、不录制**。这样弹层落在 pageBackdrop 录制层之外，
                // 玻璃采样「整页含顶栏底栏、排除弹层自身」才成立；否则弹层被录进
                // 自己正在采的层里，轻则背景错位透出自己，重则绘制期重入直接闪退。
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = Color.Transparent,
                    contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0),
                    // 提示挂在根宿主：盖在页面与底栏之上、弹层之下，切页不中断
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                ) {
            NavDisplay(
                backStack = backStack,
                onBack = { nav.pop() },
                modifier = Modifier.fillMaxSize(),
            ) {
                entry<Route.Main>(swipeDismiss = swipeDismiss) {

            Scaffold(
                    // 官方：Scaffold(modifier = Modifier.fillMaxSize().layerBackdrop(pageBackdrop))
                    // 录「顶栏 + 底栏 + 内容」整页，排除弹层与 scrim
                    modifier = Modifier.layerBackdrop(pageBackdrop),
                    containerColor = Color.Transparent,
                    // 弹层统一交给外层根宿主渲染：这层在录制层内部，若也渲染弹层，
                    // 弹层会被录进 pageBackdrop，采样即自我包含。
                    popupHost = {},
                    bottomBar = {
                        if (isFloatingStyleIos) {
                            // iOS 液态玻璃悬浮导航条
                            IosLiquidGlassNavigationBar(
                                items = navigationItems,
                                selectedIndex = selectedTab,
                                onItemClick = { index -> scope.launch { pagerState.springAnimateToPage(index) } },
                                backdrop = navBarBackdrop,
                                isBlurActive = navBarBackdrop != null,
                                isDark = isDark,
                                showLabels = true,
                            )
                        } else {
                            // 官方 OS4 玻璃胶囊导航条：材质、描边、阴影由组件内置，
                            // 不再自己叠 textureBlur。底部 inset 由组件自己按 navigationBars 计算
                            // （与自研 iOS 底栏一致），Scaffold 的 bottomBar 槽位不重复消费。
                            GlassNavigationBar(
                                items = glassNavigationItems,
                                selectedIndex = selectedTab,
                                onSelect = { index -> scope.launch { pagerState.springAnimateToPage(index) } },
                                backdrop = navBarBackdrop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp),
                            )
                        }
                    },
                ) { innerPadding ->
                    // 页面容器全屏（内容延伸到悬浮条下方，
                    // 槽位区域显示页面内容本身而非裸露背景色带），
                    // 顶部 inset 交给各页内 Scaffold 的 TopAppBar 处理（外层 MainPage
                    // Scaffold 只传 bottomPadding，内层 page Scaffold 管 topBar），此处不加 top padding。
                    // 底栏采样固定壁纸层（见上方 navBarBackdrop），不再录制整页内容。
                    val contentModifier = Modifier.fillMaxSize()
                    // 底部留白直接取 Scaffold 算出的槽高：悬浮胶囊与普通栏都适用，
                    // 不再按"胶囊高度 + 20dp"硬编码猜测（胶囊尺寸一变就会错）
                    val bottomPad = innerPadding.calculateBottomPadding()
                    // Pager 内容录制层：录所有页面内容（不含底栏 —— 底栏是 Scaffold 的
                    // bottomBar 槽位，不在本录制树内，底栏采样它不会自采样）。
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .then(if (contentBackdrop != null) Modifier.layerBackdrop(contentBackdrop) else Modifier),
                    ) {
                        // flingBehavior 用 miuix PagerNavigationSpringSpec：
                        // 页面切换/松手回弹的弹簧曲线统一，避免默认 fling 的
                        // 生硬 snap 造成的"卡一下"观感。
                        // 这两个都是 @Composable 且内部自带 remember（foundation PagerDefaults），
                        // 每次重组新建对象的说法不成立，不要在外面再包 remember。
                        val pagerFlingBehavior = PagerDefaults.flingBehavior(
                            state = pagerState,
                            snapAnimationSpec = PagerNavigationSpringSpec,
                        )
                        // miuix 定制手势管线：跨轴拦截器接管左右滑动
                        // （页面内垂直滚动与 Pager 横向滑动不打架）+ 配套嵌套滚动连接。
                        // 注意：拦截器仅作用于 Pager 内三个 tab 页；调价记录明细页已提升为
                        // MainScreen 级全屏页（脱离 Pager，见下方 if(showOilPriceDetail)），
                        // 因此其省份横滑条/走势图拖动不受拦截器影响（二级页同理）。
                        // 0.9.5 的 Pager 拦截 API 仍在（miuix-ui 的 PagerGestureUtilsKt / PagerGestureNestedScrollConnection）：
                        // cross_axis 模式下由拦截器接管左右滑动（先判主轴，页面内垂直滚动与翻页不打架），
                        // 同时关掉 Pager 自带滑动，避免两条手势管线互相抢；native 模式则完全交回 Pager。
                        // 拦截器仅作用于 Pager 内三个 tab 页；调价明细/关于页已提升到 Pager 之外，不受影响。
                        val nativePagerNestedScrollConnection = PagerDefaults.pageNestedScrollConnection(
                            state = pagerState,
                            orientation = Orientation.Horizontal,
                        )
                        // 三页同一套手势管线：数据页不再有"整页横滑切档"，也就不需要按页把
                        // mode 临时退回 Native / 关掉 Pager 自身横滑。早先那样做时，一旦用户开着
                        // 跨轴拦截（cross_axis），数据页是"拦截器不装节点 + Pager 自己被关"，
                        // 横滑两边都没人接 → 数据页滑不动。这里统一交给设置里的模式处理，
                        // 也顺带消掉了"mode 在 settle 途中按页切换 → 拦截器 detach 取消 settle
                        // → 页面卡在两页中间"的那个老问题（现在 mode 不再随当前页变化）。
                        HorizontalPager(
                                state = pagerState,
                                // 跨轴拦截：横滑前先判主轴（页面内垂直滚动与翻页不打架）；
                                // native 模式由 miuix 把手势原样交回 Pager
                                modifier = Modifier
                                    .fillMaxSize()
                                    .pagerGestureOverride(
                                        pagerState = pagerState,
                                        flingBehavior = pagerFlingBehavior,
                                        mode = pagerInterceptionMode,
                                    ),
                                verticalAlignment = Alignment.Top,
                                // 预载相邻页：滑动时页面已组合完毕，动画帧只有绘制无组合，
                                // 避免切换时整页首帧组合造成的掉帧
                                beyondViewportPageCount = 1,
                                overscrollEffect = null,
                                // 拦截器模式下由跨轴拦截节点接管横滑，Pager 自带滑动关掉，
                                // 避免两条手势管线互相抢；native 模式则完全交给 Pager。
                                userScrollEnabled = !interceptPagerGestures,
                                pageNestedScrollConnection = if (interceptPagerGestures) {
                                    PagerGestureNestedScrollConnection
                                } else {
                                    nativePagerNestedScrollConnection
                                },
                                flingBehavior = pagerFlingBehavior,
                        ) { page ->
                            // 动态底已提到 Pager 之外（全局一份常驻播放），页面里不需要
                            // 再按可见性择时推进色阶。
                            when (page) {
                                    0 -> HomeScreen(
                                        modifier = contentModifier,
                                        bottomPadding = bottomPad,
                                    )
                                    1 -> OverviewScreen(
                                        modifier = contentModifier,
                                        bottomPadding = bottomPad,
                                        // #5 空态「去录入」一键跳转录入页：与底部导航一致走 springAnimateToPage
                                        onNavigateToAdd = { scope.launch { pagerState.springAnimateToPage(0) } },
                                    )
                                    2 -> SettingsScreen(
                                        modifier = contentModifier,
                                        bottomPadding = bottomPad,
                                        onOpenPriceDetail = { nav.push(Route.OilPriceDetail) },
                                        onOpenAbout = { nav.push(Route.About) },
                                    )
                                }
                            }
                        }
                    }
                }
                entry<Route.About>(swipeDismiss = swipeDismiss) {
                    com.llpop.koil.ui.screen.settings.AboutScreen(onBack = { nav.pop() })
                }
                entry<Route.OilPriceDetail>(swipeDismiss = swipeDismiss) {
                    OilPriceDetailScreen(onBack = { nav.pop() })
                }
            }
            }
            }
                }
        }
        }
    }
