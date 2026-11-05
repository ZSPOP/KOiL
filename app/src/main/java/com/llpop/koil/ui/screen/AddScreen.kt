package com.llpop.koil.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.EvStation
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Money
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.llpop.koil.R
import com.llpop.koil.data.oil.Provinces
import com.llpop.koil.domain.logic.NumberFormat
import com.llpop.koil.ui.component.KOiLBlurBackground
import com.llpop.koil.ui.component.KOiLGlassDialog
import com.llpop.koil.ui.component.KOiLScreenScaffold
import com.llpop.koil.ui.component.sanitizeDecimalInput
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.llpop.koil.ui.screen.add.AddUiState
import com.llpop.koil.ui.screen.add.AddViewModel
import com.llpop.koil.ui.theme.KOiLColors
import kotlinx.coroutines.flow.first
import org.koin.androidx.compose.koinViewModel
import android.widget.Toast
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TextFieldDefaults
import top.yukonga.miuix.kmp.basic.DropdownDefaults
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.DropdownArrowEndAction
import top.yukonga.miuix.kmp.popup.OverlayDropdownPopup
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.util.Locale

/**
 * 录入页（M2 完整实现）
 * - 油价四宫格柔光卡（当前省份四油号最新价格）
 * - 上次加油条（一键沿用）
 * - 加油表单：日期 / 油号（联动价格）/ 里程 / 油量 / 应付（自动计算）/ 实付
 * - 实时预检：重复 / 里程递减 / 行驶里程 / 价格偏差
 * - 添加记录入库
 */
