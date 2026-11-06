package com.llpop.koil.ui.screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextStyle
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
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storage
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
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
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.runtime.DisposableEffect
import com.llpop.koil.R
import com.llpop.koil.data.oil.Provinces
import com.llpop.koil.data.settings.AppSettings
import com.llpop.koil.domain.logic.NumberFormat
import com.llpop.koil.ui.component.KOiLBlurBackground
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
import kotlinx.coroutines.delay
import kotlin.ranges.ClosedFloatingPointRange
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TextFieldDefaults
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
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
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // 全局焦点管理 + 偏差输入框点击标志（用于"点击输入框外部即收起提交"，无需坐标计算）
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val tappedOnField = remember { mutableStateOf(false) }

    val importLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let { viewModel.previewCsv(it) } }

    val backupLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let { viewModel.importBackup(it) } }

    // v1.5.28：权限联动——点开开关先请求权限；拒绝则开关保持关（不再"假开"）
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

    // v1.5.28：通知权限联动——未授权恒显示关；点开先请求；拒绝保持关 + 提示
    val context = LocalContext.current
    var pushPermissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    // v1.5.29：用户点了开关想开但未授权 → 跳系统通知设置后等待返回；ON_RESUME 检测到已授权则自动打开开关
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
                // v1.5.29：跳系统通知设置授权返回后，自动补开推送开关（用户刚表达过"想开"的意图）
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
        viewModel.loadPriceDbStatus()
        viewModel.loadEiaApiKey()
    }

    KOiLScreenScaffold(
        title = stringResource(R.string.koil_set_title),
        modifier = modifier,
    ) { _, topPadding, scrollBehavior, _ ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                // v1.6.2：edge-to-edge 下键盘不再自动 resize 窗口，缺了 imePadding 时
                // 底部输入项（EIA Key / 阈值）会被键盘盖住且滚不到
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp)
                // 点击输入框外部任意位置 = 收起焦点并提交（requireUnconsumed=false 确保子按钮消费后仍触发）
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
        ) {
            // 顶部避让 TopAppBar（含状态栏，折叠时高度联动）
            Spacer(Modifier.height(topPadding))

        // ===== 通用 =====
        SectionTitle(stringResource(R.string.koil_set_section_general))
        SettingsCard {
            // 外观（主题模式）：对齐 Mishka 下拉弹窗选择（OverlayDropdownPreference）
            val themeModes = AppSettings.ThemeMode.entries
            val themeModeIdx = themeModes.indexOf(uiState.themeMode).coerceAtLeast(0)
            OverlayDropdownPreference(
                items = themeModes.map { themeLabel(it) },
                selectedIndex = themeModeIdx,
                title = stringResource(R.string.koil_set_appearance),
                summary = themeModes.getOrElse(themeModeIdx) { themeModes.first() }
                    .let { themeLabel(it) },
                onSelectedIndexChange = { index ->
                    viewModel.setThemeMode(themeModes[index])
                },
            )
            SettingDivider()
            // 语言：对齐 Mishka 下拉弹窗选择
            val languages = AppSettings.Language.entries
            val languageIdx = languages.indexOf(uiState.language).coerceAtLeast(0)
            OverlayDropdownPreference(
                items = languages.map { langLabel(it) },
                selectedIndex = languageIdx,
                title = stringResource(R.string.koil_set_language),
                summary = languages.getOrElse(languageIdx) { languages.first() }
                    .let { langLabel(it) },
                onSelectedIndexChange = { index ->
                    viewModel.setLanguage(languages[index])
                },
            )
        }
        Spacer(Modifier.height(16.dp))

        // ===== 导航栏（对齐 Mishka ThemeSettings：Dropdown 弹窗选择）=====
        // 用 WindowDropdownPreference（系统 Dialog 渲染菜单）而非 OverlayDropdownPreference：
        // Mishka 的底栏模式位于独立二级页「外观与主题」，其 Overlay 弹窗依赖 Scaffold 内
        // MiuixPopupHost 渲染；KOiL 为「外层 Scaffold + Pager + 页内 Scaffold」双层嵌套，
        // Overlay 宿主层级错位导致点击闪退。Window 版同样式同交互，但走系统 Dialog，不闪退。
        SectionTitle(stringResource(R.string.koil_set_section_nav))
        SettingsCard {
            // v1.5.47：设置异步加载完成前不渲染开关/下拉（杜绝默认值先行导致的"关→开"闪烁）
            if (uiState.loaded) {
            SettingSwitchRow(
                            title = stringResource(R.string.koil_set_floating_bar),
                subtitle = stringResource(R.string.koil_set_floating_bar_sub),
                checked = uiState.floatingBottomBar,
                onCheckedChange = viewModel::setFloatingBottomBar,
            )
            // 悬浮样式：仅悬浮导航条开启时展示
            if (uiState.floatingBottomBar) {
                SettingDivider()
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
            }
            SettingDivider()
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
            }
        }
        Spacer(Modifier.height(16.dp))

        // ===== 录入 =====
        SectionTitle(stringResource(R.string.koil_set_section_entry))
        SettingsCard {
            // v1.5.47：设置异步加载完成前不渲染开关/下拉（杜绝"关→开"闪烁）
            if (uiState.loaded) {
            // 定位开关：开启后自动定位省份并写入默认省份
            // v1.5.28：权限联动——未授权恒显示关；点开先请求权限，同意才真正开启
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
                                arrayOf(
                                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                                    android.Manifest.permission.ACCESS_COARSE_LOCATION,
                                )
                            )
                        }
                    } else {
                        viewModel.setLocationEnabled(false)
                    }
                },
            )
            // v1.5.28：未授权提示行（点击重新请求）；v1.5.30：间距收紧贴近副标题
            if (!locationPermissionGranted) {
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
                                arrayOf(
                                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                                    android.Manifest.permission.ACCESS_COARSE_LOCATION,
                                )
                            )
                        },
                )
            }
            // 默认省份：仅定位关闭时展示（开启时由自动定位决定，隐藏手动选择）
            if (!uiState.locationEnabled) {
                SettingDivider()
                val provinces = PROVINCES
                val provinceIdx = provinces.indexOf(uiState.defaultProvince).coerceAtLeast(0)
                OverlayDropdownPreference(
                    // v1.6.7：省份是数据库主键（中文），列表与摘要都走显示名映射，否则英文界面下
                    // 这一栏恒为中文，且选完显示的还是中文（真机反馈）
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
            }
            SettingDivider()
            SettingSwitchRow(
                title = stringResource(R.string.koil_set_keep_grade),
                subtitle = stringResource(R.string.koil_set_keep_grade_sub),
                checked = uiState.keepLastGrade,
                onCheckedChange = viewModel::setKeepLastGrade,
            )
            // 默认油号：仅"沿用上次油号"关闭时展示（开启时沿用上次，无需选择）
            if (!uiState.keepLastGrade) {
                SettingDivider()
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
            }
            // 阈值与油箱容量：录入时的校验/估算参数，归到同一张卡
            SettingDivider()
            StepperInputRow(
                title = stringResource(R.string.koil_set_abnormal),
                summary = stringResource(R.string.koil_set_abnormal_sum, uiState.abnormalThreshold.toInt()),
                value = uiState.abnormalThreshold.toInt(),
                range = 0..50,
                step = 1,
                suffix = "%",
                onFieldTap = { tappedOnField.value = true },
                onCommit = { viewModel.setAbnormalThreshold(it.toDouble()) },
            )
            SettingDivider()
            StepperInputRow(
                title = stringResource(R.string.koil_set_deviation),
                summary = stringResource(R.string.koil_set_deviation_sum, uiState.deviationThreshold.toInt()),
                value = uiState.deviationThreshold.toInt(),
                range = 1..50,
                step = 1,
                suffix = "%",
                onFieldTap = { tappedOnField.value = true },
                onCommit = { viewModel.setDeviationThreshold(it.toDouble()) },
            )
            SettingDivider()
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
            }
        }
        Spacer(Modifier.height(16.dp))

        // ===== 历史油价数据库（Web 版大卡片排版） =====
        SectionTitle(stringResource(R.string.koil_set_section_price_db))
        KOiLBlurBackground(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 20f,
        ) {
            val priceDbStatus by viewModel.priceDbStatus.collectAsStateWithLifecycle()
            val pendingItems by viewModel.pendingItems.collectAsStateWithLifecycle()
            val priceDbUpdating by viewModel.priceDbUpdating.collectAsStateWithLifecycle()
            val latestShort = (priceDbStatus.lastPriceChangeDate ?: priceDbStatus.latestDate).takeLast(5)
            val versionShort = (priceDbStatus.versionDate ?: priceDbStatus.latestDate).takeLast(5)
            // 最近检查时间格式化（MM/dd HH:mm），未检查过显示 "--"
            // v1.6.2：语言用可观察的 Configuration locale（原 Locale.getDefault() 在组合内不可观察，
            // 切换语言后时间格式不会跟着变）
            val uiLocale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
            val lastCheckedText = if (priceDbStatus.lastCheckedAt > 0) {
                java.text.SimpleDateFormat("MM-dd HH:mm", uiLocale)
                    .format(java.util.Date(priceDbStatus.lastCheckedAt))
            } else "--"

            Column(modifier = Modifier.padding(16.dp)) {
                // —— 标题（油价数据库 + 版本号，有版本号时并入；v1.5.0：字号对齐下方开关行）——
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (priceDbStatus.versionLabel != "V——") {
                            stringResource(R.string.koil_pdb_db_title, priceDbStatus.versionLabel)
                        } else {
                            stringResource(R.string.koil_pdb_db_title_plain)
                        },
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Medium,
                        color = MiuixTheme.colorScheme.onBackground,
                    )
                    Spacer(Modifier.weight(1f))
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onOpenPriceDetail() }
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.koil_pdb_view_detail),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            // v1.5.1：颜色与设置页右侧文案（匯出/匯入/備份等 SettingRow value）完全一致
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
                // v1.5.0：版本号标题行与下方数据隔断（同款 0.5dp 分割线）
                Spacer(Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(0.5.dp)
                        .background(MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.12f))
                )
                // —— 完整性缺口提示（补灌未覆盖完的省，红色警示）——
                if (priceDbStatus.incompleteProvinceCount > 0) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = pluralStringResource(R.plurals.koil_pdb_incomplete, priceDbStatus.incompleteProvinceCount, priceDbStatus.incompleteProvinceCount),
                        fontSize = 12.sp,
                        color = Color(0xFFE53935),
                    )
                    // v1.5.24：缺口存在即提示后台补灌中（补完缺口归零、文案自动消失）
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.koil_pdb_seeding),
                        fontSize = 11.sp,
                        color = MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.8f),
                    )
                }
                Spacer(Modifier.height(12.dp))

                // —— 统计四宫格（蓝色图标 + 数值 + 标签，对齐截图样式）——
                Row(modifier = Modifier.fillMaxWidth()) {
                    StatQuadItem(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Filled.Place,
                        value = priceDbStatus.provinceCount.toString(),
                        label = stringResource(R.string.koil_pdb_provinces),
                    )
                    StatQuadItem(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Filled.LocalGasStation,
                        value = priceDbStatus.gradeCount.toString(),
                        label = stringResource(R.string.koil_pdb_grades),
                    )
                    StatQuadItem(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Filled.CalendarMonth,
                        value = priceDbStatus.adjustmentCount.toString(),
                        label = stringResource(R.string.koil_pdb_adjustments),
                    )
                    StatQuadItem(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Filled.Storage,
                        value = priceDbStatus.total.toString(),
                        label = stringResource(R.string.koil_pdb_records),
                    )
                }
                Spacer(Modifier.height(12.dp))

                // —— 分割线（四宫格与下方内容分隔）——
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(0.5.dp)
                        .background(MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.12f))
                )
                Spacer(Modifier.height(12.dp))

                // —— 窗口链条 + 更新于（左两行） + 立即更新（右，垂直居中）——
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        val w1Short = priceDbStatus.adjustWindowDate?.let { if (it.length >= 5) it.substring(5) else it }
                        val w2Short = priceDbStatus.nextAdjustWindowDate?.let { if (it.length >= 5) it.substring(5) else it }
                        // 行1：調整日（单独一行，含搁浅/调价状态；搁浅显示维持的上一条调价日+价格）
                        val strandedKeptDate = priceDbStatus.strandedKeptDate
                        val strandedKeptPrice = priceDbStatus.strandedKeptPrice
                        Text(
                            text = when {
                                w1Short != null && priceDbStatus.windowStranded && strandedKeptDate != null && strandedKeptPrice != null ->
                                    stringResource(R.string.koil_pdb_adjust_stranded, w1Short, strandedKeptDate, strandedKeptPrice)
                                w1Short != null && priceDbStatus.windowStranded ->
                                    stringResource(R.string.koil_pdb_adjust_stranded_short, w1Short)
                                w1Short != null && priceDbStatus.adjustWindowPrice != null ->
                                    stringResource(R.string.koil_pdb_adjust_adjusted, w1Short, priceDbStatus.adjustWindowPrice!!)
                                w1Short != null ->
                                    stringResource(R.string.koil_pdb_adjust_stranded_short, w1Short)
                                else ->
                                    stringResource(R.string.koil_pdb_not_checked)
                            },
                            fontSize = 12.sp,
                            lineHeight = 15.sp,
                            color = MiuixTheme.colorScheme.onBackgroundVariant,
                        )
                        Spacer(Modifier.height(4.dp))
                        // 行2：下個窗口 ｜ 最近更新。v1.6.8 改 FlowRow：原先裸 Row 在空间不足时
                        // 会把 "Last update: 11-06 01:58" 从中间折断，"01:58" 孤零零挂到下一行
                        //（真机截图反馈）；FlowRow 放不下时整段换行，文案永远完整。
                        @OptIn(ExperimentalLayoutApi::class)
                        FlowRow(
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            if (w2Short != null) {
                                Text(
                                    text = stringResource(R.string.koil_pdb_next_window, w2Short),
                                    fontSize = 12.sp,
                                    lineHeight = 15.sp,
                                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                                )
                                Box(
                                    Modifier
                                        .width(1.dp)
                                        .height(12.dp)
                                        .background(MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.35f))
                                )
                            }
                            Text(
                                text = stringResource(R.string.koil_pdb_last_update, lastCheckedText),
                                fontSize = 12.sp,
                                lineHeight = 15.sp,
                                color = MiuixTheme.colorScheme.onBackgroundVariant,
                            )
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(18.dp))
                            .background(KOiLColors.Primary)
                            .clickable(enabled = !priceDbUpdating) { viewModel.checkPriceDbNow() }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (priceDbUpdating) {
                                androidx.compose.material3.CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 2.dp,
                                    color = Color.White,
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
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.White,
                            )
                        }
                    }
                }
                // —— 待确认更新（有数据需核实时才展开；间隔与横线一起隐藏）——
                if (pendingItems.isNotEmpty()) {
                    // vc282：间距移进条件内——拆卡后它是卡 A 的末块，隐藏时卡底不该留 12dp 空档
                    Spacer(Modifier.height(12.dp))
                    // 展开时上边界横线（隐藏时此线不出现，下方仅保留一条）
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(0.5.dp)
                            .background(MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.12f))
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.koil_pdb_pending_title),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Medium,
                        color = MiuixTheme.colorScheme.onBackground,
                    )
                    Spacer(Modifier.height(10.dp))
                    pendingItems.forEach { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = "${provinceLabel(item.province)} · ${gradeLabel(item.grade)} · ${item.effectiveDate}",
                                    fontSize = 12.sp,
                                    color = MiuixTheme.colorScheme.onBackground,
                                )
                                Text(
                                    text = stringResource(
                                        R.string.koil_set_primary_secondary_price,
                                        item.primaryPrice ?: 0.0,
                                        item.secondaryPrice ?: 0.0,
                                    ),
                                    fontSize = 11.sp,
                                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                                )
                            }
                            Text(
                                text = stringResource(R.string.koil_set_accept_primary),
                                fontSize = 11.sp,
                                color = KOiLColors.Primary,
                                modifier = Modifier
                                    .clickable { viewModel.acceptPending(item.id, true) }
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                            Text(
                                text = stringResource(R.string.koil_set_accept_secondary),
                                fontSize = 11.sp,
                                color = KOiLColors.Primary,
                                modifier = Modifier
                                    .clickable { viewModel.acceptPending(item.id, false) }
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                            Text(
                                text = stringResource(R.string.koil_set_ignore),
                                fontSize = 11.sp,
                                color = Color(0xFFE53935),
                                modifier = Modifier
                                    .clickable { viewModel.ignorePending(item.id) }
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }
                    }
                    // 展开时下边界间距（隐藏时随之消失）
                    Spacer(Modifier.height(12.dp))
                }

            }
        }
        Spacer(Modifier.height(16.dp))

        // ===== 数据源与更新（vc282：从油价大卡拆出，状态与配置分离）=====
        // 原来「自动更新 / EIA / 同步来源」压在油价大卡最底部，整卡近 2/3 屏，
        // 想改 EIA 得先滚过全部统计与窗口信息。拆开后：上一张卡只看状态，这一张只管配置。
        KOiLBlurBackground(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 20f,
        ) {
            val priceDbStatus by viewModel.priceDbStatus.collectAsStateWithLifecycle()
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.koil_set_price_sources),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(0.5.dp)
                        .background(MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.12f))
                )
                Spacer(Modifier.height(12.dp))

                // —— 自动更新开关 ——
                if (uiState.loaded) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.koil_set_price_db_auto),
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Medium,
                                color = MiuixTheme.colorScheme.onBackground,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = stringResource(R.string.koil_pdb_auto_sub),
                                fontSize = 14.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                        Switch(
                            checked = uiState.priceDbAutoUpdate,
                            onCheckedChange = viewModel::setPriceDbAutoUpdate,
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))

                // —— EIA API Key（原油估算数据源；开关开=用 EIA 隐藏输入框，
                //    关=直接用腾讯，显示输入框供修改 Key，改完再打开即启用新 Key）——
                val eiaKey by viewModel.eiaApiKey.collectAsStateWithLifecycle()
                val eiaEnabled by viewModel.eiaEnabled.collectAsStateWithLifecycle()
                val eiaLoaded by viewModel.eiaLoaded.collectAsStateWithLifecycle()
                // EIA 配置加载完成前不渲染开关/输入框（杜绝默认值先行闪烁）
                if (eiaLoaded) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(0.5.dp)
                            .background(MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.12f))
                    )
                    Spacer(Modifier.height(12.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = stringResource(R.string.koil_set_eia_key_title),
                                modifier = Modifier.weight(1f),
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Medium,
                                color = MiuixTheme.colorScheme.onBackground,
                            )
                            Switch(
                                checked = eiaEnabled,
                                onCheckedChange = viewModel::setEiaEnabled,
                            )
                        }
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = stringResource(R.string.koil_set_eia_key_sub),
                            fontSize = 14.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                        if (!eiaEnabled) {
                            Spacer(Modifier.height(8.dp))
                            var showEiaKey by remember { mutableStateOf(false) }
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
                                visualTransformation = if (showEiaKey) {
                                    androidx.compose.ui.text.input.VisualTransformation.None
                                } else {
                                    androidx.compose.ui.text.input.PasswordVisualTransformation()
                                },
                                trailingIcon = {
                                    IconButton(onClick = { showEiaKey = !showEiaKey }) {
                                        Icon(
                                            imageVector = if (showEiaKey) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                            contentDescription = null,
                                            tint = MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.6f),
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                },
                            )
                        }
                    }
                }
                // —— 同步来源（本卡最底）——
                if (priceDbStatus.lastSyncSources.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(0.5.dp)
                            .background(MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.12f))
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = stringResource(R.string.koil_pdb_sync_sources, priceDbStatus.lastSyncSources),
                        fontSize = 12.sp,
                        lineHeight = 15.sp,
                        color = MiuixTheme.colorScheme.onBackgroundVariant,
                    )
                    Spacer(Modifier.height(1.dp))
                }
            }
        }


        // ===== 崩溃日志（v1.6.8：本地累积 + 查看/导出，仅存本机不上传）=====
        val crashContext = LocalContext.current
        val crashScope = rememberCoroutineScope()
        var showCrashDialog by remember { mutableStateOf(false) }
        var crashLogText by remember { mutableStateOf("") }
        var crashCount by remember { mutableIntStateOf(0) }
        // 首帧与每次打开弹窗都刷新（导出/清空后重新打开也拿到最新值）
        LaunchedEffect(showCrashDialog) {
            crashLogText = withContext(Dispatchers.IO) { CrashReporter.readLogs(crashContext) }
            crashCount = CrashReporter.countEntries(crashLogText)
        }

        // ===== 提醒：由数据更新触发，跟数据卡同组 =====
        SettingsCard {
            SettingSwitchRow(
                title = stringResource(R.string.koil_set_price_alert),
                subtitle = stringResource(R.string.koil_set_price_alert_sub),
                checked = uiState.priceAlert,
                onCheckedChange = viewModel::setPriceAlert,
            )
            SettingDivider()
            // 油价推送通知开关（v1.5.28：权限联动——未授权恒显示关；点开先请求系统权限，同意才开）
            SettingSwitchRow(
                title = stringResource(R.string.koil_set_price_push),
                subtitle = stringResource(R.string.koil_set_price_push_sub),
                checked = uiState.pricePush && pushPermissionGranted,
                onCheckedChange = { on ->
                    if (on) {
                        if (pushPermissionGranted) {
                            viewModel.setPricePush(true)
                        } else {
                            // v1.5.29：HyperOS 通知权限拒绝后运行时请求不弹框（静默拒绝）→ 直接跳系统通知设置，
                            // 用户开启后返回 App（ON_RESUME）自动补开开关；v1.5.30：跳转加固（NEW_TASK + fallback）
                            pendingOpenPush = true
                            openNotificationSettings(context)
                        }
                    } else {
                        viewModel.setPricePush(false)
                    }
                },
            )
            // v1.5.28：系统通知权限未开启提示行（点击跳系统通知设置一键开启）；v1.5.30：间距收紧 + 跳转加固
            if (!pushPermissionGranted) {
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
            }
        }
        Spacer(Modifier.height(16.dp))

        // ===== 数据与备份 =====
        // v1.6.3 重构：6 个出口按「加油记录 / 完整备份 / 油价历史」三组配对，
        // 之前 6 行长得一模一样，用户得逐个读副标题才知道谁跟谁是一对。
        SectionTitle(stringResource(R.string.koil_set_section_data))

        // ===== 云同步（坚果云 WebDAV：私有数据自动备份/恢复） =====
        // v1.6.3 重构：表单默认收起，常态只显示「状态 + 立即备份/从云端恢复」。
        // 旧版三行输入各带一个锁定开关（改个账号要先解锁再上锁，共 6 次点击），是设置页最大的交互负担。
        // vc282：不再作为本组最后一张卡，而是提到最前——云同步是「持续生效」的能力，
        // 下面的导出/备份是「一次性动作」，先讲机制再讲出口。
        val syncVm: SyncViewModel = koinViewModel()
        val syncUi by syncVm.uiState.collectAsStateWithLifecycle()
        KOiLBlurBackground(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 20f,
        ) {
            Column(modifier = Modifier.padding(0.dp)) {
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

                // 状态判定提到开关行之前：徽章现在挂在「云同步」标题右侧，与开关键同一行
                val configured = syncUi.url.isNotBlank() && syncUi.username.isNotBlank() && syncUi.passwordSet
                val statusText = when {
                    // 密码解不开时其余状态都是假的（lastStatus 可能还停在上次成功备份）
                    syncUi.passwordInvalid -> stringResource(R.string.koil_sync_status_password_invalid)
                    !configured -> stringResource(R.string.koil_sync_status_not_configured)
                    syncUi.busy -> stringResource(R.string.koil_sync_status_busy)
                    syncUi.lastStatus.startsWith("ok@") ->
                        stringResource(R.string.koil_sync_status_ok, syncUi.lastStatus.substringAfter("ok@"))
                    syncUi.lastStatus.startsWith("fail:") ->
                        stringResource(R.string.koil_sync_status_fail, syncUi.lastStatus.substringAfter("fail:"))
                    else -> stringResource(R.string.koil_sync_status_never)
                }
                val healthy = configured && !syncUi.passwordInvalid
                val unhealthy = !healthy || syncUi.lastStatus.startsWith("fail:")

                if (syncUi.loaded) {
                    SettingSwitchRow(
                        title = stringResource(R.string.koil_sync_switch),
                        subtitle = stringResource(R.string.koil_sync_switch_sub),
                        checked = syncUi.enabled,
                        onCheckedChange = { on ->
                            syncVm.setEnabled(on, urlDraft, usernameDraft, effectivePwd)
                        },
                        // 徽章只在开启时出现（关闭状态说「未连接」没有意义）
                        trailingContent = if (syncUi.enabled) ({ SyncStatusBadge(healthy) }) else null,
                    )
                }
                if (syncUi.enabled) {
                    SettingDivider()
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        // —— 状态行：备份状态在左，管理/收起贴着同一行右侧 ——
                        // 徽章已上移到开关标题旁，这一行只留「文本 + 管理」，少一行高度。
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
                            Text(
                                text = if (formExpanded) stringResource(R.string.koil_sync_collapse)
                                else stringResource(R.string.koil_sync_manage),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { formExpanded = !formExpanded }
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                            )
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
                            // vc282：原本是 surfaceVariant 0.6 的淡底，和卡片底色几乎同色，
                            // 真机上读起来像一行纯文字。改成主色实底按钮（busy 时降透明度），
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
                    Spacer(Modifier.height(12.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        // v1.5.44：去掉固定 40dp 高度——miuix Button 自带 13dp 垂直内边距，
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

        Spacer(Modifier.height(16.dp))

        // vc282：分组维度由「数据对象」改为「文件形态/用途」——
        // CSV 一段：人可读、Excel 能开，进出自如（加油记录双向 + 油价历史单向）；
        // JSON 一段：整包、只有本 App 能恢复，用于迁移/兜底。
        // 原先按对象分成三组，油价历史只有「出」没有「进」，只能孤零零挂在完整备份尾巴上。
        SettingsCard {
            SettingSubLabel(stringResource(R.string.koil_set_group_io))
            SettingRow(
                title = stringResource(R.string.koil_set_export_records),
                subtitle = stringResource(R.string.koil_set_export_records_sub),
                value = stringResource(R.string.koil_act_export),
                onClick = viewModel::exportCsv,
            )
            SettingDivider()
            SettingRow(
                title = stringResource(R.string.koil_set_import_records),
                subtitle = stringResource(R.string.koil_set_import_records_sub),
                value = stringResource(R.string.koil_act_import),
                onClick = { importLauncher.launch(arrayOf("text/csv", "*/*")) },
            )
            SettingDivider()
            SettingRow(
                title = stringResource(R.string.koil_set_export_price_history),
                subtitle = stringResource(R.string.koil_set_export_price_history_sub),
                value = stringResource(R.string.koil_act_export),
                onClick = viewModel::exportOilPriceHistory,
            )
            SettingDivider()
            SettingSubLabel(stringResource(R.string.koil_set_group_backup_restore))
            SettingRow(
                title = stringResource(R.string.koil_set_backup),
                subtitle = stringResource(R.string.koil_set_backup_sub),
                value = stringResource(R.string.koil_act_backup),
                onClick = viewModel::exportBackup,
            )
            SettingDivider()
            SettingRow(
                title = stringResource(R.string.koil_set_restore),
                subtitle = stringResource(R.string.koil_set_restore_sub),
                value = stringResource(R.string.koil_act_restore),
                onClick = { backupLauncher.launch(arrayOf("application/json", "*/*")) },
            )
        }
        Spacer(Modifier.height(16.dp))

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
                                text = stringResource(R.string.koil_set_crash_count, crashCount),
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
                                                crashContext.getString(R.string.koil_set_crash_cleared),
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
                                            crashContext.getString(
                                                if (ok) R.string.koil_set_crash_export_ok else R.string.koil_set_crash_export_fail
                                            ),
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


        // 导出/备份/更新结果提示：Toast 短暂提醒（自动消失，替代常驻卡片）
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
        Spacer(Modifier.height(16.dp))

        // ===== 关于（v1.6.3：从「页脚一行灰字」升级为完整分区）=====
        // 之前没有关于区，版本号只是页脚一行；而油价数据库卡片里的 V2026.11 是**油价数据版本**，
        // 很容易被当成 App 版本。这里把版本、更新检查、条款、许可、诊断集中到一处。
        AboutSection(
            crashCount = crashCount,
            onOpenCrashLog = { showCrashDialog = true },
            bottomPadding = bottomPadding,
        )
        }
    }

    // 省份选择弹窗

    // CSV 导入预览弹窗
    val importPreview by viewModel.importPreview.collectAsStateWithLifecycle()
    importPreview?.let { preview ->
        val activeTab = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(0) }
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
                                .background(if (activeTab.value == idx) KOiLColors.Primary else Color.Transparent)
                                .clickable { activeTab.value = idx }
                                .padding(horizontal = 6.dp, vertical = 4.dp),
                        ) {
                            Text(label, fontSize = 10.sp, color = if (activeTab.value == idx) Color.White else MiuixTheme.colorScheme.onBackground)
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
                        tabs[activeTab.value].second.forEach { r ->
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
                        if (tabs[activeTab.value].second.isEmpty()) {
                            Text(stringResource(R.string.koil_import_no_data), fontSize = 12.sp, color = MiuixTheme.colorScheme.onBackgroundVariant, modifier = Modifier.padding(vertical = 20.dp))
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    // v1.5.44：与恢复弹窗同修——去掉固定 40dp 高度，防文字下缘被裁
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

// ===== 组件 =====
// ===== 关于区（v1.6.3 新增）=====

private const val KOIL_REPO_URL = "https://github.com/ZSPOP/KOiL"
private const val KOIL_RELEASE_API = "https://api.github.com/repos/ZSPOP/KOiL/releases/latest"

private sealed interface UpdateCheck {
    data object Idle : UpdateCheck
    data object Checking : UpdateCheck
    data object Latest : UpdateCheck
    data class Newer(val version: String, val url: String) : UpdateCheck
    data object Failed : UpdateCheck
}

private fun openExternalUrl(context: Context, url: String) {
    val ok = runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))
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
}

/** 语义版本比较：只比数字段，忽略 V/v 前缀与非数字后缀（如 -beta） */
private fun compareVersionNames(a: String, b: String): Int {
    val pa = a.split(".", "-").mapNotNull { it.toIntOrNull() }
    val pb = b.split(".", "-").mapNotNull { it.toIntOrNull() }
    for (i in 0 until maxOf(pa.size, pb.size)) {
        val x = pa.getOrElse(i) { 0 }
        val y = pb.getOrElse(i) { 0 }
        if (x != y) return x.compareTo(y)
    }
    return 0
}

/**
 * 查 GitHub 最新 release。不引第三方库：一次 GET + 两个正则足够。
 * 失败一律回落 Failed（离线 / 限流 / 仓库不可达），UI 显示「检查失败，点击重试」，不留假状态。
 */
private fun fetchLatestRelease(): UpdateCheck {
    var conn: java.net.HttpURLConnection? = null
    return try {
        conn = java.net.URL(KOIL_RELEASE_API).openConnection() as java.net.HttpURLConnection
        conn.connectTimeout = 6000
        conn.readTimeout = 6000
        conn.setRequestProperty("User-Agent", "KOiL-Android")
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        if (conn.responseCode !in 200..299) return UpdateCheck.Failed
        val json = conn.inputStream.bufferedReader().use { it.readText() }
        val tag = Regex("\"tag_name\"\\s*:\\s*\"([^\"]+)\"").find(json)?.groupValues?.getOrNull(1)
            ?: return UpdateCheck.Failed
        val htmlUrl = Regex("\"html_url\"\\s*:\\s*\"([^\"]+)\"").find(json)?.groupValues?.getOrNull(1).orEmpty()
        val latest = tag.trim().trimStart('V', 'v')
        if (compareVersionNames(latest, com.llpop.koil.BuildConfig.VERSION_NAME) > 0) {
            UpdateCheck.Newer(latest, htmlUrl)
        } else {
            UpdateCheck.Latest
        }
    } catch (t: Throwable) {
        UpdateCheck.Failed
    } finally {
        runCatching { conn?.disconnect() }
    }
}

@Composable
private fun AboutSection(
    crashCount: Int,
    onOpenCrashLog: () -> Unit,
    bottomPadding: androidx.compose.ui.unit.Dp = 0.dp,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var update by remember { mutableStateOf<UpdateCheck>(UpdateCheck.Idle) }
    var sheet by remember { mutableStateOf("") }

    // —— 诊断：崩溃日志/问题反馈。原先是「关于·诊断」排在关于之后，
    // vc282 起独立成「诊断」组并前置，让「关于」成为整页最后一张卡（收尾位）。——
    SectionTitle(stringResource(R.string.koil_set_about_diag))
    SettingsCard {
        SettingRow(
            title = stringResource(R.string.koil_set_crash_log),
            subtitle = stringResource(R.string.koil_set_crash_log_sub),
            value = if (crashCount > 0) stringResource(R.string.koil_set_crash_count, crashCount) else "",
            onClick = onOpenCrashLog,
        )
        SettingDivider()
        SettingRow(
            title = stringResource(R.string.koil_set_about_feedback),
            subtitle = stringResource(R.string.koil_set_about_feedback_sub),
            value = "",
            onClick = { openExternalUrl(context, "$KOIL_REPO_URL/issues/new") },
        )
    }
    Spacer(Modifier.height(16.dp))

    SectionTitle(stringResource(R.string.koil_set_about))
    SettingsCard {
        // —— 品牌头：wordmark + 版本号（versionCode 与 CI 流水号对应）——
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 18.dp, bottom = 15.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                painter = painterResource(R.drawable.koil_logo),
                contentDescription = null,
                colorFilter = ColorFilter.tint(MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.65f)),
                modifier = Modifier.size(width = 64.4.dp, height = 22.dp),
            )
            Spacer(Modifier.height(7.dp))
            Text(
                text = stringResource(
                    R.string.koil_set_about_version,
                    com.llpop.koil.BuildConfig.VERSION_NAME,
                    com.llpop.koil.BuildConfig.VERSION_CODE,
                ),
                fontSize = 11.sp,
                color = MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.7f),
            )
        }
        SettingDivider()
        // —— 检查更新：查到新版本后这一行直接变成「打开 Release 页」——
        SettingRow(
            title = stringResource(R.string.koil_set_about_check_update),
            subtitle = stringResource(R.string.koil_set_about_check_update_sub),
            value = when (val u = update) {
                UpdateCheck.Idle -> ""
                UpdateCheck.Checking -> stringResource(R.string.koil_set_about_checking)
                UpdateCheck.Latest -> stringResource(R.string.koil_set_about_latest)
                is UpdateCheck.Newer -> stringResource(R.string.koil_set_about_newer, u.version)
                UpdateCheck.Failed -> stringResource(R.string.koil_set_about_check_failed)
            },
            onClick = {
                when (val u = update) {
                    is UpdateCheck.Newer -> if (u.url.isNotBlank()) openExternalUrl(context, u.url)
                    UpdateCheck.Checking -> Unit
                    else -> {
                        update = UpdateCheck.Checking
                        scope.launch {
                            update = withContext(Dispatchers.IO) { fetchLatestRelease() }
                        }
                    }
                }
            },
        )
        SettingDivider()
        SettingRow(
            title = stringResource(R.string.koil_set_about_terms),
            subtitle = "",
            value = "",
            onClick = { sheet = "terms" },
        )
        SettingDivider()
        SettingRow(
            title = stringResource(R.string.koil_set_about_privacy),
            subtitle = "",
            value = "",
            onClick = { sheet = "privacy" },
        )
        SettingDivider()
        SettingRow(
            title = stringResource(R.string.koil_set_about_licenses),
            subtitle = stringResource(R.string.koil_set_about_licenses_sub),
            value = "",
            onClick = { sheet = "licenses" },
        )
    }
    Spacer(Modifier.height(bottomPadding + 12.dp))

    if (sheet.isNotEmpty()) {
        val titleRes = when (sheet) {
            "terms" -> R.string.koil_set_about_terms
            "privacy" -> R.string.koil_set_about_privacy
            else -> R.string.koil_set_about_licenses
        }
        val bodyRes = when (sheet) {
            "terms" -> R.string.koil_set_about_terms_body
            "privacy" -> R.string.koil_set_about_privacy_body
            else -> R.string.koil_set_about_licenses_body
        }
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
                    color = MiuixTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = stringResource(bodyRes),
                    fontSize = 12.sp,
                    lineHeight = 19.sp,
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                )
                Spacer(Modifier.height(14.dp))
                Button(onClick = { sheet = "" }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.koil_common_close), fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        color = MiuixTheme.colorScheme.onBackgroundVariant,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
    )
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    KOiLBlurBackground(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 20f,
    ) {
        Column(modifier = Modifier.padding(0.dp)) {
            content()
        }
    }
}

