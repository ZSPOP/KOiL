package com.llpop.koil.ui.screen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.LazyListState
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
import androidx.compose.material.icons.filled.KeyboardArrowUp
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
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
import com.llpop.koil.data.oil.Provinces
import com.llpop.koil.ui.component.KOiLBlurBackground
import com.llpop.koil.ui.component.KOiLScreenScaffold
import com.llpop.koil.ui.component.sanitizeDecimalInput
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.llpop.koil.data.oil.OilPriceNetwork
import com.llpop.koil.domain.logic.ConsumptionStatus
import com.llpop.koil.domain.logic.NumberFormat
import com.llpop.koil.ui.screen.overview.OverviewUiState
import com.llpop.koil.ui.screen.overview.DialogUiState
import com.llpop.koil.ui.screen.overview.OverviewViewModel
import com.llpop.koil.domain.logic.RecordWithDerived
import com.llpop.koil.ui.screen.overview.TrendPoint
import com.llpop.koil.ui.theme.KOiLColors
import com.llpop.koil.ui.component.provinceLabel
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
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.textureBlur
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
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    // v1.6.8 P2-4：弹窗独立状态流——弹窗输入只重组弹窗，不牵动列表/四宫格/趋势图
    val dlg by viewModel.dialogState.collectAsStateWithLifecycle()
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
    // v1.6.5：每条记录的「近 N 次油耗均值」（含自身，往旧最多取 5 条有效油耗），供展开明细对比条
    val recentAvgMap = remember(uiState.records) {
        val map = HashMap<Long, Pair<Double, Int>>()
        val list = uiState.records
        for (i in list.indices) {
            val vals = ArrayList<Double>(5)
            var j = i
            while (j < list.size && vals.size < 5) {
                list[j].oilConsumption?.let { vals.add(it) }
                j++
            }
            if (vals.size >= 2) map[list[i].record.id] = Pair(vals.average(), vals.size)
        }
        map
    }
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
        overlay = { backdrop, _, topPadding ->
            // v1.5.50：吸顶月份标题覆盖层——位于 backdrop 录制树外，可安全 textureBlur(backdrop)
            // 达到与顶栏同款真毛玻璃（采样不到自身，无递归）
            // monthKeys 用 remember 稳定引用（避免重组重建列表导致 LaunchedEffect 重启、吸顶闪烁）
            val monthKeys = remember(monthGroups) { monthGroups.map { it.first } }
            val pinnedMonth = rememberTopPinnedMonth(listState, topPadding, monthKeys)
            if (pinnedMonth != null) {
                val group = monthGroups.firstOrNull { it.first == pinnedMonth }
                StickyMonthOverlay(
                    backdrop = backdrop,
                    pinnedMonth = pinnedMonth,
                    summary = group?.let { monthSummaryText(it.second) },
                    topPadding = topPadding,
                )
            }
        },
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

            // 范围筛选器已并入摘要卡顶部（v1.6.5）；六指标摘要（带图标 + 环比）
            item {
                SummaryGrid(
                    uiState = uiState,
                    rangeLabels = rangeLabels,
                    onRangeChange = viewModel::setRange,
                )
            }
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
                        text = pluralStringResource(R.plurals.koil_ov_total_count_in_list, uiState.records.size, uiState.records.size),
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

            // v1.5.20（#8）：LazyColumn 惰性渲染——月份标题分组展示，卡片仅组合可见项；
            // 无记录时显示空态引导。已移除手动按月分页（visibleMonths）与"滚动加载更多"提示
            // v1.5.50：月份标题从 stickyHeader 改为普通 item——吸顶由覆盖层（StickyMonthOverlay，
            // 位于 backdrop 录制树外）承担：贴顶期间列表内标题行自身透明占位（高度保持，防叠影），
            // 标题文字由覆盖层以顶栏同款真毛玻璃（textureBlur + surface 0.8）呈现
            if (uiState.records.isEmpty()) {
                item { EmptyHistoryBox(onAddClick = onNavigateToAdd) }
            } else {
                monthGroups.forEach { (monthKey, monthRecords) ->
                    item(key = "header-$monthKey") {
                        val density = LocalDensity.current
                        val topBarPx = with(density) { topPadding.toPx() }
                        val pinned by remember(topPadding) {
                            derivedStateOf {
                                val off = listState.layoutInfo.visibleItemsInfo
                                    .firstOrNull { it.key == "header-$monthKey" }?.offset ?: Int.MAX_VALUE
                                off <= topBarPx + 2
                            }
                        }
                        // 贴顶（进入顶栏区域）时自身透明占位；标题由覆盖层显示
                        Box(Modifier.alpha(if (pinned) 0f else 1f)) {
                            MonthHeaderRow(
                                monthKey = monthKey,
                                count = monthRecords.size,
                                summary = monthSummaryText(monthRecords),
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
                            recentAvg = recentAvgMap[record.record.id]?.first,
                            recentCount = recentAvgMap[record.record.id]?.second ?: 0,
                            modifier = Modifier.padding(bottom = 10.dp),
                        )
                    }
                }
            }
            // v1.6.5：底部留白加大——回顶按钮占 bottomPadding+16 起的高度，12dp 会被它盖住末卡内容
            item { Spacer(Modifier.height(bottomPadding + 80.dp)) }
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

        // v1.6.3（UX 方案 A）：返回顶部按钮——图标化 + 淡入缩放动效 + 智能回顶。
        // 智能回顶：可见项序号 > 60（几千条记录的深处）时先 scrollToItem 瞬移到近顶，
        // 再短动画收尾到 0；近处保持平滑 animateScrollToItem。不再从深处慢速长飞。
        AnimatedVisibility(
            visible = showBackToTop,
            enter = fadeIn() + scaleIn(initialScale = 0.6f),
            exit = fadeOut() + scaleOut(targetScale = 0.6f),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = bottomPadding + 16.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(KOiLColors.Primary)
                    .clickable {
                        scope.launch {
                            if (listState.firstVisibleItemIndex > 60) {
                                listState.scrollToItem(60)
                            }
                            listState.animateScrollToItem(0)
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowUp,
                    contentDescription = stringResource(R.string.koil_back_to_top),
                    tint = Color.White,
                    modifier = Modifier.size(26.dp),
                )
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
    if (dlg.showEditDialog) {
        EditRecordDialog(
            uiState = dlg,
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
    if (dlg.showBackfillDialog) {
        // v1.6.4：按窗口可用高度算（分屏/键盘弹出时 Configuration 仍是整屏）
        val maxH = with(LocalDensity.current) {
            (LocalWindowInfo.current.containerSize.height * 0.85f).toDp()
        }
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
                ProvinceSelectField(province = dlg.backfillProvince, onProvinceChange = viewModel::setBackfillProvince)
                Spacer(Modifier.height(14.dp))
                // 第一行：日期（日历可改）+ 油号（联动当日参考单价）
                Row(verticalAlignment = Alignment.Top) {
                    com.llpop.koil.ui.component.KOiLDateField(modifier = Modifier.weight(1f), date = dlg.backfillDate, recordDates = dlg.backfillRecordDates, onDateSelected = viewModel::setBackfillDate)
                    Spacer(Modifier.width(14.dp))
                    GradePriceSelect(
                        grade = dlg.backfillGrade,
                        price = dlg.backfillReferencePrice,
                        onGradeSelected = viewModel::setBackfillGrade,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(14.dp))
                // v1.5.12：选日期后该区间可输入里程范围提示（日期行与里程行之间）
                if (dlg.backfillRangeMin != null || dlg.backfillRangeMax != null) {
                    MileageRangeHint(
                        rangeMin = dlg.backfillRangeMin,
                        rangeMax = dlg.backfillRangeMax,
                    )
                    Spacer(Modifier.height(14.dp))
                }
                // 第二行：里程 + 加油量（v1.5.12 图标/红色错误态对齐首页录入卡）
                Row {
                    EditField(
                        label = stringResource(R.string.koil_add_field_mileage),
                        icon = Icons.Filled.Speed,
                        value = dlg.backfillMileage,
                        onValueChange = viewModel::setBackfillMileage,
                        keyboardType = KeyboardType.Number,
                        error = dlg.backfillMileageConflictType.isNotBlank() || dlg.backfillSameDayExists,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(14.dp))
                    EditField(
                        label = stringResource(R.string.koil_add_field_oil_amount),
                        icon = Icons.Filled.WaterDrop,
                        value = dlg.backfillOilAmount,
                        onValueChange = viewModel::setBackfillOilAmount,
                        keyboardType = KeyboardType.Decimal,
                        error = dlg.backfillOverTank,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(14.dp))
                // 第三行：应付（只读自动计算）+ 实付（v1.5.12 对齐首页录入卡）
                Row {
                    EditField(
                        label = stringResource(R.string.koil_add_field_payable),
                        icon = Icons.Filled.Money,
                        value = dlg.backfillPayableAmount,
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
                        value = dlg.backfillActualAmount,
                        onValueChange = viewModel::setBackfillActualAmount,
                        keyboardType = KeyboardType.Decimal,
                        error = dlg.backfillDeviationExceeded || dlg.backfillOverPayable,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(10.dp))
                // 实付综合信息块（对齐 Web PaymentSummaryCard：优惠概览/实际单价/参考价对比）
                PaymentSummaryCard(
                    payable = dlg.backfillPayableAmount.toDoubleOrNull(),
                    actual = dlg.backfillActualAmount.toDoubleOrNull(),
                    fuelAmount = dlg.backfillOilAmount.toDoubleOrNull(),
                    referencePrice = dlg.backfillReferencePrice,
                )
                Spacer(Modifier.height(8.dp))
                // v1.5.11：补录校验提示（用户确认规则：日期边界/重复/里程链/偏差A+B/实付>应付/油箱容量）
                BackfillPrecheckHint(
                    beforeFirst = dlg.backfillBeforeFirst,
                    sameDayExists = dlg.backfillSameDayExists,
                    mileageConflictType = dlg.backfillMileageConflictType,
                    mileageConflictDate = dlg.backfillMileageConflictDate,
                    mileageConflictValue = dlg.backfillMileageConflictValue,
                    deviationPct = dlg.backfillDeviationPct,
                    deviationExceeded = dlg.backfillDeviationExceeded,
                    overPayable = dlg.backfillOverPayable,
                    overTank = dlg.backfillOverTank,
                    tankCapacity = dlg.backfillTankCapacity,
                    ready = dlg.backfillReady,
                    readyText = stringResource(R.string.koil_ov_bf_ready),
                )
                // v1.5.16：保存失败提示（如唯一约束冲突等）
                if (dlg.saveError) {
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
                        enabled = dlg.backfillReady && !dlg.isSavingBackfill,
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

/** ④ 月度小结：「N条 · 共 ¥X · YL」（金额与油量为当月合计） */
@Composable
private fun monthSummaryText(records: List<RecordWithDerived>): String {
    var totalAmount = 0.0
    var totalOil = 0.0
    for (rr in records) {
        totalAmount += rr.record.actualAmount
        totalOil += rr.record.oilAmount
    }
    return stringResource(
        R.string.koil_ov_month_summary,
        records.size,
        NumberFormat.money(totalAmount),
        NumberFormat.fixed2(totalOil),
    )
}

/** v1.5.50：月份标题行（列表 item 与吸顶覆盖层共用，保证两处布局/字号/间距完全一致，切换无跳变） */
@Composable
private fun MonthHeaderRow(monthKey: String, count: Int, summary: String?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = monthTitle(monthKey),
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = MiuixTheme.colorScheme.onBackground,
            modifier = Modifier.padding(start = 4.dp),
        )
        Spacer(Modifier.weight(1f))
        Text(
            // ④ 有月度小结时替代纯计数
            text = summary ?: stringResource(R.string.koil_ov_month_count, count),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = MiuixTheme.colorScheme.onBackgroundVariant,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.padding(end = 4.dp),
        )
    }
}

/**
 * v1.5.50：计算"当前吸顶月份"（等价 Compose stickyHeader 钉住语义，v1.5.50fix 起回滚连续无空档）：
 * - 取所有可见月份标题中 offset ≤ 顶栏高度的**最后一个**为吸顶月份（新标题顶替旧标题）；
 * - 无标题在顶栏区时：
 *   - 无可见标题：列表已回到顶部（未进入历史区）或已滚过历史区末尾 → 取消吸顶；
 *     否则（吸顶标题滚出视口上方、下一标题未到）→ 保持吸顶；
 *   - 可见标题都在顶栏下方：吸顶标题自然位置回到顶栏（回滚松开）→ **立即接替为上一个月份标题**
 *     （此时它必已处于吸顶位，即使视口外），实现回滚时顶栏标题连续切换、无空档；
 *     吸顶标题已滚出视口上方（上滚中）→ 保持吸顶。
 * 覆盖层据此渲染顶栏同款毛玻璃标题；列表内对应标题行由各自 pinned 判定透明占位。
 *
 * v1.6.3（P2-2）：重做信号源，判定树逐分支保留 v1.5.50fix 语义。
 * 旧实现 snapshotFlow { visibleItemsInfo } 每帧发射一次完整可见项快照（60Hz 滚动下每秒
 * 数十个 List + LazyListItemInfo 快照分配），collect 内再 mapNotNull 出中间列表。
 * 现改为：
 * - 流信号改为 `firstVisibleItemIndex to firstVisibleItemScrollOffset`——snapshotFlow 按结构
 *   相等去重，静止时完全不发射；滚动时发射体只有两个装箱 int；
 * - visibleItemsInfo 改在收集体内直读（它本身仍是每帧新对象，但只被消费、不再进流），
 *   单趟循环同时得出 lastInTop / anyHeader / pinnedVisible，零中间列表分配；
 * - 判定分支仍是有状态的（"回滚接替"依赖上一个 pinned 值，纯 derivedStateOf 表达不了，
 *   强行无状态会改变回滚语义），所以保留 mutableStateOf + 写前比对：值不变不写入，
 *   订阅覆盖层不重组。
 */
@Composable
private fun rememberTopPinnedMonth(
    listState: LazyListState,
    topPadding: Dp,
    monthKeys: List<String>,
): String? {
    val density = LocalDensity.current
    val topBarPx = with(density) { topPadding.toPx() }
    var pinned by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(listState, topBarPx, monthKeys) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collect {
                val visibleItems = listState.layoutInfo.visibleItemsInfo
                var lastInTopKey: String? = null
                var pinnedVisible = false
                var anyHeader = false
                for (info in visibleItems) {
                    val key = info.key
                    if (key is String && key.startsWith("header-")) {
                        anyHeader = true
                        val month = key.removePrefix("header-")
                        if (month == pinned) pinnedVisible = true
                        if (info.offset.toFloat() <= topBarPx + 2f) lastInTopKey = month
                    }
                }
                when {
                    // ① 顶栏区内有标题：最后一个顶替（正向滚动换月）
                    lastInTopKey != null -> if (pinned != lastInTopKey) pinned = lastInTopKey
                    // ② 视口无任何标题：列表顶部（未进入历史区）或历史区已滚完 → 取消；
                    //    否则保持吸顶（吸顶标题滚出视口上方、下一标题未到）
                    !anyHeader -> {
                        val atTop = listState.firstVisibleItemIndex == 0 &&
                            listState.firstVisibleItemScrollOffset < topBarPx.toInt()
                        if ((atTop || !listState.canScrollForward) && pinned != null) pinned = null
                    }
                    // ③ 标题都在顶栏下方且 pinned 可见：回滚松开 → 上一个月份标题立即接替
                    pinnedVisible -> {
                        val idx = pinned?.let { monthKeys.indexOf(it) } ?: -1
                        val next = if (idx > 0) monthKeys[idx - 1] else null
                        if (pinned != next) pinned = next
                    }
                    // ④ 其余（pinned 已滚出视口上方，上滚中）→ 保持吸顶
                }
            }
    }
    return pinned
}

/**
 * v1.5.50：吸顶月份标题覆盖层——位于 backdrop 录制树外（KOiLScreenScaffold overlay 插槽），
 * 可安全 textureBlur(backdrop) 采样整页内容（不含自身）→ 与顶栏同款真毛玻璃：
 * blurRadius 25f + surface 0.8 混合（对齐 BlurredBar）+ 底部 0.5dp 细线（同 SettingDivider 色）。
 * 模糊不可用（SDK<33 / 无 RuntimeShader）时退化为纯色 surface 0.8。
 */
@Composable
private fun StickyMonthOverlay(
    backdrop: LayerBackdrop?,
    pinnedMonth: String,
    summary: String?,
    topPadding: Dp,
) {
    val bd = backdrop
    val canBlur = bd != null && isRuntimeShaderSupported()
    // 分隔线颜色在 Composable 上下文预取（drawBehind 内不可读主题色）
    val stickyLineColor = MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.12f)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            // 水平 12dp 对齐 LazyColumn contentPadding，标题文字与列表内标题行位置一致（切换无错位）
            .padding(start = 12.dp, end = 12.dp, top = topPadding)
            .then(
                if (canBlur) {
                    Modifier.textureBlur(
                        backdrop = bd,
                        shape = RectangleShape,
                        blurRadius = 25f,
                        colors = BlurColors(
                            blendColors = listOf(
                                BlendColorEntry(color = MiuixTheme.colorScheme.surface.copy(alpha = 0.8f)),
                            ),
                        ),
                    )
                } else {
                    Modifier.background(MiuixTheme.colorScheme.surface.copy(alpha = 0.8f))
                },
            )
            .drawBehind {
                val y = size.height - 0.5.dp.toPx()
                drawLine(
                    color = stickyLineColor,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 0.5.dp.toPx(),
                )
            },
    ) {
        MonthHeaderRow(monthKey = pinnedMonth, count = 0, summary = summary)
    }
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
private fun SummaryGrid(
    uiState: OverviewUiState,
    rangeLabels: List<Pair<String, String>>,
    onRangeChange: (String) -> Unit,
) {
    val stats = uiState.stats
    val prev = uiState.prevStats
    fun pct(cur: Double, prevVal: Double?): Double? =
        if (prevVal != null && prevVal > 0) (cur - prevVal) / prevVal * 100 else null
    // v1.6.5：环比口径文案随范围变化——原来固定「较上期」，用户看不出到底在跟哪一段比
    val compareLabel = when (uiState.range) {
        "3m" -> stringResource(R.string.koil_ov_cmp_3m)
        "6m" -> stringResource(R.string.koil_ov_cmp_6m)
        "year" -> stringResource(R.string.koil_ov_cmp_year)
        else -> stringResource(R.string.koil_ov_vs_prev)
    }

    KOiLBlurBackground(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 20f,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // v1.6.5：范围筛选器收进摘要卡顶部（原来是一层独立悬浮灰条，卡片区少一块浮块）
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
                            .clickable { onRangeChange(key) }
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
            Spacer(Modifier.height(14.dp))
            // —— 六指标 3×2（v1.6.5：字号统一 21sp；里程/油量涨幅不判好坏用中性灰）——
            Row(modifier = Modifier.fillMaxWidth()) {
                StatCell(
                    Modifier.weight(1f),
                    icon = Icons.Filled.WaterDrop,
                    label = stringResource(R.string.koil_stat_avg_consumption),
                    value = NumberFormat.measure(stats.avgOilConsumption ?: 0.0),
                    unit = "L/100km",
                    comparePct = pct(stats.avgOilConsumption ?: 0.0, prev?.avgOilConsumption),
                )
                Spacer(Modifier.width(12.dp))
                StatCell(
                    Modifier.weight(1f),
                    icon = Icons.Filled.Money,
                    label = stringResource(R.string.koil_stat_cost_per_km),
                    value = NumberFormat.money(stats.avgCostPerKm ?: 0.0),
                    unit = stringResource(R.string.koil_stat_cost_unit),
                    comparePct = pct(stats.avgCostPerKm ?: 0.0, prev?.avgCostPerKm),
                )
                Spacer(Modifier.width(12.dp))
                StatCell(
                    Modifier.weight(1f),
                    icon = Icons.Filled.Speed,
                    label = stringResource(R.string.koil_stat_total_mileage),
                    value = NumberFormat.grouped(stats.totalDistance),
                    unit = "km",
                    comparePct = pct(stats.totalDistance, prev?.totalDistance),
                    // 里程增长不代表变差：中性灰，只显示变化幅度
                    neutral = true,
                )
            }
            Spacer(Modifier.height(14.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                StatCell(
                    Modifier.weight(1f),
                    icon = Icons.Filled.LocalGasStation,
                    label = stringResource(R.string.koil_stat_total_oil),
                    value = NumberFormat.grouped(stats.totalOilAmount),
                    unit = "L",
                    comparePct = pct(stats.totalOilAmount, prev?.totalOilAmount),
                    neutral = true,
                )
                Spacer(Modifier.width(12.dp))
                StatCell(
                    Modifier.weight(1f),
                    icon = Icons.Filled.Payments,
                    label = stringResource(R.string.koil_stat_total_cost),
                    value = "¥" + NumberFormat.grouped(stats.totalActual),
                    unit = "",
                    comparePct = pct(stats.totalActual, prev?.totalActual),
                )
                Spacer(Modifier.width(12.dp))
                // v1.6.5：总应付信息量低（与总实付几乎同值），换成平均油价（含环比，可看油价走势）
                StatCell(
                    Modifier.weight(1f),
                    icon = Icons.Filled.Receipt,
                    label = stringResource(R.string.koil_stat_avg_price),
                    value = NumberFormat.measure(stats.avgUnitPriceActual ?: 0.0),
                    unit = stringResource(R.string.koil_stat_price_unit_long),
                    comparePct = pct(stats.avgUnitPriceActual ?: 0.0, prev?.avgUnitPriceActual),
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
                    text = pluralStringResource(R.plurals.koil_ov_total_count, stats.totalCount, stats.totalCount),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = stringResource(
                        R.string.koil_ov_avg_distance_line,
                        (stats.avgDistancePerReoil ?: 0.0).toInt(),
                    ),
                    fontSize = 11.sp,
                    color = MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.7f),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = compareLabel,
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
    // v1.6.5：里程/油量这类「涨了不代表变差」的指标不判好坏，只显示幅度（中性灰）
    neutral: Boolean = false,
) {
    // v1.6.5：字号统一（原来前两项 24sp、其余 19sp，视觉上误导重要性）；深色主题换亮色变体
    val darkBg = MiuixTheme.colorScheme.background.luminance() < 0.5f
    val upColor = if (darkBg) Color(0xFFFF8A65) else Color(0xFFE65100)
    val downColor = if (darkBg) Color(0xFF66BB6A) else Color(0xFF2E7D32)
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
                // v1.6.5：六个指标统一 21sp（原 24/19sp 混排）
                fontSize = 21.sp,
                fontWeight = FontWeight.Bold,
                color = MiuixTheme.colorScheme.onBackground,
                maxLines = 1,
                softWrap = false,
            )
            if (unit.isNotBlank()) {
                Spacer(Modifier.width(2.dp))
                Text(
                    text = unit,
                    fontSize = 10.sp,
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                    modifier = Modifier.padding(bottom = 3.dp),
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
        comparePct?.let { pct ->
            Spacer(Modifier.height(2.dp))
            if (abs(pct) >= 0.5) {
                val up = pct > 0
                Text(
                    text = "${if (up) "↑" else "↓"}${NumberFormat.pct(abs(pct))}%",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = when {
                        neutral -> MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.75f)
                        up -> upColor
                        else -> downColor
                    },
                )
            } else {
                // v1.6.5：「较上期」→「持平」，变化不足 0.5% 时说清是没变化，而不是让人猜在跟谁比
                Text(
                    text = stringResource(R.string.koil_ov_flat),
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
    // v1.6.2：Paint 提到绘制回调之外缓存。原实现在 Canvas 的 onDraw 里每画一个标签就 new 一个
    // android.graphics.Paint（单帧最多 4 组轴标签 + N 个日期标签，共 ~35 个对象）。
    val density = LocalDensity.current
    val axisTextSizePx = with(density) { 9.dp.toPx() }
    val dateTextSizePx = with(density) { 8.dp.toPx() }
    val axisLabelPaint = remember(labelColor, axisTextSizePx) {
        labelPaint(labelColor, alpha = 153, textSizePx = axisTextSizePx)
    }
    val dateLabelPaint = remember(labelColor, dateTextSizePx) {
        labelPaint(labelColor, alpha = 102, textSizePx = dateTextSizePx)
    }
    // v1.6.3：几何（Path、点坐标、标签文本与其基线坐标、画笔宽度）整体抽进 remember。
    // 原先这些都在 onDraw 里逐帧重建：2 个 Path、2 个 MutableList<Offset>、10 次 NumberFormat.measure
    //（每次 BigDecimal + 字符串）、以及每个标签一次字符串拼接与测量。图表本身没有动画，
    // 只有"数据/尺寸/主题色变化"才需要重算，逐帧重算纯粹是白烧 CPU 并制造垃圾。
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)
    ) {
        // 尺寸按 density 只取标量做 key：Density 是接口，不同实例未必 equals，用它当 key 会让几何每次都重算
        val wPx = if (maxWidth.value.isFinite()) with(density) { maxWidth.toPx() } else 0f
        val hPx = if (maxHeight.value.isFinite()) with(density) { maxHeight.toPx() } else 0f
        val geometry = remember(trend, wPx, hPx, density.density, density.fontScale) {
            if (wPx <= 0f || hPx <= 0f) null else buildChartGeometry(trend, wPx, hPx, density)
        }
        Canvas(modifier = Modifier.fillMaxSize()) {
            val geo = geometry ?: return@Canvas   // 尺寸异常（无界约束）时不画，也不崩
            val native = drawContext.canvas.nativeCanvas
            geo.gridLines.forEach { drawLine(gridColor, it.first, it.second, geo.gridStroke) }
            geo.axisLabels.forEach { native.drawText(it.text, it.x, it.y, axisLabelPaint) }
            geo.consumptionPath?.let { path ->
                drawPath(path = path, color = KOiLColors.Primary, style = geo.lineStroke)
                geo.consumptionPoints.forEach {
                    drawCircle(color = KOiLColors.Primary, radius = geo.dotRadius, center = it)
                }
            }
            geo.costPath?.let { path ->
                drawPath(path = path, color = CHART_COST_COLOR, style = geo.lineStroke)
                geo.costPoints.forEach {
                    drawCircle(color = CHART_COST_COLOR, radius = geo.dotRadius, center = it)
                }
            }
            geo.dateLabels.forEach { native.drawText(it.text, it.x, it.y, dateLabelPaint) }
        }
    }
}

private val CHART_COST_COLOR = Color(0xFFF97316)

/** 轴/日期标签：坐标为绘制时直接用（已包含 padding 与基线偏移） */
private class ChartLabel(val text: String, val x: Float, val y: Float)

/** 图表几何快照：全部在 remember 里算一次，onDraw 只负责画 */
private class ChartGeometry(
    val gridLines: List<Pair<Offset, Offset>>,
    val gridStroke: Float,
    val lineStroke: Stroke,
    val dotRadius: Float,
    val axisLabels: List<ChartLabel>,
    val dateLabels: List<ChartLabel>,
    val consumptionPath: Path?,
    val consumptionPoints: List<Offset>,
    val costPath: Path?,
    val costPoints: List<Offset>,
)

private fun buildChartGeometry(
    trend: List<TrendPoint>,
    widthPx: Float,
    heightPx: Float,
    density: androidx.compose.ui.unit.Density,
): ChartGeometry {
    with(density) {
        val padL = 30.dp.toPx()
        val padR = 34.dp.toPx()
        val padT = 8.dp.toPx()
        val padB = 22.dp.toPx()
        val w = widthPx - padL - padR
        val h = heightPx - padT - padB

        val consumptions = trend.mapNotNull { it.oilConsumption }
        val costs = trend.mapNotNull { it.costPerKm }

        // 左轴（油耗 2.0~8.0 对齐 Web，自动兜底）
        val leftMin = consumptions.minOrNull()?.let { it * 0.75 } ?: 2.0
        val leftMax = consumptions.maxOrNull()?.let { it * 1.25 } ?: 8.0
        val leftRange = (leftMax - leftMin).coerceAtLeast(0.1)

        // 右轴（成本 0.10~0.60 对齐 Web，自动兜底）
        val rightMin = costs.minOrNull()?.let { (it * 0.75 * 100).roundToInt() / 100.0 } ?: 0.10
        val rightMax = costs.maxOrNull()?.let { (it * 1.25 * 100).roundToInt() / 100.0 } ?: 0.60
        val rightRange = (rightMax - rightMin).coerceAtLeast(0.05)

        // 横向网格线 + 左/右轴刻度标签
        val gridLines = ArrayList<Pair<Offset, Offset>>(5)
        val axisLabels = ArrayList<ChartLabel>(10)
        val gridLinesCount = 4
        for (i in 0..gridLinesCount) {
            val frac = i.toFloat() / gridLinesCount
            val y = padT + h * frac
            gridLines.add(Offset(padL, y) to Offset(padL + w, y))
            val lv = leftMax - leftRange * frac
            axisLabels.add(ChartLabel(NumberFormat.measure(lv), padL - 4.dp.toPx(), y + 3.dp.toPx()))
            val rv = rightMin + rightRange * (1 - frac)
            axisLabels.add(
                ChartLabel(NumberFormat.measure(rv), padL + w + 4.dp.toPx(), y + 3.dp.toPx())
            )
        }

        fun xAt(i: Int): Float = padL + (i.toFloat() / (trend.size - 1).coerceAtLeast(1)) * w

        // 平均油耗线（蓝，主色）
        var consPath: Path? = null
        var consStarted = false
        val consPts = ArrayList<Offset>(trend.size)
        for ((i, p) in trend.withIndex()) {
            val v = p.oilConsumption
            if (v == null) {
                consStarted = false
                continue
            }
            val x = xAt(i)
            val y = padT + h - (((v - leftMin) / leftRange).toFloat() * h)
            consPts.add(Offset(x, y))
            if (!consStarted) {
                consPath = Path().also { it.moveTo(x, y) }
                consStarted = true
            } else {
                consPath?.lineTo(x, y)
            }
        }

        // 每公里成本线（橙）
        var costPath: Path? = null
        var costStarted = false
        val costPts = ArrayList<Offset>(trend.size)
        for ((i, p) in trend.withIndex()) {
            val v = p.costPerKm
            if (v == null) {
                costStarted = false
                continue
            }
            val x = xAt(i)
            val y = padT + h - (((v - rightMin) / rightRange).toFloat() * h)
            costPts.add(Offset(x, y))
            if (!costStarted) {
                costPath = Path().also { it.moveTo(x, y) }
                costStarted = true
            } else {
                costPath?.lineTo(x, y)
            }
        }

        // 横轴日期标签（每 2 个显示一个避免拥挤）
        val dateLabels = ArrayList<ChartLabel>((trend.size + 1) / 2)
        for (i in trend.indices) {
            if (i % 2 == 1 && trend.size > 4) continue
            dateLabels.add(
                ChartLabel(trend[i].date, xAt(i) - 12.dp.toPx(), padT + h + 14.dp.toPx())
            )
        }

        return ChartGeometry(
            gridLines = gridLines,
            gridStroke = 1.dp.toPx(),
            lineStroke = Stroke(width = 2.dp.toPx()),
            dotRadius = 2.5.dp.toPx(),
            axisLabels = axisLabels,
            dateLabels = dateLabels,
            // 单点画不出线（与原实现 consPts.size >= 2 才画一致）
            consumptionPath = consPath.takeIf { consPts.size >= 2 },
            consumptionPoints = consPts,
            costPath = costPath.takeIf { costPts.size >= 2 },
            costPoints = costPts,
        )
    }
}

/** v1.6.2：图表标签画笔工厂（供 DualAxisCanvas 的 remember 缓存，避免每帧 new Paint） */
private fun labelPaint(color: Color, alpha: Int, textSizePx: Float): android.graphics.Paint =
    android.graphics.Paint().apply {
        this.color = android.graphics.Color.argb(
            alpha,
            (color.red * 255).toInt(),
            (color.green * 255).toInt(),
            (color.blue * 255).toInt(),
        )
        this.textSize = textSizePx
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
                        text = pluralStringResource(R.plurals.koil_ov_abnormal_count, count, count),
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
                    text = pluralStringResource(R.plurals.koil_ov_record_count, recordCount, recordCount),
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
    // v1.6.5：展开明细用——本条起往旧最多 5 条的有效油耗均值与条数
    recentAvg: Double? = null,
    recentCount: Int = 0,
    // v1.6.4：Modifier 放第一个可选参数位（Compose 约定，调用方全用命名参数，无兼容问题）
    modifier: Modifier = Modifier,
    onRect: (Rect) -> Unit = {},
) {
    val r = record.record
    // v1.6.5：点击卡片展开明细（LazyColumn 滚动回收后自动收起）
    var expanded by remember(record.record.id) { mutableStateOf(false) }
    val isAbnormal = record.consumptionStatus == ConsumptionStatus.HIGH || record.consumptionStatus == ConsumptionStatus.LOW
    val isHigh = record.consumptionStatus == ConsumptionStatus.HIGH
    val accentColor = when {
        isHigh -> Color(0xFFE65100)
        record.consumptionStatus == ConsumptionStatus.LOW -> Color(0xFFF59E0B)
        else -> KOiLColors.Primary
    }
    // v1.6.5：实付/油站单价与差额（明细优惠行、底部把手可见性共用）
    val actualPrice = record.unitPriceActual ?: r.oilPrice
    val stationPrice = record.unitPricePayable ?: r.oilPrice
    val stationSaved = if (actualPrice != null && stationPrice != null && r.oilAmount > 0) {
        (stationPrice - actualPrice) * r.oilAmount
    } else null
    // v1.6.5：有无可展开内容——决定底部「展开暗示把手」是否显示
    val hasDetail = (recentAvg != null && record.oilConsumption != null) ||
        (stationSaved != null && (stationSaved > 0.005 || stationSaved < -0.005))

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
            // 异常左竖条（v1.6.5：异常卡唯一结构性信号，加宽到 4dp）
            if (isAbnormal) {
                // v1.6.5 fix：外层 Box 是 wrap-content（滚动列表里高度约束无穷），fillMaxHeight 解析为 0 高导致竖条隐身；
                // 用 matchParentSize 跟随卡片实际尺寸（不参与测量），内部再取 4dp 宽
                Box(modifier = Modifier.matchParentSize()) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(4.dp)
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
            }
            Column(
                modifier = Modifier
                    .padding(start = if (isAbnormal) 16.dp else 16.dp, end = 16.dp, top = 13.dp, bottom = 13.dp)
                    // v1.5.8：去除按压涟漪（直角长方形）+ 点击不再进入编辑；长按=弹出中央圆按钮
                    .combinedClickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { if (showMoreMenu) onMoreMenu(record) else expanded = !expanded },
                        onLongClick = { if (!showMoreMenu) onMoreMenu(record) },
                    ),
            ) {
                // —— 顶行：日期链（新日期 ← 旧日期 + 天数胶囊） ····· 右侧实付金额 ——
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = md(r.reoilDate),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MiuixTheme.colorScheme.onBackground,
                    )
                    if (prevRecord != null) {
                        Spacer(Modifier.width(4.dp))
                        // v1.6.5：方案 A——改用文字字形「←」，与里程行的「→」、环比的「↑」同一套字体风格
                        Text(
                            text = "←",
                            // v1.6.5：旧日期缩到 11sp 后，箭头同步缩小配套
                            fontSize = 12.sp,
                            color = MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.6f),
                        )
                        Spacer(Modifier.width(3.dp))
                        Text(
                            text = md(prevRecord.record.reoilDate),
                            // v1.6.5：旧日期弱化为 11sp 次要信息（用户定稿）
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MiuixTheme.colorScheme.onBackgroundVariant,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                    if (record.oilDays != null && !isInitial) {
                        Spacer(Modifier.width(6.dp))
                        // v1.6.5：设置页版本徽章同款；字号与里程胶囊统一 11sp
                        Pill(text = stringResource(R.string.koil_ov_oil_days, record.oilDays))
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = "¥" + NumberFormat.money(r.actualAmount),
                        // v1.6.5：与日期同字号 16sp，把视觉锚点让给油耗大数字
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MiuixTheme.colorScheme.onBackground,
                        maxLines = 1,
                        softWrap = false,
                    )
                }

                // —— 二行：里程区间 + 行驶胶囊 + 油号·加油量胶囊 ····· 右侧 每公里成本 ——
                Spacer(Modifier.height(7.dp))
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val gradePill = stringResource(
                            R.string.koil_ov_grade_pill,
                            r.oilGrade ?: "-",
                            NumberFormat.fixed2(r.oilAmount),
                        )
                        if (prevRecord != null && record.distance != null) {
                            Text(
                                text = stringResource(
                                    R.string.koil_ov_range_line,
                                    (r.mileage - record.distance).toInt(),
                                    r.mileage.toInt(),
                                ),
                                fontSize = 11.sp,
                                color = MiuixTheme.colorScheme.onBackgroundVariant,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            Spacer(Modifier.width(5.dp))
                            Pill(text = stringResource(R.string.koil_ov_distance_pill, record.distance.toInt()))
                            Spacer(Modifier.width(5.dp))
                            // ② 胶囊分色：油号胶囊用中性灰，与蓝色里程胶囊区分语义
                            Pill(text = gradePill, neutral = true)
                        } else {
                            Text(
                                text = stringResource(R.string.koil_ov_current_mileage, r.mileage.toInt()),
                                fontSize = 11.sp,
                                color = MiuixTheme.colorScheme.onBackgroundVariant,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            Spacer(Modifier.width(5.dp))
                            Pill(text = gradePill, neutral = true)
                        }
                    }
                    // v1.6.5：每公里成本放在「¥实付金额」正下方（第二行右侧），不再占灰块右列
                    if (record.costPerKm != null) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "¥" + NumberFormat.money(record.costPerKm) + "/km",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MiuixTheme.colorScheme.onBackground,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                }

                // —— 油耗区（v1.6.5：去掉灰底，改淡分割线分组；数字左对齐、单价右对齐到内容线）——
                if (record.oilConsumption != null && record.distance != null) {
                    // 14. 分割线：1dp、内容区全宽、文字色 10%
                    Spacer(Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.10f)),
                    )
                    Spacer(Modifier.height(10.dp))
                    // v1.6.5 细节：深色主题下 2E7D32/E65100 对比度不足，按背景亮度切换亮色变体
                    val darkBg = MiuixTheme.colorScheme.background.luminance() < 0.5f
                    val delta = deltaText(
                        record.oilConsumption,
                        prevRecord?.oilConsumption,
                        lowerBetter = true,
                        goodColor = if (darkBg) Color(0xFF66BB6A) else Color(0xFF2E7D32),
                        badColor = if (darkBg) Color(0xFFFF8A65) else Color(0xFFE65100),
                    )
                    // v1.6.5：实付单价（实付金额/加油量）与油站单价（挂牌/应付单价）都固定两位小数
                    // （actualPrice / stationPrice 已提升到卡片顶层，与明细区共用）
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = NumberFormat.fixed2(record.oilConsumption),
                            // v1.6.5：加大到 33sp——数字墨迹与右列两行的墨迹等高（行高仍锁 31sp，不撑高卡片）
                            fontSize = 33.sp,
                            lineHeight = 31.sp,
                            fontWeight = FontWeight.ExtraBold,
                            // 异常时数字本身着色（偏高橙 / 偏低琥珀），正常用正文色
                            color = if (isAbnormal) accentColor else MiuixTheme.colorScheme.onBackground,
                            maxLines = 1,
                            softWrap = false,
                        )
                        // v1.6.5：用户要求去掉数字右边的异常圆点（异常已由左竖条 + 数字着色表达）
                        Spacer(Modifier.width(8.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = "L/100km",
                                fontSize = 10.sp,
                                lineHeight = 13.sp,
                                color = MiuixTheme.colorScheme.onBackgroundVariant,
                            )
                            if (delta != null) {
                                Text(
                                    text = delta.text,
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = delta.color,
                                    maxLines = 1,
                                    softWrap = false,
                                )
                            } else if (prevRecord == null) {
                                // ③ 仅「真的没有上一条」才叫首次记录；
                                // 有上一条但上条无有效油耗（短途不计/里程异常）时留空——那张卡自己有提示行
                                Text(
                                    text = stringResource(R.string.koil_ov_first_tag),
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                                    maxLines = 1,
                                    softWrap = false,
                                )
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        Column(
                            horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            // 6. 换位：油站单价 10sp 灰在上、实付单价 12sp 白在下（上小下大、上灰下白）
                            Text(
                                text = stringResource(
                                    R.string.koil_ov_unit_price_station,
                                    stationPrice?.let { NumberFormat.fixed2(it) } ?: NumberFormat.PLACEHOLDER,
                                ),
                                fontSize = 10.sp,
                                lineHeight = 13.sp,
                                color = MiuixTheme.colorScheme.onBackgroundVariant,
                                maxLines = 1,
                                softWrap = false,
                            )
                            Text(
                                text = stringResource(
                                    R.string.koil_ov_unit_price_actual,
                                    actualPrice?.let { NumberFormat.fixed2(it) } ?: NumberFormat.PLACEHOLDER,
                                ),
                                fontSize = 12.sp,
                                lineHeight = 16.sp,
                                color = MiuixTheme.colorScheme.onBackground,
                                maxLines = 1,
                                softWrap = false,
                            )
                        }
                    }
                } else if (isInitial) {
                    // Initial Record 虚线提示（对齐 Web）
                    Spacer(Modifier.height(9.dp))
                    HintLine(stringResource(R.string.koil_ov_first_record))
                } else if (record.distanceAnomaly) {
                    // v1.6.5：里程段为负——数据录错，明说而不是静默消失
                    Spacer(Modifier.height(9.dp))
                    HintLine(stringResource(R.string.koil_ov_distance_anomaly))
                } else if (record.shortDistanceSkipped) {
                    // v1.6.5：短途不计油耗——给一行文字兜底，不能只靠"没有灰块"
                    Spacer(Modifier.height(9.dp))
                    HintLine(stringResource(R.string.koil_ov_short_skip))
                }

                // —— v1.6.5：点击展开明细——油耗对比条 + 较油站单价优惠 ——
                AnimatedVisibility(visible = expanded) {
                    Column {
                        val darkBg = MiuixTheme.colorScheme.background.luminance() < 0.5f
                        val goodColor = if (darkBg) Color(0xFF66BB6A) else Color(0xFF2E7D32)
                        val badColor = if (darkBg) Color(0xFFFF8A65) else Color(0xFFE65100)
                        if (recentAvg != null && record.oilConsumption != null) {
                            Spacer(Modifier.height(10.dp))
                            val cur = record.oilConsumption!!
                            val avg = recentAvg
                            val maxV = maxOf(cur, avg)
                            val curColor = if (cur <= avg) goodColor else badColor
                            DetailBarRow(
                                label = stringResource(R.string.koil_ov_detail_this),
                                value = NumberFormat.fixed2(cur),
                                valueColor = curColor,
                                fraction = (cur / maxV).toFloat().coerceIn(0.05f, 1f),
                                fillColor = curColor,
                            )
                            Spacer(Modifier.height(6.dp))
                            DetailBarRow(
                                label = stringResource(R.string.koil_ov_detail_avg, recentCount),
                                value = NumberFormat.fixed2(avg),
                                valueColor = MiuixTheme.colorScheme.onBackgroundVariant,
                                fraction = (avg / maxV).toFloat().coerceIn(0.05f, 1f),
                                fillColor = MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.35f),
                            )
                        }
                        // 优惠：实付低于油站（挂牌）单价的差额 × 加油量
                        val saved = stationSaved
                        if (saved != null && (saved > 0.005 || saved < -0.005)) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = if (saved > 0) {
                                    stringResource(R.string.koil_ov_detail_saved, NumberFormat.money(saved))
                                } else {
                                    stringResource(R.string.koil_ov_detail_over, NumberFormat.money(-saved))
                                },
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (saved > 0) goodColor else badColor,
                                maxLines = 1,
                                softWrap = false,
                            )
                        }
                    }
                }
                // v1.6.5：底部展开暗示把手（28×4dp 圆角条）——常驻：收起时提示可展开，展开后提示可收起
                if (hasDetail) {
                    Spacer(Modifier.height(9.dp))
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .width(28.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(MiuixTheme.colorScheme.onBackground.copy(alpha = 0.15f)),
                    )
                }
            }
        }
    }
}

/** v1.6.5：展开明细的单行对比条（标签 + 比例条 + 数值） */
@Composable
private fun DetailBarRow(
    label: String,
    value: String,
    valueColor: Color,
    fraction: Float,
    fillColor: Color,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            fontSize = 11.sp,
            color = MiuixTheme.colorScheme.onBackgroundVariant,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.width(64.dp),
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.10f)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(3.dp))
                    .background(fillColor),
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = value,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = valueColor,
            textAlign = TextAlign.End,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.width(48.dp),
        )
    }
}