@Composable
fun AddScreen(
    modifier: Modifier = Modifier,
    bottomPadding: androidx.compose.ui.unit.Dp = 0.dp,
    viewModel: AddViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val locationHelper = org.koin.compose.koinInject<com.llpop.koil.data.location.LocationHelper>()

    // 定位权限提醒弹窗显隐
    val showPermissionDialog = remember { mutableStateOf(false) }
    // 定位成功一次性提示（Toast，文案走 stringResource 本地化）
    val context = LocalContext.current
    // v1.6.2：文案在组合期解析（stringResource 跟随 Configuration 变化），
    // 不在副作用里读 context.getString——语言切换后不会拿到旧语种的缓存值
    val locateToast = uiState.locateMessage?.let {
        stringResource(R.string.koil_add_locate_switched, it)
    }
    LaunchedEffect(uiState.locateMessage, locateToast) {
        if (locateToast != null) {
            Toast.makeText(context, locateToast, Toast.LENGTH_SHORT).show()
            viewModel.clearLocateMessage()
        }
    }

    // 首次打开：未授权定位时自动弹出定位权限提醒引导（仅一次，之后由用户主动点击定位按钮）
    val settings = org.koin.compose.koinInject<com.llpop.koil.data.settings.AppSettings>()
    LaunchedEffect(Unit) {
        if (!locationHelper.hasPermission() && !settings.hasPromptedLocation.first()) {
            settings.setHasPromptedLocation(true)
            showPermissionDialog.value = true
        }
    }

    // v1.6.2：录入成功的一次性反馈（清除状态，避免下次进入页面重复提示）
    LaunchedEffect(uiState.addSuccess) {
        if (uiState.addSuccess) {
            Toast.makeText(context, R.string.koil_precheck_added, Toast.LENGTH_SHORT).show()
            viewModel.consumeAddSuccess()
        }
    }

    val locationPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions(),
    ) { permissions ->
        val granted = permissions.entries.any { it.value }
        if (granted) viewModel.locateProvince()
    }

    KOiLScreenScaffold(
        title = stringResource(R.string.koil_add_title),
        modifier = modifier,
    ) { _, topPadding, scrollBehavior, _ ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                // v1.6.2：edge-to-edge 下键盘不 resize 窗口，补 imePadding 让表单底部（实付/提交按钮）不被键盘遮挡
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp),
        ) {
            // 顶部避让 TopAppBar（含状态栏，折叠时高度联动）
            Spacer(Modifier.height(topPadding))

        // 油价四宫格卡
        OilPriceCard(
            uiState = uiState,
            onProvinceSelect = viewModel::setProvince,
            onLocateClick = {
                if (locationHelper.hasPermission()) {
                    viewModel.locateProvince()
                } else {
                    showPermissionDialog.value = true
                }
            },
        )
        // v1.5.46：#8 首页密度微调——区块间 14dp 收紧至 12dp，高频录入页一屏更紧凑
        Spacer(Modifier.height(12.dp))

        // 上次加油条
        LastRecordBar(uiState = uiState, onApply = viewModel::applyLastRecord)
        Spacer(Modifier.height(12.dp))

        // 加油表单卡
        KOiLBlurBackground(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 20f,
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // 第一行：日期（默认今天，只读展示；组件结构与补录/编辑统一，后续开启可编辑直接传回调）+ 油号（联动价格）
                FormRow {
                    com.llpop.koil.ui.component.KOiLDateField(
                        modifier = Modifier.weight(1f),
                        date = uiState.date,
                    )
                    Spacer(Modifier.width(12.dp))
                    GradeField(
                        modifier = Modifier.weight(1f),
                        grade = uiState.oilGrade,
                        priceText = uiState.currentPrice?.let { "¥${NumberFormat.measure(it)}/L" } ?: "¥--/L",
                        onGradeSelected = viewModel::setOilGrade,
                    )
                }
                Spacer(Modifier.height(14.dp))

                // 第二行：里程 + 油量
                FormRow {
                    FormField(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Filled.Speed,
                        label = stringResource(R.string.koil_add_field_mileage),
                        value = uiState.mileage,
                        onValueChange = viewModel::setMileage,
                        keyboardType = KeyboardType.Number,
                        error = uiState.precheck.mileageDecreased || uiState.precheck.isDuplicate,
                    )
                    Spacer(Modifier.width(12.dp))
                    FormField(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Filled.WaterDrop,
                        label = stringResource(R.string.koil_add_field_oil_amount),
                        value = uiState.oilAmount,
                        onValueChange = viewModel::setOilAmount,
                        keyboardType = KeyboardType.Decimal,
                        error = uiState.precheck.oilOverTank,
                    )
                }
                Spacer(Modifier.height(14.dp))

                // 第三行：应付 + 实付
                FormRow {
                    FormField(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Filled.Money,
                        label = stringResource(R.string.koil_add_field_payable),
                        value = uiState.payableAmount,
                        onValueChange = viewModel::setPayableAmount,
                        keyboardType = KeyboardType.Decimal,
                        readOnly = true,
                        placeholder = stringResource(R.string.koil_ov_payable_pending),
                    )
                    Spacer(Modifier.width(12.dp))
                    FormField(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Filled.Money,
                        label = stringResource(R.string.koil_add_field_actual),
                        value = uiState.actualAmount,
                        onValueChange = viewModel::setActualAmount,
                        keyboardType = KeyboardType.Decimal,
                        error = uiState.precheck.priceDeviationExceeded || uiState.precheck.amountOverPayable,
                    )
                }
            }
        }
        // v1.5.46：#8 首页密度微调
        Spacer(Modifier.height(12.dp))

        // 实时预检提示
        PrecheckBanner(uiState = uiState)
        Spacer(Modifier.height(16.dp))

        // 添加记录按钮（全部校验通过 → 蓝色可提交，否则一律提交不了）
        Button(
            onClick = viewModel::addRecord,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            enabled = uiState.precheck.isReady && !uiState.isAdding,
            colors = ButtonDefaults.buttonColors(
                color = KOiLColors.Primary,
                contentColor = Color.White,
                disabledColor = MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                disabledContentColor = MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.4f),
            ),
        ) {
            Text(
                text = if (uiState.isAdding) stringResource(R.string.koil_add_adding) else stringResource(R.string.koil_add_add_record),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
            )
        }
        Spacer(Modifier.height(bottomPadding + 12.dp))
        }
    }

    // 定位权限提醒弹窗（未授权时点击定位按钮弹出，去授权→拉起系统权限请求）
    if (showPermissionDialog.value) {
        PermissionDialog(
            onGrant = {
                showPermissionDialog.value = false
                locationPermissionLauncher.launch(
                    arrayOf(
                        android.Manifest.permission.ACCESS_FINE_LOCATION,
                        android.Manifest.permission.ACCESS_COARSE_LOCATION,
                    )
                )
            },
            onDismiss = { showPermissionDialog.value = false },
        )
    }
}