/**
 * 卡内小节标题（v1.6.3）：同一条目族内的分组，比 SectionTitle 弱一档——
 * 只在「数据与备份」这种一卡多组里用，避免和分组标题抢层级。
 */
@Composable
private fun SettingSubLabel(text: String) {
    Text(
        text = text,
        fontSize = 11.sp,
        color = MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.75f),
        modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 3.dp),
    )
}

@Composable
private fun SettingDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(0.5.dp)
            .padding(horizontal = 16.dp)
            .background(MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.12f))
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
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
                color = MiuixTheme.colorScheme.onBackground,
            )
            // v1.6.3：副标题可空——关于区里「条款/隐私」这类行只有标题，
            // 无条件渲染会白占一行高度（空 Text + 2dp），行高忽高忽低
            if (subtitle.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    fontSize = 14.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }
        Text(
            text = value,
            fontSize = 14.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantActions,
            fontWeight = FontWeight.Medium,
        )
        if (showArrow) {
            Spacer(Modifier.width(4.dp))
            Icon(
                imageVector = arrowIcon,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** 四宫格统计项（蓝色图标 + 数值 + 标签，对齐截图样式） */
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
    val fieldWidthPx = remember { mutableStateOf(0) }
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
                        .onSizeChanged { if (it.width > 0) fieldWidthPx.value = it.width }
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
                            .onSizeChanged { if (it.width > 0) fieldWidthPx.value = it.width },
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
                            .width(with(fieldDensity) { fieldWidthPx.value.toDp() })
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
    /** 标题右侧的附加内容（v1.6.3：云同步的「已连接/未配置」状态徽章挂这里） */
    trailingContent: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onBackground,
                )
                if (trailingContent != null) {
                    Spacer(Modifier.width(8.dp))
                    trailingContent()
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                fontSize = 14.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
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
    "adopted" -> stringResource(R.string.koil_msg_adopted)
    "ignored" -> stringResource(R.string.koil_msg_ignored)
    "adopt_failed" -> stringResource(R.string.koil_msg_adopt_failed)
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



/** 云同步结果通知翻译（backup_done / backup_failed / restore_no_cloud / restore_done / restore_failed / config_saved / config_test_failed） */
@Composable
private fun formatSyncNotice(key: String): String = when (key) {
    "backup_done" -> stringResource(R.string.koil_sync_backup_done)
    "backup_failed" -> stringResource(R.string.koil_sync_backup_failed)
    "restore_no_cloud" -> stringResource(R.string.koil_sync_restore_no_cloud)
    "restore_done" -> stringResource(R.string.koil_sync_restore_done)
    "restore_failed" -> stringResource(R.string.koil_sync_restore_failed)
    "config_saved" -> stringResource(R.string.koil_sync_config_saved)
    "config_test_failed" -> stringResource(R.string.koil_sync_config_test_failed)
    else -> key
}

/**
 * v1.5.30：跳系统通知设置（通知开关/提示行共用）。
 * 加固要点：
 * 1. NEW_TASK——context 非 Activity（如 application context）时 startActivity 否则抛异常（v1.5.29 就是被 runCatching
 *    静默吞掉导致"点开关没反应"）；
 * 2. resolveActivity 预检——部分 ROM 对 ACTION_APP_NOTIFICATION_SETTINGS 无处理者（ActivityNotFoundException），
 *    失败 fallback 到应用信息页（所有 ROM 必有）；
 * 3. 不再静默吞异常——仍异常则 fallback 应用信息页，保证有可感知的跳转结果。
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
