package com.llpop.koil.ui.screen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Money
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WaterDrop
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.combinedClickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.ui.Alignment
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.input.pointer.pointerInput
import com.llpop.koil.R
import com.llpop.koil.ui.component.KOiLBlurBackground
import com.llpop.koil.ui.component.KOiLScreenScaffold
import com.llpop.koil.ui.component.sanitizeDecimalInput
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.llpop.koil.data.oil.OilPriceNetwork
import com.llpop.koil.domain.logic.ConsumptionStatus
import com.llpop.koil.domain.logic.NumberFormat
import com.llpop.koil.ui.screen.overview.OverviewUiState
import com.llpop.koil.ui.screen.overview.OverviewViewModel
import com.llpop.koil.domain.logic.RecordWithDerived
import com.llpop.koil.ui.screen.overview.TrendPoint
import com.llpop.koil.ui.theme.KOiLColors
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.DropdownArrowEndAction
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TextFieldDefaults
import top.yukonga.miuix.kmp.popup.WindowDropdownPopup
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.util.Locale

private enum class SwipeActionValue { CLOSED, OPEN }

/**
 * 总览页（对齐 Web 手机端）
 * - 六指标 2×2 网格（带图标 + 较上期）
 * - 趋势分析：双轴折线图（左轴油耗 L/100km，右轴成本 元/km）
 * - 异常卡（空态 + 右上"N 次加油"徽标）
 * - 加油历史：按月分组 + 三段式记录卡（胶囊/区间/核心三栏带环比/次要三栏）
 * - 左滑出操作（编辑蓝 / 删除红）+ 右下角返回顶部
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OverviewScreen(
    modifier: Modifier = Modifier,
    bottomPadding: androidx.compose.ui.unit.Dp = 0.dp,
    onNavigateToAdd: (() -> Unit)? = null,
    viewModel: OverviewViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    // v1.5.20：全页改 LazyColumn 惰性渲染，历史记录只组合可见项；不再需要按月手动分页（visibleMonths）
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var showBackToTop by remember { mutableStateOf(false) }
    var moreMenuFor by remember { mutableStateOf<Long?>(null) }
    // v1.5.31：异常卡点击 → 异常明细弹窗
    var showAbnormalDetail by remember { mutableStateOf(false) }
    // v1.5.5：长按操作的卡片引用 + 全局位置（全屏遮罩点击任意处收起）
    var actionRecord by remember { mutableStateOf<RecordWithDerived?>(null) }
    var actionRect by remember { mutableStateOf<Rect?>(null) }
    // v1.5.9：待确认删除的记录（弹确认框后才真正删除）
    var pendingDelete by remember { mutableStateOf<RecordWithDerived?>(null) }

    // v1.5.20（#11）：snapshotFlow 监听 LazyList 滚动位置，仅在"是否显示返回顶部"翻转时更新状态，
    // 不再用 LaunchedEffect(scrollState.value) 每次像素变化都触发
    LaunchedEffect(listState) {
        snapshotFlow {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 300
        }
            .distinctUntilChanged()
            .collect { showBackToTop = it }
    }

    // v1.5.20（#9）：月分组与 prevMap 只在 records 变化时重算，不再每次重组都 groupBy/buildPrevMap
    val monthGroups = remember(uiState.records) {
        uiState.records.groupBy { it.record.reoilDate.substring(0, 7) }.toList()
    }
    val prevMap = remember(uiState.records) { buildPrevMap(uiState.records) }
    // v1.5.46：趋势图可选的油号（当前范围有数据的油号，按 92/95/98/0 固定顺序）
    val availableGrades = remember(uiState.records) {
        val order = listOf("92", "95", "98", "0")
        uiState.records.mapNotNull { it.record.oilGrade }
            .distinct()
            .filter { it in order }
            .sortedBy { order.indexOf(it) }
    }
    // v1.5.20（#10）：长按相关回调稳定（remember 一次），配合 items(key=id) 让未变化卡片可跳过重组
    val onMoreMenuCard = remember {
        { rec: RecordWithDerived ->
            if (moreMenuFor == rec.record.id) {
                moreMenuFor = null
                actionRecord = null
            } else {
                moreMenuFor = rec.record.id
                actionRecord = rec
            }
        }
    }
    // 只有当前选中卡片才会触发 onRect（RecordCard 内以 showMoreMenu 门控），故无需 id 即可稳定
    val onActionRect = remember { { rect: Rect -> actionRect = rect } }

    val rangeLabels = listOf(
        "all" to stringResource(R.string.koil_range_all),
        "year" to stringResource(R.string.koil_range_year),
        "6m" to stringResource(R.string.koil_range_6m),
        "3m" to stringResource(R.string.koil_range_3m),
    )

    KOiLScreenScaffold(
        title = stringResource(R.string.koil_ov_title),
        modifier = modifier,
    ) { _, topPadding, scrollBehavior, _ ->
        Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(horizontal = 12.dp),
        ) {
            // 顶部避让 TopAppBar（含状态栏，折叠时高度联动）
            item { Spacer(Modifier.height(topPadding)) }

            // 范围筛选器（对齐 Web：全部/今年/近6个月/近3个月）
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    rangeLabels.forEach { (key, label) ->
                        val selected = uiState.range == key
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (selected) KOiLColors.Primary else Color.Transparent)
                                .clickable { viewModel.setRange(key) }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                        ) {
                            Text(
                                text = label,
                                fontSize = 12.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                color = if (selected) Color.White else MiuixTheme.colorScheme.onBackground,
                            )
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(12.dp)) }

            // 六指标 2×2 网格（对齐 Web：带图标 + 较上期）
            item { SummaryGrid(uiState = uiState) }
            item { Spacer(Modifier.height(12.dp)) }

            // 趋势分析（双轴一张图）；v1.5.46：支持油号筛选
            item {
                TrendChart(
                    trend = uiState.trend,
                    grade = uiState.trendGrade,
                    onGradeChange = viewModel::setTrendGrade,
                    availableGrades = availableGrades,
                )
            }
            item { Spacer(Modifier.height(12.dp)) }

            // 异常卡（对齐 Web：空态 + 右上"N 次加油"徽标）；v1.5.31：有异常时可点击查看明细
            item {
                AbnormalAlertCard(
                    count = uiState.abnormalRecords.size,
                    rangeLabel = rangeLabel(uiState.range),
                    recordCount = uiState.records.size,
                    avgConsumption = uiState.avgConsumption,
                    onClick = if (uiState.abnormalRecords.isNotEmpty()) { { showAbnormalDetail = true } } else { null },
                )
            }
            item { Spacer(Modifier.height(12.dp)) }

            // 加油历史区（对齐 Web：标题 + 共N条 + 补录）
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.koil_ov_history),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MiuixTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.koil_ov_total_count_in_list, uiState.records.size),
                        fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.onBackgroundVariant,
                    )
                    Spacer(Modifier.weight(1f))
                    // v1.5.4：補錄改为胶囊按钮（主色浅底 + 加号图标），点击区域加大
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(KOiLColors.Primary.copy(alpha = 0.12f))
                            .clickable { viewModel.openBackfill() }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = null,
                            tint = KOiLColors.Primary,
                            modifier = Modifier.size(15.dp),
                        )
                        Spacer(Modifier.width(3.dp))
                        Text(
                            text = stringResource(R.string.koil_ov_backfill),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = KOiLColors.Primary,
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(10.dp)) }

            // v1.5.20（#8）：LazyColumn 惰性渲染——月份标题 stickyHeader 吸顶，卡片仅组合可见项；
            // 无记录时显示空态引导。已移除手动按月分页（visibleMonths）与"滚动加载更多"提示
            if (uiState.records.isEmpty()) {
                item { EmptyHistoryBox(onAddClick = onNavigateToAdd) }
            } else {
                monthGroups.forEach { (monthKey, monthRecords) ->
                    stickyHeader(key = "header-$monthKey") {
                        // v1.5.24：吸顶检测（用 layoutInfo offset，不受自身绘制 offset 影响，无振荡）——
                        // 标题进入顶栏区域(offset ≤ 顶栏高度)即视为贴顶：绘制 offset 下移顶栏高度贴顶栏底部（连续无跳变），
                        // 未贴顶时透明无背景色块
                        // v1.5.25：吸顶背景改半透明 surface——原 textureBlur(backdrop) 会采样到 header 自身
                        //（header 位于 backdrop 录制子树内部，与顶栏不同）→ 自采样递归导致闪退；
                        // 半透明 surface 观感接近顶栏毛玻璃且无递归风险
                        val density = LocalDensity.current
                        val topBarPx = with(density) { topPadding.toPx() }
                        // v1.5.49：吸顶分隔线颜色在 Composable 上下文取（drawBehind 内不可读主题色）
                        val stickyLineColor = MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.12f)
                        val sticky by remember(topPadding) {
                            derivedStateOf {
                                val off = listState.layoutInfo.visibleItemsInfo
                                    .firstOrNull { it.key == "header-$monthKey" }?.offset ?: Int.MAX_VALUE
                                off <= topBarPx + 2
                            }
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(
                                    if (sticky) {
                                        Modifier.offset { IntOffset(0, topBarPx.roundToInt()) }
                                    } else {
                                        Modifier
                                    },
                                )
                                .then(
                                    if (sticky) {
                                        // v1.5.49：吸顶背景对齐顶栏混合参数（surface 0.8，与 BlurredBar 同参）
                                        // + 底部 0.5dp 细线（同 SettingDivider 颜色），视觉上呈顶栏"半透明延续"
                                        Modifier
                                            .background(MiuixTheme.colorScheme.surface.copy(alpha = 0.8f))
                                            .drawBehind {
                                                val y = size.height - 0.5.dp.toPx()
                                                drawLine(
                                                    color = stickyLineColor,
                                                    start = Offset(0f, y),
                                                    end = Offset(size.width, y),
                                                    strokeWidth = 0.5.dp.toPx(),
                                                )
                                            }
                                    } else {
                                        Modifier
                                    },
                                )
                                .padding(top = 6.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // v1.5.24：月份文字 16sp；笔数同字号、右对齐
                            Text(
                                text = monthTitle(monthKey),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MiuixTheme.colorScheme.onBackground,
                                modifier = Modifier.padding(start = 4.dp),
                            )
                            Spacer(Modifier.weight(1f))
                            Text(
                                text = stringResource(R.string.koil_ov_month_count, monthRecords.size),
                                fontSize = 16.sp,
                                color = MiuixTheme.colorScheme.onBackgroundVariant,
                                modifier = Modifier.padding(end = 4.dp),
                            )
                        }
                    }
                    // v1.5.20（#10）：key=记录 id，回调稳定；未变化的滚出/滚入卡片可被跳过/回收
                    items(items = monthRecords, key = { it.record.id }) { record ->
                        RecordCard(
                            record = record,
                            prevRecord = prevMap[record.record.id],
                            isInitial = prevMap[record.record.id] == null,
                            showMoreMenu = moreMenuFor == record.record.id,
                            onMoreMenu = onMoreMenuCard,
                            onRect = onActionRect,
                            modifier = Modifier.padding(bottom = 10.dp),
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(bottomPadding + 12.dp)) }
        }

        // v1.5.5：长按全屏操作层——点击任意处收起；圆按钮定位在长按卡片中央
        AnimatedVisibility(
            visible = moreMenuFor != null,
            enter = fadeIn() + scaleIn(initialScale = 0.95f, animationSpec = tween(150)),
            exit = fadeOut() + scaleOut(targetScale = 0.98f, animationSpec = tween(120)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.38f))
                    .clickable {
                        moreMenuFor = null
                        actionRecord = null
                        actionRect = null
                    },
            ) {
                actionRect?.let { r ->
                    val density = LocalDensity.current
                    val rowW = with(density) { 54.dp.toPx() * 2 + 28.dp.toPx() }
                    val rowH = with(density) { 54.dp.toPx() + 6.dp.toPx() + 16.dp.toPx() }
                    Row(
                        modifier = Modifier.offset {
                            IntOffset(
                                (r.center.x - rowW / 2).roundToInt(),
                                (r.center.y - rowH / 2).roundToInt(),
                            )
                        },
                        horizontalArrangement = Arrangement.spacedBy(28.dp),
                    ) {
                        LongPressCircleAction(
                            icon = Icons.Filled.Edit,
                            label = stringResource(R.string.koil_ov_edit),
                            bg = Color(0xFF2563EB),
                        ) {
                            moreMenuFor = null
                            actionRecord?.let { viewModel.startEdit(it.record) }
                            actionRecord = null
                            actionRect = null
                        }
                        LongPressCircleAction(
                            icon = Icons.Filled.Delete,
                            label = stringResource(R.string.koil_ov_delete),
                            bg = Color(0xFFE53935),
                        ) {
                            val rec = actionRecord
                            moreMenuFor = null
                            actionRecord = null
                            actionRect = null
                            // v1.5.9：先弹确认框，确认后才真正删除
                            rec?.let { pendingDelete = it }
                        }
                    }
                }
            }
        }

        // 右下角返回顶部按钮（对齐 Web；bottom 抬高避让悬浮导航条）
        if (showBackToTop) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = bottomPadding + 16.dp)
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(KOiLColors.Primary)
                    .clickable {
                        scope.launch { listState.animateScrollToItem(0) }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(text = "↑", fontSize = 18.sp, color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
    }

    // v1.5.31：异常明细弹窗（有异常时点异常卡弹出）；v1.5.32：修复记录叠加——KOiLGlassDialog 滚动容器是
    // Box，多个直接子项会全部叠在同一位置，必须在 content 内包 Column 垂直排列；补标题（title 参数未渲染）；偏差取绝对值
    if (showAbnormalDetail && uiState.abnormalRecords.isNotEmpty()) {
        com.llpop.koil.ui.component.KOiLGlassDialog(
            show = true,
            onDismissRequest = { showAbnormalDetail = false },
            contentPadding = 20.dp,
            scrollable = true,
            maxHeight = 480.dp,
            widthFraction = 0.82f,
        ) {
            Column(Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.koil_ov_abnormal_detail_title, uiState.abnormalRecords.size),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = MiuixTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.height(4.dp))
                uiState.abnormalRecords.forEachIndexed { index, d ->
                    val rec = d.record
                    val isHigh = d.consumptionStatus == ConsumptionStatus.HIGH
                    val accentColor = if (isHigh) Color(0xFFE65100) else Color(0xFFF59E0B)
                    val statusTxt = stringResource(
                        if (isHigh) R.string.koil_ov_abnormal_high else R.string.koil_ov_abnormal_low
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = rec.reoilDate.takeLast(5),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = MiuixTheme.colorScheme.onBackground,
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "${rec.oilGrade ?: "-"}#",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = KOiLColors.Primary,
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = "${NumberFormat.measure(d.oilConsumption ?: 0.0)} L/100km",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MiuixTheme.colorScheme.onBackground,
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "$statusTxt ${NumberFormat.pct(abs(d.deviationPct))}%",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = accentColor,
                        )
                    }
                    if (index < uiState.abnormalRecords.lastIndex) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(0.5.dp)
                                .background(MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.12f))
                        )
                    }
                }
            }
        }
    }

    // v1.5.9：删除确认弹窗（防误删）
    pendingDelete?.let { rec ->
        com.llpop.koil.ui.component.KOiLGlassDialog(
            show = true,
            onDismissRequest = { pendingDelete = null },
            contentPadding = 20.dp,
            widthFraction = 0.82f,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = stringResource(R.string.koil_ov_delete_confirm_title),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = MiuixTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = stringResource(R.string.koil_ov_delete_confirm_sub),
                    fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                )
                Spacer(Modifier.height(20.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.koil_common_cancel),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = MiuixTheme.colorScheme.onBackgroundVariant,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .clickable { pendingDelete = null }
                            .padding(vertical = 11.dp),
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = stringResource(R.string.koil_ov_delete_confirm),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFFE53935))
                            .clickable {
                                pendingDelete = null
                                viewModel.deleteRecord(rec.record.id)
                            }
                            .padding(vertical = 11.dp),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }

    // 编辑弹窗（v1.5.13 复刻补录卡：日期只读 + 全量校验 + 保存置灰/变蓝）
    if (uiState.showEditDialog) {
        EditRecordDialog(
            uiState = uiState,
            onMileageChange = viewModel::setEditMileage,
            onOilAmountChange = viewModel::setEditOilAmount,
            onActualAmountChange = viewModel::setEditActualAmount,
            onGradeChange = viewModel::setEditGrade,
            onProvinceChange = viewModel::setEditProvince,
            onPayableChange = viewModel::setEditPayableAmount,
            onSave = viewModel::saveEdit,
            onDismiss = viewModel::cancelEdit,
        )
    }

    // 补录弹窗（source=backfill_modal）
    if (uiState.showBackfillDialog) {
        val maxH = (LocalConfiguration.current.screenHeightDp * 0.85f).dp
        com.llpop.koil.ui.component.KOiLGlassDialog(
            show = true,
            onDismissRequest = viewModel::cancelBackfill,
            contentPadding = 16.dp,
            scrollable = true,
            maxHeight = maxH,
            widthFraction = 0.90f,
        ) {
            Column {
                Text(stringResource(R.string.koil_ov_backfill_title), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MiuixTheme.colorScheme.onBackground)
                Spacer(Modifier.height(16.dp))
                // 加油省份（与设置页同款下拉列表）
                ProvinceSelectField(province = uiState.backfillProvince, onProvinceChange = viewModel::setBackfillProvince)
                Spacer(Modifier.height(14.dp))
                // 第一行：日期（日历可改）+ 油号（联动当日参考单价）
                Row(verticalAlignment = Alignment.Top) {
                    com.llpop.koil.ui.component.KOiLDateField(modifier = Modifier.weight(1f), date = uiState.backfillDate, recordDates = uiState.backfillRecordDates, onDateSelected = viewModel::setBackfillDate)
                    Spacer(Modifier.width(14.dp))
                    GradePriceSelect(
                        grade = uiState.backfillGrade,
                        price = uiState.backfillReferencePrice,
                        onGradeSelected = viewModel::setBackfillGrade,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(14.dp))
                // v1.5.12：选日期后该区间可输入里程范围提示（日期行与里程行之间）
                if (uiState.backfillRangeMin != null || uiState.backfillRangeMax != null) {
                    MileageRangeHint(
                        rangeMin = uiState.backfillRangeMin,
                        rangeMax = uiState.backfillRangeMax,
                    )
                    Spacer(Modifier.height(14.dp))
                }
                // 第二行：里程 + 加油量（v1.5.12 图标/红色错误态对齐首页录入卡）
                Row {
                    EditField(
                        label = stringResource(R.string.koil_add_field_mileage),
                        icon = Icons.Filled.Speed,
                        value = uiState.backfillMileage,
                        onValueChange = viewModel::setBackfillMileage,
                        keyboardType = KeyboardType.Number,
                        error = uiState.backfillMileageConflictType.isNotBlank() || uiState.backfillSameDayExists,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(14.dp))
                    EditField(
                        label = stringResource(R.string.koil_add_field_oil_amount),
                        icon = Icons.Filled.WaterDrop,
                        value = uiState.backfillOilAmount,
                        onValueChange = viewModel::setBackfillOilAmount,
                        keyboardType = KeyboardType.Decimal,
                        error = uiState.backfillOverTank,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(14.dp))
                // 第三行：应付（只读自动计算）+ 实付（v1.5.12 对齐首页录入卡）
                Row {
                    EditField(
                        label = stringResource(R.string.koil_add_field_payable),
                        icon = Icons.Filled.Money,
                        value = uiState.backfillPayableAmount,
                        onValueChange = viewModel::setBackfillPayableAmount,
                        keyboardType = KeyboardType.Decimal,
                        readOnly = true,
                        // v1.5.14：应付未计算（油量/参考价缺失）时显示占位
                        placeholder = stringResource(R.string.koil_ov_payable_pending),
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(14.dp))
                    EditField(
                        label = stringResource(R.string.koil_add_field_actual),
                        icon = Icons.Filled.Money,
                        value = uiState.backfillActualAmount,
                        onValueChange = viewModel::setBackfillActualAmount,
                        keyboardType = KeyboardType.Decimal,
                        error = uiState.backfillDeviationExceeded || uiState.backfillOverPayable,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(10.dp))
                // 实付综合信息块（对齐 Web PaymentSummaryCard：优惠概览/实际单价/参考价对比）
                PaymentSummaryCard(
                    payable = uiState.backfillPayableAmount.toDoubleOrNull(),
                    actual = uiState.backfillActualAmount.toDoubleOrNull(),
                    fuelAmount = uiState.backfillOilAmount.toDoubleOrNull(),
                    referencePrice = uiState.backfillReferencePrice,
                )
                Spacer(Modifier.height(8.dp))
                // v1.5.11：补录校验提示（用户确认规则：日期边界/重复/里程链/偏差A+B/实付>应付/油箱容量）
                BackfillPrecheckHint(
                    beforeFirst = uiState.backfillBeforeFirst,
                    sameDayExists = uiState.backfillSameDayExists,
                    mileageConflictType = uiState.backfillMileageConflictType,
                    mileageConflictDate = uiState.backfillMileageConflictDate,
                    mileageConflictValue = uiState.backfillMileageConflictValue,
                    deviationPct = uiState.backfillDeviationPct,
                    deviationExceeded = uiState.backfillDeviationExceeded,
                    overPayable = uiState.backfillOverPayable,
                    overTank = uiState.backfillOverTank,
                    tankCapacity = uiState.backfillTankCapacity,
                    ready = uiState.backfillReady,
                    readyText = stringResource(R.string.koil_ov_bf_ready),
                )
                // v1.5.16：保存失败提示（如唯一约束冲突等）
                if (uiState.saveError) {
                    Spacer(Modifier.height(8.dp))
                    SaveErrorHint()
                }
                Spacer(Modifier.height(20.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    Button(onClick = viewModel::cancelBackfill, modifier = Modifier.weight(1f).height(44.dp)) { Text(stringResource(R.string.koil_common_cancel), fontSize = 14.sp) }
                    Spacer(Modifier.width(12.dp))
                    Button(
                        onClick = viewModel::saveBackfill,
                        modifier = Modifier.weight(1f).height(44.dp),
                        enabled = uiState.backfillReady && !uiState.isSavingBackfill,
                        // v1.5.13：全部校验通过 → 蓝色可保存；否则灰色置灰（对齐首页录入卡 addRecord）
                        colors = ButtonDefaults.buttonColors(
                            color = KOiLColors.Primary,
                            contentColor = Color.White,
                            disabledColor = MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            disabledContentColor = MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.4f),
                        ),
                    ) { Text(stringResource(R.string.koil_common_save), fontSize = 14.sp, fontWeight = FontWeight.Medium) }
                }
            }
        }
    }
}

@Composable
private fun rangeLabel(range: String): String = when (range) {
    "year" -> stringResource(R.string.koil_range_year)
    "6m" -> stringResource(R.string.koil_range_6m)
    "3m" -> stringResource(R.string.koil_range_3m)
    else -> stringResource(R.string.koil_range_all)
}

@Composable
private fun monthTitle(monthKey: String): String {
    val parts = monthKey.split("-")
    return if (parts.size == 2)
        stringResource(R.string.koil_ov_month_title, parts[0].toInt(), parts[1].toInt())
    else monthKey
}

/** 构建正序前一条记录映射（用于环比与区间） */
private fun buildPrevMap(records: List<RecordWithDerived>): Map<Long, RecordWithDerived> {
    val map = mutableMapOf<Long, RecordWithDerived>()
    val asc = records.asReversed()
    for (i in 1 until asc.size) {
        map[asc[i].record.id] = asc[i - 1]
    }
    return map
}

