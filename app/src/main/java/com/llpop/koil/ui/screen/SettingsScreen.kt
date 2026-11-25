package com.llpop.koil.ui.screen

import android.os.Build

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import com.llpop.koil.ui.component.groupedCardItems
import com.llpop.koil.ui.component.CardItem
import com.llpop.koil.ui.component.KOiLPasswordMask
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import top.yukonga.miuix.kmp.basic.IconButton
import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.drop
import androidx.compose.runtime.snapshotFlow
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import top.yukonga.miuix.kmp.glass.GlassTextField
import com.llpop.koil.ui.component.KOiLNoWhitespaceInput
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.runtime.DisposableEffect
import com.llpop.koil.KOiLApplication
import com.llpop.koil.R
import com.llpop.koil.data.settings.AppSettings
import com.llpop.koil.data.sync.SyncConfig
import com.llpop.koil.ui.component.KOiLGlassDialog
import com.llpop.koil.ui.component.GlassDialogTextButton
import com.llpop.koil.ui.component.pageScrollModifiers
import com.llpop.koil.ui.component.KOiLScreenScaffold
import com.llpop.koil.ui.component.pageContentPadding
import com.llpop.koil.ui.component.rememberPageBackdropForGlass
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import com.llpop.koil.ui.screen.settings.SettingsViewModel
import com.llpop.koil.ui.screen.settings.SyncViewModel
import com.llpop.koil.ui.theme.PagerGestureMode
import com.llpop.koil.ui.theme.FloatingBottomBarStyle
import com.llpop.koil.ui.theme.KOiLColors
import com.llpop.koil.ui.component.provinceLabel
import org.koin.androidx.compose.koinViewModel
import org.koin.core.context.GlobalContext
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TextFieldDefaults
import top.yukonga.miuix.kmp.glass.GlassDropdownPreference
import top.yukonga.miuix.kmp.glass.GlassDialogButton
import top.yukonga.miuix.kmp.glass.GlassDialogButtonRole
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Backup
import top.yukonga.miuix.kmp.icon.extended.File
import top.yukonga.miuix.kmp.icon.extended.FileDownloads
import top.yukonga.miuix.kmp.icon.extended.Import
import top.yukonga.miuix.kmp.icon.extended.MoveFile
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.llpop.koil.util.CrashReporter
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import com.llpop.koil.ui.theme.KOiLType
import com.llpop.koil.data.update.UpdateChannelIssue
import com.llpop.koil.data.update.updateChannelIssue
import com.llpop.koil.ui.component.LocalPageBackdrop
import com.llpop.koil.ui.component.blur.rememberBlurBackdrop
import com.llpop.koil.ui.component.blur.overScrollVertical
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.utils.scrollEndHaptic



