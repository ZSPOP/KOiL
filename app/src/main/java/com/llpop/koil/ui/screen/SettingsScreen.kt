package com.llpop.koil.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import com.llpop.koil.ui.component.groupedCardItems
import com.llpop.koil.ui.component.CardItem
import com.llpop.koil.ui.component.CardSegment
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import top.yukonga.miuix.kmp.basic.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
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
import com.llpop.koil.R
import com.llpop.koil.data.settings.AppSettings
import com.llpop.koil.data.sync.SyncConfig
import com.llpop.koil.ui.component.KOiLGlassDialog
import com.llpop.koil.ui.component.KOiLScreenScaffold
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import com.llpop.koil.ui.screen.settings.SettingsViewModel
import com.llpop.koil.ui.screen.settings.SyncViewModel
import com.llpop.koil.ui.theme.BottomBarMode
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
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.llpop.koil.util.CrashReporter
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import com.llpop.koil.ui.theme.KOiLType



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
    // 返回前台时刷新权限状态（用户可能在系统设置里改动过通知/定位权限）
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                pushPermissionGranted =
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
                locationPermissionGranted = viewModel.hasLocationPermission()
                // 跳系统通知设置授权返回后，自动补开推送开关（用户刚表达过"想开"的意图）
                if (pushPermissionGranted && pendingOpenPush) {
                    pendingOpenPush = false
                    viewModel.setPricePush(true)
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

    KOiLScreenScaffold(
        title = stringResource(R.string.koil_set_title),
        modifier = modifier,
    ) { _, topPadding, scrollBehavior, _ ->
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
        val syncVm: SyncViewModel = koinViewModel()
        val syncUi by syncVm.uiState.collectAsStateWithLifecycle()
        // 健康判定与「已設定完整」同一口径：只用数据层算好的 configured，界面不再自己拼
        val syncHealthy = syncUi.configured

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
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
            contentPadding = PaddingValues(top = topPadding, bottom = bottomPadding),
        ) {
            // 顶部避让 TopAppBar（含状态栏，折叠时高度联动）

            item { SmallTitle(text = stringResource(R.string.koil_set_section_general)) }
            groupedCardItems(
                keyPrefix = "general",
                outerBottomPadding = 12.dp,
                items = buildList {
                    add(CardItem("theme") {
                        val themeModes = AppSettings.ThemeMode.entries
                        val themeModeIdx = themeModes.indexOf(uiState.themeMode).coerceAtLeast(0)
                        OverlayDropdownPreference(
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
                        OverlayDropdownPreference(
                            items = languages.map { langLabel(it) },
                            selectedIndex = languageIdx,
                            title = stringResource(R.string.koil_set_language),
                            summary = languages.getOrElse(languageIdx) { languages.first() }.let { langLabel(it) },
                            onSelectedIndexChange = { index -> viewModel.setLanguage(languages[index]) },
                        )
                    })
                },
            )
            // ===== 导航栏（SmallTitle + 分段卡）=====
            item { SmallTitle(text = stringResource(R.string.koil_set_section_nav)) }
            groupedCardItems(
                keyPrefix = "settings_nav",
                outerBottomPadding = 12.dp,
                items = buildList {
                    if (uiState.loaded) {
                        add(CardItem("floatingBar") {
                            SettingSwitchRow(
                                title = stringResource(R.string.koil_set_floating_bar),
                                subtitle = stringResource(R.string.koil_set_floating_bar_sub),
                                checked = uiState.floatingBottomBar,
                                onCheckedChange = viewModel::setFloatingBottomBar,
                            )
                        })
                        if (uiState.floatingBottomBar) {
                            add(CardItem("floatingStyle") {
                                val floatingStyles = FloatingBottomBarStyle.entries
                                val floatingStyleIdx = floatingStyles.indexOf(
                                    FloatingBottomBarStyle.fromStorage(uiState.floatingBottomBarStyle),
                                ).coerceAtLeast(0)
                                OverlayDropdownPreference(
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
                        }
                        add(CardItem("barMode") {
                            val barModes = BottomBarMode.entries
                            val barModeIdx = barModes.indexOf(
                                BottomBarMode.fromStorage(uiState.bottomBarMode),
                            ).coerceAtLeast(0)
                            OverlayDropdownPreference(
                                items = barModes.map { modeLabel(it) },
                                selectedIndex = barModeIdx,
                                title = stringResource(R.string.koil_set_bar_mode),
                                summary = barModes.getOrElse(barModeIdx) { barModes.first() }
                                    .let { modeLabel(it) },
                                onSelectedIndexChange = { index ->
                                    viewModel.setBottomBarMode(barModes[index].storageValue)
                                },
                            )
                        })
                    }
                },
            )

            // ===== 录入（SmallTitle + 分段卡）=====
            item { SmallTitle(text = stringResource(R.string.koil_set_section_entry)) }
            groupedCardItems(
                keyPrefix = "settings_entry",
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
                                OverlayDropdownPreference(
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
                                OverlayDropdownPreference(
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

            // ===== 数据源与提醒（SmallTitle 缺省不设，直接分段卡）=====
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
                                            TextField(
                                                value = eiaKey,
                                                onValueChange = { viewModel.setEiaApiKey(it) },
                                                modifier = Modifier.fillMaxWidth(),
                                                singleLine = true,
                                                keyboardOptions = KeyboardOptions(
                                                    keyboardType = KeyboardType.Ascii,
                                                    imeAction = androidx.compose.ui.text.input.ImeAction.Done,
                                                ),
                                                colors = TextFieldDefaults.textFieldColors(
                                                    backgroundColor = MiuixTheme.colorScheme.secondaryContainer,
                                                    labelColor = MiuixTheme.colorScheme.onSecondaryContainer,
                                                    borderColor = Color.Transparent,
                                                ),
                                                cursorBrush = SolidColor(MiuixTheme.colorScheme.onBackground),
                                                visualTransformation = if (showKey) {
                                                    androidx.compose.ui.text.input.VisualTransformation.None
                                                } else {
                                                    androidx.compose.ui.text.input.PasswordVisualTransformation()
                                                },
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
                    add(CardItem("priceAlert") {
            // —— 调价窗口提醒 / 推送通知 ——
            SettingSwitchRow(
                title = stringResource(R.string.koil_set_price_alert),
                subtitle = stringResource(R.string.koil_set_price_alert_sub),
                checked = uiState.priceAlert,
                onCheckedChange = viewModel::setPriceAlert,
            )
                    })
                    add(CardItem("pricePush") {
            SettingSwitchRow(
                title = stringResource(R.string.koil_set_price_push),
                subtitle = stringResource(R.string.koil_set_price_push_sub),
                checked = uiState.pricePush && pushPermissionGranted,
                onCheckedChange = { on ->
                    if (on) {
                        if (pushPermissionGranted) {
                            viewModel.setPricePush(true)
                        } else {
                            // HyperOS 通知权限拒绝后运行时请求不弹框（静默拒绝）→ 直接跳系统通知设置，
                            // 用户开启后返回 App（ON_RESUME）自动补开开关；跳转用 NEW_TASK + fallback 加固
                            pendingOpenPush = true
                            openNotificationSettings(context)
                        }
                    } else {
                        viewModel.setPricePush(false)
                    }
                },
            )
                    })
                    if (!pushPermissionGranted) {
                        add(CardItem("pushDenied") {
                Text(
                    text = stringResource(R.string.koil_set_price_push_denied),
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

                // ===== 数据与备份 =====
                // 6 个出口按「加油记录 / 完整备份 / 油价历史」三组配对，
                // 不分组的话 6 行长得一模一样，用户得逐个读副标题才知道谁跟谁是一对。
                SmallTitle(
                    text = stringResource(R.string.koil_set_section_data),
                    insideMargin = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            // ===== 数据与备份：导出入口 / 云同步开关 / 云同步面板 =====
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
                    if (syncUi.enabled) {
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
                contentPadding = 16.dp,
                widthFraction = 0.90f,
            ) {
                Column {
                    // 不设「数据与备份」标题——入口行本身就叫「导出与备份」，
                    // 弹窗再顶一个近义大标题只是重复占高度，直接让选项当首屏内容。
                    // —— CSV 段：人可读、Excel 能开 ——
                    DataIoDialogItem(
                        title = stringResource(R.string.koil_set_export_records),
                        subtitle = stringResource(R.string.koil_set_export_records_sub),
                        onClick = {
                            showDataIoDialog.value = false
                            viewModel.exportCsv()
                        },
                    )
                    DataIoDialogItem(
                dividerAbove = true,
                        title = stringResource(R.string.koil_set_import_records),
                        subtitle = stringResource(R.string.koil_set_import_records_sub),
                        onClick = {
                            showDataIoDialog.value = false
                            importLauncher.launch(arrayOf("text/csv", "*/*"))
                        },
                    )
                    DataIoDialogItem(
                dividerAbove = true,
                        title = stringResource(R.string.koil_set_export_price_history),
                        subtitle = stringResource(R.string.koil_set_export_price_history_sub),
                        onClick = {
                            showDataIoDialog.value = false
                            viewModel.exportOilPriceHistory()
                        },
                    )

                    // —— JSON 段：整包、只有本 App 能恢复 ——
                    DataIoDialogItem(
                dividerAbove = true,
                        title = stringResource(R.string.koil_set_backup),
                        subtitle = stringResource(R.string.koil_set_backup_sub),
                        onClick = {
                            showDataIoDialog.value = false
                            viewModel.exportBackup()
                        },
                    )
                    DataIoDialogItem(
                dividerAbove = true,
                        title = stringResource(R.string.koil_set_restore),
                        subtitle = stringResource(R.string.koil_set_restore_sub),
                        onClick = {
                            showDataIoDialog.value = false
                            backupLauncher.launch(arrayOf("application/json", "*/*"))
                        },
                    )
                }
            }
        }

        if (showCrashDialog) {
            KOiLGlassDialog(
                show = true,
                onDismissRequest = { showCrashDialog = false },
                contentPadding = 16.dp,
                scrollable = true,
                widthFraction = 0.90f,
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.koil_set_crash_log),
                            fontSize = KOiLType.Headline,
                            fontWeight = FontWeight.Bold,
                            color = MiuixTheme.colorScheme.onBackground,
                            modifier = Modifier.weight(1f),
                        )
                        if (crashCount > 0) {
                            Text(
                                text = pluralStringResource(R.plurals.koil_set_crash_count, crashCount, crashCount),
                                fontSize = KOiLType.BodySmall,
                                color = MiuixTheme.colorScheme.onBackgroundVariant,
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
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
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                    .clickable {
                                        crashScope.launch {
                                            CrashReporter.clear(crashContext)
                                            crashLogText = ""
                                            crashCount = 0
                                            android.widget.Toast.makeText(
                                                crashContext,
                                                crashClearedText,
                                                android.widget.Toast.LENGTH_SHORT,
                                            ).show()
                                        }
                                    }
                                    .padding(vertical = 11.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = stringResource(R.string.koil_set_crash_clear),
                                    fontSize = KOiLType.BodyLarge,
                                    fontWeight = FontWeight.Medium,
                                    color = MiuixTheme.colorScheme.onBackground,
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (crashLogText.isNotBlank()) KOiLColors.Primary else KOiLColors.Primary.copy(alpha = 0.4f))
                                .clickable(enabled = crashLogText.isNotBlank()) {
                                    crashScope.launch {
                                        val ok = withContext(Dispatchers.IO) { CrashReporter.exportToDownloads(crashContext) }
                                        android.widget.Toast.makeText(
                                            crashContext,
                                            if (ok) crashExportOkText else crashExportFailText,
                                            android.widget.Toast.LENGTH_SHORT,
                                        ).show()
                                    }
                                }
                                .padding(vertical = 11.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = stringResource(R.string.koil_set_crash_export),
                                fontSize = KOiLType.BodyLarge,
                                fontWeight = FontWeight.Medium,
                                color = Color.White,
                            )
                        }
                    }
                }
            }

        }


        // 导出/备份/更新结果提示：Toast 短暂提醒（自动消失，不用常驻卡片）
        val exportNotice by viewModel.exportMessage.collectAsStateWithLifecycle()
        val toastContext = LocalContext.current
        val exportText = exportNotice.key?.let { formatNotice(it, exportNotice.args) }
        LaunchedEffect(exportNotice) {
            if (exportText != null) {
                android.widget.Toast.makeText(
                    toastContext,
                    exportText,
                    android.widget.Toast.LENGTH_SHORT,
                ).show()
                viewModel.clearExportMessage()
            }
        }
            }

            // ===== 关于（完整分区）=====
            // 版本、更新检查、诊断集中到一处；组底距走 groupedCardItems 的 outerBottomPadding。
            aboutSection(
                crashCount = crashCount,
                onOpenCrashLog = { showCrashDialog = true },
                onOpenAbout = onOpenAbout,
            )
        }
    }

    // 省份选择弹窗

    // CSV 导入预览弹窗
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
    if (!ok) {
        android.widget.Toast.makeText(
            context,
            R.string.koil_set_about_open_failed,
            android.widget.Toast.LENGTH_SHORT,
        ).show()
    }
    return ok
}

/** 检查入口统一收在 data.update.UpdateChecker（冷启动静默检查与手动检查共用一条通道） */

/**
 * 「上次检查」相对时间（刚刚 / 12 分钟前 / 3 小时前 / 2 天前）。
 * 从未查过（0）或时钟倒挂时返回 null，调用方回落到默认副标题。
 */

/**
 * 「其它」一块（崩溃日志 / 问题反馈 / 关于 KOiL 三行连排），直接产出 lazy item 的
 * LazyListScope 扩展；组底距统一走 groupedCardItems 的 outerBottomPadding，不再手写 CardSegment。
 */
fun LazyListScope.aboutSection(
    crashCount: Int,
    onOpenCrashLog: () -> Unit,
    onOpenAbout: () -> Unit,
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
                )
            },
            CardItem("feedback") {
                val context = LocalContext.current
                SettingRow(
                    title = stringResource(R.string.koil_set_about_feedback),
                    subtitle = stringResource(R.string.koil_set_about_feedback_sub),
                    value = "",
                    onClick = { openExternalUrl(context, "$KOIL_REPO_URL/issues/new") },
                )
            },
            CardItem("about") {
                // 红点只看「有没有新版本」，**不看 seenTag**：首页横幅的 × 只收起那条提醒，
                // 不代表用户不想知道有更新；红点要一直亮到装上最新版为止
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