/** 加油历史空态：无记录时的引导提示 */
@Composable
private fun EmptyHistoryBox(onAddClick: (() -> Unit)? = null) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Filled.WaterDrop,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.4f),
                modifier = Modifier.size(36.dp),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.koil_ov_empty_title),
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = MiuixTheme.colorScheme.onBackgroundVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.koil_ov_empty_hint),
                fontSize = 12.sp,
                color = MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.7f),
            )
            // v1.5.46：#5 空状态引导——可一键前往录入页（仅当宿主传入导航回调时显示）
            if (onAddClick != null) {
                Spacer(Modifier.height(16.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .background(KOiLColors.Primary)
                        .clickable(onClick = onAddClick)
                        .padding(horizontal = 22.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.koil_ov_empty_action),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                    )
                }
            }
        }
    }
}

// ===== 六指标 2×2 网格（对齐 Web） =====
@Composable
private fun SummaryGrid(uiState: OverviewUiState) {
    val stats = uiState.stats
    val prev = uiState.prevStats
    fun pct(cur: Double, prevVal: Double?): Double? =
        if (prevVal != null && prevVal > 0) (cur - prevVal) / prevVal * 100 else null

    KOiLBlurBackground(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 20f,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // —— 六指标 3×2（v1.5.24：2×3 改 3×2，每行 3 项；v1.5.26：列间显式 12dp 间距 + 内容居中）——
            Row(modifier = Modifier.fillMaxWidth()) {
                StatCell(
                    Modifier.weight(1f),
                    icon = Icons.Filled.WaterDrop,
                    label = stringResource(R.string.koil_stat_avg_consumption),
                    value = NumberFormat.measure(stats.avgOilConsumption ?: 0.0),
                    unit = "L/100km",
                    comparePct = pct(stats.avgOilConsumption ?: 0.0, prev?.avgOilConsumption),
                    valueFontSize = 24.sp,
                )
                Spacer(Modifier.width(12.dp))
                StatCell(
                    Modifier.weight(1f),
                    icon = Icons.Filled.Money,
                    label = stringResource(R.string.koil_stat_cost_per_km),
                    value = NumberFormat.money(stats.avgCostPerKm ?: 0.0),
                    unit = stringResource(R.string.koil_stat_cost_unit),
                    comparePct = pct(stats.avgCostPerKm ?: 0.0, prev?.avgCostPerKm),
                    valueFontSize = 24.sp,
                )
                Spacer(Modifier.width(12.dp))
                StatCell(
                    Modifier.weight(1f),
                    icon = Icons.Filled.Speed,
                    label = stringResource(R.string.koil_stat_total_mileage),
                    value = "${stats.totalDistance.toInt()}",
                    unit = "km",
                    comparePct = pct(stats.totalDistance, prev?.totalDistance),
                )
            }
            Spacer(Modifier.height(14.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                StatCell(
                    Modifier.weight(1f),
                    icon = Icons.Filled.LocalGasStation,
                    label = stringResource(R.string.koil_stat_total_oil),
                    value = "${stats.totalOilAmount.toInt()}",
                    unit = "L",
                    comparePct = pct(stats.totalOilAmount, prev?.totalOilAmount),
                )
                Spacer(Modifier.width(12.dp))
                StatCell(
                    Modifier.weight(1f),
                    icon = Icons.Filled.Payments,
                    label = stringResource(R.string.koil_stat_total_cost),
                    value = "¥${NumberFormat.money(stats.totalActual)}",
                    unit = "",
                    comparePct = pct(stats.totalActual, prev?.totalActual),
                )
                Spacer(Modifier.width(12.dp))
                StatCell(
                    Modifier.weight(1f),
                    icon = Icons.Filled.Receipt,
                    label = stringResource(R.string.koil_stat_total_payable),
                    value = "¥${NumberFormat.money(stats.totalPayable)}",
                    unit = "",
                    comparePct = pct(stats.totalPayable, prev?.totalPayable),
                )
            }
            // v1.5.24：删除「實付 vs 應付 对比条」——用户要求移除节省提示
            Spacer(Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.12f))
            )
            Spacer(Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.koil_ov_total_count, stats.totalCount),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "%s ¥%s/%s · %s %d km".format(Locale.ROOT, 
                        stringResource(R.string.koil_stat_avg_price),
                        NumberFormat.measure(stats.avgUnitPriceActual ?: 0.0),
                        stringResource(R.string.koil_stat_price_unit),
                        stringResource(R.string.koil_stat_avg_distance),
                        (stats.avgDistancePerReoil ?: 0.0).toInt(),
                    ),
                    fontSize = 11.sp,
                    color = MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.7f),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.koil_ov_vs_prev),
                    fontSize = 11.sp,
                    color = MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.6f),
                )
            }
        }
    }
}

