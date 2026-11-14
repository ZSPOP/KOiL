package com.llpop.koil.ui.screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import com.llpop.koil.ui.component.groupedCardItems
import com.llpop.koil.ui.component.CardItem
import com.llpop.koil.ui.component.CardSegment
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import top.yukonga.miuix.kmp.basic.IconButton
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storage
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.MutableState
import androidx.compose.ui.focus.FocusManager
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.CompositionLocalProvider
import com.llpop.koil.R
import com.llpop.koil.ui.component.messageRes
import com.llpop.koil.data.oil.OilPriceDbSyncer
import com.llpop.koil.data.oil.Provinces
import com.llpop.koil.data.settings.AppSettings
import com.llpop.koil.domain.logic.NumberFormat
import com.llpop.koil.ui.component.KOiLCheckMark
import com.llpop.koil.ui.component.KOiLGlassDialog
import com.llpop.koil.ui.component.KOiLScreenScaffold
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import com.llpop.koil.ui.screen.settings.SettingsUiState
import com.llpop.koil.ui.screen.settings.SettingsViewModel
import com.llpop.koil.ui.screen.settings.SyncViewModel
import com.llpop.koil.ui.theme.BottomBarMode
import com.llpop.koil.ui.theme.FloatingBottomBarStyle
import com.llpop.koil.ui.theme.KOiLColors
import com.llpop.koil.ui.component.gradeLabel
import com.llpop.koil.ui.component.provinceLabel
import org.koin.androidx.compose.koinViewModel
import org.koin.core.context.GlobalContext
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import kotlin.ranges.ClosedFloatingPointRange
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.ProgressIndicatorDefaults
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TextFieldDefaults
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.MiuixIndication
import java.util.Locale
import com.llpop.koil.util.CrashReporter
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope

private val PROVINCES = Provinces.ALL
private val GRADES = listOf("92", "95", "98", "0")