/**
 * 设置页（M4：14 项设置完整实现）
 */
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    bottomPadding: androidx.compose.ui.unit.Dp = 0.dp,
    onOpenPriceDetail: () -> Unit = {},
    onOpenAbout: () -> Unit = {},
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // 全局焦点管理 + 偏差输入框点击标志（用于"点击输入框外部即收起提交"，无需坐标计算）
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val tappedOnField = remember { mutableStateOf(false) }
    // 数据导入/导出弹窗开关：云同步卡与主函数尾部弹窗共享，声明在页面级（M-07 拆分时上提）
    val showDataIoDialog = remember { mutableStateOf(false) }

    val importLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let { viewModel.previewCsv(it) } }

    val backupLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let { viewModel.importBackup(it) } }

    // 权限联动——点开开关先请求权限；拒绝则开关保持关（不会出现"假开"）
    var locationPermissionGranted by remember { mutableStateOf(viewModel.hasLocationPermission()) }
    val locationPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions(),
    ) { permissions ->
        val granted = permissions.entries.any { it.value }
        locationPermissionGranted = granted
        if (granted) {
            viewModel.setLocationEnabled(true)
            viewModel.locateAndSetProvince()
        } else {
            viewModel.setLocationEnabled(false)
        }
    }

    // 通知权限联动——未授权恒显示关；点开先请求；拒绝保持关 + 提示
    val context = LocalContext.current
    var pushPermissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    // 用户点了开关想开但未授权 → 跳系统通知设置后等待返回；ON_RESUME 检测到已授权则自动打开开关
    var pendingOpenPush by remember { mutableStateOf(false) }
    // 「应用更新」渠道在系统侧可能被关掉、也可能只是被设成静音——两种都不会弹横幅，
    // 而 App 自己的开关看不出来，所以在副标题里点明是哪一种
    var channelIssue by remember {
        mutableStateOf(updateChannelIssue(context))
    }
    // 返回前台时刷新权限状态（用户可能在系统设置里改动过通知/定位权限）
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                pushPermissionGranted =
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
                channelIssue = updateChannelIssue(context)
                locationPermissionGranted = viewModel.hasLocationPermission()
                // 跳系统通知设置授权返回后，自动补开推送开关（用户刚表达过"想开"的意图）
                if (pushPermissionGranted && pendingOpenPush) {
                    pendingOpenPush = false
                    viewModel.setNotifyEnabled(true)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        // 打开页面时做一次维护扫描（假生效日只记录不删库）；后续刷新不再重复跑全表 DISTINCT
        viewModel.loadPriceDbStatus(runMaintenance = true)
        viewModel.loadEiaApiKey()
    }

    val pageBackdrop = rememberBlurBackdrop()
    val listState = rememberLazyListState()
    KOiLScreenScaffold(
        title = stringResource(R.string.koil_set_title),
        modifier = modifier,
        backdrop = pageBackdrop,
        listState = listState,
    ) { innerPadding, topPadding, scrollBehavior, pageBackdrop ->
        val priceDbStatus by viewModel.priceDbStatus.collectAsStateWithLifecycle()
        val eiaKey by viewModel.eiaApiKey.collectAsStateWithLifecycle()
        val eiaEnabled by viewModel.eiaEnabled.collectAsStateWithLifecycle()
        val eiaLoaded by viewModel.eiaLoaded.collectAsStateWithLifecycle()

        // 崩溃日志状态提到列表外：关于区（LazyListScope 扩展）与日志状态 item 都要读它
        val crashContext = LocalContext.current
        var showCrashDialog by remember { mutableStateOf(false) }
        var crashLogText by remember { mutableStateOf("") }
        var crashCount by remember { mutableIntStateOf(0) }
        val crashClearedText = stringResource(R.string.koil_set_crash_cleared)
        val crashExportOkText = stringResource(R.string.koil_set_crash_export_ok)
        val crashExportFailText = stringResource(R.string.koil_set_crash_export_fail)
        val crashScope = rememberCoroutineScope()
        // 崩溃日志操作结果走全局 Snackbar
        val crashSnackbar = com.llpop.koil.ui.component.rememberSnackbar(
            duration = top.yukonga.miuix.kmp.basic.SnackbarDuration.Long,
            withDismissAction = true,
        )
        val syncVm: SyncViewModel = koinViewModel()
        val syncUi by syncVm.uiState.collectAsStateWithLifecycle()
        // 健康判定与「已設定完整」同一口径：只用数据层算好的 configured，界面不再自己拼
        val syncHealthy = syncUi.configured

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .pageScrollModifiers(
                    enableScrollEndHaptic = true,
                    showTopAppBar = true,
                    topAppBarScrollBehavior = scrollBehavior,
                )
                .imePadding()
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        val up = waitForUpOrCancellation()
                        if (up != null) {
                            if (!tappedOnField.value) {
                                focusManager.clearFocus()
                            }
                            tappedOnField.value = false
                        }
                    }
                },
            contentPadding = pageContentPadding(innerPadding, extraBottom = bottomPadding),
        ) {
            // 顶部避让 TopAppBar（含状态栏，折叠时高度联动）

            // ===== 外观：主题 / 语言 / 悬浮样式 / 模糊 =====
            if (uiState.loaded) {
                item { SmallTitle(text = stringResource(R.string.koil_set_section_appearance)) }
            }
            groupedCardItems(
                keyPrefix = "settings_appearance",
                outerBottomPadding = 12.dp,
                items = buildList {
                    // 整卡等首值到达再渲染：主题/语言/悬浮样式/模糊读的都是首帧默认值，逐行补守卫会让整行
                    // 先消失再插入，跳得比翻一下更显眼；整块出现只跳一次，也不会「默认值 → 真实值」翻转。
                    if (uiState.loaded) {
                        add(CardItem("theme") {
                            val themeModes = AppSettings.ThemeMode.entries
                            val themeModeIdx = themeModes.indexOf(uiState.themeMode).coerceAtLeast(0)
                            GlassDropdownPreference(
                                backdrop = rememberPageBackdropForGlass(),
                                items = themeModes.map { themeLabel(it) },
                                selectedIndex = themeModeIdx,
                                title = stringResource(R.string.koil_set_appearance),
                                summary = themeModes.getOrElse(themeModeIdx) { themeModes.first() }.let { themeLabel(it) },
                                onSelectedIndexChange = { index -> viewModel.setThemeMode(themeModes[index]) },
                            )
                        })
                        add(CardItem("language") {
                            val languages = AppSettings.Language.entries
                            val languageIdx = languages.indexOf(uiState.language).coerceAtLeast(0)
                            GlassDropdownPreference(
                                backdrop = rememberPageBackdropForGlass(),
                                items = languages.map { langLabel(it) },
                                selectedIndex = languageIdx,
                                title = stringResource(R.string.koil_set_language),
                                summary = languages.getOrElse(languageIdx) { languages.first() }.let { langLabel(it) },
                                onSelectedIndexChange = { index -> viewModel.setLanguage(languages[index]) },
                            )
                        })
                        // 悬浮样式：底栏形态属于外观，归到本段（原先在「导航」段，与三个手势混在一起）
                        add(CardItem("floatingStyle") {
                            val floatingStyles = FloatingBottomBarStyle.entries
                            val floatingStyleIdx = floatingStyles.indexOf(
                                FloatingBottomBarStyle.fromStorage(uiState.floatingBottomBarStyle),
                            ).coerceAtLeast(0)
                            GlassDropdownPreference(
                                backdrop = rememberPageBackdropForGlass(),
                                items = floatingStyles.map { styleLabel(it) },
                                selectedIndex = floatingStyleIdx,
                                title = stringResource(R.string.koil_set_floating_style),
                                summary = floatingStyles.getOrElse(floatingStyleIdx) { floatingStyles.first() }
                                    .let { styleLabel(it) },
                                onSelectedIndexChange = { index ->
                                    viewModel.setFloatingBottomBarStyle(floatingStyles[index].storageValue)
                                },
                            )
                        })
                        // 等 DataStore 首值到达再渲染：否则冷启动会先显示默认「开」，
                        // 真实值（关）到达后再翻回去，出现「关→开→关」的闪烁。
                        add(CardItem("blur") {
                            SettingSwitchRow(
                                title = stringResource(R.string.koil_set_blur),
                                subtitle = stringResource(R.string.koil_set_blur_sub),
                                checked = uiState.blurEnabled,
                                onCheckedChange = viewModel::setBlurEnabled,
                            )
                        })
                        add(CardItem("wallpaper") {
                            SettingSwitchRow(
                                title = stringResource(R.string.koil_set_wallpaper),
                                subtitle = stringResource(R.string.koil_set_wallpaper_sub),
                                checked = uiState.wallpaperEnabled,
                                onCheckedChange = viewModel::setWallpaperEnabled,
                            )
                        })
                    }
                },
            )

            // ===== 偏好：定位 / 油号 =====
            if (uiState.loaded) {
                item { SmallTitle(text = stringResource(R.string.koil_set_section_pref)) }
            }
            groupedCardItems(
                keyPrefix = "settings_pref",
                outerBottomPadding = 12.dp,
                items = buildList {
                    if (uiState.loaded) {
                        add(CardItem("location") {
                            SettingSwitchRow(
                                title = stringResource(R.string.koil_set_location),
                                subtitle = stringResource(R.string.koil_set_location_sub),
                                checked = uiState.locationEnabled && locationPermissionGranted,
                                onCheckedChange = { on ->
                                    if (on) {
                                        if (locationPermissionGranted) {
                                            viewModel.setLocationEnabled(true)
                                            viewModel.locateAndSetProvince()
                                        } else {
                                            locationPermissionLauncher.launch(
                                                arrayOf(android.Manifest.permission.ACCESS_COARSE_LOCATION)
                                            )
                                        }
                                    } else {
                                        viewModel.setLocationEnabled(false)
                                    }
                                },
                            )
                        })
                        if (!locationPermissionGranted) {
                            add(CardItem("locationDenied") {
                                Text(
                                    text = stringResource(R.string.koil_set_location_denied),
                                    fontSize = KOiLType.CaptionLarge,
                                    lineHeight = 14.sp,
                                    color = KOiLColors.Warning,
                                    modifier = Modifier
                                        .padding(horizontal = 16.dp)
                                        .offset(y = (-3).dp)
                                        .clickable {
                                            locationPermissionLauncher.launch(
                                                arrayOf(android.Manifest.permission.ACCESS_COARSE_LOCATION)
                                            )
                                        },
                                )
                            })
                        }
                        if (!uiState.locationEnabled) {
                            add(CardItem("province") {
                                val provinces = PROVINCES
                                val provinceIdx = provinces.indexOf(uiState.defaultProvince).coerceAtLeast(0)
                                GlassDropdownPreference(
                                    backdrop = rememberPageBackdropForGlass(),
                                    items = provinces.map { provinceLabel(it) },
                                    selectedIndex = provinceIdx,
                                    title = stringResource(R.string.koil_set_default_province),
                                    summary = stringResource(
                                        R.string.koil_set_default_province_sum,
                                        provinceLabel(provinces.getOrElse(provinceIdx) { provinces.first() }),
                                    ),
                                    onSelectedIndexChange = { index ->
                                        viewModel.setDefaultProvince(provinces[index])
                                    },
                                )
                            })
                        }
                        add(CardItem("keepGrade") {
                            SettingSwitchRow(
                                title = stringResource(R.string.koil_set_keep_grade),
                                subtitle = stringResource(R.string.koil_set_keep_grade_sub),
                                checked = uiState.keepLastGrade,
                                onCheckedChange = viewModel::setKeepLastGrade,
                            )
                        })
                        if (!uiState.keepLastGrade) {
                            add(CardItem("grade") {
                                val oilGrades = GRADES
                                val gradeIdx = oilGrades.indexOf(uiState.defaultOilGrade).coerceAtLeast(0)
                                GlassDropdownPreference(
                                    backdrop = rememberPageBackdropForGlass(),
                                    items = oilGrades.map { "${it}#" },
                                    selectedIndex = gradeIdx,
                                    title = stringResource(R.string.koil_set_default_grade),
                                    summary = stringResource(
                                        R.string.koil_set_default_grade_sum,
                                        oilGrades.getOrElse(gradeIdx) { oilGrades.first() },
                                    ),
                                    onSelectedIndexChange = { index ->
                                        viewModel.setDefaultOilGrade(oilGrades[index])
                                    },
                                )
                            })
                        }
                    }
                },
            )

            // ===== 交互：三个手势 =====
            if (uiState.loaded) {
                item { SmallTitle(text = stringResource(R.string.koil_set_section_interaction)) }
            }
            groupedCardItems(
                keyPrefix = "settings_interaction",
                outerBottomPadding = 12.dp,
                items = buildList {
                    if (uiState.loaded) {
                        add(CardItem("pagerGesture") {
                            val pagerModes = PagerGestureMode.entries
                            val pagerModeIdx = pagerModes.indexOf(
                                PagerGestureMode.fromStorage(uiState.pagerGestureMode),
                            ).coerceAtLeast(0)
                            GlassDropdownPreference(
                                backdrop = rememberPageBackdropForGlass(),
                                items = pagerModes.map { pagerGestureLabel(it) },
                                selectedIndex = pagerModeIdx,
                                title = stringResource(R.string.koil_set_pager_gesture),
                                summary = pagerModes.getOrElse(pagerModeIdx) { pagerModes.first() }
                                    .let { pagerGestureLabel(it) },
                                onSelectedIndexChange = { index ->
                                    viewModel.setPagerGestureMode(pagerModes[index].storageValue)
                                },
                            )
                        })
                        // 预测性返回：只有 Android 14 起才有返回预览动画，低版本不显示该开关
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                            add(CardItem("predictiveBack") {
                                val activity = LocalActivity.current
                                SettingSwitchRow(
                                    title = stringResource(R.string.koil_set_predictive_back),
                                    subtitle = stringResource(R.string.koil_set_predictive_back_sub),
                                    checked = uiState.predictiveBack,
                                    onCheckedChange = { enabled ->
                                        viewModel.setPredictiveBack(enabled)
                                        // ApplicationInfo 上的标志只能反射改，且要重建 Activity
                                        // 才会被窗口系统重新读取（与系统行为一致）
                                        (activity?.application as? KOiLApplication)?.setPredictiveBack(enabled)
                                        activity?.recreate()
                                    },
                                )
                            })
                        }
                        // 页面横移返回：二级页可交互式侧滑关闭（0.9.5 导航层能力，Android 全版本可用）
                        add(CardItem("swipeDismiss") {
                            SettingSwitchRow(
                                title = stringResource(R.string.koil_set_swipe_dismiss),
                                subtitle = stringResource(R.string.koil_set_swipe_dismiss_sub),
                                checked = uiState.swipeDismissEnabled,
                                onCheckedChange = viewModel::setSwipeDismissEnabled,
                            )
                        })
                    }
                },
            )

            // ===== 消息推送：一处管全应用（总开关 + 调价提醒）=====
            if (uiState.loaded) {
                item { SmallTitle(text = stringResource(R.string.koil_set_section_notify)) }
            }
            groupedCardItems(
                keyPrefix = "settings_notify",
                outerBottomPadding = 12.dp,
                items = buildList {
                    if (uiState.loaded) {
                        // 「系统通知」总开关：调价提醒与新版本提醒共用系统通知权限，
                        // 一条开关管两端（渠道各自独立，用户仍可在系统设置里单独关掉某个渠道）
                        // 与 blur 同一病因：notifyEnabled 默认 true，存量关过的用户冷启动会「开→关」闪
                        add(CardItem("notify") {
                            SettingSwitchRow(
                                title = stringResource(R.string.koil_set_notify),
                                subtitle = when (channelIssue) {
                                    UpdateChannelIssue.Disabled ->
                                        stringResource(R.string.koil_set_notify_sub_channel_off)
                                    UpdateChannelIssue.Quiet ->
                                        stringResource(R.string.koil_set_notify_sub_channel_quiet)
                                    UpdateChannelIssue.None ->
                                        stringResource(R.string.koil_set_notify_sub)
                                },
                                checked = uiState.notifyEnabled && pushPermissionGranted,
                                onCheckedChange = { on ->
                                    if (on) {
                                        if (pushPermissionGranted) {
                                            viewModel.setNotifyEnabled(true)
                                        } else {
                                            // HyperOS 通知权限拒绝后运行时请求不弹框（静默拒绝）→ 直接跳系统通知设置，
                                            // 用户开启后返回 App（ON_RESUME）自动补开开关；跳转用 NEW_TASK + fallback 加固
                                            pendingOpenPush = true
                                            openNotificationSettings(context)
                                        }
                                    } else {
                                        viewModel.setNotifyEnabled(false)
                                    }
                                },
                            )
                        })
                        if (!pushPermissionGranted) {
                            add(CardItem("notifyDenied") {
                                Text(
                                    text = stringResource(R.string.koil_set_notify_denied),
                                    fontSize = KOiLType.CaptionLarge,
                                    lineHeight = 14.sp,
                                    color = KOiLColors.Warning,
                                    modifier = Modifier
                                        .padding(horizontal = 16.dp)
                                        .offset(y = (-3).dp)
                                        .clickable { openNotificationSettings(context) },
                                )
                            })
                        }
                        // 调价提醒原先挂在「油价数据」段，与总开关隔了两段——用户要关提醒得先找对段，
                        // 挪到总开关下面，一格之内就能看到「总开关 / 其中一路」
                        add(CardItem("priceAlert") {
                            SettingSwitchRow(
                                title = stringResource(R.string.koil_set_price_alert),
                                subtitle = stringResource(R.string.koil_set_price_alert_sub),
                                checked = uiState.priceAlert,
                                onCheckedChange = viewModel::setPriceAlert,
                            )
                        })
                    }
                },
            )

            // ===== 阈值与容量：三个数值项 =====
            if (uiState.loaded) {
                item { SmallTitle(text = stringResource(R.string.koil_set_section_calc)) }
            }
            groupedCardItems(
                keyPrefix = "settings_calc",
                outerBottomPadding = 12.dp,
                items = buildList {
                    if (uiState.loaded) {
                        add(CardItem("abnormal") {
                            StepperInputRow(
                                title = stringResource(R.string.koil_set_abnormal),
                                summary = stringResource(R.string.koil_set_abnormal_sum, uiState.abnormalThreshold.roundToInt()),
                                value = uiState.abnormalThreshold.roundToInt(),
                                range = 0..50,
                                step = 1,
                                suffix = "%",
                                onFieldTap = { tappedOnField.value = true },
                                onCommit = { viewModel.setAbnormalThreshold(it.toDouble()) },
                            )
                        })
                        add(CardItem("deviation") {
                            StepperInputRow(
                                title = stringResource(R.string.koil_set_deviation),
                                summary = stringResource(R.string.koil_set_deviation_sum, uiState.deviationThreshold.roundToInt()),
                                value = uiState.deviationThreshold.roundToInt(),
                                range = 1..50,
                                step = 1,
                                suffix = "%",
                                onFieldTap = { tappedOnField.value = true },
                                onCommit = { viewModel.setDeviationThreshold(it.toDouble()) },
                            )
                        })
                        add(CardItem("tank") {
                            StepperInputRow(
                                title = stringResource(R.string.koil_set_tank),
                                summary = stringResource(R.string.koil_set_tank_sub_fmt, uiState.tankCapacity.toInt()),
                                value = uiState.tankCapacity.toInt(),
                                range = 5..200,
                                step = 5,
                                suffix = "L",
                                onFieldTap = { tappedOnField.value = true },
                                onCommit = { viewModel.setTankCapacity(it.toFloat()) },
                            )
                        })
                    }
                },
            )

            item { SmallTitle(text = stringResource(R.string.koil_set_section_price_db)) }

            // ===== 油价数据库：状态卡 + 取数源（提醒已挪到「消息推送」段）=====
            groupedCardItems(
                keyPrefix = "settings_source",
                outerBottomPadding = 12.dp,
                items = buildList {
                    add(CardItem("pricedb") { PriceDbSection(viewModel, uiState, onOpenPriceDetail) })
                    if (eiaLoaded) {
                        add(CardItem("eia") {
                            SwitchPreference(
                                checked = eiaEnabled,
                                onCheckedChange = { viewModel.setEiaEnabled(it) },
                                title = stringResource(R.string.koil_set_eia_key_title),
                                summary = stringResource(R.string.koil_set_eia_key_sub),
                                bottomAction = if (eiaEnabled) {
                                    null
                                } else {
                                    {
                                        Column {
                                            Spacer(Modifier.height(8.dp))
                                            var showKey by remember { mutableStateOf(false) }
                                            val eiaState = rememberTextFieldState(eiaKey)
                                            // DataStore 侧的值异步到达时回填；内容一致就不动，不打断正在输入的光标
                                            LaunchedEffect(eiaKey) {
                                                if (eiaState.text.toString() != eiaKey) {
                                                    eiaState.setTextAndPlaceCursorAtEnd(eiaKey)
                                                }
                                            }
                                            // 输入写回 ViewModel；drop(1) 跳过初值那一次，避免开局无谓回写
                                            LaunchedEffect(eiaState) {
                                                snapshotFlow { eiaState.text.toString() }
                                                    .drop(1)
                                                    .collect { viewModel.setEiaApiKey(it) }
                                            }
                                            GlassTextField(
                                                state = eiaState,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    // 外接键盘的回车从这里进来，软键盘走下面的键盘动作 / 字符提交两条路
                                                    .onPreviewKeyEvent { event ->
                                                        if (event.type == KeyEventType.KeyDown &&
                                                            (event.key == Key.Enter || event.key == Key.NumPadEnter)
                                                        ) {
                                                            viewModel.setEiaApiKey(eiaState.text.toString())
                                                            focusManager.clearFocus()
                                                            true
                                                        } else {
                                                            false
                                                        }
                                                    },
                                                lineLimits = TextFieldLineLimits.SingleLine,
                                                keyboardOptions = KeyboardOptions(
                                                    keyboardType = KeyboardType.Ascii,
                                                    imeAction = androidx.compose.ui.text.input.ImeAction.Done,
                                                ),
                                                // 回车 = 提交 + 收键盘；同时抹掉输入法回车补的空格，
                                                // 否则尾部空格会被写进 DataStore 并让 EIA 鉴权失败
                                                inputTransformation = KOiLNoWhitespaceInput {
                                                    viewModel.setEiaApiKey(eiaState.text.toString())
                                                    focusManager.clearFocus()
                                                },
                                                onKeyboardAction = {
                                                    viewModel.setEiaApiKey(eiaState.text.toString())
                                                    focusManager.clearFocus()
                                                },
                                                outputTransformation = if (showKey) null else KOiLPasswordMask,
                                                trailingIcon = {
                                                    IconButton(onClick = { showKey = !showKey }) {
                                                        Icon(
                                                            imageVector = if (showKey) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                                            // 读屏标签随当前状态切换：明文态读出「隐藏密码」，密文态读出「显示密码」
                                                            contentDescription = stringResource(
                                                                if (showKey) R.string.koil_a11y_hide_secret else R.string.koil_a11y_show_secret,
                                                            ),
                                                            tint = MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.6f),
                                                            modifier = Modifier.size(18.dp),
                                                        )
                                                    }
                                                },
                                            )
                                            Spacer(Modifier.height(13.dp))
                                        }
                                    }
                                },
                            )
                        })
                    }
                    if (priceDbStatus.lastSyncSources.isNotBlank()) {
                        add(CardItem("syncSources") {

                Text(
                    text = stringResource(R.string.koil_pdb_sync_sources, priceDbStatus.lastSyncSources),
                    fontSize = KOiLType.BodySmall,
                    lineHeight = 15.sp,
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                )
                        })
                    }
                },
            )

            item {
                // —— 崩溃日志状态：首帧与每次打开弹窗都刷新（导出/清空后重开也拿到最新值） ——
                LaunchedEffect(showCrashDialog) {
                    crashLogText = withContext(Dispatchers.IO) { CrashReporter.readLogs(crashContext) }
                    crashCount = CrashReporter.countEntries(crashLogText)
                }

                // ===== 资料与同步 =====
                // 入口按「加油记录 / 完整备份 / 油价历史」三组配对，
                // 不分组的话 6 行长得一模一样，用户得逐个读副标题才知道谁跟谁是一对。
                SmallTitle(
                    text = stringResource(R.string.koil_set_section_data),
                    insideMargin = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            // ===== 资料与同步：导出入口 / 云同步开关 / 云同步面板 =====
            groupedCardItems(
                keyPrefix = "settings_data",
                outerBottomPadding = 12.dp,
                items = buildList {
                    add(CardItem("dataIo") {
                        SettingRow(
                            title = stringResource(R.string.koil_set_data_io_title),
                            subtitle = stringResource(R.string.koil_set_data_io_sub),
                            value = "",
                            onClick = { showDataIoDialog.value = true },
                            holdDownState = showDataIoDialog.value,
                        )
                    })
                    if (syncUi.loaded) {
                        add(CardItem("syncSwitch") {
                            SettingSwitchRow(
                                title = stringResource(R.string.koil_sync_switch),
                                subtitle = stringResource(R.string.koil_sync_switch_sub),
                                checked = syncUi.enabled,
                                onCheckedChange = { on ->
                                    // 不再把默认值当草稿传下去：有效配置由 SyncConfig 在数据层兜底，
                                    // 默认值永不落盘。开关只负责「开 / 关 + 触发一次同步」。
                                    //
                                    // 未配置时打开 = 开关亮了却什么都不发生（旧版最被诟病的那种静默）：
                                    // 开关是受控组件（checked = syncUi.enabled），这里不调就是直接回弹。
                                    // 关闭方向永远放行——包括「开了但密码解不开」时让用户能关掉。
                                    when {
                                        !on -> syncVm.setEnabled(false)      // 关闭方向永远放行
                                        syncUi.configured -> syncVm.setEnabled(true)
                                        else -> syncVm.notifyNotConfigured()  // 回弹 + 说明缺什么
                                    }
                                },
                                trailingContent = null,
                                titleTrailing = if (syncUi.enabled) ({ SyncStatusBadge(syncHealthy) }) else null,
                            )
                        })
                    }
                    // 未配置时也要露出面板：否则清空密码 → 开关被关 → 面板随之消失，
                    // 用户既开不了开关也找不到填密码的地方（整块功能死锁）
                    if (syncUi.enabled || !syncUi.configured) {
                        add(CardItem("syncPanel") {
                            SyncPanelBody(tappedOnField = tappedOnField, focusManager = focusManager)
                        })
                    }
                },
            )

            item {
                if (showDataIoDialog.value) {
                    KOiLGlassDialog(
                        show = true,
                        onDismissRequest = { showDataIoDialog.value = false },
                        // 标题与入口行同名，让弹窗自己说清「这是哪几个动作」，
                        // 内容里就不再重复顶一个大标题。
                        title = stringResource(R.string.koil_set_data_io_title),
                        // 横向内边距归零 → 行节点铺满面板宽，按下高亮横向满宽，
                        // 四个角由面板自己的 squircle 裁切；纵向 24 留出上下白。
                        // 不再包 CardSegment：那层圆角裁剪会把短行裁成胶囊，比面板直接裁更“厚”。
                        insideMargin = DpSize(0.dp, 24.dp),
                    ) {
                        // 行内边距 24/12：横向 24 是文字缩进、行节点仍然铺满面板宽，
                        // 所以高亮满宽；纵向 12 撑出的行高让 5 条选项排满一屏又不互相黏住。
                        val rowMargin = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
                        // —— CSV 段：人可读、Excel 能开 ——
                        DataIoDialogItem(
                            insideMargin = rowMargin,
                            icon = MiuixIcons.FileDownloads,
                            title = stringResource(R.string.koil_set_export_records),
                            onClick = {
                                showDataIoDialog.value = false
                                viewModel.exportCsv()
                            },
                        )
                        DataIoDialogItem(
                            dividerAbove = true,
                            insideMargin = rowMargin,
                            icon = MiuixIcons.Import,
                            title = stringResource(R.string.koil_set_import_records),
                            onClick = {
                                showDataIoDialog.value = false
                                importLauncher.launch(arrayOf("text/csv", "*/*"))
                            },
                        )
                        DataIoDialogItem(
                            dividerAbove = true,
                            insideMargin = rowMargin,
                            icon = MiuixIcons.File,
                            title = stringResource(R.string.koil_set_export_price_history),
                            onClick = {
                                showDataIoDialog.value = false
                                viewModel.exportOilPriceHistory()
                            },
                        )

                        // —— JSON 段：整包、只有本 App 能恢复 ——
                        DataIoDialogItem(
                            dividerAbove = true,
                            insideMargin = rowMargin,
                            icon = MiuixIcons.Backup,
                            title = stringResource(R.string.koil_set_backup),
                            onClick = {
                                showDataIoDialog.value = false
                                viewModel.exportBackup()
                            },
                        )
                        DataIoDialogItem(
                            dividerAbove = true,
                            insideMargin = rowMargin,
                            icon = MiuixIcons.MoveFile,
                            title = stringResource(R.string.koil_set_restore),
                            onClick = {
                                showDataIoDialog.value = false
                                backupLauncher.launch(arrayOf("application/json", "*/*"))
                            },
                        )
                        // 取消：横向 24、顶部 12。弹窗也能点外部或返回关闭，
                        // 这里是给「一项都不想选」一个明确的出口。
                        GlassDialogTextButton(
                            text = stringResource(R.string.koil_common_cancel),
                            onClick = { showDataIoDialog.value = false },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp, start = 24.dp, end = 24.dp),
                        )
                    }
                }

        if (showCrashDialog) {
            KOiLGlassDialog(
                show = true,
                onDismissRequest = { showCrashDialog = false },
                contentPadding = 16.dp,
                scrollable = true,
                title = stringResource(R.string.koil_set_crash_log),
                summary = if (crashCount > 0) {
                    pluralStringResource(R.plurals.koil_set_crash_count, crashCount, crashCount)
                } else {
                    null
                },
            ) {
                Column {
                    if (crashLogText.isBlank()) {
                        Text(
                            text = stringResource(R.string.koil_set_crash_none),
                            fontSize = KOiLType.Body,
                            color = MiuixTheme.colorScheme.onBackgroundVariant,
                        )
                    } else {
                        // 日志原文（等宽观感：小号字 + 按行呈现；GlassDialog 的 scrollable 负责滚动）
                        Text(
                            text = crashLogText,
                            fontSize = KOiLType.CaptionLarge,
                            lineHeight = 14.sp,
                            color = MiuixTheme.colorScheme.onBackgroundVariant,
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        if (crashLogText.isNotBlank()) {
                            GlassDialogButton(
                                onClick = {
                                    crashScope.launch {
                                        CrashReporter.clear(crashContext)
                                        crashLogText = ""
                                        crashCount = 0
                                        crashSnackbar(crashClearedText)
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                role = GlassDialogButtonRole.Normal,
                            ) {
                                Text(
                                    text = stringResource(R.string.koil_set_crash_clear),
                                    style = MiuixTheme.textStyles.button,
                                )
                            }
                        }
                        GlassDialogButton(
                            onClick = {
                                crashScope.launch {
                                    val ok = withContext(Dispatchers.IO) { CrashReporter.exportToDownloads(crashContext) }
                                    crashSnackbar(if (ok) crashExportOkText else crashExportFailText)
                                }
                            },
                            modifier = Modifier.weight(1f),
                            enabled = crashLogText.isNotBlank(),
                            role = GlassDialogButtonRole.Normal,
                        ) {
                            Text(
                                text = stringResource(R.string.koil_set_crash_export),
                                style = MiuixTheme.textStyles.button,
                            )
                        }
                    }
                }
            }

        }


        // 导出/备份/更新结果提示：全局 Snackbar 短暂提醒（自动消失，不用常驻卡片）
        val exportNotice by viewModel.exportMessage.collectAsStateWithLifecycle()
        val exportSnackbar = com.llpop.koil.ui.component.rememberSnackbar(
            duration = top.yukonga.miuix.kmp.basic.SnackbarDuration.Long,
            withDismissAction = true,
        )
        val exportText = exportNotice.key?.let { formatNotice(it, exportNotice.args) }
        LaunchedEffect(exportNotice) {
            if (exportText != null) {
                exportSnackbar(exportText)
                viewModel.clearExportMessage()
            }
        }
            }

            // ===== 关于与支持（完整分区）=====
            // 版本、更新检查、诊断集中到一处；组底距走 groupedCardItems 的 outerBottomPadding。
            aboutSection(
                crashCount = crashCount,
                onOpenCrashLog = { showCrashDialog = true },
                onOpenAbout = onOpenAbout,
                crashDialogOpen = showCrashDialog,
            )
        }
    }

    // 省份选择弹窗

    // 导入预览弹窗由 MainScreen 的根宿主渲染、在页面录制层之外，
    // 采样源直接沿用外层的整页 backdrop，这里不需要再补 CompositionLocal。
    ImportPreviewDialog(viewModel)
}

// ===== 组件 =====
// ===== 关于区 =====

internal const val KOIL_REPO_URL = "https://github.com/ZSPOP/KOiL"

/**
 * 打开外部链接；返回是否真的拉起了处理程序。
 * 调用方据此决定要不要把「已读」落库——startActivity 失败时用户什么都没看到，
 * 此时标记已读会让红点白白消失且不再有第二次提醒。
 */
internal fun openExternalUrl(context: Context, url: String): Boolean {
    val ok = runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, url.toUri())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }.isSuccess
    return ok
}