@Composable
private fun StatCell(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    value: String,
    unit: String,
    comparePct: Double? = null,
    valueFontSize: TextUnit = 19.sp,
) {
    // v1.5.26：单元格内容水平居中——原 start 对齐导致三列内容都挤在格子左侧、
    // 右侧留白约 20% 屏宽，视觉整体偏左；居中后三列视觉完全平衡
    Column(modifier = modifier.padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = KOiLColors.Primary.copy(alpha = 0.85f),
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = label,
                fontSize = 11.sp,
                color = MiuixTheme.colorScheme.onBackgroundVariant,
            )
        }
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = value,
                fontSize = valueFontSize,
                fontWeight = FontWeight.Bold,
                color = MiuixTheme.colorScheme.onBackground,
            )
            if (unit.isNotBlank()) {
                Spacer(Modifier.width(2.dp))
                Text(
                    text = unit,
                    fontSize = 10.sp,
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                    modifier = Modifier.padding(bottom = 3.dp),
                )
            }
        }
        comparePct?.let { pct ->
            if (abs(pct) >= 0.5) {
                Spacer(Modifier.height(2.dp))
                val up = pct > 0
                Text(
                    text = "${if (up) "↑" else "↓"}${NumberFormat.pct(abs(pct))}%",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (up) Color(0xFFE65100) else Color(0xFF2E7D32),
                )
            } else {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.koil_ov_vs_prev),
                    fontSize = 10.sp,
                    color = MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.5f),
                )
            }
        }
    }
}