// ===== 省份选择弹窗 =====
private val PROVINCES = Provinces.ALL

@Composable
private fun ProvincePickerDialog(
    currentProvince: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    KOiLGlassDialog(show = true, onDismissRequest = onDismiss) {
        Column {
            Text(
                text = stringResource(R.string.koil_add_pick_province),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MiuixTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(14.dp))
            // 列表式上下滚动单选（与设置页「默认省份」同款观感）
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Column {
                    PROVINCES.forEach { province ->
                        val isSelected = province == currentProvince
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) KOiLColors.Primary.copy(alpha = 0.12f) else Color.Transparent)
                                .clickable { onSelect(province) }
                                .padding(horizontal = 14.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = province,
                                fontSize = 14.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) KOiLColors.Primary else MiuixTheme.colorScheme.onBackground,
                                modifier = Modifier.weight(1f),
                            )
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = KOiLColors.Primary,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
            ) {
                Text(stringResource(R.string.koil_common_cancel), fontSize = 14.sp)
            }
        }
    }
}

// ===== 定位权限提醒弹窗 =====
@Composable
private fun PermissionDialog(
    onGrant: () -> Unit,
    onDismiss: () -> Unit,
) {
    KOiLGlassDialog(show = true, onDismissRequest = onDismiss) {
        Column {
            Text(
                text = stringResource(R.string.koil_add_loc_permission_title),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MiuixTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.koil_add_loc_permission_message),
                fontSize = 13.sp,
                color = MiuixTheme.colorScheme.onBackgroundVariant,
            )
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f).height(44.dp),
                    colors = ButtonDefaults.buttonColors(
                        color = MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        contentColor = MiuixTheme.colorScheme.onBackground,
                    ),
                ) {
                    Text(stringResource(R.string.koil_add_loc_cancel), fontSize = 14.sp)
                }
                Button(
                    onClick = onGrant,
                    modifier = Modifier.weight(1f).height(44.dp),
                    colors = ButtonDefaults.buttonColors(color = KOiLColors.Primary, contentColor = Color.White),
                ) {
                    Text(stringResource(R.string.koil_add_loc_grant), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

// ===== 油价四宫格卡 =====
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OilPriceCard(
    uiState: AddUiState,
    onProvinceSelect: (String) -> Unit,
    onLocateClick: () -> Unit,
) {
    val showProvincePicker = remember { mutableStateOf(false) }
    // 主题感知配色：深色下用柔和亮蓝主色 + 蓝色柔光容器底（避免亮色调 PrimaryContainer 发白突兀），浅色保持原样
    val dark = isSystemInDarkTheme()
    val accent = MiuixTheme.colorScheme.primary            // 深色=#0A84FF 柔和亮蓝 / 浅色=#0071E3
    val containerBg = if (dark) MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f) else KOiLColors.PrimaryContainer.copy(alpha = 0.5f)
    val containerText = if (dark) accent else KOiLColors.OnPrimaryContainer
    val urgentColor = if (dark) Color(0xFFFF9F0A) else Color(0xFFE65100)
    KOiLBlurBackground(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 20f,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.LocalGasStation,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.koil_add_province_price, uiState.viewProvince),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MiuixTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = stringResource(R.string.koil_add_price_db_version, uiState.versionLabel),
                        fontSize = 11.sp,
                        color = MiuixTheme.colorScheme.onBackgroundVariant,
                    )
                }
                // 定位按钮
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(containerBg)
                        .clickable { onLocateClick() }
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.MyLocation,
                        contentDescription = stringResource(R.string.koil_add_locate_cd),
                        tint = containerText,
                        modifier = Modifier.size(14.dp),
                    )
                }
                Spacer(Modifier.width(6.dp))
                // 省份选择按钮（点击弹设置页同款 OverlayDropdownPopup 下拉，锚定按钮下方、跟随位置）
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(containerBg)
                        .clickable { showProvincePicker.value = true }
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // v1.5.34：省份文字 14sp Bold + 箭头直接跟随（无手动间距）——与设置页 Dropdown / 油号选择行尾完全同款
                        // v1.5.35：文字→箭头加 8dp 间距（设置页行尾箭头靠 weight 撑开有空隙，紧凑按钮内需显式间距对齐观感）
                        Text(
                            text = uiState.viewProvince,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = containerText,
                        )
                        Spacer(Modifier.width(8.dp))
                        // v1.5.33：省份下拉箭头——换 Miuix DropdownArrowEndAction（与设置页 Dropdown / 油号选择
                        // 行尾同款线条风格），替代 v1.5.31 的实心小三角 ArrowDropDown（用户反馈不显眼）
                        DropdownArrowEndAction(actionColor = containerText)
                    }
                }
                if (showProvincePicker.value) {
                    OverlayDropdownPopup(
                        entry = DropdownEntry(
                            items = PROVINCES.map { p ->
                                DropdownItem(
                                    text = p,
                                    selected = p == uiState.viewProvince,
                                    onClick = {
                                        onProvinceSelect(p)
                                        showProvincePicker.value = false
                                    },
                                )
                            }
                        ),
                        show = showProvincePicker.value,
                        onDismiss = { showProvincePicker.value = false },
                        onDismissFinished = {},
                        maxHeight = 300.dp,
                        dropdownColors = DropdownDefaults.dropdownColors(),
                        renderInRootScaffold = true,
                        collapseOnSelection = true,
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            // 四宫格：92# / 95# / 98# / 0#（仅价格，无涨跌幅）
            Row(modifier = Modifier.fillMaxWidth()) {
                PriceCell(
                    modifier = Modifier.weight(1f),
                    grade = "92#",
                    price = uiState.priceMap["92"],
                )
                PriceCell(
                    modifier = Modifier.weight(1f),
                    grade = "95#",
                    price = uiState.priceMap["95"],
                )
                PriceCell(
                    modifier = Modifier.weight(1f),
                    grade = "98#",
                    price = uiState.priceMap["98"],
                )
                PriceCell(
                    modifier = Modifier.weight(1f),
                    grade = "0#",
                    price = uiState.priceMap["0"],
                )
            }
            // 调价窗口提示（对齐 Web 三块式：预计调价元/吨 · 折合元/升 · 建议）
            // 由设置页「油价调整窗口提醒」开关控制显示（开显示，关隐藏）
            if (uiState.showPriceAdjust) uiState.nextAdjustDate?.let { nextDate ->
                Spacer(Modifier.height(12.dp))
                val urgent = uiState.adjustUrgent
                val days = uiState.daysUntilAdjust
                val forecast = uiState.forecastAmount
                val perLiter = uiState.perLiterEquivalent
                val rising = forecast != null && forecast > 0
                // v1.5.22：估算失败（EIA+腾讯均不可用）时不驱动建议——历史涨跌不预示下一轮方向（因果不成立），
                // 只显示「待检测」徽章（forecast==null 时 suggestion 为空，由 else 分支兜底）
                val suggestion = when {
                    rising -> stringResource(R.string.koil_add_suggest_early)
                    forecast != null -> stringResource(R.string.koil_add_suggest_wait)
                    else -> ""
                }
                val bgColor = if (dark) Color(0xFF17191D)
                    else if (urgent) urgentColor.copy(alpha = 0.16f)
                    else KOiLColors.PrimaryContainer.copy(alpha = 0.5f)
                val textColor = if (urgent) urgentColor
                    else if (dark) accent
                    else KOiLColors.OnPrimaryContainer
                val barColor = if (urgent) urgentColor else accent
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(bgColor),
                ) {
                    Box(
                        modifier = Modifier
                            .width(4.dp)
                            .fillMaxHeight()
                            .background(barColor),
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (urgent) Icons.Filled.Warning else Icons.Filled.Info,
                                contentDescription = null,
                                tint = if (urgent) urgentColor else accent,
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = buildString {
                                    append(stringResource(R.string.koil_add_window_prefix, nextDate.let { if (it.length >= 5) it.substring(5) else it }))
                                    if (days >= 0) append(stringResource(R.string.koil_add_window_days, days))
                                },
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = textColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            // v1.5.24：建议徽章从第二行移到窗口日行右侧（右对齐、垂直居中）
                            if (suggestion.isNotEmpty()) {
                                Spacer(Modifier.width(6.dp))
                                AdjustBadge(
                                    text = suggestion,
                                    color = if (urgent) urgentColor else accent,
                                    bold = true,
                                )
                            }
                        }
                        if (forecast != null) {
                            Spacer(Modifier.height(6.dp))
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                // v1.5.0：预测幅度 + 折合单价合并为一个徽章；v1.5.21 起 apizero forecast 彻底移除，
                                // 幅度恒来自 EIA/腾讯本地估算 → 「估算」标注恒显示
                                // v1.5.24：(估算/单日参考) 前置到「预计」后 → 预计(估算)上调170元/吨
                                if (perLiter != null) {
                                    AdjustBadge(
                                        text = stringResource(
                                            if (rising) R.string.koil_add_forecast_up else R.string.koil_add_forecast_down,
                                            "(" + stringResource(
                                                // v1.5.22：按来源标注——EIA=「估算」（窗口幅度）；腾讯=「单日参考」（当日波动，仅方向）
                                                if (uiState.forecastSource == "腾讯") R.string.koil_add_forecast_single_day
                                                else R.string.koil_add_forecast_estimated,
                                            ) + ")",
                                        ) +
                                            "${kotlin.math.abs(forecast).toInt()}" +
                                            stringResource(R.string.koil_add_forecast_per_ton) +
                                            // v1.5.23：EIA 数据 T+1 滞后——窗口日当天缺末段，标注数据截至日，避免误解
                                            if (uiState.forecastSource != "腾讯" && uiState.forecastDataDate != null) {
                                                "(" + stringResource(
                                                    R.string.koil_add_forecast_as_of,
                                                    uiState.forecastDataDate.takeLast(5),
                                                ) + ")"
                                            } else {
                                                ""
                                            } +
                                            // v1.5.24：截至标注后加逗号分隔折合文案
                                            stringResource(R.string.koil_add_forecast_sep) +
                                            stringResource(
                                                if (rising) R.string.koil_add_approx_per_liter_up
                                                else R.string.koil_add_approx_per_liter_down,
                                                kotlin.math.abs(perLiter),
                                            ),
                                        color = textColor,
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

@Composable
private fun AdjustBadge(
    text: String,
    color: Color,
    bold: Boolean = false,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 7.dp, vertical = 3.dp),
    ) {
        Text(
            text = text,
            fontSize = 11.sp,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium,
            color = color,
        )
    }
}

@Composable
private fun PriceCell(
    modifier: Modifier = Modifier,
    grade: String,
    price: Double?,
) {
    Column(
        modifier = modifier.padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = grade,
            fontSize = 12.sp,
            color = MiuixTheme.colorScheme.onBackgroundVariant,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = if (price != null) NumberFormat.measure(price) else "--",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = MiuixTheme.colorScheme.primary,
        )
        Text(
            text = stringResource(R.string.koil_add_unit_per_liter),
            fontSize = 10.sp,
            color = MiuixTheme.colorScheme.onBackgroundVariant,
        )
    }
}

// ===== 上次加油条 =====
@Composable
private fun LastRecordBar(
    uiState: AddUiState,
    onApply: () -> Unit,
) {
    val last = uiState.lastRecord
    KOiLBlurBackground(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 20f,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Timer,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.onBackgroundVariant,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.koil_add_last_refuel),
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                )
                if (last != null) {
                    Text(
                        // v1.6.2：原实现直接拼 Double（"45.0 L"/"油量 38.0"）且金额 toInt() 截断分位，
                        // 与全站金额口径（两位小数）不一致。统一走 NumberFormat。
                        text = "${last.reoilDate.takeLast(5)} · ${NumberFormat.measure(last.mileage)} km · " +
                            "${NumberFormat.measure(last.oilAmount)} L · ¥${NumberFormat.money(last.actualAmount)}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = MiuixTheme.colorScheme.onBackground,
                        maxLines = 1,
                    )
                } else {
                    Text(
                        text = stringResource(R.string.koil_add_no_record),
                        fontSize = 13.sp,
                        color = MiuixTheme.colorScheme.onBackgroundVariant,
                    )
                }
            }
            if (last != null) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(KOiLColors.Primary)
                        .clickable(onClick = onApply)
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                ) {
                    Text(
                        text = stringResource(R.string.koil_add_apply_last),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White,
                    )
                }
            }
        }
    }
}

