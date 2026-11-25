package com.llpop.koil.ui.screen

import com.llpop.koil.ui.component.rememberPopupBackdrop
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.drop
import top.yukonga.miuix.kmp.glass.GlassTextField
import top.yukonga.miuix.kmp.glass.GlassTextFieldDefaults
import com.llpop.koil.ui.component.KOiLDecimalInput
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WaterDrop
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.boundsInWindow
import top.yukonga.miuix.kmp.glass.glassPopupAnchorValue
import top.yukonga.miuix.kmp.glass.rememberGlassPopupAnchor
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.llpop.koil.R
import com.llpop.koil.data.oil.ForecastDirection
import com.llpop.koil.data.oil.forecastDirection
import com.llpop.koil.domain.logic.NumberFormat
import com.llpop.koil.ui.component.gradeValueLabel
import com.llpop.koil.ui.component.fieldSurfaceColor
import com.llpop.koil.ui.component.groupedCardItems
import com.llpop.koil.ui.component.CardItem
import com.llpop.koil.ui.component.LocalOnGlassSurface
import com.llpop.koil.ui.component.CardSegment
import com.llpop.koil.ui.component.KOiLGlassDialog
import com.llpop.koil.ui.component.KOiLScreenScaffold
import com.llpop.koil.ui.component.pageContentPadding
import com.llpop.koil.ui.component.ImeGap
import com.llpop.koil.ui.component.sanitizeDecimalInput
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.llpop.koil.ui.screen.add.AddUiState
import com.llpop.koil.ui.screen.add.AddViewModel
import com.llpop.koil.ui.theme.KOiLColors
import com.llpop.koil.ui.theme.LocalKOiLDarkTheme
import com.llpop.koil.ui.component.provinceLabel
import kotlinx.coroutines.flow.first
import org.koin.androidx.compose.koinViewModel
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.glass.GlassDialogButton
import top.yukonga.miuix.kmp.glass.GlassDialogButtonRole
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TextFieldDefaults
import top.yukonga.miuix.kmp.basic.DropdownArrowEndAction
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.llpop.koil.ui.theme.KOiLType
import com.llpop.koil.ui.component.blur.rememberBlurBackdrop
import com.llpop.koil.ui.component.blur.overScrollVertical
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import top.yukonga.miuix.kmp.glass.GlassPopupItem
import com.llpop.koil.ui.component.GlassPopupOverlay
import com.llpop.koil.ui.component.pageScrollModifiers
import com.llpop.koil.ui.component.LocalPageBackdrop
import com.llpop.koil.ui.component.rememberSnackbar
import com.llpop.koil.ui.component.FullTankSwitchRow
import com.llpop.koil.ui.component.KOiLDateField
import com.llpop.koil.data.location.LocationHelper
import com.llpop.koil.data.settings.AppSettings

