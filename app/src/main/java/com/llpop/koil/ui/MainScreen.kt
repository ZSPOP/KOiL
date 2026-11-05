package com.llpop.koil.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.llpop.koil.R
import com.llpop.koil.data.settings.AppSettings
import com.llpop.koil.ui.component.BlurredBar
import com.llpop.koil.ui.component.liquid.IosLiquidGlassNavigationBar
import com.llpop.koil.ui.screen.AddScreen
import com.llpop.koil.ui.screen.OverviewScreen
import com.llpop.koil.ui.screen.SettingsScreen
import com.llpop.koil.ui.screen.settings.OilPriceDetailScreen
import com.llpop.koil.ui.theme.BottomBarMode
import com.llpop.koil.ui.theme.FloatingBottomBarStyle
import com.llpop.koil.ui.theme.KOiLTheme
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarDisplayMode
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.NavigationItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.highlight.Highlight
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PagerGestureNestedScrollConnection
import top.yukonga.miuix.kmp.utils.PagerInterceptionMode
import top.yukonga.miuix.kmp.utils.PagerNavigationSpringSpec
import top.yukonga.miuix.kmp.utils.pagerGestureOverride
import top.yukonga.miuix.kmp.utils.springAnimateToPage

/**
 * 主界面：三 Tab（录入 / 总览 / 设置）+ 底部导航 + 主题三档。
 *
 * 底部导航条参考 Mishka（https://github.com/YuKongA/Mishka）悬浮导航体系：
 * - 悬浮导航开关：floatingBottomBar（默认开启）
 * - 悬浮样式：Miuix（胶囊悬浮 + textureBlur 玻璃） / iOS 液态玻璃（IosLiquidGlassNavigationBar）
 * - 底栏模式：图标+文字 / 仅图标
 * 非悬浮时保留普通 NavigationBar + BlurredBar 模糊。
 */
