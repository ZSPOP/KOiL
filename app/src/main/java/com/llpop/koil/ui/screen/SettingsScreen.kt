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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.collectAsState
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

private val PROVINCES = listOf(
    "广东", "北京", "上海", "江苏", "浙江", "山东", "四川", "湖北", "湖南",
    "河南", "河北", "福建", "安徽", "江西", "辽宁", "陕西", "云南", "广西",
    "重庆", "天津", "山西", "贵州", "黑龙江", "吉林", "甘肃", "内蒙古",
    "新疆", "海南", "宁夏", "青海", "西藏",
)
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
    val uiState by viewModel.uiState.collectAsState()

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
            Build.VERSION.SDK_INT < 33 ||
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
                pushPermissionGranted = Build.VERSION.SDK_INT < 33 ||
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
                    items = provinces,
                    selectedIndex = provinceIdx,
                    title = stringResource(R.string.koil_set_default_province),
                    summary = stringResource(
                        R.string.koil_set_default_province_sum,
                        provinces.getOrElse(provinceIdx) { provinces.first() },
                    ),
                    onSelectedIndexChange = { index ->
                        viewModel.setDefaultProvince(provinces[index])
                    },
                )
            }
            SettingDivider()
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
            }
        }
        Spacer(Modifier.height(16.dp))

        // ===== 提醒与校验 =====
        SectionTitle(stringResource(R.string.koil_set_section_remind))
        SettingsCard {
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
        Spacer(Modifier.height(16.dp))

        // ===== 历史油价数据库（Web 版大卡片排版） =====
        SectionTitle(stringResource(R.string.koil_set_section_price_db))
        KOiLBlurBackground(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 20f,
        ) {
            val priceDbStatus by viewModel.priceDbStatus.collectAsState()
            val pendingItems by viewModel.pendingItems.collectAsState()
            val priceDbUpdating by viewModel.priceDbUpdating.collectAsState()
            val latestShort = (priceDbStatus.lastPriceChangeDate ?: priceDbStatus.latestDate).takeLast(5)
            val versionShort = (priceDbStatus.versionDate ?: priceDbStatus.latestDate).takeLast(5)
            // 最近检查时间格式化（MM/dd HH:mm），未检查过显示 "--"
            val lastCheckedText = if (priceDbStatus.lastCheckedAt > 0) {
                java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault())
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
                        text = stringResource(R.string.koil_pdb_incomplete, priceDbStatus.incompleteProvinceCount),
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
                        // 行2：下個窗口 ｜ 最近更新（紧凑同行，竖线分隔）
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
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
                Spacer(Modifier.height(12.dp))
                // —— 待确认更新（有数据需核实时才展开；无数据整块连间距、横线一起隐藏）——
                if (pendingItems.isNotEmpty()) {
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
                                    text = "${item.province} · ${item.grade} · ${item.effectiveDate}",
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

                // —— 分割线 ——
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

                // —— EIA API Key（原油估算数据源；v1.5.1：数据源开关——开=用 EIA 隐藏输入框；
                //    关=直接用腾讯，显示输入框供修改 Key，改完再打开即启用新 Key）——
                val eiaKey by viewModel.eiaApiKey.collectAsState()
                val eiaEnabled by viewModel.eiaEnabled.collectAsState()
                val eiaLoaded by viewModel.eiaLoaded.collectAsState()
                // v1.5.47：EIA 配置加载完成前不渲染开关/输入框（杜绝默认值先行闪烁）
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
                    // 开关开 = 用 EIA → 隐藏输入框（防误改）；开关关 = 用腾讯 → 显示输入框供修改
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
                // v1.5.33：同步来源放油价库区块最底（EIA 项之后、区块闭合前）——用户指定位置；
                // 上方分隔线与区块内其它分隔线同款（0.5dp / alpha 0.12 / 通栏）
                // v1.5.34：文字行与卡片圆角边框底部间隔 12dp → 6dp（用户反馈偏高）
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
        Spacer(Modifier.height(16.dp))

        // ===== 数据 =====
        SectionTitle(stringResource(R.string.koil_set_section_data))
        SettingsCard {
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
                            title = stringResource(R.string.koil_set_backup),
                subtitle = stringResource(R.string.koil_set_backup_sub),
                value = stringResource(R.string.koil_act_backup),
                onClick = viewModel::exportBackup,
            )
            SettingDivider()
            SettingRow(
                            title = stringResource(R.string.koil_set_export_price_history),
                subtitle = stringResource(R.string.koil_set_export_price_history_sub),
                value = stringResource(R.string.koil_act_export),
                onClick = viewModel::exportOilPriceHistory,
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

        // ===== 云同步（坚果云 WebDAV：私有数据自动备份/恢复，docs/KOiL云同步设计.md） =====
        val syncVm: SyncViewModel = koinViewModel()
        val syncUi by syncVm.uiState.collectAsState()
        SectionTitle(stringResource(R.string.koil_sync_section))
        KOiLBlurBackground(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 20f,
        ) {
            Column(modifier = Modifier.padding(0.dp)) {
                // —— 配置草稿：URL/账号以本地草稿驱动输入（每键写 DataStore 会造成受控组件
                //    光标错乱），失焦、重新锁定或执行备份/恢复时才落盘；外部变化（恢复/启动加载）同步回草稿。
                //    默认值预填：账号/应用密码为作者自用默认（未设置过才预填，已设置显示存储值）——
                var urlDraft by remember { mutableStateOf(syncUi.url) }
                var usernameDraft by remember { mutableStateOf(syncUi.username.ifBlank { DEFAULT_SYNC_USERNAME }) }
                var pwdDraft by remember { mutableStateOf(if (syncUi.passwordSet) "" else DEFAULT_SYNC_APP_PASSWORD) }
                LaunchedEffect(syncUi.url) { if (urlDraft != syncUi.url) urlDraft = syncUi.url }
                LaunchedEffect(syncUi.username) {
                    // 仅外部真实写入（如云端恢复）才同步；空值不覆盖预填默认账号
                    if (syncUi.username.isNotBlank() && usernameDraft != syncUi.username) {
                        usernameDraft = syncUi.username
                    }
                }
                // —— 锁定/编辑开关：默认锁定（只读防误改）；关=可编辑；重新开=自动存储 ——
                var urlLocked by remember { mutableStateOf(true) }
                var usernameLocked by remember { mutableStateOf(true) }
                var pwdLocked by remember { mutableStateOf(true) }
                // —— 开关行（复用工程规范组件：自带 16dp 内边距，与其他板块同基准）——
                //    打开开关 = 立即同步：先落盘草稿（含默认值）→ 空库自动恢复 / 非空库立即推送
                // v1.5.47：云同步配置加载完成前不渲染总开关（杜绝"关→开"闪烁）
                if (syncUi.loaded) {
                SettingSwitchRow(
                    title = stringResource(R.string.koil_sync_switch),
                    subtitle = stringResource(R.string.koil_sync_switch_sub),
                    checked = syncUi.enabled,
                    onCheckedChange = { on ->
                        syncVm.setEnabled(on, urlDraft, usernameDraft, pwdDraft)
                    },
                )
                }
                if (syncUi.enabled) {
                    SettingDivider()
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {

                    // —— 配置表单：URL / 账号 / 应用密码（标签行右侧为锁定/编辑小开关）——
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.koil_sync_url),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = MiuixTheme.colorScheme.onBackgroundVariant,
                            modifier = Modifier.weight(1f),
                        )
                        Switch(
                            checked = urlLocked,
                            onCheckedChange = { locked ->
                                if (locked && urlDraft != syncUi.url) {
                                    syncVm.setUrl(urlDraft)
                                }
                                urlLocked = locked
                            },
                            modifier = Modifier.scale(0.8f),
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    TextField(
                        value = urlDraft,
                        onValueChange = { urlDraft = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { state ->
                                tappedOnField.value = state.isFocused
                                if (!state.isFocused && urlDraft != syncUi.url) {
                                    syncVm.setUrl(urlDraft)
                                }
                            },
                        readOnly = urlLocked,
                        // v1.5.44 补丁：miuix 的 labelColor 不作用于输入文字，锁定态用 textStyle 强制暗色（整体禁用观感）
                        textStyle = TextStyle(
                            color = if (urlLocked) {
                                MiuixTheme.colorScheme.onBackground.copy(alpha = 0.30f)
                            } else {
                                MiuixTheme.colorScheme.onBackground
                            },
                        ),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                            imeAction = androidx.compose.ui.text.input.ImeAction.Next,
                        ),
                        colors = TextFieldDefaults.textFieldColors(
                            backgroundColor = if (urlLocked) {
                                MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.08f)
                            } else {
                                MiuixTheme.colorScheme.secondaryContainer
                            },
                            labelColor = if (urlLocked) {
                                // v1.5.44：锁定态文字用暗化禁用色（原 onBackgroundVariant 45% 仍偏白）
                                MiuixTheme.colorScheme.onBackground.copy(alpha = 0.30f)
                            } else {
                                MiuixTheme.colorScheme.onSecondaryContainer
                            },
                            borderColor = Color.Transparent,
                        ),
                        cursorBrush = SolidColor(MiuixTheme.colorScheme.onBackground),
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.koil_sync_username),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = MiuixTheme.colorScheme.onBackgroundVariant,
                            modifier = Modifier.weight(1f),
                        )
                        Switch(
                            checked = usernameLocked,
                            onCheckedChange = { locked ->
                                if (locked && usernameDraft != syncUi.username) {
                                    syncVm.setUsername(usernameDraft)
                                }
                                usernameLocked = locked
                            },
                            modifier = Modifier.scale(0.8f),
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    TextField(
                        value = usernameDraft,
                        onValueChange = { usernameDraft = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { state ->
                                tappedOnField.value = state.isFocused
                                if (!state.isFocused && usernameDraft != syncUi.username) {
                                    syncVm.setUsername(usernameDraft)
                                }
                            },
                        readOnly = usernameLocked,
                        // v1.5.44 补丁：锁定态用 textStyle 强制暗色文字（labelColor 不作用于输入文字）
                        textStyle = TextStyle(
                            color = if (usernameLocked) {
                                MiuixTheme.colorScheme.onBackground.copy(alpha = 0.30f)
                            } else {
                                MiuixTheme.colorScheme.onBackground
                            },
                        ),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Ascii,
                            imeAction = androidx.compose.ui.text.input.ImeAction.Next,
                        ),
                        colors = TextFieldDefaults.textFieldColors(
                            backgroundColor = if (usernameLocked) {
                                MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.08f)
                            } else {
                                MiuixTheme.colorScheme.secondaryContainer
                            },
                            labelColor = if (usernameLocked) {
                                // v1.5.44：锁定态文字用暗化禁用色（原 onBackgroundVariant 45% 仍偏白）
                                MiuixTheme.colorScheme.onBackground.copy(alpha = 0.30f)
                            } else {
                                MiuixTheme.colorScheme.onSecondaryContainer
                            },
                            borderColor = Color.Transparent,
                        ),
                        cursorBrush = SolidColor(MiuixTheme.colorScheme.onBackground),
                    )
                    Spacer(Modifier.height(10.dp))
                    // —— 应用密码（不回显；输入新值即覆盖；辅助行展示设置状态；右侧眼睛可临时查看明文）——
                    var showPwd by remember { mutableStateOf(false) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.koil_sync_app_password),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = MiuixTheme.colorScheme.onBackgroundVariant,
                            modifier = Modifier.weight(1f),
                        )
                        Switch(
                            checked = pwdLocked,
                            onCheckedChange = { locked ->
                                if (locked && pwdDraft.isNotBlank()) {
                                    syncVm.setPassword(pwdDraft)
                                }
                                pwdLocked = locked
                            },
                            modifier = Modifier.scale(0.8f),
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    TextField(
                        value = pwdDraft,
                        onValueChange = { pwdDraft = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { state ->
                                tappedOnField.value = state.isFocused
                                if (!state.isFocused && pwdDraft.isNotBlank()) {
                                    syncVm.setPassword(pwdDraft)
                                }
                            },
                        readOnly = pwdLocked,
                        // v1.5.44 补丁：锁定态用 textStyle 强制暗色文字（labelColor 不作用于输入文字）
                        textStyle = TextStyle(
                            color = if (pwdLocked) {
                                MiuixTheme.colorScheme.onBackground.copy(alpha = 0.30f)
                            } else {
                                MiuixTheme.colorScheme.onBackground
                            },
                        ),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = androidx.compose.ui.text.input.ImeAction.Done,
                        ),
                        keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                            onDone = {
                                if (pwdDraft.isNotBlank()) {
                                    syncVm.setPassword(pwdDraft)
                                }
                                focusManager.clearFocus()
                            },
                        ),
                        colors = TextFieldDefaults.textFieldColors(
                            backgroundColor = if (pwdLocked) {
                                MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.08f)
                            } else {
                                MiuixTheme.colorScheme.secondaryContainer
                            },
                            labelColor = if (pwdLocked) {
                                // v1.5.44：锁定态文字用暗化禁用色（原 onBackgroundVariant 45% 仍偏白）
                                MiuixTheme.colorScheme.onBackground.copy(alpha = 0.30f)
                            } else {
                                MiuixTheme.colorScheme.onSecondaryContainer
                            },
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
                    // 密码状态行 + 明文存储警示
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = when {
                                !syncUi.passwordSet -> stringResource(R.string.koil_sync_app_password_unset)
                                !syncUi.passwordEncrypted -> stringResource(R.string.koil_sync_password_plain_warn)
                                else -> stringResource(R.string.koil_sync_app_password_set)
                            },
                            fontSize = 11.sp,
                            lineHeight = 14.sp,
                            color = if (syncUi.passwordSet && !syncUi.passwordEncrypted) KOiLColors.Warning
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
                    Spacer(Modifier.height(12.dp))

                    // —— 保存并测试连接：配置草稿落盘 + PROPFIND 验证（明确"登入"反馈）——
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(MiuixTheme.colorScheme.secondaryContainer)
                            .clickable(enabled = !syncUi.busy) {
                                syncVm.saveAndTest(urlDraft, usernameDraft, pwdDraft)
                            }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                tint = MiuixTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.koil_sync_save_test),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = MiuixTheme.colorScheme.onSecondaryContainer,
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))

                    // —— 操作按钮行：立即备份 / 从云端恢复 ——
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(18.dp))
                                .background(KOiLColors.Primary)
                                .clickable(enabled = !syncUi.busy) {
                                    syncVm.backupNow(urlDraft, usernameDraft, pwdDraft)
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
                                .background(KOiLColors.Primary)
                                .clickable(enabled = !syncUi.busy) {
                                    // 恢复后保留密码草稿（不再清空，避免误以为未填）
                                    syncVm.restoreNow(urlDraft, usernameDraft, pwdDraft)
                                }
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Filled.Download,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp),
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = stringResource(R.string.koil_sync_restore),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.White,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))

                    // —— 状态行（未配置 / 已备份时间 / 失败待重试）——
                    val configured = syncUi.url.isNotBlank() && syncUi.username.isNotBlank() && syncUi.passwordSet
                    val statusText = when {
                        !configured -> stringResource(R.string.koil_sync_status_not_configured)
                        syncUi.busy -> stringResource(R.string.koil_sync_status_busy)
                        syncUi.lastStatus.startsWith("ok@") ->
                            stringResource(R.string.koil_sync_status_ok, syncUi.lastStatus.substringAfter("ok@"))
                        syncUi.lastStatus.startsWith("fail:") ->
                            stringResource(R.string.koil_sync_status_fail, syncUi.lastStatus.substringAfter("fail:"))
                        else -> stringResource(R.string.koil_sync_status_never)
                    }
                    Text(
                        text = statusText,
                        fontSize = 12.sp,
                        lineHeight = 15.sp,
                        color = if (!configured || syncUi.lastStatus.startsWith("fail:")) KOiLColors.Warning
                        else MiuixTheme.colorScheme.onBackgroundVariant,
                    )
                        Spacer(Modifier.height(1.dp))
                    }
                }
            }
        }

        // 云同步 Toast（备份/恢复结果）
        val syncMessage by syncVm.message.collectAsState()
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
        val syncRestorePreview by syncVm.restorePreview.collectAsState()
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
                        text = stringResource(
                            R.string.koil_sync_restore_confirm_msg,
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

        // 导出/备份/更新结果提示：Toast 短暂提醒（自动消失，替代常驻卡片）
        val exportNotice by viewModel.exportMessage.collectAsState()
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
        Spacer(Modifier.height(24.dp))

        // 关于：KOiL 品牌矢量 logo（v1.5.33：Unbounded Bold 字形轮廓转矢量 drawable，
        // 不再引用字体——字体文件已随本版删除）+ 版本号（V 大写）
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                painter = painterResource(R.drawable.koil_logo),
                contentDescription = null,
                colorFilter = ColorFilter.tint(MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.6f)),
                // v1.5.35：logo 放大 10%（58.52×20 → 64.4×22，保持比例）
                modifier = Modifier.size(width = 64.4.dp, height = 22.dp),
            )
            Spacer(Modifier.height(2.dp))
            Text(
                // v1.6.0：版本号显示附带 (versionCode)，与 CI 注入的构建流水号对应
                text = "V${com.llpop.koil.BuildConfig.VERSION_NAME} (${com.llpop.koil.BuildConfig.VERSION_CODE})",
                fontSize = 11.sp,
                color = MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.6f),
            )
        }
        Spacer(Modifier.height(bottomPadding + 12.dp))
        }
    }

    // 省份选择弹窗

    // CSV 导入预览弹窗
    val importPreview by viewModel.importPreview.collectAsState()
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
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                fontSize = 14.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
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
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
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