/**
 * 录入页（M2 完整实现）
 * - 油价四宫格柔光卡（当前省份四油号最新价格）
 * - 上次加油条（一键沿用）
 * - 加油表单：日期 / 油号（联动价格）/ 里程 / 油量 / 应付（自动计算）/ 实付
 * - 实时预检：重复 / 里程递减 / 行驶里程 / 价格偏差
 * - 添加记录入库
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    bottomPadding: androidx.compose.ui.unit.Dp = 0.dp,
    viewModel: AddViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val locationHelper = org.koin.compose.koinInject<LocationHelper>()

    // 定位权限提醒弹窗显隐
    val showPermissionDialog = remember { mutableStateOf(false) }
    // 定位成功一次性提示（Snackbar，文案走 stringResource 本地化）
    val context = LocalContext.current
    // 一次性反馈统一走全局 Snackbar（挂在根宿主，切页不丢）
    val snackbar = rememberSnackbar()
    // 首页不再显示任何「有新版本」提示：新版本改由系统通知告知一次，
    // 下载与安装全部留在关于页（通知点进来直接落在那一页）。
    // 文案在组合期解析（stringResource 跟随 Configuration 变化），
    // 不在副作用里读 context.getString——语言切换后不会拿到旧语种的缓存值
    val locateToast = uiState.locateMessage?.let {
        stringResource(R.string.koil_add_locate_switched, it)
    }
    LaunchedEffect(uiState.locateMessage, locateToast) {
        if (locateToast != null) {
            snackbar(locateToast)
            viewModel.clearLocateMessage()
        }
    }

    // 首次打开：未授权定位时自动弹出定位权限提醒引导（仅一次，之后由用户主动点击定位按钮）
    val settings = org.koin.compose.koinInject<AppSettings>()
    LaunchedEffect(Unit) {
        if (!locationHelper.hasPermission() && !settings.hasPromptedLocation.first()) {
            settings.setHasPromptedLocation(true)
            showPermissionDialog.value = true
        }
    }

    // 录入成功的一次性反馈（清除状态，避免下次进入页面重复提示）
    // 文案在组合期解析：不能等副作用里再读 context.getString（Configuration 变化后可能拿到旧语种）
    val precheckAddedText = stringResource(R.string.koil_precheck_added)
    LaunchedEffect(uiState.addSuccess) {
        if (uiState.addSuccess) {
            snackbar(precheckAddedText)
            viewModel.consumeAddSuccess()
        }
    }

    val locationPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions(),
    ) { permissions ->
        val granted = permissions.entries.any { it.value }
        if (granted) viewModel.locateProvince()
    }

    // 顶栏模糊判据用列表状态（见 KOiLScreenScaffold 的 listState 参数）
    val listState = rememberLazyListState()
    // 页面自己的 backdrop，录制挂在下方的滚动容器上（顶栏渐变带与卡片玻璃都采样它）
    val pageBackdrop = rememberBlurBackdrop()
    KOiLScreenScaffold(
        title = stringResource(R.string.koil_add_title),
        modifier = modifier,
        backdrop = pageBackdrop,
        listState = listState,
    ) { innerPadding, topPadding, scrollBehavior, pageBackdrop ->
        val imeVisible = WindowInsets.isImeVisible
        // 首页所有输入框都在同一张录入卡里：键盘一弹就让整张卡完整落进可视区（列表可视区已被 imePadding 切到键盘上沿）。
        // 走 bringIntoView 而不是 scrollToItem/scrollBy：前者和"手指点进输入框"是同一条滚动路径，
        // 会经过顶栏的嵌套滚动，顶栏才会照常折叠、模糊态才成立；程序化跳转不经过那条路径，
        // 顶栏会停在展开态（大标题还在左边、也没有模糊）。
        val formBringIntoView = remember { BringIntoViewRequester() }
        // 只在"首页自己的输入框拿到焦点"时抬卡：别的页面（补录/编辑弹层）把键盘弹起来时，
        // 首页仍被 Pager 组合着，若无条件 bringIntoView，请求会顺着 Pager 的滚动链把当前页滚回首页。
        var homeFieldFocused by remember { mutableStateOf(false) }
        LaunchedEffect(imeVisible, homeFieldFocused) {
            if (!imeVisible || !homeFieldFocused) return@LaunchedEffect
            // 键盘动画期间 inset 连续变化，等它稳定再定位
            delay(240)
            formBringIntoView.bringIntoView()
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .pageScrollModifiers(
                    enableScrollEndHaptic = true,
                    showTopAppBar = true,
                    topAppBarScrollBehavior = scrollBehavior,
                )
                // edge-to-edge 下键盘不 resize 窗口，补 imePadding 让表单底部（实付/提交按钮）不被键盘遮挡
                .imePadding(),
            // 底部留白统一走 contentPadding（与总览页、设置页同一种写法）。
            // 键盘弹起时只留 12dp：此时底部悬浮条已被键盘盖住，再留一整段导航栏高度会像"内容没渲染出来"；
            // 键盘收起才让出 bottomPadding（底栏自身占的高度）。
            // 横向边距不在骨架里给：由 CardSegment / groupedCardItems 的 outerHorizontalPadding(12dp) 承担
            overscrollEffect = null,
            contentPadding = pageContentPadding(
                innerPadding,
                extraBottom = if (imeVisible) ImeGap else bottomPadding,
            ),
        ) {

            // 油价四宫格：一组一卡
            groupedCardItems(
                keyPrefix = "add_oilPrice",
                outerBottomPadding = 12.dp,
                items = listOf(
                    CardItem("oilPrice") {
                        OilPriceCard(
                    uiState = uiState,
                    onProvinceSelect = viewModel::setProvince,
                    backdrop = pageBackdrop,
                    onLocateClick = {
                        if (locationHelper.hasPermission()) {
                            viewModel.locateProvince()
                        } else {
                            showPermissionDialog.value = true
                        }
                    },
                )
                    },
                ),
            )

            // 上次加油条：一组一卡，没有上一条记录时不出现
            if (uiState.lastRecord != null) {
                groupedCardItems(
                    keyPrefix = "add_lastRecord",
                    outerBottomPadding = 12.dp,
                        items = listOf(
                        CardItem("lastRecord") {
                            LastRecordBar(uiState = uiState, onApply = viewModel::applyLastRecord)
                        },
                    ),
                )
            }
            // 加油表单 / 预检提示 / 提交按钮：三个 CardItem 走同一个入口，
            // 组底距统一由 groupedCardItems 的 outerBottomPadding 承担
            groupedCardItems(
                keyPrefix = "add_form",
                outerBottomPadding = 12.dp,
                items = listOf(
                    CardItem("form") {
                    Column(
                        modifier = Modifier.bringIntoViewRequester(formBringIntoView),
                    ) {
                        // 表单三行：底部 12dp = 块模型 ①「上方内容 → 块上缘」，留在高亮之外——
                        // 上距若塞进行内 insideMargin，高亮会一路铺到上一字段下方、看起来「贴住」上一行。
                        Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 12.dp)) {
                            // 第一行：日期（默认今天，只读展示；组件结构与补录/编辑统一，后续开启可编辑直接传回调）+ 油号（联动价格）
                            FormRow {
                                KOiLDateField(
                                    modifier = Modifier.weight(1f),
                                    date = uiState.date,
                                )
                                Spacer(Modifier.width(12.dp))
                                GradeField(
                                    modifier = Modifier.weight(1f),
                                    grade = uiState.oilGrade,
                                    price = uiState.currentPrice,
                                    onGradeSelected = viewModel::setOilGrade,
                                    backdrop = pageBackdrop,
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
                                    onFieldFocusChange = { homeFieldFocused = it },
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
                                    onFieldFocusChange = { homeFieldFocused = it },
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
                                    onFieldFocusChange = { homeFieldFocused = it },
                                    readOnly = true,
                                    placeholder = stringResource(R.string.koil_ov_payable_pending),
                                )
                                Spacer(Modifier.width(12.dp))
                                FormField(
                                    modifier = Modifier.weight(1f),
                                    icon = Icons.Filled.Money,
                                    label = stringResource(R.string.koil_add_field_actual),
                                    value = uiState.actualAmount,
                                    isLastField = true,
                                    onValueChange = viewModel::setActualAmount,
                                    keyboardType = KeyboardType.Decimal,
                                    onFieldFocusChange = { homeFieldFocused = it },
                                    error = uiState.precheck.priceDeviationExceeded || uiState.precheck.amountOverPayable,
                                )
                            }
                        }
                        // 满箱标记（跳枪）：油耗按满箱区间结算的关键输入，默认勾选；整行铺满卡片宽。
                        // 与补录弹窗同一个参数（bottomInset 走默认 8dp）：跳枪行已不在卡片底部，
                        // 高亮停在块下缘即可，块下缘 → 下一段的间距由下面段的容器自己承担。
                        FullTankSwitchRow(
                            checked = uiState.isFullTank,
                            onCheckedChange = viewModel::setFullTank,
                        )
                    }
                    },
                    CardItem("precheck") {
                        Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                            PrecheckBanner(uiState = uiState)
                        }
                    },
                    CardItem("submit") {
                        // 底部 16dp = miuix 行内基准（BasicComponentDefaults.InsideMargin），与
                        // 设置页等卡片末行到卡底的距离一致；原先四边统一 8dp，玻璃整卡化后
                        // 按钮离卡片下缘明显比别的卡窄
                        Box(modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 16.dp)) {
                            // 添加记录按钮（全部校验通过 → 蓝色可提交，否则一律提交不了）
                            Button(
                    onClick = viewModel::addRecord,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp),
                    enabled = uiState.precheck.isReady && !uiState.isAdding,
                    // 主按钮预设自带禁用态配色（深色下是深蓝，而不是纯黑）
                    colors = ButtonDefaults.buttonColorsPrimary(color = KOiLColors.Primary),
                ) {
                    Text(
                        text = if (uiState.isAdding) stringResource(R.string.koil_add_adding) else stringResource(R.string.koil_add_add_record),
                        fontSize = KOiLType.Title,
                        fontWeight = FontWeight.Medium,
                            )
                        }
                        }
                    },
                ),
            )
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

// ===== 定位权限提醒弹窗 =====
@Composable
private fun PermissionDialog(
    onGrant: () -> Unit,
    onDismiss: () -> Unit,
) {
    KOiLGlassDialog(
        show = true,
        onDismissRequest = onDismiss,
        title = stringResource(R.string.koil_add_loc_permission_title),
    ) {
        Column {
            Text(
                text = stringResource(R.string.koil_add_loc_permission_message),
                fontSize = KOiLType.Body,
                color = MiuixTheme.colorScheme.onBackgroundVariant,
            )
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                GlassDialogButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    role = GlassDialogButtonRole.Normal,
                ) {
                    Text(
                        text = stringResource(R.string.koil_add_loc_cancel),
                        style = MiuixTheme.textStyles.button,
                    )
                }
                GlassDialogButton(
                    onClick = onGrant,
                    modifier = Modifier.weight(1f),
                    role = GlassDialogButtonRole.Normal,
                ) {
                    Text(
                        text = stringResource(R.string.koil_add_loc_grant),
                        style = MiuixTheme.textStyles.button,
                    )
                }
            }
        }
    }
}

// ===== 油价卡头部的迷你胶囊按钮 =====
/**
 * 油价卡标题行右侧的定位/省份胶囊。
 *
 * 用实心 surfaceVariant 铺在采样玻璃上会显成一块「贴片」，更糟的是默认波纹（Ripple）是
 * 亮色矩形/圆，按下瞬间比按钮本身亮得多，在两个小胶囊上格外跳。玻璃面上因此改为
 * onSurface 薄涂 + 0.8dp 细边，按下只加深底色（不走 indication），边界由细边交代。
 * 非玻璃面（模糊关闭 / 实色卡）保持原来的实色底 + 按下加深，不加边。
 */