/** v1.6.5：无油耗记录（首次 / 短途 / 里程异常）的统一提示行 */
@Composable
private fun HintLine(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.10f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("ℹ", fontSize = 13.sp, color = MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.7f))
            Spacer(Modifier.width(6.dp))
            Text(
                text = text,
                fontSize = 11.sp,
                color = MiuixTheme.colorScheme.onBackgroundVariant,
            )
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
private fun deltaText(
    cur: Double?,
    prev: Double?,
    lowerBetter: Boolean,
    goodColor: Color = Color(0xFF2E7D32),
    badColor: Color = Color(0xFFE65100),
    digits: Int = 2,
): DeltaText? {
    if (cur == null || prev == null || prev <= 0) return null
    val diff = cur - prev
    val pct = diff / prev * 100
    val arrow = if (diff > 0) "↑" else "↓"
    val good = if (lowerBetter) diff < 0 else diff > 0
    val color = if (good) goodColor else badColor
    val fmt = if (digits <= 0) "%.0f" else "%.${digits}f"
    val diffTxt = if (digits <= 0) fmt.format(abs(diff)) else NumberFormat.measure(abs(diff))
    return DeltaText(
        // v1.6.5：箭头放数字后面
        text = "$diffTxt (${NumberFormat.pct(abs(pct))}%) $arrow",
        color = color,
    )
}