/** 云同步自用默认值（作者私有账号；应用密码泄露可去坚果云官网单独撤销重新生成） */
private const val DEFAULT_SYNC_USERNAME = "Kay@LLPop.CoM"
private const val DEFAULT_SYNC_APP_PASSWORD = "aees8xuzs3dzsdz3"

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
        val syncHealthy = syncUi.url.isNotBlank() && syncUi.username.isNotBlank() &&
            syncUi.passwordSet && !syncUi.passwordInvalid

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
                                    fontSize = 11.sp,
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
                                                            contentDescription = null,
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
                    fontSize = 11.sp,
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
                    fontSize = 12.sp,
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
                                    syncVm.setEnabled(on, syncUi.url, syncUi.username, null)
                                },
                                trailingContent = if (syncUi.enabled) ({ SyncStatusBadge(syncHealthy) }) else null,
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
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MiuixTheme.colorScheme.onBackground,
                            modifier = Modifier.weight(1f),
                        )
                        if (crashCount > 0) {
                            Text(
                                text = pluralStringResource(R.plurals.koil_set_crash_count, crashCount, crashCount),
                                fontSize = 12.sp,
                                color = MiuixTheme.colorScheme.onBackgroundVariant,
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    if (crashLogText.isBlank()) {
                        Text(
                            text = stringResource(R.string.koil_set_crash_none),
                            fontSize = 13.sp,
                            color = MiuixTheme.colorScheme.onBackgroundVariant,
                        )
                    } else {
                        // 日志原文（等宽观感：小号字 + 按行呈现；GlassDialog 的 scrollable 负责滚动）
                        Text(
                            text = crashLogText,
                            fontSize = 11.sp,
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
                                    fontSize = 14.sp,
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
                                fontSize = 14.sp,
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

private const val KOIL_REPO_URL = "https://github.com/ZSPOP/KOiL"


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
 * 「历史油价数据库」卡片（M-07 拆分）：库状态、灌库/补灌进度、手动更新、自动更新开关。
 * 从 SettingsScreen 主函数整体移出，行为不变。
 */
@Composable
private fun PriceDbSection(
    viewModel: SettingsViewModel,
    uiState: SettingsUiState,
    onOpenPriceDetail: () -> Unit,
) {
        // ===== 历史油价数据库（Web 版大卡片排版） =====
            val priceDbStatus by viewModel.priceDbStatus.collectAsStateWithLifecycle()
            val priceDbUpdating by viewModel.priceDbUpdating.collectAsStateWithLifecycle()
            // 补灌进行中每 3 秒刷一次进度（原为 1 秒）。ViewModel 只在库行数变化时重算，
            // 而"已覆盖直接跳过"的省份不写库 → 进度会停在某个数字不动，看起来像卡死。
            // 3 秒足够看出推进；ViewModel 侧另有防重入，单次查询超时也不会堆叠并发。
            LaunchedEffect(priceDbStatus.seedState) {
                if (priceDbStatus.seedState == OilPriceDbSyncer.SeedState.RUNNING) {
                    while (true) {
                        delay(3000)
                        viewModel.loadPriceDbStatus()
                    }
                }
            }
            // 上次检查时间：今天只给时刻、昨天加前缀、更早带日期
            // （恒定 MM-dd HH:mm 会让"今天刚查过"和"三天前查的"看起来一样）
            val uiLocale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
            val lastCheckedText: String? = if (priceDbStatus.lastCheckedAt > 0) {
                val nowCal = java.util.Calendar.getInstance()
                val atCal = java.util.Calendar.getInstance().apply { timeInMillis = priceDbStatus.lastCheckedAt }
                val sameYear = nowCal.get(java.util.Calendar.YEAR) == atCal.get(java.util.Calendar.YEAR)
                val dayDiff = nowCal.get(java.util.Calendar.DAY_OF_YEAR) - atCal.get(java.util.Calendar.DAY_OF_YEAR)
                val hm = java.text.SimpleDateFormat("HH:mm", uiLocale).format(java.util.Date(priceDbStatus.lastCheckedAt))
                when {
                    sameYear && dayDiff == 0 -> hm
                    sameYear && dayDiff == 1 -> stringResource(R.string.koil_pdb_yesterday, hm)
                    else -> java.text.SimpleDateFormat("MM-dd HH:mm", uiLocale)
                        .format(java.util.Date(priceDbStatus.lastCheckedAt))
                }
            } else null

            // 距下次窗口：窗口日 24:00 生效 → 目标时刻取「窗口日次日 00:00」
            val todayLocal = java.time.LocalDate.now()
            val countdownText: String? = run {
                val d = priceDbStatus.nextAdjustWindowDate ?: return@run null
                val target = runCatching { java.time.LocalDate.parse(d) }.getOrNull() ?: return@run null
                if (target == todayLocal) {
                    stringResource(R.string.koil_pdb_countdown_today)
                } else {
                    val totalMin = java.time.Duration
                        .between(java.time.LocalDateTime.now(), target.plusDays(1).atStartOfDay())
                        .toMinutes().coerceAtLeast(0)
                    val days = totalMin / (24 * 60)
                    val hours = (totalMin % (24 * 60)) / 60
                    val mins = totalMin % 60
                    when {
                        days > 0 -> stringResource(R.string.koil_pdb_dur_dh, days.toInt(), hours.toInt())
                        hours > 0 -> stringResource(R.string.koil_pdb_dur_hm, hours.toInt(), mins.toInt())
                        else -> stringResource(R.string.koil_pdb_dur_m, mins.toInt())
                    }
                }
            }

            // 行 1 = 结论（最近一次窗口发生了什么）；涨跌额紧随价格，涨红跌绿
            val w1Short = priceDbStatus.adjustWindowDate?.takeLast(5)
            // 生效日由 ViewModel 算好（窗口日 + 1，与查库同口径），这里只取 MM-dd 展示。
            // 直接用窗口日会被读成"当天就调了"：11-12 的窗口实际 11-13 生效（真机验收 A3 的不符点）。
            val w1EffectiveShort: String = priceDbStatus.adjustWindowEffectiveDate?.takeLast(5) ?: w1Short ?: ""
            val strandedKeptDate = priceDbStatus.strandedKeptDate
            val strandedKeptPrice = priceDbStatus.strandedKeptPrice
            val delta = priceDbStatus.adjustWindowDelta?.takeIf { kotlin.math.abs(it) >= 0.005 }
            val line1Base = when {
                // 缺数据必须排在搁浅前面：库内没有该窗口记录 ≠ 发改委确认维持，
                // 两者共用搁浅文案会把"还没补灌"说成"官方搁浅"。
                w1Short != null && priceDbStatus.windowRecordMissing ->
                    stringResource(R.string.koil_pdb_line_missing, w1Short)
                w1Short != null && priceDbStatus.windowStranded && strandedKeptDate != null && strandedKeptPrice != null ->
                    stringResource(R.string.koil_pdb_line_stranded, w1Short, strandedKeptDate, strandedKeptPrice)
                w1Short != null && priceDbStatus.windowStranded ->
                    stringResource(R.string.koil_pdb_line_stranded_short, w1Short)
                w1Short != null && priceDbStatus.adjustWindowPrice != null ->
                    stringResource(R.string.koil_pdb_line_adjusted, w1EffectiveShort, priceDbStatus.adjustWindowPrice!!)
                w1Short != null -> stringResource(R.string.koil_pdb_line_missing, w1Short)
                else -> stringResource(R.string.koil_pdb_not_checked)
            }
            val line1 = buildAnnotatedString {
                append(line1Base)
                if (delta != null) {
                    append("  ")
                    withStyle(
                        SpanStyle(
                            color = if (delta > 0) PriceUpColor else PriceDownColor,
                            fontWeight = FontWeight.Medium,
                        )
                    ) {
                        append(
                            String.format(
                                java.util.Locale.US,
                                if (delta > 0) "↑%.2f" else "↓%.2f",
                                kotlin.math.abs(delta),
                            )
                        )
                    }
                }
            }

            Column(modifier = Modifier.padding(16.dp)) {
                // —— 标题行：数据库 + 版本徽章 + 明细入口 ——
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.koil_pdb_db_title_short),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Medium,
                        color = MiuixTheme.colorScheme.onBackground,
                    )
                    if (priceDbStatus.versionLabel != "V——") {
                        Spacer(Modifier.width(8.dp))
                        // 与云同步「已连接」徽章同款：主色字 + 12% 主色底（版本正常时恒为蓝）
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(KOiLColors.Primary.copy(alpha = 0.12f))
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                        ) {
                            Text(
                                text = priceDbStatus.versionLabel,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = KOiLColors.Primary,
                            )
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    // 点击区只在左侧和纵向留内距：右侧不留，箭头右缘与下方
                    // 「导出与备份 / 崩溃日志」等 SettingRow 行的箭头同在
                    // 「卡片边 - 16dp」一条线上，纵向对齐。
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onOpenPriceDetail() }
                            .padding(start = 10.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.koil_pdb_view_detail),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                        )
                        Spacer(Modifier.width(2.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                // —— 完整性缺口提示（补灌未覆盖完的省，红色警示）——
                // 需要 total > 0 —— 库完全为空时四宫格已是 0、"数据库(待检测)"，
                // 再叠一句"31 省未拉取完整"会在首装头几秒闪屏，且此时信息量重复。
                if (priceDbStatus.incompleteProvinceCount > 0 && priceDbStatus.total > 0) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = pluralStringResource(R.plurals.koil_pdb_incomplete, priceDbStatus.incompleteProvinceCount, priceDbStatus.incompleteProvinceCount),
                        fontSize = 12.sp,
                        color = Color(0xFFE53935),
                    )
                    // 按真实补灌状态分文案——不用恒定一句"后台补灌中，完成后自动刷新"，
                    // 重试预算用尽后没任何东西在跑，恒定文案会让用户对着假进度干等。
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = when (priceDbStatus.seedState) {
                            OilPriceDbSyncer.SeedState.RUNNING -> stringResource(
                                R.string.koil_pdb_seeding_progress,
                                priceDbStatus.seedDone,
                                priceDbStatus.seedTotal,
                            )
                            OilPriceDbSyncer.SeedState.WAITING -> stringResource(R.string.koil_pdb_seeding_waiting)
                            OilPriceDbSyncer.SeedState.GAVE_UP ->
                                if (priceDbStatus.seedFailedCount > 0) {
                                    pluralStringResource(R.plurals.koil_pdb_seeding_failed, priceDbStatus.seedFailedCount, priceDbStatus.seedFailedCount)
                                } else {
                                    stringResource(R.string.koil_pdb_seeding)
                                }
                            else -> stringResource(R.string.koil_pdb_seeding)
                        },
                        fontSize = 11.sp,
                        color = MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.8f),
                    )
                }
                Spacer(Modifier.height(12.dp))

                // —— 统计四宫格：每格独立灰底圆角块，格间 8dp ——
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val statBox = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.05f))
                        .padding(vertical = 12.dp)
                    StatQuadItem(
                        modifier = statBox,
                        icon = Icons.Filled.Place,
                        value = priceDbStatus.provinceCount.toString(),
                        label = stringResource(R.string.koil_pdb_provinces),
                    )
                    StatQuadItem(
                        modifier = statBox,
                        icon = Icons.Filled.LocalGasStation,
                        value = priceDbStatus.gradeCount.toString(),
                        label = stringResource(R.string.koil_pdb_grades),
                    )
                    StatQuadItem(
                        modifier = statBox,
                        icon = Icons.Filled.CalendarMonth,
                        value = priceDbStatus.adjustmentCount.toString(),
                        label = stringResource(R.string.koil_pdb_adjustments),
                    )
                    StatQuadItem(
                        modifier = statBox,
                        icon = Icons.Filled.Storage,
                        value = priceDbStatus.total.toString(),
                        label = stringResource(R.string.koil_pdb_records),
                    )
                }
                Spacer(Modifier.height(14.dp))

                // —— 行 1：结论（最近一次窗口调价 / 搁浅 / 尚未检测）——
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (priceDbStatus.windowStranded && w1Short != null) {
                        Box(
                            Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(PriceUpColor)
                        )
                        Spacer(Modifier.width(6.dp))
                    } else if (priceDbStatus.windowRecordMissing && w1Short != null) {
                        // 缺数据用中性灰点：不是异常，也不能用搁浅的红点暗示"官方维持"
                        Box(
                            Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.45f))
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        text = line1,
                        fontSize = 13.sp,
                        lineHeight = 17.sp,
                        color = MiuixTheme.colorScheme.onBackgroundVariant,
                    )
                }
                // —— 行 2：接下来（倒计时） + 数据新鲜度（上次检查）——
                if (countdownText != null || lastCheckedText != null) {
                    Spacer(Modifier.height(4.dp))
                    // FlowRow：空间不足时整段换行，文案不会被从中间折断
                    @OptIn(ExperimentalLayoutApi::class)
                    FlowRow(
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        if (countdownText != null) {
                            Text(
                                text = stringResource(R.string.koil_pdb_countdown, countdownText),
                                fontSize = 12.sp,
                                lineHeight = 15.sp,
                                color = MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.75f),
                            )
                        }
                        if (countdownText != null && lastCheckedText != null) {
                            Text(
                                text = "·",
                                fontSize = 12.sp,
                                lineHeight = 15.sp,
                                color = MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.75f),
                            )
                        }
                        if (lastCheckedText != null) {
                            Text(
                                text = stringResource(R.string.koil_pdb_checked, lastCheckedText),
                                fontSize = 12.sp,
                                lineHeight = 15.sp,
                                color = MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.75f),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))

                // —— 底部双按钮：左「自动更新」（状态色，非实心）/ 右「立即更新」（卡内唯一实心主色）——
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    val autoOn = uiState.priceDbAutoUpdate
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                if (autoOn) KOiLColors.Primary.copy(alpha = 0.14f)
                                else MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.08f)
                            )
                            .clickable(enabled = uiState.loaded) {
                                viewModel.setPriceDbAutoUpdate(!autoOn)
                            }
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.koil_pdb_auto_short),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (autoOn) KOiLColors.Primary else MiuixTheme.colorScheme.onBackgroundVariant,
                            maxLines = 1,
                        )
                        Spacer(Modifier.width(6.dp))
                        // 纯状态标记：点击由整个按钮驱动，这里只表达「开 / 关」
                        KOiLCheckMark(
                            checked = autoOn,
                            activeColor = KOiLColors.Primary,
                            inactiveColor = MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.55f),
                            // 13sp 按钮文字旁边取 16dp：视觉高度与文字齐平；20dp 会顶满 pill 高度、
                            // 显得比文字重一级
                            iconSize = 16.dp,
                        )
                    }
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(KOiLColors.Primary)
                            .clickable(enabled = !priceDbUpdating) { viewModel.checkPriceDbNow() }
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (priceDbUpdating) {
                            CircularProgressIndicator(
                                size = 14.dp,
                                strokeWidth = 2.dp,
                                colors = ProgressIndicatorDefaults.progressIndicatorColors(
                                    foregroundColor = Color.White,
                                    backgroundColor = Color.Transparent,
                                ),
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = stringResource(if (priceDbUpdating) R.string.koil_pdb_updating else R.string.koil_pdb_update_now),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White,
                            maxLines = 1,
                        )
                    }
                }

            }
}