/** 检查入口统一收在 data.update.UpdateChecker（冷启动静默检查与手动检查共用一条通道） */

/**
 * 「上次检查」相对时间（刚刚 / 12 分钟前 / 3 小时前 / 2 天前）。
 * 从未查过（0）或时钟倒挂时返回 null，调用方回落到默认副标题。
 */

/**
 * 「关于与支持」一块（崩溃日志 / 问题反馈 / 关于 KOiL 三行连排），直接产出 lazy item 的
 * LazyListScope 扩展；组底距统一走 groupedCardItems 的 outerBottomPadding，不再手写 CardSegment。
 */
fun LazyListScope.aboutSection(
    crashCount: Int,
    onOpenCrashLog: () -> Unit,
    onOpenAbout: () -> Unit,
    /** 崩溃日志弹窗是否正开在这一行上：开着时该行保持按下高亮，点的是哪一行不会断线 */
    crashDialogOpen: Boolean = false,
) {
    item {
        SmallTitle(
            // 「关于」二字留给行标题（关于 KOiL），分组名改成「其他」，
            // 否则屏幕上会出现「关于」→「关于 KOiL」两级同名
            text = stringResource(R.string.koil_set_other),
            insideMargin = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        )
    }
    groupedCardItems(
        keyPrefix = "about_app",
        outerBottomPadding = 12.dp,
        items = listOf(
            // 崩溃日志 / 问题反馈并入「其它」，排在「关于 KOiL」之上；原「诊断」分组撤销
            CardItem("crashLog") {
                SettingRow(
                    title = stringResource(R.string.koil_set_crash_log),
                    subtitle = stringResource(R.string.koil_set_crash_log_sub),
                    value = if (crashCount > 0) pluralStringResource(R.plurals.koil_set_crash_count, crashCount, crashCount) else "",
                    onClick = onOpenCrashLog,
                    holdDownState = crashDialogOpen,
                )
            },
            CardItem("feedback") {
                val context = LocalContext.current
                val feedbackSnackbar = com.llpop.koil.ui.component.rememberSnackbar()
                SettingRow(
                    title = stringResource(R.string.koil_set_about_feedback),
                    subtitle = stringResource(R.string.koil_set_about_feedback_sub),
                    value = "",
                    onClick = {
                        if (!openExternalUrl(context, "$KOIL_REPO_URL/issues/new")) {
                            feedbackSnackbar(context.getString(R.string.koil_set_about_open_failed))
                        }
                    },
                )
            },
            CardItem("about") {
                // 红点只看「有没有新版本」，不受「系统通知」开关影响：
                // 红点要一直亮到装上最新版为止 —— 装成功后版本号不再比本机新，红点自然灭。
                val updateChecker = remember {
                    org.koin.core.context.GlobalContext.get()
                        .get<com.llpop.koil.data.update.UpdateChecker>()
                }
                val target by updateChecker.updateTarget()
                    .collectAsStateWithLifecycle(initialValue = null)
                // 后台静默检查失败是完全无声的：不弹、不亮红点，Gitee 长期不可用时用户只会觉得
                // 「永远没有新版本」。这里借右侧文字位提示一句；任意一次检查成功即清零，不需要已读机制。
                val lastFailedAt by updateChecker.lastFailedAt()
                    .collectAsStateWithLifecycle(initialValue = 0L)
                SettingRow(
                    // 标题是「关于 KOiL」，副标题仍是当前版本号（不因有新版本而变化）
                    title = stringResource(R.string.koil_set_about_koil),
                    subtitle = stringResource(R.string.koil_set_about_version, com.llpop.koil.BuildConfig.VERSION_NAME, com.llpop.koil.BuildConfig.VERSION_CODE),
                    value = if (lastFailedAt > 0) {
                        stringResource(R.string.koil_set_update_check_failed)
                    } else {
                        ""
                    },
                    onClick = onOpenAbout,
                    badgeDot = com.llpop.koil.data.update.shouldBadgeUpdate(
                        target?.tag.orEmpty(),
                        com.llpop.koil.BuildConfig.VERSION_NAME,
                    ),
                )
            },
        ),
    )
}

/**
 * 跳系统通知设置（通知开关/提示行共用）。
 * 加固要点：
 * 1. NEW_TASK——context 非 Activity（如 application context）时 startActivity 否则抛异常（被 runCatching
 *    静默吞掉导致"点开关没反应"）；
 * 2. resolveActivity 预检——部分 ROM 对 ACTION_APP_NOTIFICATION_SETTINGS 无处理者（ActivityNotFoundException），
 *    失败 fallback 到应用信息页（所有 ROM 必有）；
 * 3. 不静默吞异常——仍异常则 fallback 应用信息页，保证有可感知的跳转结果。
 */
internal fun openNotificationSettings(context: Context) {
    val pkg = context.packageName
    val target = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        .putExtra(Settings.EXTRA_APP_PACKAGE, pkg)
    if (target.resolveActivity(context.packageManager) != null) {
        context.startActivity(target)
        return
    }
    // fallback：应用信息页（通知开关在 App 详情里）
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .setData(android.net.Uri.fromParts("package", pkg, null))
        )
    }
}