// ===== 趋势分析：双轴折线图（对齐 Web） =====
@Composable
private fun TrendChart(
    trend: List<TrendPoint>,
    grade: String,
    onGradeChange: (String) -> Unit,
    availableGrades: List<String>,
) {
    KOiLBlurBackground(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (availableGrades.size >= 2) 228.dp else 200.dp),
        cornerRadius = 20f,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                    contentDescription = null,
                    tint = KOiLColors.Primary,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.koil_ov_trend),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.weight(1f))
                // 图例
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(KOiLColors.Primary))
                    Spacer(Modifier.width(3.dp))
                    Text(stringResource(R.string.koil_ov_legend_consumption), fontSize = 9.sp, color = MiuixTheme.colorScheme.onBackgroundVariant)
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.size(6.dp).clip(CircleShape).background(Color(0xFFF97316)))
                    Spacer(Modifier.width(3.dp))
                    Text(stringResource(R.string.koil_ov_legend_cost), fontSize = 9.sp, color = MiuixTheme.colorScheme.onBackgroundVariant)
                }
            }
            Spacer(Modifier.height(8.dp))
            // v1.5.46：油号筛选胶囊（全部 + 当前范围有数据的油号）
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                GradeFilterChip(
                    label = stringResource(R.string.koil_ov_trend_all),
                    selected = grade == "all",
                    onClick = { onGradeChange("all") },
                )
                availableGrades.forEach { g ->
                    GradeFilterChip(
                        label = OilPriceNetwork.GRADE_LABELS[g]?.removeSuffix("号汽油")?.removeSuffix("号柴油")
                            ?.plus("#") ?: "$g#",
                        selected = grade == g,
                        onClick = { onGradeChange(g) },
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            if (trend.size >= 2) {
                DualAxisCanvas(trend = trend)
            } else {
                Box(
                    modifier = Modifier.fillMaxWidth().height(118.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.koil_ov_trend_insufficient),
                        fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.onBackgroundVariant,
                    )
                }
            }
        }
    }
}

/** v1.5.46：趋势图油号筛选胶囊（选中主色实底） */
@Composable
private fun GradeFilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) KOiLColors.Primary else MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) Color.White else MiuixTheme.colorScheme.onBackgroundVariant,
        )
    }
}