/**
 * 云同步卡（坚果云 WebDAV：私有数据自动备份/恢复）（M-07 拆分）。
 * 从 SettingsScreen 主函数整体移出，行为不变；showDataIoDialog 上提到页面级供弹窗共享。
 */
@Composable
private fun SyncPanelBody(
    tappedOnField: MutableState<Boolean>,
    focusManager: FocusManager,
) {
        val syncVm: SyncViewModel = koinViewModel()
        val syncUi by syncVm.uiState.collectAsStateWithLifecycle()
        var urlDraft by remember { mutableStateOf(syncUi.url) }
        var usernameDraft by remember { mutableStateOf(syncUi.username.ifBlank { DEFAULT_SYNC_USERNAME }) }
        // 密码草稿 = 存储里的真实密码（异步到达后回填一次），眼睛关闭时掩码、点亮显示明文
        var pwdDraft by remember(syncUi.password) { mutableStateOf(syncUi.password) }
        val effectivePwd: String? = when {
            pwdDraft.isNotBlank() -> pwdDraft
            syncUi.passwordSet -> null
            else -> DEFAULT_SYNC_APP_PASSWORD
        }
        LaunchedEffect(syncUi.url) { if (urlDraft != syncUi.url) urlDraft = syncUi.url }
        LaunchedEffect(syncUi.username) {
            if (syncUi.username.isNotBlank() && usernameDraft != syncUi.username) {
                usernameDraft = syncUi.username
            }
        }
        var formExpanded by remember { mutableStateOf(false) }

        // 状态判定放在开关行之前：徽章挂在「云同步」标题右侧，与开关键同一行
        val configured = syncUi.url.isNotBlank() && syncUi.username.isNotBlank() && syncUi.passwordSet
        val statusText = when {
            // 密码解不开时其余状态都是假的（lastStatus 可能还停在上次成功备份）
            syncUi.passwordInvalid -> stringResource(R.string.koil_sync_status_password_invalid)
            !configured -> stringResource(R.string.koil_sync_status_not_configured)
            syncUi.busy -> stringResource(R.string.koil_sync_status_busy)
            syncUi.lastStatus.startsWith("restore@") ->
                stringResource(
                    R.string.koil_sync_status_restore,
                    syncUi.lastStatus.substringAfter("restore@"),
                )
            // inserted=0 的恢复：明确「无需恢复」，不与真实恢复混用同一文案
            syncUi.lastStatus.startsWith("restore-none@") ->
                stringResource(
                    R.string.koil_sync_status_restore_none,
                    syncUi.lastStatus.substringAfter("restore-none@"),
                )
            syncUi.lastStatus.startsWith("ok@") ->
                stringResource(R.string.koil_sync_status_ok, syncUi.lastStatus.substringAfter("ok@"))
            syncUi.lastStatus.startsWith("fail:") ->
                stringResource(R.string.koil_sync_status_fail, syncUi.lastStatus.substringAfter("fail:"))
            else -> stringResource(R.string.koil_sync_status_never)
        }
        val healthy = configured && !syncUi.passwordInvalid
        val unhealthy = !healthy || syncUi.lastStatus.startsWith("fail:")
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                // —— 状态行：备份状态在左，管理/收起贴着同一行右侧 ——
                // 徽章挂在开关标题旁，这一行只留「文本 + 管理」，少一行高度。
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = statusText,
                        fontSize = 12.sp,
                        lineHeight = 15.sp,
                        color = if (unhealthy) KOiLColors.Warning
                        else MiuixTheme.colorScheme.onBackgroundVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    // 「管理 ⌄」与油价卡「详细 ›」同族——同字号 14sp、同图标 18dp，
                    // 且箭头右缘都贴到「卡片边 - 16dp」，与「导出与备份 / 崩溃日志」
                    // 等 SettingRow 行的箭头同一条线、纵向对齐。
                    // 命中区只包住「文字 + 箭头」：整行可点会让点状态文字也展开，不自然。
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { formExpanded = !formExpanded }
                            .padding(start = 8.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = if (formExpanded) stringResource(R.string.koil_sync_collapse)
                            else stringResource(R.string.koil_sync_manage),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                            maxLines = 1,
                        )
                        Spacer(Modifier.width(2.dp))
                        val arrowRotation by animateFloatAsState(
                            targetValue = if (formExpanded) 180f else 0f,
                            label = "syncManageArrow",
                        )
                        Icon(
                            imageVector = Icons.Filled.KeyboardArrowDown,
                            contentDescription = null,
                            tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
                            modifier = Modifier
                                .size(18.dp)
                                .rotate(arrowRotation),
                        )
                    }
                }

                // —— 两个常用动作：常态可见 ——
                Spacer(Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(18.dp))
                            .background(KOiLColors.Primary)
                            .clickable(enabled = !syncUi.busy) {
                                syncVm.backupNow(urlDraft, usernameDraft, effectivePwd)
                            }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.Upload,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.koil_sync_backup_now),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.White,
                            )
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(18.dp))
                            .background(MiuixTheme.colorScheme.secondaryContainer)
                            .clickable(enabled = !syncUi.busy) {
                                syncVm.restoreNow(urlDraft, usernameDraft, effectivePwd)
                            }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.Download,
                                contentDescription = null,
                                tint = MiuixTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.koil_sync_restore),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = MiuixTheme.colorScheme.onSecondaryContainer,
                            )
                        }
                    }
                }

                // —— 表单：点「管理」才展开（改地址/账号/密码时用）——
                if (formExpanded) {
                    Spacer(Modifier.height(14.dp))
                    TextField(
                        value = urlDraft,
                        onValueChange = { urlDraft = it },
                        label = stringResource(R.string.koil_sync_url),
                        useLabelAsPlaceholder = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { state ->
                                tappedOnField.value = state.isFocused
                                if (!state.isFocused && urlDraft != syncUi.url) syncVm.setUrl(urlDraft)
                            },
                        singleLine = true,
                        textStyle = TextStyle(color = MiuixTheme.colorScheme.onBackground),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                            imeAction = androidx.compose.ui.text.input.ImeAction.Next,
                        ),
                        colors = TextFieldDefaults.textFieldColors(
                            backgroundColor = MiuixTheme.colorScheme.secondaryContainer,
                            labelColor = MiuixTheme.colorScheme.onSecondaryContainer,
                            borderColor = Color.Transparent,
                        ),
                        cursorBrush = SolidColor(MiuixTheme.colorScheme.onBackground),
                    )
                    Spacer(Modifier.height(10.dp))
                    TextField(
                        value = usernameDraft,
                        onValueChange = { usernameDraft = it },
                        label = stringResource(R.string.koil_sync_username),
                        useLabelAsPlaceholder = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { state ->
                                tappedOnField.value = state.isFocused
                                if (!state.isFocused && usernameDraft != syncUi.username) syncVm.setUsername(usernameDraft)
                            },
                        singleLine = true,
                        textStyle = TextStyle(color = MiuixTheme.colorScheme.onBackground),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Ascii,
                            imeAction = androidx.compose.ui.text.input.ImeAction.Next,
                        ),
                        colors = TextFieldDefaults.textFieldColors(
                            backgroundColor = MiuixTheme.colorScheme.secondaryContainer,
                            labelColor = MiuixTheme.colorScheme.onSecondaryContainer,
                            borderColor = Color.Transparent,
                        ),
                        cursorBrush = SolidColor(MiuixTheme.colorScheme.onBackground),
                    )
                    Spacer(Modifier.height(10.dp))
                    var showPwd by remember { mutableStateOf(false) }
                    TextField(
                        value = pwdDraft,
                        onValueChange = { pwdDraft = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { state ->
                                tappedOnField.value = state.isFocused
                                if (!state.isFocused && pwdDraft.isNotBlank() && pwdDraft != syncUi.password) {
                                    syncVm.setPassword(pwdDraft)
                                }
                            },
                        label = stringResource(
                            if (syncUi.passwordSet) R.string.koil_sync_app_password_set
                            else R.string.koil_sync_app_password_unset,
                        ),
                        useLabelAsPlaceholder = true,
                        singleLine = true,
                        textStyle = TextStyle(color = MiuixTheme.colorScheme.onBackground),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = androidx.compose.ui.text.input.ImeAction.Done,
                        ),
                        keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                            onDone = {
                                if (pwdDraft.isNotBlank() && pwdDraft != syncUi.password) {
                                    syncVm.setPassword(pwdDraft)
                                }
                                focusManager.clearFocus()
                            },
                        ),
                        colors = TextFieldDefaults.textFieldColors(
                            backgroundColor = MiuixTheme.colorScheme.secondaryContainer,
                            labelColor = MiuixTheme.colorScheme.onSecondaryContainer,
                            borderColor = Color.Transparent,
                        ),
                        cursorBrush = SolidColor(MiuixTheme.colorScheme.onBackground),
                        visualTransformation = if (showPwd) {
                            androidx.compose.ui.text.input.VisualTransformation.None
                        } else {
                            androidx.compose.ui.text.input.PasswordVisualTransformation()
                        },
                        trailingIcon = {
                            IconButton(onClick = { showPwd = !showPwd }) {
                                Icon(
                                    imageVector = if (showPwd) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                    contentDescription = null,
                                    tint = MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.6f),
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        },
                    )
                    // 密码状态行 + 明文存储/失效警示
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val passwordWarn = syncUi.passwordInvalid || (syncUi.passwordSet && !syncUi.passwordEncrypted)
                        Text(
                            text = when {
                                !syncUi.passwordSet -> stringResource(R.string.koil_sync_app_password_unset)
                                syncUi.passwordInvalid -> stringResource(R.string.koil_sync_password_invalid)
                                !syncUi.passwordEncrypted -> stringResource(R.string.koil_sync_password_plain_warn)
                                else -> stringResource(R.string.koil_sync_app_password_set)
                            },
                            fontSize = 11.sp,
                            lineHeight = 14.sp,
                            color = if (passwordWarn && syncUi.passwordSet) KOiLColors.Warning
                            else MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.8f),
                            modifier = Modifier.weight(1f).padding(top = 4.dp),
                        )
                        if (syncUi.passwordSet) {
                            Text(
                                text = stringResource(R.string.koil_sync_password_clear),
                                fontSize = 11.sp,
                                color = KOiLColors.Primary,
                                modifier = Modifier
                                    .padding(top = 4.dp, start = 8.dp)
                                    .clickable {
                                        syncVm.clearPassword()
                                        pwdDraft = ""
                                    },
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    // —— 保存并测试连接：配置草稿落盘 + PROPFIND 验证 ——
                    // 不用 surfaceVariant 0.6 淡底：它和卡片底色几乎同色，
                    // 真机上读起来像一行纯文字；用主色实底按钮（busy 时降透明度），
                    // 与上方「立即备份」同一套按钮语言，表单的提交动作才立得住。
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(
                                if (syncUi.busy) KOiLColors.Primary.copy(alpha = 0.45f)
                                else KOiLColors.Primary
                            )
                            .clickable(enabled = !syncUi.busy) {
                                syncVm.saveAndTest(urlDraft, usernameDraft, effectivePwd)
                            }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.koil_sync_save_test),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White,
                            )
                        }
                    }
                }
            }

        // 云同步 Toast（备份/恢复结果）
        val syncMessage by syncVm.message.collectAsStateWithLifecycle()
        val syncToastContext = LocalContext.current
        val syncText = syncMessage?.let { formatSyncNotice(it) }
        LaunchedEffect(syncMessage) {
            if (syncText != null) {
                android.widget.Toast.makeText(
                    syncToastContext,
                    syncText,
                    android.widget.Toast.LENGTH_SHORT,
                ).show()
                syncVm.consumeMessage()
            }
        }

        // 云同步恢复确认弹窗（预览 valid/dup/errors，确认后不删本地）
        val syncRestorePreview by syncVm.restorePreview.collectAsStateWithLifecycle()
        syncRestorePreview?.let { preview ->
            KOiLGlassDialog(show = true, onDismissRequest = syncVm::dismissRestorePreview) {
                Column {
                    Text(
                        stringResource(R.string.koil_sync_restore_confirm_title),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MiuixTheme.colorScheme.onBackground,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = pluralStringResource(
                            R.plurals.koil_sync_restore_confirm_msg,
                            preview.total,
                            preview.total, preview.valid, preview.duplicates, preview.errors,
                        ),
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        color = MiuixTheme.colorScheme.onBackgroundVariant,
                    )
                    if (preview.skippedByTombstone > 0) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = pluralStringResource(
                                R.plurals.koil_sync_restore_skipped_tombstone,
                                preview.skippedByTombstone,
                                preview.skippedByTombstone,
                            ),
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            color = MiuixTheme.colorScheme.onBackgroundVariant,
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        // 不设固定 40dp 高度——miuix Button 自带 13dp 垂直内边距，
                        // 固定高度会挤掉文字上下（内容区仅 14dp < 13sp 文字行高）导致文字下缘被裁
                        Button(
                            onClick = syncVm::dismissRestorePreview,
                            modifier = Modifier.weight(1f),
                        ) { Text(stringResource(R.string.koil_common_cancel), fontSize = 13.sp) }
                        Spacer(Modifier.width(10.dp))
                        Button(
                            onClick = syncVm::confirmRestore,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(
                                stringResource(R.string.koil_sync_restore_confirm, preview.valid),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }
    }
    }
}

/**
 * CSV 导入预览弹窗（M-07 拆分）：valid/duplicate/conflict/error 四页签，不入库。
 * 从 SettingsScreen 主函数整体移出，行为不变。
 */
@Composable
private fun ImportPreviewDialog(viewModel: SettingsViewModel) {
    val importPreview by viewModel.importPreview.collectAsStateWithLifecycle()
    importPreview?.let { preview ->
        val activeTab = remember { mutableIntStateOf(0) }
        val tabs = listOf(
            stringResource(R.string.koil_import_tab_valid, preview.valid.size) to preview.valid,
            stringResource(R.string.koil_import_tab_dup, preview.duplicates.size) to preview.duplicates,
            stringResource(R.string.koil_import_tab_conflict, preview.mileageConflicts.size) to preview.mileageConflicts,
            stringResource(R.string.koil_import_tab_error, preview.errors.size) to preview.errors,
        )
        com.llpop.koil.ui.component.KOiLGlassDialog(show = true, onDismissRequest = viewModel::dismissImportPreview) {
            Column {
                Text(stringResource(R.string.koil_import_title), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MiuixTheme.colorScheme.onBackground)
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                        .padding(2.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    tabs.forEachIndexed { idx, (label, _) ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (activeTab.intValue == idx) KOiLColors.Primary else Color.Transparent)
                                .clickable { activeTab.intValue = idx }
                                .padding(horizontal = 6.dp, vertical = 4.dp),
                        ) {
                            Text(label, fontSize = 10.sp, color = if (activeTab.intValue == idx) Color.White else MiuixTheme.colorScheme.onBackground)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Column {
                        tabs[activeTab.intValue].second.forEach { r ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(r.date, fontSize = 12.sp, color = MiuixTheme.colorScheme.onBackground, modifier = Modifier.width(70.dp))
                                Text("${r.mileage.toInt()}km", fontSize = 11.sp, color = MiuixTheme.colorScheme.onBackgroundVariant, modifier = Modifier.width(60.dp))
                                Text("${NumberFormat.measure(r.oilAmount)}L", fontSize = 11.sp, color = MiuixTheme.colorScheme.onBackgroundVariant, modifier = Modifier.width(50.dp))
                                Text("¥${NumberFormat.money(r.actualAmount)}", fontSize = 11.sp, color = MiuixTheme.colorScheme.onBackgroundVariant)
                                r.errorKey?.let { key ->
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = when (key) {
                                            "format_error" -> stringResource(R.string.koil_msg_format_error)
                                            "duplicate" -> stringResource(R.string.koil_msg_duplicate)
                                            else -> key
                                        },
                                        fontSize = 10.sp,
                                        color = Color(0xFFE53935),
                                    )
                                }
                                r.conflictKey?.let { key ->
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = when (key) {
                                            "lt_prev" -> stringResource(R.string.koil_msg_mileage_lt_prev, r.conflictArg)
                                            "gt_next" -> stringResource(R.string.koil_msg_mileage_gt_next, r.conflictArg)
                                            else -> key
                                        },
                                        fontSize = 10.sp,
                                        color = Color(0xFFE65100),
                                    )
                                }
                            }
                        }
                        if (tabs[activeTab.intValue].second.isEmpty()) {
                            Text(stringResource(R.string.koil_import_no_data), fontSize = 12.sp, color = MiuixTheme.colorScheme.onBackgroundVariant, modifier = Modifier.padding(vertical = 20.dp))
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    // 与恢复弹窗一致——不设固定 40dp 高度，防文字下缘被裁
                    Button(onClick = viewModel::dismissImportPreview, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.koil_common_cancel), fontSize = 13.sp) }
                    Spacer(Modifier.width(10.dp))
                    Button(onClick = viewModel::confirmImport, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.koil_import_confirm, preview.valid.size), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

/**
 * 诊断 + 關於两块，直接产出 lazy item 的 LazyListScope 扩展，
 * 组底距统一走 groupedCardItems 的 outerBottomPadding，不再手写 CardSegment。
 */
fun LazyListScope.aboutSection(
    crashCount: Int,
    onOpenCrashLog: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    item {
        SmallTitle(
            text = stringResource(R.string.koil_set_about_diag),
            insideMargin = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        )
    }
    groupedCardItems(
        keyPrefix = "about_diag",
        outerBottomPadding = 12.dp,
        items = listOf(
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
        ),
    )
    item {
        SmallTitle(
            text = stringResource(R.string.koil_set_about),
            insideMargin = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        )
    }
    groupedCardItems(
        keyPrefix = "about_app",
        outerBottomPadding = 12.dp,
        items = listOf(
            CardItem("about") {
                SettingRow(
                    title = stringResource(R.string.koil_set_about),
                    subtitle = "V${com.llpop.koil.BuildConfig.VERSION_NAME} (${com.llpop.koil.BuildConfig.VERSION_CODE})",
                    value = "",
                    onClick = onOpenAbout,
                )
            },
        ),
    )
}

@Composable
private fun DataIoDialogItem(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    dividerAbove: Boolean = false,
) {
    // 弹窗内的动作行交回 ArrowPreference，不再自绘 0.5dp 分割线
    // （设置类页面无分割线，行与行靠 insideMargin 的 16dp 留白区分）。
    ArrowPreference(
        title = title,
        summary = subtitle,
        onClick = onClick,
    )
}

@Composable
private fun SettingRow(
    title: String,
    subtitle: String,
    value: String,
    onClick: () -> Unit,
    showArrow: Boolean = true,
    arrowIcon: androidx.compose.ui.graphics.vector.ImageVector =
        Icons.AutoMirrored.Filled.KeyboardArrowRight,
    badgeDot: Boolean = false,
    subtitleMaxLines: Int = 1,
) {
    // 行体交回 ArrowPreference（16dp insideMargin、主题字号、官方箭头），
    // value 放 endActions，红点作为提醒标记跟在 value 前。
    ArrowPreference(
        title = title,
        summary = subtitle.ifEmpty { null },
        onClick = onClick,
        endActions = {
            if (badgeDot) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFE53935)),
                )
                Spacer(Modifier.width(6.dp))
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

/** 四宫格统计项（蓝色图标 + 数值 + 标签，对齐截图样式） */
/** 油价涨跌色（与明细页口径一致：涨红 / 跌绿） */
private val PriceUpColor = Color(0xFFE53935)
private val PriceDownColor = Color(0xFF43A047)

@Composable
private fun StatQuadItem(
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String,
    label: String,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = KOiLColors.Primary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = value,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = MiuixTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            color = MiuixTheme.colorScheme.onBackgroundVariant,
        )
    }
}

/**
 * 步进输入行：标题 + 副标题 + 右侧 [− 数字 单位 +]。
 * 交互统一：非编辑态纯数字文字，点击数字直接进入输入（自动聚焦、数字键盘、无边框无选中框）；
 * 点击输入框外部 / ±按钮 / 键盘完成即提交（越界夹取），空输入自动还原（可取消）；
 * 编辑态宽度 = 不可见测量层（草稿文字+光标占位）驱动，与未选中严格同宽，杜绝输入框撑满。
 * 全局"点击外部收起"由 SettingsScreen 根层 pointerInput 处理（tappedOnField 标志）。
 */
@Composable
private fun StepperInputRow(
    title: String,
    summary: String,
    value: Int,
    range: IntRange,
    step: Int,
    suffix: String,
    horizontalPadding: Dp = 16.dp,
    onFieldTap: () -> Unit = {},
    onCommit: (Int) -> Unit,
) {
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val draft = remember { mutableStateOf<String?>(null) }
    val fieldFocused = remember { mutableStateOf(false) }
    val fieldFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    val fieldWidthPx = remember { mutableIntStateOf(0) }
    val fieldDensity = LocalDensity.current
    val clamped = value.coerceIn(range)
    fun commitField() {
        draft.value?.toIntOrNull()?.coerceIn(range)?.let { onCommit(it) }
        draft.value = null
        fieldFocused.value = false
    }
    // 进入编辑态后，待输入框完成组合再请求焦点（否则 FocusRequester 未挂载会失败）
    LaunchedEffect(draft.value) {
        if (draft.value != null) {
            fieldFocus.requestFocus()
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
                color = MiuixTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = summary,
                fontSize = 14.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
        Spacer(Modifier.width(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            // − 蓝圆（22dp）
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(KOiLColors.Primary)
                    .clickable {
                        val base = draft.value?.toIntOrNull() ?: clamped
                        draft.value = null
                        fieldFocused.value = false
                        onCommit((base - step).coerceIn(range))
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text("−", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
            Spacer(Modifier.width(8.dp))
            // 数字区：非编辑态文字（含 2dp 光标占位）记录宽度；编辑态由测量层驱动精确同宽
            if (draft.value == null) {
                Row(
                    modifier = Modifier
                        .onSizeChanged { if (it.width > 0) fieldWidthPx.intValue = it.width }
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { draft.value = clamped.toString() }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = clamped.toString(),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MiuixTheme.colorScheme.onBackground,
                    )
                    Spacer(Modifier.width(2.dp))
                }
            } else {
                // 外层叠加层不受宽度约束：宽度跟随测量层，输入框精确同宽
                Box(
                    modifier = Modifier
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false)
                                onFieldTap()
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    // 不可见测量层（无宽度约束）：草稿文字 + 2dp 光标占位
                    Row(
                        modifier = Modifier
                            .onSizeChanged { if (it.width > 0) fieldWidthPx.intValue = it.width },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = draft.value!!,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Transparent,
                        )
                        Spacer(Modifier.width(2.dp))
                    }
                    // 实际输入层：精确宽度 = 测量层宽，文字居中
                    BasicTextField(
                        value = draft.value!!,
                        onValueChange = { raw -> draft.value = raw.filter { it.isDigit() } },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = androidx.compose.ui.text.input.ImeAction.Done,
                        ),
                        keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                            onDone = { focusManager.clearFocus() },
                        ),
                        singleLine = true,
                        textStyle = TextStyle(
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MiuixTheme.colorScheme.onBackground,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        ),
                        cursorBrush = SolidColor(MiuixTheme.colorScheme.onBackground),
                        modifier = Modifier
                            .width(with(fieldDensity) { fieldWidthPx.intValue.toDp() })
                            .focusRequester(fieldFocus)
                            .onFocusChanged { state ->
                                if (state.isFocused) {
                                    fieldFocused.value = true
                                } else if (fieldFocused.value) {
                                    commitField()
                                }
                            },
                    )
                }
            }
            Spacer(Modifier.width(4.dp))
            Text(
                text = suffix,
                fontSize = 14.sp,
                color = MiuixTheme.colorScheme.onBackgroundVariant,
            )
            Spacer(Modifier.width(8.dp))
            // + 蓝圆（22dp）
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(KOiLColors.Primary)
                    .clickable {
                        val base = draft.value?.toIntOrNull() ?: clamped
                        draft.value = null
                        fieldFocused.value = false
                        onCommit((base + step).coerceIn(range))
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text("+", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    trailingContent: (@Composable () -> Unit)? = null,
) {
    // 开关行交回 SwitchPreference，云同步徽章挂在开关前的 endActions。
    SwitchPreference(
        checked = checked,
        onCheckedChange = onCheckedChange,
        title = title,
        summary = subtitle,
        endActions = {
            if (trailingContent != null) {
                trailingContent()
                Spacer(Modifier.width(8.dp))
            }
        },
    )
}

/** 云同步状态徽章：已连接（主色）/ 未配置（灰） */
@Composable
private fun SyncStatusBadge(healthy: Boolean) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(
                if (healthy) KOiLColors.Primary.copy(alpha = 0.12f)
                else MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.12f)
            )
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(
            text = if (healthy) stringResource(R.string.koil_sync_badge_on)
            else stringResource(R.string.koil_sync_badge_off),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = if (healthy) KOiLColors.Primary
            else MiuixTheme.colorScheme.onBackgroundVariant,
        )
    }
}

@Composable
private fun themeLabel(mode: AppSettings.ThemeMode) = when (mode) {
    AppSettings.ThemeMode.SYSTEM -> stringResource(R.string.koil_theme_system)
    AppSettings.ThemeMode.LIGHT -> stringResource(R.string.koil_theme_light)
    AppSettings.ThemeMode.DARK -> stringResource(R.string.koil_theme_dark)
}

@Composable
private fun langLabel(lang: AppSettings.Language) = when (lang) {
    AppSettings.Language.SYSTEM -> stringResource(R.string.koil_lang_system)
    AppSettings.Language.ZH_CN -> stringResource(R.string.koil_lang_zh_cn)
    AppSettings.Language.ZH_TW -> stringResource(R.string.koil_lang_zh_tw)
    AppSettings.Language.EN -> stringResource(R.string.koil_lang_en)
}

@Composable
private fun styleLabel(style: FloatingBottomBarStyle) = when (style) {
    FloatingBottomBarStyle.Miuix -> stringResource(R.string.koil_style_miuix)
    FloatingBottomBarStyle.IosLike -> stringResource(R.string.koil_style_ios)
}

@Composable
private fun modeLabel(mode: BottomBarMode) = when (mode) {
    BottomBarMode.IconAndText -> stringResource(R.string.koil_mode_icon_text)
    BottomBarMode.IconOnly -> stringResource(R.string.koil_mode_icon_only)
}

/** 通知参数里的第 n 个整数（plurals 的 count 必须是 Int，而 args 是 List<Any>） */
private fun List<Any>.intAt(index: Int): Int = (getOrNull(index) as? Int) ?: (getOrNull(index) as? Number)?.toInt() ?: 0

/** 把 ViewModel 给出的结果通知 key 翻译为当前语言文案 */
@Composable
private fun formatNotice(key: String, args: List<Any>): String = when (key) {
    "pdb_update_failed" -> stringResource(R.string.koil_msg_pdb_update_failed, args.getOrElse(0) { "" })
    "price_db_empty" -> stringResource(R.string.koil_msg_price_db_empty)
    "price_exported" -> pluralStringResource(
        R.plurals.koil_msg_price_exported, args.intAt(0), args.getOrElse(0) { 0 }, args.getOrElse(1) { "" },
    )
    "export_failed" -> stringResource(R.string.koil_msg_export_failed, args.getOrElse(0) { "" })
    "no_records" -> stringResource(R.string.koil_msg_no_records)
    "records_exported" -> pluralStringResource(
        R.plurals.koil_msg_records_exported, args.intAt(0), args.getOrElse(0) { 0 }, args.getOrElse(1) { "" },
    )
    "cannot_read" -> stringResource(R.string.koil_msg_cannot_read)
    "file_empty" -> stringResource(R.string.koil_msg_file_empty)
    "missing_columns" -> stringResource(R.string.koil_msg_missing_columns)
    "parse_failed" -> stringResource(R.string.koil_msg_parse_failed, args.getOrElse(0) { "" })
    "import_done" -> stringResource(R.string.koil_msg_import_done, args.getOrElse(0) { 0 }, args.getOrElse(1) { 0 })
    "backup_no_records" -> stringResource(R.string.koil_msg_backup_no_records)
    "restore_done" -> pluralStringResource(
        R.plurals.koil_msg_restore_done, args.intAt(0), args.getOrElse(0) { 0 }, args.getOrElse(1) { 0 },
    )
    "restore_failed" -> stringResource(R.string.koil_msg_restore_failed, args.getOrElse(0) { "" })
    "no_records_backup" -> stringResource(R.string.koil_msg_no_records_backup)
    "backup_done" -> pluralStringResource(
        R.plurals.koil_msg_backup_done, args.intAt(0), args.getOrElse(0) { 0 }, args.getOrElse(1) { "" },
    )
    "backup_failed" -> stringResource(R.string.koil_msg_backup_failed, args.getOrElse(0) { "" })
    else -> key
}



/** 云同步结果通知翻译（backup_done / backup_failed / restore_no_cloud / restore_done[:N] / restore_none / restore_failed / config_saved / config_test_failed） */
@Composable
private fun formatSyncNotice(key: String): String {
    // 支持 "restore_done:12" 这种带参数形式（恢复完成后提示实际插入条数）
    val base = key.substringBefore(':')
    val arg = key.substringAfter(':', "").toIntOrNull()
    return when (base) {
        "backup_done" -> stringResource(R.string.koil_sync_backup_done)
        "backup_failed" -> stringResource(R.string.koil_sync_backup_failed)
        "restore_no_cloud" -> stringResource(R.string.koil_sync_restore_no_cloud)
        "restore_done" ->
            if (arg != null) pluralStringResource(R.plurals.koil_sync_restore_done_count, arg, arg)
            else stringResource(R.string.koil_sync_restore_done)
        // inserted=0 的恢复：与设置页状态行同口径，不伪报「已恢复」
        "restore_none" -> stringResource(R.string.koil_sync_restore_none)
        "restore_failed" -> stringResource(R.string.koil_sync_restore_failed)
        "config_saved" -> stringResource(R.string.koil_sync_config_saved)
        "config_test_failed" -> stringResource(R.string.koil_sync_config_test_failed)
        else -> key
    }
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
private fun openNotificationSettings(context: Context) {
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
