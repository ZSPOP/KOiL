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
import com.llpop.koil.ui.component.KOiLUpdateSection
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
                                contentDescription = stringResource(R.string.koil_set_about),
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
                            logoHeightDp + 52.dp + logoPadding.calculateTopPadding() -
                                scrollPadding.calculateTopPadding() + 126.dp,
                        ),
                    contentAlignment = Alignment.TopCenter,
                    content = { },
                )
            }

            item(key = "update") {
                Column(modifier = Modifier.padding(bottom = 12.dp)) {
                    SmallTitle(text = stringResource(R.string.koil_set_about_check_update))
                    KOiLUpdateSection(
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
                            // 关键：blur 生效时底色必须透明，否则 Card 自绘的实色会把采样结果整块盖掉
                            colors = CardDefaults.defaultColors(
                                if (blurEnabled) Color.Transparent else colorScheme.surfaceContainer,
                                Color.Transparent,
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
    onMeasured: (androidx.compose.ui.unit.Dp) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
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
        Text(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    val p = versionCodeProgress()
                    alpha = 1 - p
                    scaleX = 1 - (p * 0.05f)
                    scaleY = 1 - (p * 0.05f)
                },
            color = colorScheme.onSurfaceVariantSummary,
            text = "V${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
        )
    }
}