@Composable
private fun DualAxisCanvas(trend: List<TrendPoint>) {
    val gridColor = MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.1f)
    val labelColor = MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.4f)
    val consumptions = trend.mapNotNull { it.oilConsumption }
    val costs = trend.mapNotNull { it.costPerKm }
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)
    ) {
        val padL = 30.dp.toPx()
        val padR = 34.dp.toPx()
        val padT = 8.dp.toPx()
        val padB = 22.dp.toPx()
        val w = size.width - padL - padR
        val h = size.height - padT - padB

        // 左轴（油耗 2.0~8.0 对齐 Web，自动兜底）
        val leftMin = consumptions.minOrNull()?.let { it * 0.75 } ?: 2.0
        val leftMax = consumptions.maxOrNull()?.let { it * 1.25 } ?: 8.0
        val leftRange = (leftMax - leftMin).coerceAtLeast(0.1)

        // 右轴（成本 0.10~0.60 对齐 Web，自动兜底）
        val rightMin = costs.minOrNull()?.let { (it * 0.75 * 100).roundToInt() / 100.0 } ?: 0.10
        val rightMax = costs.maxOrNull()?.let { (it * 1.25 * 100).roundToInt() / 100.0 } ?: 0.60
        val rightRange = (rightMax - rightMin).coerceAtLeast(0.05)

        // 横向网格线 + 左/右轴刻度标签
        val gridLines = 4
        for (i in 0..gridLines) {
            val frac = i.toFloat() / gridLines
            val y = padT + h * frac
            drawLine(
                color = gridColor,
                start = Offset(padL, y),
                end = Offset(padL + w, y),
                strokeWidth = 1.dp.toPx(),
            )
            // 左轴标签
            val lv = leftMax - leftRange * frac
            drawContext.canvas.nativeCanvas.apply {
                val paint = android.graphics.Paint().apply {
                    color = android.graphics.Color.argb(
                        153,
                        (labelColor.red * 255).toInt(),
                        (labelColor.green * 255).toInt(),
                        (labelColor.blue * 255).toInt(),
                    )
                    textSize = 9.dp.toPx()
                }
                drawText(NumberFormat.measure(lv), padL - 4.dp.toPx(), y + 3.dp.toPx(), paint)
                // 右轴标签
                val rv = rightMin + rightRange * (1 - frac)
                drawText(NumberFormat.measure(rv), padL + w + 4.dp.toPx(), y + 3.dp.toPx(), paint)
            }
        }

        fun xAt(i: Int): Float = padL + (i.toFloat() / (trend.size - 1).coerceAtLeast(1)) * w

        // 平均油耗线（蓝，主色）
        val consPath = Path()
        var consStarted = false
        val consPts = mutableListOf<Offset>()
        for ((i, p) in trend.withIndex()) {
            if (p.oilConsumption == null) { consStarted = false; continue }
            val x = xAt(i)
            val y = padT + h - (((p.oilConsumption - leftMin) / leftRange).toFloat() * h)
            consPts.add(Offset(x, y))
            if (!consStarted) { consPath.moveTo(x, y); consStarted = true }
            else consPath.lineTo(x, y)
        }
        if (consPts.size >= 2) {
            drawPath(path = consPath, color = KOiLColors.Primary, style = Stroke(width = 2.dp.toPx()))
            consPts.forEach { drawCircle(color = KOiLColors.Primary, radius = 2.5.dp.toPx(), center = it) }
        }

        // 每公里成本线（橙）
        val costPath = Path()
        var costStarted = false
        val costPts = mutableListOf<Offset>()
        for ((i, p) in trend.withIndex()) {
            if (p.costPerKm == null) { costStarted = false; continue }
            val x = xAt(i)
            val y = padT + h - (((p.costPerKm - rightMin) / rightRange).toFloat() * h)
            costPts.add(Offset(x, y))
            if (!costStarted) { costPath.moveTo(x, y); costStarted = true }
            else costPath.lineTo(x, y)
        }
        if (costPts.size >= 2) {
            drawPath(path = costPath, color = Color(0xFFF97316), style = Stroke(width = 2.dp.toPx()))
            costPts.forEach { drawCircle(color = Color(0xFFF97316), radius = 2.5.dp.toPx(), center = it) }
        }

        // 横轴日期标签（每2个显示一个避免拥挤）
        for (i in trend.indices) {
            if (i % 2 == 1 && trend.size > 4) continue
            val x = xAt(i)
            val label = trend[i].date
            drawContext.canvas.nativeCanvas.apply {
                val paint = android.graphics.Paint().apply {
                    color = android.graphics.Color.argb(
                        102,
                        (labelColor.red * 255).toInt(),
                        (labelColor.green * 255).toInt(),
                        (labelColor.blue * 255).toInt(),
                    )
                    textSize = 8.dp.toPx()
                }
                drawText(label, x - 12.dp.toPx(), padT + h + 14.dp.toPx(), paint)
            }
        }
    }
}

// ===== 异常卡（对齐 Web：空态 + 右上"N 次加油"）；v1.5.31：有异常时可点击查看明细 =====
@Composable
private fun AbnormalAlertCard(
    count: Int,
    rangeLabel: String,
    recordCount: Int,
    avgConsumption: Double,
    onClick: (() -> Unit)? = null,
) {
    KOiLBlurBackground(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 20f,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (onClick != null) {
                        Modifier
                            .clickable(enabled = count > 0, onClick = onClick)
                            .padding(16.dp)
                    } else {
                        Modifier.padding(16.dp)
                    }
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = null,
                tint = if (count > 0) Color(0xFFE65100) else Color(0xFF34C759),
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                if (count > 0) {
                    Text(
                        text = stringResource(R.string.koil_ov_abnormal_count, count),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MiuixTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = stringResource(R.string.koil_ov_abnormal_avg, avgConsumption),
                        fontSize = 11.sp,
                        color = MiuixTheme.colorScheme.onBackgroundVariant,
                    )
                } else {
                    Text(
                        text = stringResource(R.string.koil_ov_abnormal_empty),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MiuixTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = stringResource(R.string.koil_ov_abnormal_stable),
                        fontSize = 11.sp,
                        color = MiuixTheme.colorScheme.onBackgroundVariant,
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = rangeLabel,
                    fontSize = 11.sp,
                    color = KOiLColors.Primary,
                )
                Text(
                    text = stringResource(R.string.koil_ov_record_count, recordCount),
                    fontSize = 10.sp,
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                )
            }
        }
    }
}