@Composable
private fun GlassAwareHeaderChip(
    onGlass: Boolean,
    fallbackBg: Color,
    onClick: () -> Unit,
    padding: PaddingValues,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val shape = RoundedCornerShape(8.dp)
    // 玻璃面上这两枚胶囊彻底不铺底：0.07 的灰底 + 描边在深色玻璃上仍会被看成
    // 「贴上去的方块」。可点的暗示由图标本身和省份文字后的 ⌄ 承担，按下才浮出一层
    // 很淡的底（0.12），松手即消失。
    val bg = when {
        onGlass -> MiuixTheme.colorScheme.onSurface.copy(alpha = if (pressed) 0.12f else 0f)
        pressed -> fallbackBg.copy(alpha = (fallbackBg.alpha * 1.8f).coerceAtMost(1f))
        else -> fallbackBg
    }
    Box(
        // 命中区仍撑到 48×48dp：胶囊本身只有 30×24dp 左右，远低于 Material 的 48dp 建议
        modifier = Modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            // indication = null 时无需 clip 抑制波纹矩形；裁圆角只为视觉一致
            .clip(RoundedCornerShape(12.dp))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .clip(shape)
                .background(bg)
                .padding(padding),
            contentAlignment = Alignment.Center,
        ) {
            content()
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
    backdrop: LayerBackdrop?,
) {
    val showProvincePicker = remember { mutableStateOf(false) }
    var provinceBounds by remember { mutableStateOf(androidx.compose.ui.geometry.Rect.Zero) }
    val provinceAnchor = rememberGlassPopupAnchor()
    // 主题感知配色：深色下用柔和亮蓝主色 + 蓝色柔光容器底（避免亮色调 PrimaryContainer 发白突兀），浅色保持原样
    // 主题是设置项（跟随系统/浅色/深色）：必须读实际生效值，读系统值会在手动主题下反色
    val dark = LocalKOiLDarkTheme.current
    val accent = MiuixTheme.colorScheme.primary            // 深色=#0A84FF 柔和亮蓝 / 浅色=#0071E3
    // 玻璃卡内的小胶囊按钮不能再铺实色灰：0.6 alpha 的 surfaceVariant 叠在采样玻璃上会
    // 变成一块「贴片」，与周围透出的底色断开。玻璃面上换成 onSurface 的低 alpha 中性薄涂，
    // 可点的语义由图标/箭头自身承担。
    val onGlass = LocalOnGlassSurface.current
    val containerBg = when {
        onGlass -> MiuixTheme.colorScheme.onSurface.copy(alpha = 0.07f)
        dark -> MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        else -> KOiLColors.PrimaryContainer.copy(alpha = 0.5f)
    }
    val containerText = if (dark) accent else KOiLColors.OnPrimaryContainer
    // 深色档与「油耗偏高」共用 AttentionNight：同为「需要注意」语义，不再各留一个值。
    val urgentColor = if (dark) KOiLColors.AttentionNight else KOiLColors.AttentionDay
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
                    fontSize = KOiLType.Title * titleScale,
                    fontWeight = FontWeight.Bold,
                    color = MiuixTheme.colorScheme.onBackground,
                    maxLines = 1,
                    softWrap = false,
                    onTextLayout = { if (it.hasVisualOverflow && titleScale > 0.6f) titleScale *= 0.92f },
                )
                Text(
                    text = stringResource(R.string.koil_add_price_db_version, uiState.versionLabel),
                    fontSize = KOiLType.CaptionLarge,
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                    maxLines = 1,
                    softWrap = false,
                )
            }
            // 定位按钮：48×48dp 命中区托着一个小胶囊，视觉尺寸不变。
            // 玻璃面上不走波纹——默认波纹是亮色矩形/圆，叠在采样玻璃上比按钮本身还显眼，
            // 点击反馈改成胶囊底色加深（见 GlassAwareHeaderChip）。
            GlassAwareHeaderChip(
                onGlass = onGlass,
                fallbackBg = containerBg,
                onClick = onLocateClick,
                padding = PaddingValues(horizontal = 8.dp, vertical = 5.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.MyLocation,
                    contentDescription = stringResource(R.string.koil_add_locate_cd),
                    tint = containerText,
                    modifier = Modifier.size(14.dp),
                )
            }
            Spacer(Modifier.width(2.dp))
            // 省份选择按钮（点击弹官方玻璃下拉菜单，锚定按钮、跟随位置）
            // 同上——玻璃面上去波纹，点击反馈走底色
            GlassAwareHeaderChip(
                onGlass = onGlass,
                fallbackBg = containerBg,
                onClick = { showProvincePicker.value = true },
                padding = PaddingValues(horizontal = 10.dp, vertical = 5.dp),
            ) {
                Row(
                    modifier = Modifier
                        // 记录可见胶囊自身的窗口坐标，官方玻璃面板按这个矩形对齐到正下方
                        .onGloballyPositioned { provinceBounds = it.boundsInWindow() },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // 省份文字 14sp Bold + 箭头直接跟随（无手动间距）
                    Text(
                        // 省份是数据库主键（中文），显示层用本地化名
                        text = provinceLabel(uiState.viewProvince),
                        fontSize = KOiLType.BodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = containerText,
                    )
                    Spacer(Modifier.width(8.dp))
                    // 锚点只挂右侧箭头：值留在左边，面板右边缘对齐胶囊右边
                    Row(
                        modifier = Modifier.glassPopupAnchorValue(provinceAnchor),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // 省份下拉箭头——用 Miuix DropdownArrowEndAction（与设置页 Dropdown / 油号选择
                        // 行尾同款线条风格），不用实心小三角 ArrowDropDown（用户反馈不显眼）
                        DropdownArrowEndAction(actionColor = containerText)
                    }
                }
            }
            // 统一走根宿主的玻璃面板（与数据总览补录/编辑窗同一套 GlassPopupOverlay）
            GlassPopupOverlay(
                show = showProvincePicker.value,
                anchorBounds = provinceBounds,
                anchor = provinceAnchor,
                backdrop = rememberPopupBackdrop(),
                onDismissRequest = { showProvincePicker.value = false },
                maxHeight = 360.dp,
            ) {
                PROVINCES.forEach { p ->
                    GlassPopupItem(
                        text = provinceLabel(p),
                        selected = p == uiState.viewProvince,
                        onClick = {
                            onProvinceSelect(p)
                            showProvincePicker.value = false
                        },
                    )
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        // 四宫格：92# / 95# / 98# / 0#（仅价格，无涨跌幅）
        // 每格独立灰底圆角块，格间 8dp —— 与设置页「油价数据与提醒」的统计四宫格同款
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PriceCell(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.05f)),
                grade = "92#",
                price = uiState.priceMap["92"],
            )
            PriceCell(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.05f)),
                grade = "95#",
                price = uiState.priceMap["95"],
            )
            PriceCell(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.05f)),
                grade = "98#",
                price = uiState.priceMap["98"],
            )
            PriceCell(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.05f)),
                grade = "0#",
                price = uiState.priceMap["0"],
            )
        }
        // 调价窗口提示（对齐 Web 三块式：预计调价元/吨 · 折合元/升 · 建议）
        // 由设置页「油价调整窗口提醒」开关控制显示（开显示，关隐藏）
        if (uiState.showPriceAdjust) uiState.nextAdjustDate?.let { nextDate ->
            Spacer(Modifier.height(6.dp))
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
            // 提醒条是玻璃卡内部的强调块：底色留半透，让卡片的磨砂底透上来，
            // 不自己再叠一层采样（否则同区域内两层模糊，观感变灰、多一层绘制）。
            // 0xFF17191D 这类「按实色卡调的深色块」铺在采样玻璃上仍会显成一贴片，
            // 玻璃面上统一退成 onSurface 中性薄涂，强调交给左侧色条 + 橙色文字。
            val bgColor = when {
                onGlass -> MiuixTheme.colorScheme.onSurface.copy(alpha = 0.06f)
                dark -> Color(0xFF17191D).copy(alpha = 0.45f)
                urgent -> urgentColor.copy(alpha = 0.16f)
                else -> KOiLColors.PrimaryContainer.copy(alpha = 0.5f)
            }
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
                            fontSize = KOiLType.Body,
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
                            fontSize = KOiLType.Body,
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
                                fontSize = KOiLType.CaptionLarge,
                                color = textColor.copy(alpha = 0.65f),
                            )
                        } else {
                            // 无 EIA 时没有「截至」行，免责句仍需出现，单独作末段
                            Spacer(Modifier.height(3.dp))
                            Text(
                                text = stringResource(R.string.koil_add_forecast_caveat),
                                fontSize = KOiLType.CaptionLarge,
                                color = textColor.copy(alpha = 0.65f),
                            )
                        }
                        if (uiState.forecastFxStale) {
                            // 汇率缓存超 90 天：数值仍给出（有边界比没有好），但如实告知数据旧，
                            // 提示用户这次误差可能比平时大
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = stringResource(R.string.koil_add_forecast_fx_stale),
                                fontSize = KOiLType.CaptionLarge,
                                color = textColor.copy(alpha = 0.65f),
                            )
                        }
                    } else if (uiState.forecastFxMissing) {
                        // 与普通「待检测」区分开：这是汇率没取到（快照到位会触发重算、自动出数），
                        // 不是 EIA/腾讯都挂了。合并成一个空态用户会以为卡死。
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = stringResource(R.string.koil_add_forecast_fx_pending),
                            fontSize = KOiLType.CaptionLarge,
                            color = textColor.copy(alpha = 0.65f),
                        )
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
            fontSize = KOiLType.CaptionLarge,
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
        // 上下 12dp：与色块一起构成设置页四宫格同款的内边距
        modifier = modifier.padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = grade,
            fontSize = KOiLType.BodySmall,
            color = MiuixTheme.colorScheme.onBackgroundVariant,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = if (price != null) NumberFormat.measure(price) else "--",
            fontSize = KOiLType.Headline,
            fontWeight = FontWeight.Bold,
            color = MiuixTheme.colorScheme.primary,
        )
        Text(
            text = stringResource(R.string.koil_add_unit_per_liter),
            fontSize = KOiLType.Caption,
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
                fontSize = KOiLType.BodySmall,
                color = MiuixTheme.colorScheme.onBackgroundVariant,
            )
            if (last != null) {
                Text(
                    // 统一走 NumberFormat：直接拼 Double（"45.0 L"/"油量 38.0"）会带多余小数点，金额 toInt() 截断分位，
                    // 与全站金额口径（两位小数）不一致。
                    text = "${last.reoilDate.takeLast(5)} · ${NumberFormat.measure(last.mileage)} km · " +
                        "${NumberFormat.measure(last.oilAmount)} L · ¥${NumberFormat.money(last.actualAmount)}",
                    fontSize = KOiLType.Body,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onBackground,
                    maxLines = 1,
                )
            } else {
                Text(
                    text = stringResource(R.string.koil_add_no_record),
                    fontSize = KOiLType.Body,
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
                    fontSize = KOiLType.BodySmall,
                    fontWeight = FontWeight.Medium,
                    color = Color.White,
                )
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
    onFieldFocusChange: ((Boolean) -> Unit)? = null,
    /** 本窗口最后一个可编辑字段：回车=收键盘，而不是跳下一项 */
    isLastField: Boolean = false,
) {
    val errorColor = KOiLColors.Error
    val focusManager = LocalFocusManager.current
    val submitTick = remember { mutableIntStateOf(0) }
    // 同一次回车可能同时被 ImeAction 与字符提交两条信号捕获：350ms 内只跳一次，
    // 否则会连跳两个字段（设置页同款门闩）
    var navGateAt by remember { mutableLongStateOf(0L) }
    val advanceFromField: () -> Unit = {
        val now = System.currentTimeMillis()
        if (now - navGateAt >= 350L) {
            navGateAt = now
            if (isLastField) focusManager.clearFocus() else focusManager.moveFocus(FocusDirection.Next)
        }
    }
    LaunchedEffect(Unit) {
        snapshotFlow { submitTick.intValue }.drop(1).collect { advanceFromField() }
    }
    val state = rememberTextFieldState(value)
    var fieldFocused by remember { mutableStateOf(false) }
    // 外部值变更时回填；只在未聚焦时覆盖，避免打断正在输入的光标
    LaunchedEffect(value, fieldFocused) {
        if (!fieldFocused && state.text.toString() != value) {
            state.setTextAndPlaceCursorAtEnd(value)
        }
    }
    LaunchedEffect(state) {
        snapshotFlow { state.text.toString() }.drop(1).collect { onValueChange(it) }
    }
    // 与设置页（EIA / WebDAV 三框）同一套配色：默认玻璃表面底色 + 聚焦时主色边框。
    // 错误态只把边框/标签换成红色，其余不覆盖——否则又回到「一个组件的两种观感」。
    val tfColors = if (error) {
        GlassTextFieldDefaults.textFieldColors(labelColor = errorColor, borderColor = errorColor)
    } else {
        GlassTextFieldDefaults.textFieldColors()
    }
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
                fontSize = KOiLType.BodySmall,
                color = if (error) errorColor else MiuixTheme.colorScheme.onBackgroundVariant,
            )
        }
        Spacer(Modifier.height(6.dp))
        // miuix TextField 无 placeholder 参数，用 Box 叠加层实现"待计算"占位（与补录卡同款做法）
        Box(modifier = Modifier.fillMaxWidth()) {
            GlassTextField(
                state = state,
                modifier = Modifier
                    .fillMaxWidth()
                    // 只读字段（如自动计算出的应付）不参与焦点序列，回车能直接跳过它
                    .focusProperties { canFocus = !readOnly }
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown &&
                            (event.key == Key.Enter || event.key == Key.NumPadEnter)
                        ) {
                            advanceFromField()
                            true
                        } else {
                            false
                        }
                    }
                    .onFocusChanged {
                        fieldFocused = it.isFocused
                        onFieldFocusChange?.invoke(it.isFocused)
                    },
                readOnly = readOnly,
                lineLimits = TextFieldLineLimits.SingleLine,
                keyboardOptions = KeyboardOptions(
                    keyboardType = keyboardType,
                    imeAction = if (isLastField) ImeAction.Done else ImeAction.Next,
                ),
                onKeyboardAction = { advanceFromField() },
                colors = tfColors,
                inputTransformation = KOiLDecimalInput { submitTick.intValue++ },
            )
            if (placeholder != null && state.text.isEmpty()) {
                Text(
                    text = placeholder,
                    // 与框内输入文字同字号，避免「提示小一号、输入大一号」
                    style = MiuixTheme.textStyles.main,
                    color = MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.5f),
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = GlassTextFieldDefaults.InsideMargin.width),
                )
            }
        }
    }
}