// ===== 表单行 / 字段 =====
@Composable
private fun FormRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        content = content,
    )
}

@Composable
private fun FormField(
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text,
    readOnly: Boolean = false,
    error: Boolean = false,
    placeholder: String? = null,
) {
    val errorColor = Color(0xFFFF3B30)
    // 错误态：用 TextFieldColors 把边框/标签设为红色；正常态边框透明（无蓝色聚焦框）
    val tfColors = TextFieldDefaults.textFieldColors(
        backgroundColor = MiuixTheme.colorScheme.secondaryContainer,
        labelColor = if (error) errorColor else MiuixTheme.colorScheme.onSecondaryContainer,
        borderColor = if (error) errorColor else Color.Transparent,
    )
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (error) errorColor else MiuixTheme.colorScheme.onBackgroundVariant,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = label,
                fontSize = 12.sp,
                color = if (error) errorColor else MiuixTheme.colorScheme.onBackgroundVariant,
            )
        }
        Spacer(Modifier.height(6.dp))
        // v1.5.31：miuix TextField 无 placeholder 参数，用 Box 叠加层实现"待计算"占位（对齐补录卡 v1.5.14 同款做法）
        Box(modifier = Modifier.fillMaxWidth()) {
            TextField(
                value = value,
                onValueChange = { onValueChange(it.sanitizeDecimalInput()) },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                readOnly = readOnly,
                singleLine = true,
                colors = tfColors,
                cursorBrush = SolidColor(MiuixTheme.colorScheme.onBackground),
            )
            if (placeholder != null && value.isEmpty()) {
                Text(
                    text = placeholder,
                    fontSize = 14.sp,
                    color = MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.5f),
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 14.dp),
                )
            }
        }
    }
}

