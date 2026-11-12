package com.llpop.koil.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
import org.koin.core.context.GlobalContext
import com.llpop.koil.data.oil.ForecastDirection
import com.llpop.koil.data.oil.Provinces
import com.llpop.koil.data.oil.forecastDirection
import com.llpop.koil.domain.logic.NumberFormat
import com.llpop.koil.ui.component.KOiLBlurBackground
import com.llpop.koil.ui.component.KOiLGlassDialog
import com.llpop.koil.BuildConfig
import com.llpop.koil.data.update.ApkUpdater
import com.llpop.koil.data.update.UpdateDownloadState
import com.llpop.koil.data.update.UpdateChecker
import com.llpop.koil.data.update.normalizeVersion
import com.llpop.koil.data.update.shouldBadgeUpdate
import com.llpop.koil.data.update.releasePageUrl
import com.llpop.koil.ui.component.UpdateBanner
import com.llpop.koil.ui.component.messageRes
import com.llpop.koil.ui.component.KOiLScreenScaffold
import com.llpop.koil.ui.component.sanitizeDecimalInput
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.llpop.koil.ui.screen.add.AddUiState
import com.llpop.koil.ui.screen.add.AddViewModel
import com.llpop.koil.ui.theme.KOiLColors
import com.llpop.koil.ui.theme.LocalKOiLDarkTheme
import com.llpop.koil.ui.component.provinceLabel
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
    // 更新横幅：与设置页共用同一个 UpdateChecker（冷启动静默检查已把 tag 落到 DataStore）。
    // 判据复用红点那一个有单测的 shouldBadgeUpdate，不在首页再手写一套 —— 否则将来
    // 红点判定加条件（如 ④ 下载失败后的重试策略），横幅不会跟着变。
    val updateChecker = remember { GlobalContext.get().get<UpdateChecker>() }
    // tag 与直链必须来自**同一快照**：分别订阅是两条 collect，中间有一帧
    // 「新 tag + 旧 url」——下的是上一版的包，校验全过，装完仍是旧版还把新版标成已读
    val updateTarget by updateChecker.updateTarget().collectAsStateWithLifecycle(initialValue = null)
    val latestTag = updateTarget?.tag.orEmpty()
    val seenTag by updateChecker.seenTag().collectAsStateWithLifecycle(initialValue = "")
    val bannerTag = latestTag.takeIf { shouldBadgeUpdate(it, seenTag, BuildConfig.VERSION_NAME) }
    // 应用内更新：直链与 tag 来自同一次检查（见 UpdateChecker.refresh），这里只做搬运。
    // 下载/安装的状态由下载器自己管，失败原因用 Toast 回一次，不弹对话框（用户可能已经在别的页面）。
    val apkUpdater = remember { GlobalContext.get().get<ApkUpdater>() }
    val apkUrl = updateTarget?.apkUrl.orEmpty()
    // 下载/安装状态就地展示（横幅上显示百分比、失败标记），不弹 Toast：
    // Toast 是"一次事件"，首页与设置页同时组合会各弹一次；用 SharedFlow 又会在无人订阅时丢事件。
    // 失败本身是持续状态，贴近"有更新但没装成"这个事实，所以让横幅自己说话。
    val downloadState by apkUpdater.state.collectAsStateWithLifecycle()
    // 文案在组合期解析（stringResource 跟随 Configuration 变化），
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

    // 录入成功的一次性反馈（清除状态，避免下次进入页面重复提示）
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
                // edge-to-edge 下键盘不 resize 窗口，补 imePadding 让表单底部（实付/提交按钮）不被键盘遮挡
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
        // 区块间取 12dp，高频录入页一屏更紧凑
        Spacer(Modifier.height(12.dp))

        // 更新横幅：首页摘要卡（油价四宫格卡）之后。间距沿用区块间的 12dp，
        // 最终是「摘要卡 → 12dp → 横幅 → 12dp → 上次加油条」，不是 24dp。
        if (bannerTag != null) {
            // 下载中把进度接在版本文案上（"1.1.10 · 42%"）：6.6MB 在国内要几十秒，
            // 没有可见进度就会变成"点了没反应"。失败也走同一处，并把动作换成「重试」。
            val baseVersion = normalizeVersion(bannerTag)
            val st = downloadState
            val bannerVersion = when (st) {
                is UpdateDownloadState.Downloading ->
                    if (st.percent >= 0) {
                        stringResource(R.string.koil_update_progress, baseVersion, st.percent)
                    } else {
                        stringResource(R.string.koil_update_progress_unknown, baseVersion)
                    }
                else -> baseVersion
            }
            val bannerFailed = st as? UpdateDownloadState.Failed
            UpdateBanner(
                version = bannerVersion,
                // 失败时用具体原因当主文案（签名不一致 ≠ 网络失败，不能一律让用户重试）
                title = bannerFailed?.let { stringResource(it.reason.messageRes()) },
                actionLabel = if (bannerFailed != null) {
                    stringResource(R.string.koil_update_action_retry)
                } else {
                    stringResource(R.string.koil_ov_update_action)
                },
                onOpen = {
                    // 应用内下载：拉起安装器成功时由 ApkUpdater 内部 markSeen，
                    // 这里不能提前标记 —— 下载失败/取消时横幅必须还在。
                    // 只有拿不到直链（旧数据、Gitee 侧还没镜像完）才回退浏览器。
                    if (apkUrl.isNotBlank()) {
                        // 进度直接显示在横幅上，重复点击由 download() 自己挡（job 在跑就 return）
                        apkUpdater.download(bannerTag, apkUrl)
                    } else if (openExternalUrl(context, releasePageUrl(bannerTag))) {
                        updateChecker.markSeen(bannerTag)
                    }
                },
                // 主动关掉 = 已知晓，同一版本不再提醒（不做「7 天后再提醒」）
                onDismiss = { updateChecker.markSeen(bannerTag) },
            )
            Spacer(Modifier.height(12.dp))
        }

        // 上次加油条
        LastRecordBar(uiState = uiState, onApply = viewModel::applyLastRecord)
        Spacer(Modifier.height(12.dp))

        // 加油表单卡
        KOiLBlurBackground(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 20f,
        ) {
            // KOiLBlurBackground 的内容槽是 @Composable BoxScope（内部就是 Box），多个平级子项会按
            // TopStart 逐个叠放、彼此覆盖。所以纵向堆叠必须交给这一层不带 padding 的 Column：
            // 它只负责顺序，横向 16dp 由内层表单 Column 提供；跳枪行的横向 16dp 由它自己的
            // insideMargin 承担（FullTankSwitchRow 契约要求调用方零横向内距），因此它与内层平级。
            Column {
                // 表单三行：底部 12dp = 块模型 ①「上方内容 → 块上缘」，留在高亮之外——
                // 上距若塞进行内 insideMargin，高亮会一路铺到上一字段下方、看起来「贴住」上一行。
                Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 12.dp)) {
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
                            onValueChange = {},
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
                // 满箱标记（跳枪）：油耗按满箱区间结算的关键输入，默认勾选；整行铺满卡片宽。
                // 块模型：上距 12dp 在高亮外（上方表单 Column 的 bottom），块内上距 8dp 在行内；
                // bottomInset = 16dp → 高亮从块上缘铺到卡片底边，块下缘→卡底的 16dp 在高亮之内
                //（顶角直角、底角随卡片圆角收边，开发者定稿确认）。
                com.llpop.koil.ui.component.FullTankSwitchRow(
                    checked = uiState.isFullTank,
                    onCheckedChange = viewModel::setFullTank,
                    bottomInset = 16.dp,
                )
            }
        }
        // 区块间取 12dp
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
        // 键盘弹起时不保留底部悬浮导航条的留白。
        // 底部导航条（84dp）此时已被键盘盖住，再留 84dp 会让「添加记录」下方出现一大块空白，
        // 观感像"内容没渲染出来"（真机反馈原话：只见空白、没有内容）。
        Spacer(Modifier.height(if (WindowInsets.ime.getBottom(LocalDensity.current) > 0) 12.dp else bottomPadding + 12.dp))
        }
    }

    // 定位权限提醒弹窗（未授权时点击定位按钮弹出，去授权→拉起系统权限请求）
    if (showPermissionDialog.value) {
        PermissionDialog(
            onGrant = {
                showPermissionDialog.value = false
                locationPermissionLauncher.launch(
                    // 省份级反查只需要 COARSE；Manifest 也只声明了它。
                    // 请求未声明的 FINE 会被系统直接判拒，还会让权限弹窗多一个「精确位置」开关。
                    arrayOf(android.Manifest.permission.ACCESS_COARSE_LOCATION)
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
                                text = provinceLabel(province),
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
    // 主题是设置项（跟随系统/浅色/深色）：必须读实际生效值，读系统值会在手动主题下反色
    val dark = LocalKOiLDarkTheme.current
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
                    // 标题自动缩字不换行——宽度不够时（英文 "Guangdong fuel prices" 较长，
                    // 加上定位/省份按钮占位）就硬折成两行，真机反馈不美观。溢出时按 0.92 缩字号直到放下。
                    var titleScale by remember { mutableFloatStateOf(1f) }
                    Text(
                        text = stringResource(R.string.koil_add_province_price, provinceLabel(uiState.viewProvince)),
                        fontSize = 16.sp * titleScale,
                        fontWeight = FontWeight.Bold,
                        color = MiuixTheme.colorScheme.onBackground,
                        maxLines = 1,
                        softWrap = false,
                        onTextLayout = { if (it.hasVisualOverflow && titleScale > 0.6f) titleScale *= 0.92f },
                    )
                    Text(
                        text = stringResource(R.string.koil_add_price_db_version, uiState.versionLabel),
                        fontSize = 11.sp,
                        color = MiuixTheme.colorScheme.onBackgroundVariant,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
                // 定位按钮
                // 命中区取 48×48dp（视觉仍是小胶囊）：30×24dp 远低于 Material 的 48dp 建议，
                // 手指点偏就落到省份按钮上。做法是外层撑开 48dp 承担点击，内层保持原样只管外观。
                // clip 必须放在 clickable 之前——否则按下波纹是 48dp 外层的矩形色块
                //（真机反馈"点击出现矩形色块"）。裁成圆角后波纹跟随圆角轮廓。
                Box(
                    modifier = Modifier
                        .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClick = onLocateClick),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(containerBg)
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.MyLocation,
                            contentDescription = stringResource(R.string.koil_add_locate_cd),
                            tint = containerText,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
                Spacer(Modifier.width(2.dp))
                // 省份选择按钮（点击弹设置页同款 OverlayDropdownPopup 下拉，锚定按钮下方、跟随位置）
                // 同定位按钮——clip 在 clickable 之前，波纹圆角不出现矩形色块
                Box(
                    modifier = Modifier
                        .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { showProvincePicker.value = true },
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(containerBg)
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // 省份文字 14sp Bold + 箭头直接跟随（无手动间距）——与设置页 Dropdown / 油号选择行尾完全同款
                        // 文字→箭头间 8dp 间距（设置页行尾箭头靠 weight 撑开有空隙，紧凑按钮内需显式间距对齐观感）
                        Text(
                            // 省份是数据库主键（中文），显示层用本地化名
                            text = provinceLabel(uiState.viewProvince),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = containerText,
                        )
                        Spacer(Modifier.width(8.dp))
                        // 省份下拉箭头——用 Miuix DropdownArrowEndAction（与设置页 Dropdown / 油号选择
                        // 行尾同款线条风格），不用实心小三角 ArrowDropDown（用户反馈不显眼）
                        DropdownArrowEndAction(actionColor = containerText)
                    }
                }
                if (showProvincePicker.value) {
                    OverlayDropdownPopup(
                        entry = DropdownEntry(
                            items = PROVINCES.map { p ->
                                DropdownItem(
                                    text = provinceLabel(p),
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
                val forecast = uiState.forecastAmount
                val perLiter = uiState.perLiterEquivalent
                val rising = forecast != null && forecast > 0
                // 搁浅判定要先于建议计算：stranded 依赖 eiaSource + 方向，原先写在 forecast != null
                // 块内，导致建议只能看到涨/跌两态，搁浅时误显示「建议等降价」。
                val eiaSource = uiState.forecastSource != "腾讯"
                val direction = forecast?.let { forecastDirection(it) }
                val stranded = eiaSource && direction == ForecastDirection.STRANDED
                // 估算失败时不驱动建议——历史涨跌不预示下一轮方向（因果不成立），不给任何建议徽章；
                // forecast==null 时下方按原因分两种空态：汇率没取到显示「汇率获取中」，其余什么都不显示
                val suggestion = when {
                    forecast == null -> ""
                    stranded -> stringResource(R.string.koil_add_suggest_stranded)
                    rising -> stringResource(R.string.koil_add_suggest_early)
                    else -> stringResource(R.string.koil_add_suggest_wait)
                }
                // 搁浅既不是「赶紧加」也不是「等降」，徽章用中性色；涨/跌仍沿用现有强调色
                val suggestionColor = if (stranded) MiuixTheme.colorScheme.onBackgroundVariant
                    else if (urgent) urgentColor
                    else accent
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
                                // 图标跟随本行字号：文字 12sp→13sp 后仍是 14dp 会显得偏小，
                                // 与下方「机制预测」行的字重/字号也对不上（真机反馈两行大小不一）
                                modifier = Modifier.size(15.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = stringResource(
                                    R.string.koil_add_window_prefix,
                                    nextDate.let { if (it.length >= 5) it.substring(5) else it },
                                ),
                                // 与「機制預測」行同字号：两行是同一张卡里的并列结论，12sp 显得矮一截
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = textColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            // 建议徽章贴在窗口日行右侧：它是对「这一轮调价」的结论，跟日期同屏最直观
                            if (suggestion.isNotEmpty()) {
                                Spacer(Modifier.width(6.dp))
                                AdjustBadge(
                                    text = suggestion,
                                    color = suggestionColor,
                                    bold = true,
                                )
                            }
                        }
                        if (forecast != null) {
                            // 头行给结论（「机制预测：上调约 X 元/吨」），随后是数据时点行。
                            // 建议徽章放在最上面的窗口日行右侧（与「这一轮调什么」同屏），
                            // 免责句则并进数据时点行末尾的括号里，不再单独占一行。
                            // 官方搁浅门槛：|幅度| < 50 元/吨本轮不作调整。不单独分支就会显示
                            // 「预计下调约 0 元/吨」或「预计上调约 47 元/吨」，用户以为会调、公告却是「不调」
                            val headRes = when {
                                stranded -> R.string.koil_add_forecast_mech_stranded
                                eiaSource && direction == ForecastDirection.UP -> R.string.koil_add_forecast_mech_up
                                eiaSource -> R.string.koil_add_forecast_mech_down
                                rising -> R.string.koil_add_forecast_single_up
                                else -> R.string.koil_add_forecast_single_down
                            }
                            // 折合值的方向必须与 headRes 同源：EIA 源走向量 direction（含搁浅），
                            // 腾讯源只有涨/跌两态。不能只看 rising（forecast > 0），否则搁浅时
                            // 会出现「机制预测：搁浅」+「折合约涨」的自相矛盾。
                            // 搁浅不显示折合：<50 元/吨 折不到 0.04 元/升，四舍五入常显示 0.00，
                            // 反而让人以为「白算了一个数」。
                            val perLiterRes = when {
                                stranded -> null
                                eiaSource && direction == ForecastDirection.UP -> R.string.koil_add_forecast_per_liter_up
                                eiaSource -> R.string.koil_add_forecast_per_liter_down
                                rising -> R.string.koil_add_forecast_per_liter_up
                                else -> R.string.koil_add_forecast_per_liter_down
                            }
                            val perLiterText = if (perLiterRes != null && perLiter != null) {
                                stringResource(perLiterRes, kotlin.math.abs(perLiter))
                            } else {
                                ""
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = stringResource(
                                    headRes,
                                    kotlin.math.abs(forecast).toInt().toString(),
                                ) + perLiterText,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = textColor,
                            )
                            if (eiaSource) {
                                // 样本天数恒显示：满窗口也写「已计入 10 个交易日」，
                                // 只在 <10 时显示会让满窗口看起来像没标样本的最终值。
                                // 脚注只保留两段事实：数据截至哪天、算进了几个交易日，末尾拼免责句。
                                // 发布日期不再展示——它只是对滞后的解释性信息，「数据截至」本身已能
                                // 回答「数新不新」，砍掉后 360dp 窄屏也更容易单行放下。
                                val meta = buildList {
                                    uiState.forecastDataDate?.let {
                                        add(
                                            stringResource(
                                                R.string.koil_add_forecast_as_of,
                                                it.takeLast(5),
                                            ),
                                        )
                                    }
                                    uiState.forecastSampleDays?.let {
                                        add(stringResource(R.string.koil_add_forecast_sample, it))
                                    }
                                }
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    text = meta.joinToString(
                                        stringResource(R.string.koil_add_forecast_meta_sep),
                                    ) + stringResource(R.string.koil_add_forecast_caveat_inline),
                                    fontSize = 11.sp,
                                    color = textColor.copy(alpha = 0.65f),
                                )
                            } else {
                                // 无 EIA 时没有「截至」行，免责句仍需出现，单独作末段
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    text = stringResource(R.string.koil_add_forecast_caveat),
                                    fontSize = 11.sp,
                                    color = textColor.copy(alpha = 0.65f),
                                )
                            }
                            if (uiState.forecastFxStale) {
                                // 汇率缓存超 90 天：数值仍给出（有边界比没有好），但如实告知数据旧，
                                // 提示用户这次误差可能比平时大
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = stringResource(R.string.koil_add_forecast_fx_stale),
                                    fontSize = 11.sp,
                                    color = textColor.copy(alpha = 0.65f),
                                )
                            }
                        } else if (uiState.forecastFxMissing) {
                            // 与普通「待检测」区分开：这是汇率没取到（快照到位会触发重算、自动出数），
                            // 不是 EIA/腾讯都挂了。合并成一个空态用户会以为卡死。
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = stringResource(R.string.koil_add_forecast_fx_pending),
                                fontSize = 11.sp,
                                color = textColor.copy(alpha = 0.65f),
                            )
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
                        // 统一走 NumberFormat：直接拼 Double（"45.0 L"/"油量 38.0"）会带多余小数点，金额 toInt() 截断分位，
                        // 与全站金额口径（两位小数）不一致。
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
        // miuix TextField 无 placeholder 参数，用 Box 叠加层实现"待计算"占位（与补录卡同款做法）
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
        "add_failed" -> stringResource(R.string.koil_precheck_add_failed_generic)  // 固定文案，不透出内部异常
        else -> stringResource(R.string.koil_precheck_default)
    }
    val (bgColor, textColor) = when {
        precheck.isDuplicate || precheck.mileageDecreased ->
            Color(0x1AFF3B30) to Color(0xFFFF3B30)
        precheck.priceDeviationExceeded ->
            Color(0x1AFF9500) to Color(0xFFFF9500)
        // "已添加"与"可提交"同用绿色成功态（灰色默认态会像提示又像报错）
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