// ===== 三段式记录卡（对齐 Web） =====
@Composable
private fun RecordCard(
    record: RecordWithDerived,
    prevRecord: RecordWithDerived?,
    isInitial: Boolean,
    showMoreMenu: Boolean,
    onMoreMenu: (RecordWithDerived) -> Unit,
    onRect: (Rect) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val r = record.record
    val isAbnormal = record.consumptionStatus == ConsumptionStatus.HIGH || record.consumptionStatus == ConsumptionStatus.LOW
    val isHigh = record.consumptionStatus == ConsumptionStatus.HIGH
    val accentColor = when {
        isHigh -> Color(0xFFE65100)
        record.consumptionStatus == ConsumptionStatus.LOW -> Color(0xFFF59E0B)
        else -> KOiLColors.Primary
    }
    val accentBg = when {
        isHigh -> Color(0xFFE65100).copy(alpha = 0.06f)
        record.consumptionStatus == ConsumptionStatus.LOW -> Color(0xFFF59E0B).copy(alpha = 0.07f)
        else -> MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            // v1.5.5：长按显示时上报自身全局位置（页面级全屏层据此定位圆按钮）
            .onGloballyPositioned { if (showMoreMenu) onRect(it.boundsInRoot()) },
    ) {
        KOiLBlurBackground(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 20f,
        ) {
            // 异常左竖条
            if (isAbnormal) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(3.dp)
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    if (isHigh) Color(0xFFE65100) else Color(0xFFF59E0B),
                                    if (isHigh) Color(0xFFD84315) else Color(0xFFD97706),
                                )
                            )
                        )
                        .align(Alignment.CenterStart),
                )
            }
            Column(
                modifier = Modifier
                    .padding(start = if (isAbnormal) 15.dp else 16.dp, end = 12.dp, top = 14.dp, bottom = 14.dp)
                    // v1.5.8：去除按压涟漪（直角长方形）+ 点击不再进入编辑；长按=弹出中央圆按钮
                    .combinedClickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { if (showMoreMenu) onMoreMenu(record) },
                        onLongClick = { if (!showMoreMenu) onMoreMenu(record) },
                    ),
            ) {
                // —— 顶部：日期大字 + 油号胶囊 + 天数胶囊 + 异常胶囊 + ⋮ ——
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = r.reoilDate,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MiuixTheme.colorScheme.onBackground,
                    )
                    Spacer(Modifier.width(8.dp))
                    Pill(
                        text = if (r.oilPrice != null) "${r.oilGrade ?: "-"}# ¥${NumberFormat.measure(r.oilPrice)}" else "${r.oilGrade ?: "-"}#",
                        color = KOiLColors.Primary.copy(alpha = 0.12f),
                        textColor = KOiLColors.Primary,
                        border = KOiLColors.Primary.copy(alpha = 0.25f),
                        // 窄屏多胶囊并存时油号胶囊可收缩省略，防止顶行挤出屏幕
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (record.oilDays != null && !isInitial) {
                        Spacer(Modifier.width(6.dp))
                        Pill(
                            text = stringResource(R.string.koil_ov_oil_days, record.oilDays),
                            color = MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                            textColor = MiuixTheme.colorScheme.onBackgroundVariant,
                            border = MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.2f),
                        )
                    }
                    if (isAbnormal) {
                        Spacer(Modifier.width(6.dp))
                        Pill(
                            text = if (isHigh) stringResource(R.string.koil_ov_high) else stringResource(R.string.koil_ov_low),
                            color = accentColor.copy(alpha = 0.14f),
                            textColor = accentColor,
                            border = accentColor.copy(alpha = 0.3f),
                        )
                    }
                    // 省份徽标：多省加油时可一眼区分记录来源（沿用油号胶囊同款可收缩样式）
                    if (!r.province.isNullOrBlank()) {
                        Spacer(Modifier.width(6.dp))
                        Pill(
                            text = r.province,
                            color = MiuixTheme.colorScheme.secondaryContainer.copy(alpha = 0.8f),
                            textColor = MiuixTheme.colorScheme.onSecondaryContainer,
                            border = MiuixTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.15f),
                            modifier = Modifier.weight(1f, fill = false),
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    // v1.5.4：移除 ⋮ 菜单，长按交互统一（见卡片外层操作层）
                    if (false) Unit
                }

                // —— 区间行：上次 X月X日 · 里程 a → b km / 当前里程 ——
                Spacer(Modifier.height(6.dp))
                if (prevRecord != null) {
                    Text(
                        text = stringResource(
                            R.string.koil_ov_prev_line,
                            md(prevRecord.record.reoilDate),
                            (r.mileage - (record.distance ?: 0.0)).toInt(),
                            r.mileage.toInt(),
                        ),
                        fontSize = 11.sp,
                        color = MiuixTheme.colorScheme.onBackgroundVariant,
                    )
                } else {
                    Text(
                        text = stringResource(R.string.koil_ov_current_mileage, r.mileage.toInt()),
                        fontSize = 11.sp,
                        color = MiuixTheme.colorScheme.onBackgroundVariant,
                    )
                }

                // —— 核心三栏（带环比 delta） ——
                if (record.oilConsumption != null && record.distance != null) {
                    Spacer(Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(accentBg),
                    ) {
                        Row(modifier = Modifier.padding(vertical = 10.dp)) {
                            MetricCell(
                                Modifier.weight(1f),
                                label = stringResource(R.string.koil_ov_metric_consumption),
                                value = NumberFormat.measure(record.oilConsumption),
                                unit = "L/100km",
                                valueColor = accentColor,
                                delta = deltaText(record.oilConsumption, prevRecord?.oilConsumption, lowerBetter = true),
                            )
                            MetricCell(
                                Modifier.weight(1f),
                                label = stringResource(R.string.koil_ov_metric_cost),
                                value = "¥${NumberFormat.money(record.costPerKm ?: 0.0)}",
                                unit = "",
                                valueColor = accentColor,
                                delta = deltaText(record.costPerKm, prevRecord?.costPerKm, lowerBetter = true),
                            )
                            MetricCell(
                                Modifier.weight(1f),
                                label = stringResource(R.string.koil_ov_metric_distance),
                                value = "${record.distance.toInt()}",
                                unit = "km",
                                valueColor = accentColor,
                                delta = deltaText(record.distance, prevRecord?.distance, lowerBetter = false, digits = 0),
                            )
                        }
                    }
                } else if (isInitial) {
                    // Initial Record 虚线提示（对齐 Web）
                    Spacer(Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("ℹ", fontSize = 13.sp, color = MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.7f))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.koil_ov_first_record),
                                fontSize = 11.sp,
                                color = MiuixTheme.colorScheme.onBackgroundVariant,
                            )
                        }
                    }
                }

                // —— 底部三栏：加油量 | 实付金额 | 实付单价 ——
                Spacer(Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(0.5.dp)
                        .background(MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.12f))
                )
                Spacer(Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    RecordDetail(
                        Modifier.weight(1f),
                        stringResource(R.string.koil_col_oil_amount),
                        NumberFormat.measure(r.oilAmount),
                        "L",
                    )
                    RecordDetail(
                        Modifier.weight(1f),
                        stringResource(R.string.koil_col_actual),
                        "¥${NumberFormat.money(r.actualAmount)}",
                        "",
                    )
                    RecordDetail(
                        Modifier.weight(1f),
                        stringResource(R.string.koil_col_unit_price),
                        "¥${NumberFormat.measure(record.unitPriceActual ?: 0.0)}",
                        "",
                        warn = false,
                    )
                }
            }
        }
    }
}

/** v1.5.4：长按弹出的圆形操作按钮（图标 + 文字标签） */
@Composable
private fun LongPressCircleAction(
    icon: ImageVector,
    label: String,
    bg: Color,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(CircleShape)
                .background(bg)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White,
        )
    }
}

private fun RecordWithDerived.isLow(): Boolean = consumptionStatus == ConsumptionStatus.LOW

/** 环比 delta 结果（文本 + 好坏颜色，渲染时按颜色展示，对齐 Web 绿涨/橙跌语义） */
private data class DeltaText(val text: String, val color: Color)

/** 环比 delta 文本（对齐 Web：↓0.26 (4.0%)；里程整数指标 digits=0 → ↓35 (5.7%)，不再显示 35.00） */
private fun deltaText(cur: Double?, prev: Double?, lowerBetter: Boolean, digits: Int = 2): DeltaText? {
    if (cur == null || prev == null || prev <= 0) return null
    val diff = cur - prev
    val pct = diff / prev * 100
    val arrow = if (diff > 0) "↑" else "↓"
    val good = if (lowerBetter) diff < 0 else diff > 0
    val color = if (good) Color(0xFF2E7D32) else Color(0xFFE65100)
    val fmt = if (digits <= 0) "%.0f" else "%.${digits}f"
    val diffTxt = if (digits <= 0) fmt.format(abs(diff)) else NumberFormat.measure(abs(diff))
    return DeltaText(
        text = "$arrow $diffTxt (${NumberFormat.pct(abs(pct))}%)",
        color = color,
    )
}