// ===== 油号选择字段（行样式与 DateField 一致：标签行 + 52dp 输入框；弹窗用 miuix OverlayDropdownPopup 与设置页语言选择同款：锚定、无分割线、选中蓝点、缩放动画） =====
@Composable
private fun GradeField(
    modifier: Modifier = Modifier,
    grade: String,
    priceText: String,
    onGradeSelected: (String) -> Unit,
) {
    val gradeLabels = listOf("92", "95", "98", "0")
    val showMenu = remember { mutableStateOf(false) }
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.EvStation,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.onBackgroundVariant,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = stringResource(R.string.koil_add_grade_title),
                fontSize = 12.sp,
                color = MiuixTheme.colorScheme.onBackgroundVariant,
            )
        }
        Spacer(Modifier.height(6.dp))
        // 52dp 圆角灰底框，与 DateField 输入框、FormField(TextField) 完全同高同色；框内自绘当前值
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MiuixTheme.colorScheme.secondaryContainer)
                .clickable { showMenu.value = true }
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "$grade# · $priceText",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MiuixTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                )
                // 与设置页语言选择同款下拉箭头（miuix DropdownArrowEndAction）
                DropdownArrowEndAction(
                    actionColor = MiuixTheme.colorScheme.onBackgroundVariant,
                )
            }
        }
    }

    // 语言弹窗同款弹窗：单条 DropdownEntry 内含多个 DropdownItem（分割线只画在 entry 之间，单 entry 无分割线）
    if (showMenu.value) {
        OverlayDropdownPopup(
            entry = DropdownEntry(
                items = gradeLabels.map { g ->
                    val label = if (g == "0") stringResource(R.string.koil_add_grade_diesel)
                    else stringResource(R.string.koil_add_grade_gasoline, g)
                    DropdownItem(
                        text = label,
                        selected = g == grade,
                        onClick = {
                            onGradeSelected(g)
                            showMenu.value = false
                        },
                    )
                }
            ),
            show = showMenu.value,
            onDismiss = { showMenu.value = false },
            onDismissFinished = {},
            maxHeight = 220.dp,
            dropdownColors = DropdownDefaults.dropdownColors(),
            renderInRootScaffold = true,
            collapseOnSelection = true,
        )
    }
}