/** 把 ViewModel 给出的结果通知 key 翻译为当前语言文案 */
@Composable
private fun formatNotice(key: String, args: List<Any>): String = when (key) {
    "adopted" -> stringResource(R.string.koil_msg_adopted)
    "ignored" -> stringResource(R.string.koil_msg_ignored)
    "adopt_failed" -> stringResource(R.string.koil_msg_adopt_failed)
    "pdb_update_failed" -> stringResource(R.string.koil_msg_pdb_update_failed, args.getOrElse(0) { "" })
    "price_db_empty" -> stringResource(R.string.koil_msg_price_db_empty)
    "price_exported" -> stringResource(R.string.koil_msg_price_exported, args.getOrElse(0) { 0 }, args.getOrElse(1) { "" })
    "export_failed" -> stringResource(R.string.koil_msg_export_failed, args.getOrElse(0) { "" })
    "no_records" -> stringResource(R.string.koil_msg_no_records)
    "records_exported" -> stringResource(R.string.koil_msg_records_exported, args.getOrElse(0) { 0 }, args.getOrElse(1) { "" })
    "cannot_read" -> stringResource(R.string.koil_msg_cannot_read)
    "file_empty" -> stringResource(R.string.koil_msg_file_empty)
    "missing_columns" -> stringResource(R.string.koil_msg_missing_columns)
    "parse_failed" -> stringResource(R.string.koil_msg_parse_failed, args.getOrElse(0) { "" })
    "import_done" -> stringResource(R.string.koil_msg_import_done, args.getOrElse(0) { 0 }, args.getOrElse(1) { 0 })
    "backup_no_records" -> stringResource(R.string.koil_msg_backup_no_records)
    "restore_done" -> stringResource(R.string.koil_msg_restore_done, args.getOrElse(0) { 0 }, args.getOrElse(1) { 0 })
    "restore_failed" -> stringResource(R.string.koil_msg_restore_failed, args.getOrElse(0) { "" })
    "no_records_backup" -> stringResource(R.string.koil_msg_no_records_backup)
    "backup_done" -> stringResource(R.string.koil_msg_backup_done, args.getOrElse(0) { 0 }, args.getOrElse(1) { "" })
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