/**
 * v1.6.5：设置页版本徽章同款胶囊——主色字 + 12% 主色底、圆角 20dp。
 * 顶行天数用 12sp（与旧日期一致），第二行两颗用 11sp 以便三样连排进一行。
 * 深浅主题都有对比（主色底不会像 surfaceVariant 那样与卡片同色而隐形）。
 */
@Composable
private fun Pill(
    text: String,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 11.sp,
    // ② neutral = true：中性灰变体（正文色 8~10% 底 + 次要灰字），用于油号胶囊与蓝色里程胶囊区分
    neutral: Boolean = false,
) {
    val darkBg = MiuixTheme.colorScheme.background.luminance() < 0.5f
    val bg = if (neutral) {
        MiuixTheme.colorScheme.onBackground.copy(alpha = if (darkBg) 0.10f else 0.08f)
    } else {
        KOiLColors.Primary.copy(alpha = 0.12f)
    }
    val fg = if (neutral) MiuixTheme.colorScheme.onBackgroundVariant else KOiLColors.Primary
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(
            text = text,
            fontSize = fontSize,
            fontWeight = FontWeight.Medium,
            color = fg,
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

// ===== 编辑记录弹窗（v1.5.13 复刻补录卡：字段结构/图标/红色错误态/里程区间提示/校验提示条/应付只读自动算/保存置灰变蓝；唯一差异：日期只读） =====
@Composable
private fun EditRecordDialog(
    uiState: DialogUiState,
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
        maxHeight = with(LocalDensity.current) {
            (LocalWindowInfo.current.containerSize.height * 0.85f).toDp()
        },
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
private val PROVINCES = Provinces.ALL

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
                    text = if (province.isBlank()) stringResource(R.string.koil_add_pick_province) else provinceLabel(province),
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
        com.llpop.koil.ui.component.AnchoredPopupAnchor(
            show = true,
            id = "ov-province-picker",
            anchorBounds = anchorBounds.value,
            maxHeight = 320.dp,
            onDismiss = { showPicker.value = false },
        ) {
            com.llpop.koil.ui.component.AnchoredDropdownColumn {
                PROVINCES.forEachIndexed { index, p ->
                    com.llpop.koil.ui.component.AnchoredDropdownItemRow(
                        text = provinceLabel(p),
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
    // v1.6.4：Modifier 放第一个可选参数位（Compose 约定）
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    readOnly: Boolean = false,
    error: Boolean = false,
    placeholder: String? = null,
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
        com.llpop.koil.ui.component.AnchoredPopupAnchor(
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