@Composable
private fun MetricCell(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    unit: String,
    valueColor: Color,
    delta: DeltaText?,
) {
    Column(
        modifier = modifier.padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label,
            fontSize = 9.sp,
            color = valueColor.copy(alpha = 0.6f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(2.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = value,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = valueColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (unit.isNotBlank()) {
                Spacer(Modifier.width(1.dp))
                Text(
                    text = unit,
                    fontSize = 8.sp,
                    color = valueColor.copy(alpha = 0.5f),
                    modifier = Modifier.padding(bottom = 2.dp),
                )
            }
        }
        if (delta != null) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = delta.text,
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium,
                color = delta.color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun Pill(
    text: String,
    color: Color,
    textColor: Color,
    border: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(color)
            .padding(horizontal = 7.dp, vertical = 2.dp),
    ) {
        Text(
            text = text,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun md(date: String): String {
    val parts = date.split("-")
    return if (parts.size == 3)
        stringResource(R.string.koil_ov_md_date, parts[1].toInt(), parts[2].toInt())
    else date
}

@Composable
private fun RecordDetail(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    unit: String,
    warn: Boolean = false,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = value,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = MiuixTheme.colorScheme.onBackground,
            )
            if (unit.isNotBlank()) {
                Spacer(Modifier.width(1.dp))
                Text(
                    text = unit,
                    fontSize = 9.sp,
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                    modifier = Modifier.padding(bottom = 1.dp),
                )
            }
        }
        Text(
            text = label,
            fontSize = 9.sp,
            color = MiuixTheme.colorScheme.onBackgroundVariant,
        )
    }
}

// ===== 编辑记录弹窗（v1.5.13 复刻补录卡：字段结构/图标/红色错误态/里程区间提示/校验提示条/应付只读自动算/保存置灰变蓝；唯一差异：日期只读） =====
@Composable
private fun EditRecordDialog(
    uiState: OverviewUiState,
    onMileageChange: (String) -> Unit,
    onOilAmountChange: (String) -> Unit,
    onActualAmountChange: (String) -> Unit,
    onGradeChange: (String) -> Unit,
    onProvinceChange: (String) -> Unit,
    onPayableChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    com.llpop.koil.ui.component.KOiLGlassDialog(
        show = true,
        onDismissRequest = onDismiss,
        contentPadding = 16.dp,
        scrollable = true,
        maxHeight = (LocalConfiguration.current.screenHeightDp * 0.85f).dp,
        widthFraction = 0.90f,
    ) {
        Column {
            Text(
                text = stringResource(R.string.koil_ov_edit_title),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MiuixTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(16.dp))
            // 加油省份（与设置页同款下拉列表）
            ProvinceSelectField(province = uiState.editProvince, onProvinceChange = onProvinceChange)
            Spacer(Modifier.height(14.dp))
            // 第一行：日期（只读，不可修改）+ 油号（联动当日参考单价）
            Row(verticalAlignment = Alignment.Top) {
                com.llpop.koil.ui.component.KOiLDateField(
                    modifier = Modifier.weight(1f),
                    date = uiState.editDate,
                    recordDates = uiState.editRecordDates,
                    onDateSelected = null,
                )
                Spacer(Modifier.width(14.dp))
                GradePriceSelect(
                    grade = uiState.editGrade,
                    price = uiState.editReferencePrice,
                    onGradeSelected = onGradeChange,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(14.dp))
            // 打开即显示该日期（只读）的里程区间提示（对齐补录卡：日期行与里程行之间）
            if (uiState.editRangeMin != null || uiState.editRangeMax != null) {
                MileageRangeHint(
                    rangeMin = uiState.editRangeMin,
                    rangeMax = uiState.editRangeMax,
                )
                Spacer(Modifier.height(14.dp))
            }
            // 第二行：里程 + 加油量（红色错误态对齐补录卡）
            Row {
                EditField(
                    label = stringResource(R.string.koil_add_field_mileage),
                    icon = Icons.Filled.Speed,
                    value = uiState.editMileage,
                    onValueChange = onMileageChange,
                    keyboardType = KeyboardType.Number,
                    error = uiState.editMileageConflictType.isNotBlank() || uiState.editSameDayExists,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(14.dp))
                EditField(
                    label = stringResource(R.string.koil_add_field_oil_amount),
                    icon = Icons.Filled.WaterDrop,
                    value = uiState.editOilAmount,
                    onValueChange = onOilAmountChange,
                    keyboardType = KeyboardType.Decimal,
                    error = uiState.editOverTank,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(14.dp))
            // 第三行：应付（只读自动计算）+ 实付（对齐补录卡）
            Row {
                EditField(
                    label = stringResource(R.string.koil_add_field_payable),
                    icon = Icons.Filled.Money,
                    value = uiState.editPayableAmount,
                    onValueChange = onPayableChange,
                    keyboardType = KeyboardType.Decimal,
                    readOnly = true,
                    // v1.5.14：应付未计算（油量/参考价缺失）时显示占位
                    placeholder = stringResource(R.string.koil_ov_payable_pending),
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(14.dp))
                EditField(
                    label = stringResource(R.string.koil_add_field_actual),
                    icon = Icons.Filled.Money,
                    value = uiState.editActualAmount,
                    onValueChange = onActualAmountChange,
                    keyboardType = KeyboardType.Decimal,
                    error = uiState.editDeviationExceeded || uiState.editOverPayable,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(10.dp))
            // 实付综合信息块（对齐 Web PaymentSummaryCard：优惠概览/实际单价/参考价对比）
            PaymentSummaryCard(
                payable = uiState.editPayableAmount.toDoubleOrNull(),
                actual = uiState.editActualAmount.toDoubleOrNull(),
                fuelAmount = uiState.editOilAmount.toDoubleOrNull(),
                referencePrice = uiState.editReferencePrice,
            )
            Spacer(Modifier.height(8.dp))
            // 编辑校验提示条（v1.5.13 复刻补录卡：日期边界/同日重复/里程链/偏差A+B/实付>应付/油箱容量，通过绿色）
            BackfillPrecheckHint(
                beforeFirst = uiState.editBeforeFirst,
                sameDayExists = uiState.editSameDayExists,
                mileageConflictType = uiState.editMileageConflictType,
                mileageConflictDate = uiState.editMileageConflictDate,
                mileageConflictValue = uiState.editMileageConflictValue,
                deviationPct = uiState.editDeviationPct,
                deviationExceeded = uiState.editDeviationExceeded,
                overPayable = uiState.editOverPayable,
                overTank = uiState.editOverTank,
                tankCapacity = uiState.editTankCapacity,
                ready = uiState.editReady,
                readyText = stringResource(R.string.koil_ov_edit_ready),
            )
            // v1.5.16：保存失败提示（如唯一约束冲突等）
            if (uiState.saveError) {
                Spacer(Modifier.height(8.dp))
                SaveErrorHint()
            }
            Spacer(Modifier.height(20.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f).height(44.dp),
                ) { Text(stringResource(R.string.koil_common_cancel), fontSize = 14.sp) }
                Spacer(Modifier.width(12.dp))
                Button(
                    onClick = onSave,
                    modifier = Modifier.weight(1f).height(44.dp),
                    enabled = uiState.editReady && !uiState.isSavingEdit,
                    // v1.5.13：全部校验通过 → 蓝色可保存；否则灰色置灰（对齐补录卡/首页录入卡）
                    colors = ButtonDefaults.buttonColors(
                        color = KOiLColors.Primary,
                        contentColor = Color.White,
                        disabledColor = MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        disabledContentColor = MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.4f),
                    ),
                ) { Text(stringResource(R.string.koil_common_save), fontSize = 14.sp, fontWeight = FontWeight.Medium) }
            }
        }
    }
}

/** v1.5.11：补录校验提示（用户确认规则：早于第一条/同日重复/里程链/偏差A+B/实付>应付/油箱容量，通过绿色；v1.5.14 readyText 支持编辑卡/补录卡差异化文案） */
@Composable
private fun BackfillPrecheckHint(
    beforeFirst: Boolean,
    sameDayExists: Boolean,
    mileageConflictType: String,
    mileageConflictDate: String,
    mileageConflictValue: Double,
    deviationPct: Double?,
    deviationExceeded: Boolean,
    overPayable: Boolean,
    overTank: Boolean,
    tankCapacity: Double,
    ready: Boolean,
    readyText: String,
) {
    val text = when {
        beforeFirst -> stringResource(R.string.koil_ov_bf_before_first)
        sameDayExists -> stringResource(R.string.koil_ov_bf_dup)
        mileageConflictType == "prev" -> stringResource(
            R.string.koil_ov_bf_mileage_prev, mileageConflictDate, mileageConflictValue
        )
        mileageConflictType == "next" -> stringResource(
            R.string.koil_ov_bf_mileage_next, mileageConflictDate, mileageConflictValue
        )
        deviationExceeded && deviationPct != null -> stringResource(
            R.string.koil_precheck_deviation, deviationPct
        )
        overPayable -> stringResource(R.string.koil_precheck_over_payable)
        overTank -> stringResource(R.string.koil_precheck_over_tank, tankCapacity.toInt())
        ready -> readyText
        else -> return
    }
    val blocking = beforeFirst || sameDayExists ||
        mileageConflictType.isNotBlank() || deviationExceeded || overPayable || overTank
    val (bg, fg) = if (blocking) Color(0x1AFF9500) to Color(0xFFFF9500)
    else Color(0x1A34C759) to Color(0xFF34C759)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(text, fontSize = 12.sp, color = fg)
    }
}

/** v1.5.16：补录/编辑保存失败提示（红色），不再静默吞异常 */
@Composable
private fun SaveErrorHint() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0x1AFF3B30))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(
            text = stringResource(R.string.koil_ov_save_failed),
            fontSize = 12.sp,
            color = Color(0xFFFF3B30),
        )
    }
}

/** v1.5.11：选日期后该区间可输入里程范围提示（中性信息，不阻断） */
@Composable
private fun MileageRangeHint(rangeMin: Double?, rangeMax: Double?) {
    val text = when {
        rangeMin != null && rangeMax != null ->
            stringResource(R.string.koil_ov_bf_mileage_range, rangeMin.toInt(), rangeMax.toInt())
        rangeMin != null ->
            stringResource(R.string.koil_ov_bf_mileage_range_min, rangeMin.toInt())
        else ->
            stringResource(R.string.koil_ov_bf_mileage_range_max, rangeMax?.toInt() ?: 0)
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0x14FFFFFF))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(text, fontSize = 12.sp, color = Color(0xFF9AA0A6))
    }
}

// ===== 全国 31 省（与 AddScreen 一致） =====
private val PROVINCES = listOf(
    "广东", "北京", "上海", "江苏", "浙江", "山东", "四川", "湖北", "湖南",
    "河南", "河北", "福建", "安徽", "江西", "辽宁", "陕西", "云南", "广西",
    "重庆", "天津", "山西", "贵州", "黑龙江", "吉林", "甘肃", "内蒙古",
    "新疆", "海南", "宁夏", "青海", "西藏",
)