/** 玻璃面板内的一行：选中态加粗 + 主色 + 勾，与官方弹出菜单观感一致 */

// ===== 油号选择字段（行样式与 DateField 一致：标签行 + 52dp 输入框；弹窗用官方玻璃下拉菜单：锚定输入框、面板跳卡片裁剪、采样页面 backdrop） =====
@Composable
private fun GradeField(
    modifier: Modifier = Modifier,
    grade: String,
    price: Double?,
    onGradeSelected: (String) -> Unit,
    backdrop: LayerBackdrop?,
) {
    val gradeLabels = listOf("92", "95", "98", "0")
    val showMenu = remember { mutableStateOf(false) }
    var gradeBounds by remember { mutableStateOf(androidx.compose.ui.geometry.Rect.Zero) }
    val gradeAnchor = rememberGlassPopupAnchor()
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
                fontSize = KOiLType.BodySmall,
                color = MiuixTheme.colorScheme.onBackgroundVariant,
            )
        }
        Spacer(Modifier.height(6.dp))
        // 与 DateField、FormField(GlassTextField) 同构：同一圆角 / 同一内边距 / 同一行文字，高度自然相等
        Box(
            modifier = Modifier
                .fillMaxWidth()
                // 记录整个输入框的窗口矩形，玻璃面板据此对齐到正下方
                .onGloballyPositioned { gradeBounds = it.boundsInWindow() }
                // 与 GlassTextField 同构：同一圆角、同一内边距、同一行文字，高度自然相等
                .clip(RoundedCornerShape(GlassTextFieldDefaults.CornerRadius))
                .background(fieldSurfaceColor())
                .clickable { showMenu.value = true }
                .padding(
                    horizontal = GlassTextFieldDefaults.InsideMargin.width,
                    vertical = GlassTextFieldDefaults.InsideMargin.height,
                ),
            contentAlignment = Alignment.CenterStart,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 值在左、箭头在右（锚点只挂右侧箭头）；文案与补录窗共用 gradeValueLabel，两处同源
                Text(
                    text = gradeValueLabel(grade, price),
                    style = MiuixTheme.textStyles.main,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                Row(
                    modifier = Modifier.glassPopupAnchorValue(gradeAnchor),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // 与设置页语言选择同款下拉箭头（miuix DropdownArrowEndAction）
                    DropdownArrowEndAction(
                        actionColor = MiuixTheme.colorScheme.onBackgroundVariant,
                    )
                }
            }
        }
    }

    // 统一走根宿主的玻璃面板
    GlassPopupOverlay(
        show = showMenu.value,
        anchorBounds = gradeBounds,
        anchor = gradeAnchor,
        backdrop = rememberPopupBackdrop(),
        onDismissRequest = { showMenu.value = false },
        maxHeight = 360.dp,
    ) {
        gradeLabels.forEach { g ->
            val label = if (g == "0") stringResource(R.string.koil_add_grade_diesel)
                else stringResource(R.string.koil_add_grade_gasoline, g)
            GlassPopupItem(
                text = label,
                selected = g == grade,
                onClick = {
                    onGradeSelected(g)
                    showMenu.value = false
                },
            )
        }
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
    // 语义容器色是 10% alpha 的 tint，按实色卡调的；铺到采样玻璃上仍会显成一块色片。
    // 玻璃面上把 tint 再压一半、默认态的实心灰换成 onSurface 薄涂，语义交给文字色本身。
    val onGlass = LocalOnGlassSurface.current
    val (bgColor, textColor) = when {
        precheck.isDuplicate || precheck.mileageDecreased ->
            (if (onGlass) KOiLColors.ErrorContainer.copy(alpha = 0.055f) else KOiLColors.ErrorContainer) to
                KOiLColors.Error
        precheck.priceDeviationExceeded ->
            (if (onGlass) KOiLColors.CautionContainer.copy(alpha = 0.055f) else KOiLColors.CautionContainer) to
                KOiLColors.Caution
        // "已添加"与"可提交"同用绿色成功态（灰色默认态会像提示又像报错）
        precheck.isReady || precheck.messageKey == "added" ->
            (if (onGlass) KOiLColors.SuccessContainer.copy(alpha = 0.055f) else KOiLColors.SuccessContainer) to
                KOiLColors.Success
        else ->
            // 在卡组内做凹槽底：用主题的次要禁用色，深色下比卡片亮一档、浅色下是浅灰
            (if (onGlass) MiuixTheme.colorScheme.onSurface.copy(alpha = 0.06f)
            else MiuixTheme.colorScheme.disabledSecondaryVariant) to
                MiuixTheme.colorScheme.onBackgroundVariant
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
                    fontSize = KOiLType.BodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = textColor,
                )
                Spacer(Modifier.width(6.dp))
            }
            Text(
                text = precheckText,
                fontSize = KOiLType.Body,
                color = textColor,
            )
        }
    }
}