@Composable
fun MainScreen(viewModel: MainViewModel = koinViewModel()) {
    val themeMode by viewModel.themeMode.collectAsState()
    val language by viewModel.language.collectAsState()
    val floatingBottomBar by viewModel.floatingBottomBar.collectAsState()
    val floatingStyle by viewModel.floatingBottomBarStyle.collectAsState()
    val barMode by viewModel.bottomBarMode.collectAsState()

    val darkTheme = when (themeMode) {
        AppSettings.ThemeMode.SYSTEM -> isSystemInDarkTheme()
        AppSettings.ThemeMode.LIGHT -> false
        AppSettings.ThemeMode.DARK -> true
    }

    // 运行时语言切换：按所选 Language 覆盖 Configuration + LocalContext，
    // 使 stringResource 立即按目标 locale 取资源（无需重启 Activity）。
    val baseConfig = androidx.compose.ui.platform.LocalConfiguration.current
    val baseContext = androidx.compose.ui.platform.LocalContext.current
    val locale = remember(language) { com.llpop.koil.util.LocaleManager.resolve(language) }
    val overrideConfig = remember(locale, baseConfig) {
        android.content.res.Configuration(baseConfig).apply { setLocale(locale) }
    }
    val localizedContext = remember(overrideConfig, baseContext) {
        baseContext.createConfigurationContext(overrideConfig)
    }
    // 覆盖 LocalContext 后，activity-compose 1.9.3 的
    // LocalActivityResultRegistryOwner 兜底解析（沿 LocalContext.base 链查找
    // ActivityResultRegistryOwner）会因 createConfigurationContext 的 wrapper
    // 链不含 Activity 而失败，导致 AddScreen 定位权限 launcher 抛
    // No ActivityResultRegistryOwner。故从原始 baseContext 解析出 owner 显式补回。
    val registryOwner = remember(baseContext) {
        var cur: android.content.Context? = baseContext
        while (cur is android.content.ContextWrapper && cur !is androidx.activity.result.ActivityResultRegistryOwner) {
            cur = cur.baseContext
        }
        cur as? androidx.activity.result.ActivityResultRegistryOwner
    }
    check(registryOwner != null) { "KOiL: 无法从 baseContext 解析 ActivityResultRegistryOwner" }

    androidx.compose.runtime.CompositionLocalProvider(
        androidx.compose.ui.platform.LocalConfiguration provides overrideConfig,
        androidx.compose.ui.platform.LocalContext provides localizedContext,
        androidx.activity.compose.LocalActivityResultRegistryOwner provides registryOwner,
    ) {
        KOiLTheme(darkTheme = darkTheme) {
        // 底栏玻璃 backdrop：drawRect(surface) + drawContent() 录制页面真实内容，
        // 供悬浮导航条/底栏模糊采样产生通透感（对齐 Mishka rememberBlurBackdrop 用 surface 铺底）。
        // 页面底色 = Miuix Scaffold 默认 containerColor=surface（浅 #F7F7F7 / 深 #000000），
        // 卡片为 surfaceContainer（浅白 / 深 #242424），两者天然拉开色差。
        val surfaceColor = MiuixTheme.colorScheme.surface
        val bottomBarBackdrop = rememberLayerBackdrop {
            drawRect(surfaceColor)
            drawContent()
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                // 最底层填充：Scaffold 已透明，若不铺背景会露出窗口底色
                .background(surfaceColor),
        ) {
            // 三页用 HorizontalPager 承载（对齐 Mishka MainPage）：
            // 页面常驻 + 滑动过渡动画，避免 when 切换整树销毁重建导致的卡顿
            val pagerState = rememberPagerState(pageCount = { 3 })
            val scope = rememberCoroutineScope()
            val selectedTab by remember { derivedStateOf { pagerState.currentPage } }
            // 调价记录明细页开关：true 时在 Pager 之外全屏显示（对齐 Mishka 二级路由页，
            // 脱离 Pager 的手势拦截器，页面内横向组件可自由滑动）
            var showOilPriceDetail by rememberSaveable { mutableStateOf(false) }

            // v1.6.2：导航项列表按标签缓存。原实现每次重组都重新 listOf 三个 NavigationItem
            // （含 stringResource 解析），Pager 滑动引发的高频重组下属于纯浪费。
            val navAddLabel = stringResource(R.string.koil_nav_add)
            val navOverviewLabel = stringResource(R.string.koil_nav_overview)
            val navSettingsLabel = stringResource(R.string.koil_nav_settings)
            val navigationItems = remember(navAddLabel, navOverviewLabel, navSettingsLabel) {
                listOf(
                    NavigationItem(label = navAddLabel, icon = Icons.Filled.Home),
                    NavigationItem(label = navOverviewLabel, icon = Icons.Filled.Storage),
                    NavigationItem(label = navSettingsLabel, icon = Icons.Filled.Settings),
                )
            }
            val showLabels = barMode == BottomBarMode.IconAndText.storageValue
            val isFloatingStyleIos = floatingStyle == FloatingBottomBarStyle.IosLike.storageValue
            val isDark = darkTheme
            // 对齐 Mishka：深色 GlassStrokeMiddleDark / 浅色 GlassStrokeMiddleLight 高光边
            val floatingHighlight = remember(isDark) {
                if (isDark) Highlight.GlassStrokeMiddleDark else Highlight.GlassStrokeMiddleLight
            }

            Scaffold(
                    containerColor = Color.Transparent,
                    bottomBar = {
                        // 二级全屏页（调价记录明细页）隐藏底部导航（对齐 Mishka 二级路由页规范）
                        if (!showOilPriceDetail) {
                        if (floatingBottomBar) {
                            if (isFloatingStyleIos) {
                                // iOS 液态玻璃悬浮导航条（Mishka 移植）
                                IosLiquidGlassNavigationBar(
                                    items = navigationItems,
                                    selectedIndex = selectedTab,
                                    onItemClick = { index -> scope.launch { pagerState.springAnimateToPage(index) } },
                                    backdrop = bottomBarBackdrop,
                                    isBlurActive = true,
                                    isDark = isDark,
                                    showLabels = showLabels,
                                )
                            } else {
                                // Miuix 胶囊悬浮导航条 + 玻璃（对齐 Mishka 全部参数）
                                val floatingPillRadius = 50.dp
                                val floatingBarShape = RoundedCornerShape(floatingPillRadius)
                                val floatingBarModifier = Modifier.textureBlur(
                                    backdrop = bottomBarBackdrop,
                                    shape = floatingBarShape,
                                    blurRadius = 25f,
                                    colors = BlurDefaults.blurColors(
                                        blendColors = listOf(
                                            BlendColorEntry(
                                                color = MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.6f),
                                            ),
                                        ),
                                    ),
                                    highlight = floatingHighlight,
                                )
                                top.yukonga.miuix.kmp.basic.FloatingNavigationBar(
                                    modifier = floatingBarModifier,
                                    color = Color.Transparent,
                                    cornerRadius = floatingPillRadius,
                                ) {
                                    navigationItems.forEachIndexed { index, item ->
                                        MiuixFloatingNavigationBarItem(
                                            item = item,
                                            selected = selectedTab == index,
                                            onClick = { scope.launch { pagerState.springAnimateToPage(index) } },
                                            showLabel = showLabels,
                                        )
                                    }
                                }
                            }
                        } else {
                            // 普通导航条 + 模糊底栏
                            BlurredBar(
                                backdrop = bottomBarBackdrop,
                                blurActive = true,
                            ) {
                                NavigationBar(
                                    color = Color.Transparent,
                                    mode = if (showLabels) {
                                        NavigationBarDisplayMode.IconAndText
                                    } else {
                                        NavigationBarDisplayMode.IconOnly
                                    },
                                ) {
                                    navigationItems.forEachIndexed { index, item ->
                                        NavigationBarItem(
                                            selected = selectedTab == index,
                                            onClick = { scope.launch { pagerState.springAnimateToPage(index) } },
                                            icon = item.icon,
                                            label = item.label,
                                        )
                                    }
                                }
                            }
                        }
                        }
                    },
                ) { innerPadding ->
                    // 参考 Mishka：页面容器全屏（内容延伸到悬浮条下方，
                    // 槽位区域显示页面内容本身而非裸露背景色带），
                    // 顶部 inset 交给各页内 Scaffold 的 TopAppBar 处理（对齐 Mishka：外层 MainPage
                    // Scaffold 只传 bottomPadding，内层 page Scaffold 管 topBar），此处不加 top padding。
                    // 【对齐 Mishka 关键结构】layerBackdrop(bottomBarBackdrop) 挂在 HorizontalPager 外层
                    // （录制整个 Pager 含所有页面内容），悬浮条在 bottomBar 槽位不在录制范围内，安全；
                    // 左右滑动切换时悬浮条永远能采样到页面内容 → 滑动过程保持通透。
                    val contentModifier = Modifier.fillMaxSize()
                    val bottomPad = if (floatingBottomBar) {
                        val floatingBarHeight = if (isFloatingStyleIos) 100.dp else 64.dp
                        floatingBarHeight + 20.dp
                    } else {
                        innerPadding.calculateBottomPadding()
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .layerBackdrop(bottomBarBackdrop),
                    ) {
                        // 【对齐 Mishka】flingBehavior 用 miuix PagerNavigationSpringSpec：
                        // 页面切换/松手回弹的弹簧曲线与 Mishka 完全一致，避免默认 fling 的
                        // 生硬 snap 造成的"卡一下"观感
                        val pagerFlingBehavior = PagerDefaults.flingBehavior(
                            state = pagerState,
                            snapAnimationSpec = PagerNavigationSpringSpec,
                        )
                        // 【对齐 Mishka】miuix 定制手势管线：跨轴拦截器接管左右滑动
                        // （页面内垂直滚动与 Pager 横向滑动不打架）+ 配套嵌套滚动连接。
                        // 注意：拦截器仅作用于 Pager 内三个 tab 页；调价记录明细页已提升为
                        // MainScreen 级全屏页（脱离 Pager，见下方 if(showOilPriceDetail)），
                        // 因此其省份横滑条/走势图拖动不受拦截器影响（同 Mishka 二级页）。
                        val pagerGestureModifier = Modifier
                            .fillMaxSize()
                            .pagerGestureOverride(
                                pagerState = pagerState,
                                mode = PagerInterceptionMode.CrossAxisInterceptor,
                            )
                        if (showOilPriceDetail) {
                            // 调价记录明细页：全屏承载在 Pager 之外（对齐 Mishka 二级路由页），
                            // 横向手势完全归页面内组件所有；无底栏，底部留白用系统导航条 inset
                            OilPriceDetailScreen(
                                onBack = { showOilPriceDetail = false },
                                bottomPadding = innerPadding.calculateBottomPadding(),
                            )
                        } else {
                            HorizontalPager(
                                state = pagerState,
                                modifier = pagerGestureModifier,
                                verticalAlignment = Alignment.Top,
                                // 预载相邻页：滑动时页面已组合完毕，动画帧只有绘制无组合，
                                // 避免切换时整页首帧组合造成的掉帧（v1.4.7 失败因 glassActive 每帧重组，已移除）
                                beyondViewportPageCount = 1,
                                overscrollEffect = null,
                                userScrollEnabled = false,
                                pageNestedScrollConnection = PagerGestureNestedScrollConnection,
                                flingBehavior = pagerFlingBehavior,
                            ) { page ->
                                when (page) {
                                    0 -> AddScreen(modifier = contentModifier, bottomPadding = bottomPad)
                                    1 -> OverviewScreen(
                                        modifier = contentModifier,
                                        bottomPadding = bottomPad,
                                        // v1.5.46：#5 空态「去录入」一键跳转录入页
                                        // v1.5.50fix：animateScrollToPage 在自定义手势管线（pagerGestureOverride +
                                        // userScrollEnabled=false）下不生效，改用与底部导航一致的 springAnimateToPage（已验证可用）
                                        onNavigateToAdd = { scope.launch { pagerState.springAnimateToPage(0) } },
                                    )
                                    2 -> SettingsScreen(
                                        modifier = contentModifier,
                                        bottomPadding = bottomPad,
                                        onOpenPriceDetail = { showOilPriceDetail = true },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        }
    }

/** 参考 Mishka AppNavigation.MiuixFloatingNavigationBarItem：按压反馈 + 图标/文字 */
@Composable
private fun MiuixFloatingNavigationBarItem(
    item: NavigationItem,
    selected: Boolean,
    onClick: () -> Unit,
    showLabel: Boolean,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val onSurfaceContainerColor = MiuixTheme.colorScheme.onSurfaceContainer
    val tint = when {
        isPressed -> onSurfaceContainerColor.copy(alpha = if (selected) 0.7f else 0.5f)
        selected -> onSurfaceContainerColor
        else -> onSurfaceContainerColor.copy(alpha = 0.6f)
    }

    Column(
        modifier = modifier
            .defaultMinSize(minWidth = if (showLabel) 56.dp else 48.dp, minHeight = 48.dp)
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.Tab,
                interactionSource = interactionSource,
                indication = null,
            )
            .padding(horizontal = if (showLabel) 8.dp else 6.dp, vertical = 5.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            modifier = Modifier.size(22.dp),
            imageVector = item.icon,
            contentDescription = if (showLabel) null else item.label,
            tint = tint,
        )
        if (showLabel) {
            Text(
                text = item.label,
                color = tint,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