/** 加油省份选择字段（锚定下拉列表：紧贴字段下方弹出、空间不足自动上翻、底色与首页下拉一致，联动参考价/应付/偏差预检） */
@Composable
private fun ProvinceSelectField(province: String, onProvinceChange: (String) -> Unit) {
    val showPicker = remember { mutableStateOf(false) }
    val anchorBounds = remember { mutableStateOf<androidx.compose.ui.unit.IntRect?>(null) }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.LocalGasStation,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.onBackgroundVariant,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = stringResource(R.string.koil_ov_field_province),
                fontSize = 12.sp,
                color = MiuixTheme.colorScheme.onBackgroundVariant,
            )
        }
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MiuixTheme.colorScheme.secondaryContainer)
                .onGloballyPositioned {
                    val b = it.boundsInWindow()
                    anchorBounds.value = androidx.compose.ui.unit.IntRect(b.left.toInt(), b.top.toInt(), b.right.toInt(), b.bottom.toInt())
                }
                .clickable { showPicker.value = true }
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (province.isBlank()) stringResource(R.string.koil_add_pick_province) else province,
                    fontSize = 14.sp,
                    fontWeight = if (province.isBlank()) FontWeight.Normal else FontWeight.Bold,
                    color = if (province.isBlank()) MiuixTheme.colorScheme.onBackgroundVariant else MiuixTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                )
                DropdownArrowEndAction(actionColor = MiuixTheme.colorScheme.onBackgroundVariant)
            }
        }
    }
    if (showPicker.value) {
        // 弹窗根级锚定下拉：注册到 KOiLGlassDialog 的 overlay（窗口坐标定位，与 miuix 同款视觉）
        com.llpop.koil.ui.component.rememberAnchoredPopupRegistration(
            show = true,
            id = "ov-province-picker",
            anchorBounds = anchorBounds.value,
            maxHeight = 320.dp,
            onDismiss = { showPicker.value = false },
        ) {
            com.llpop.koil.ui.component.AnchoredDropdownColumn {
                PROVINCES.forEachIndexed { index, p ->
                    com.llpop.koil.ui.component.AnchoredDropdownItemRow(
                        text = p,
                        selected = p == province,
                        isFirst = index == 0,
                        isLast = index == PROVINCES.lastIndex,
                        onClick = {
                            onProvinceChange(p)
                            showPicker.value = false
                        },
                    )
                }
            }
        }
    }
}

/** 实付综合信息块（对齐 Web PaymentSummaryCard：优惠概览 / 实际单价 · 参考价对比） */
@Composable
private fun PaymentSummaryCard(
    payable: Double?,
    actual: Double?,
    fuelAmount: Double?,
    referencePrice: Double?,
) {
    val hasPayable = payable != null && payable > 0
    val hasActual = actual != null && actual > 0
    val hasFuel = fuelAmount != null && fuelAmount > 0
    val hasReference = referencePrice != null && referencePrice > 0

    val green = Color(0xFF34C759)
    val amber = Color(0xFFFF9500)
    val gray = MiuixTheme.colorScheme.onBackgroundVariant

    // 第一行：优惠概览（应付/实付都有时显示）
    var savingsText: String? = null
    var savingsColor: Color = gray
    if (hasPayable && hasActual) {
        val diff = payable - actual
        val pct = if (payable > 0) diff / payable * 100 else 0.0
        when {
            diff > 0.005 -> { savingsText = stringResource(R.string.koil_ps_savings, abs(diff), abs(pct)); savingsColor = green }
            diff < -0.005 -> { savingsText = stringResource(R.string.koil_ps_overpay, abs(diff)); savingsColor = amber }
            else -> { savingsText = stringResource(R.string.koil_ps_no_savings); savingsColor = gray }
        }
    }

    // 第二行：实际单价对比（加油量>0 且 实付>0）
    val actualUnitPrice = if (hasFuel && hasActual) actual / fuelAmount else null
    var unitDiffText: String? = null
    var unitDiffColor: Color = gray
    if (actualUnitPrice != null && actualUnitPrice > 0 && hasReference) {
        val diff = referencePrice - actualUnitPrice
        val pct = if (referencePrice > 0) diff / referencePrice * 100 else 0.0
        when {
            diff > 0.005 -> { unitDiffText = stringResource(R.string.koil_ps_save_per_l, abs(diff), abs(pct)); unitDiffColor = green }
            diff < -0.005 -> { unitDiffText = stringResource(R.string.koil_ps_dear_per_l, abs(diff), abs(pct)); unitDiffColor = amber }
            else -> { unitDiffText = stringResource(R.string.koil_ps_match_ref); unitDiffColor = gray }
        }
    }

    if (savingsText == null && unitDiffText == null) return

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Column {
            if (savingsText != null && hasPayable && hasActual) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.koil_ps_payable_actual, payable, actual),
                        fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.onBackgroundVariant,
                        modifier = Modifier.weight(1f),
                    )
                    Text(savingsText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = savingsColor)
                }
                Spacer(Modifier.height(6.dp))
            }
            if (unitDiffText != null && actualUnitPrice != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = buildString {
                            append(stringResource(R.string.koil_ps_actual_unit, actualUnitPrice))
                            if (hasReference) append("  ·  ").append(stringResource(R.string.koil_ps_ref_price, referencePrice))
                        },
                        fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.onBackgroundVariant,
                        modifier = Modifier.weight(1f),
                    )
                    Text(unitDiffText, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = unitDiffColor)
                }
            }
        }
    }
}

/** 补录弹窗字段（v1.5.12 对齐首页录入卡 FormField：图标 + 标签行，红色错误态边框/图标/标签） */
@Composable
private fun EditField(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text,
    readOnly: Boolean = false,
    error: Boolean = false,
    placeholder: String? = null,
    modifier: Modifier = Modifier,
) {
    val errorColor = Color(0xFFFF3B30)
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
        // v1.5.14：miuix TextField 无 placeholder 参数，用 Box 叠加层实现"待计算"占位（value 为空时显示）
        Box(modifier = Modifier.fillMaxWidth()) {
            TextField(
                value = value,
                onValueChange = { onValueChange(it.sanitizeDecimalInput()) },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                singleLine = true,
                readOnly = readOnly,
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

/** 油号选择 + 当日参考单价（对齐首页录入 GradeField：显示 `92# · ¥X.XX/L`，点击弹锚定下拉） */
@Composable
private fun GradePriceSelect(
    grade: String,
    price: Double?,
    onGradeSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val gradeLabels = listOf("92", "95", "98", "0")
    val showMenu = remember { mutableStateOf(false) }
    val anchorBounds = remember { mutableStateOf<androidx.compose.ui.unit.IntRect?>(null) }
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.LocalGasStation,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.onBackgroundVariant,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(text = stringResource(R.string.koil_ov_grade), fontSize = 12.sp, color = MiuixTheme.colorScheme.onBackgroundVariant)
        }
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MiuixTheme.colorScheme.secondaryContainer)
                .onGloballyPositioned {
                    val b = it.boundsInWindow()
                    anchorBounds.value = androidx.compose.ui.unit.IntRect(b.left.toInt(), b.top.toInt(), b.right.toInt(), b.bottom.toInt())
                }
                .clickable { showMenu.value = true }
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "$grade# · ${price?.let { "¥${NumberFormat.measure(it)}/L" } ?: "¥--/L"}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MiuixTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                )
                DropdownArrowEndAction(actionColor = MiuixTheme.colorScheme.onBackgroundVariant)
            }
        }
    }
    if (showMenu.value) {
        // 弹窗根级锚定下拉：注册到 KOiLGlassDialog 的 overlay（窗口坐标定位，与 miuix 同款视觉）
        com.llpop.koil.ui.component.rememberAnchoredPopupRegistration(
            show = true,
            id = "ov-grade-picker",
            anchorBounds = anchorBounds.value,
            maxHeight = 240.dp,
            onDismiss = { showMenu.value = false },
        ) {
            com.llpop.koil.ui.component.AnchoredDropdownColumn {
                gradeLabels.forEachIndexed { index, g ->
                    val label = if (g == "0") stringResource(R.string.koil_add_grade_diesel)
                    else stringResource(R.string.koil_add_grade_gasoline, g)
                    com.llpop.koil.ui.component.AnchoredDropdownItemRow(
                        text = label,
                        selected = g == grade,
                        isFirst = index == 0,
                        isLast = index == gradeLabels.lastIndex,
                        onClick = {
                            onGradeSelected(g)
                            showMenu.value = false
                        },
                    )
                }
            }
        }
    }
}