// ===== 实时预检横幅 =====
@Composable
private fun PrecheckBanner(uiState: AddUiState) {
    val precheck = uiState.precheck
    val precheckText = when (precheck.messageKey) {
        "duplicate" -> stringResource(R.string.koil_precheck_duplicate)
        "mileage_decreased" -> stringResource(
            R.string.koil_precheck_mileage_decreased,
            precheck.messageArgs.firstOrNull() ?: 0,
        )
        "over_payable" -> stringResource(R.string.koil_precheck_over_payable)
        "over_tank" -> stringResource(R.string.koil_precheck_over_tank, precheck.messageArgs.firstOrNull() ?: 0)
        "deviation" -> stringResource(R.string.koil_precheck_deviation, precheck.messageArgs.firstOrNull() ?: 0.0)
        "ready" -> stringResource(
            R.string.koil_precheck_ready,
            precheck.messageArgs.getOrNull(0) ?: 0.0,
            precheck.messageArgs.getOrNull(1) ?: 0.0,
        )
        "added" -> stringResource(R.string.koil_precheck_added)
        "add_failed" -> stringResource(R.string.koil_precheck_add_failed_generic)  // v1.5.36 P7：固定文案，不透出内部异常
        else -> stringResource(R.string.koil_precheck_default)
    }
    val (bgColor, textColor) = when {
        precheck.isDuplicate || precheck.mileageDecreased ->
            Color(0x1AFF3B30) to Color(0xFFFF3B30)
        precheck.priceDeviationExceeded ->
            Color(0x1AFF9500) to Color(0xFFFF9500)
        // v1.6.2："已添加"与"可提交"同用绿色成功态（原实现落在灰色默认态，像提示又像报错）
        precheck.isReady || precheck.messageKey == "added" ->
            Color(0x1A34C759) to Color(0xFF34C759)
        else ->
            MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) to MiuixTheme.colorScheme.onBackgroundVariant
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(bgColor)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (precheck.isReady) {
                Text(
                    text = "✓",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = textColor,
                )
                Spacer(Modifier.width(6.dp))
            }
            Text(
                text = precheckText,
                fontSize = 13.sp,
                color = textColor,
            )
        }
    }
}
