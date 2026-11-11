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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
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
    // 弹窗独立状态流——弹窗输入只重组弹窗，不牵动列表/四宫格/趋势图
    val dlg by viewModel.dialogState.collectAsStateWithLifecycle()
    // 全页用 LazyColumn 惰性渲染，历史记录只组合可见项；不需要按月手动分页（visibleMonths）
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var showBackToTop by remember { mutableStateOf(false) }
    var moreMenuFor by remember { mutableStateOf<Long?>(null) }
    // 异常卡点击 → 异常明细弹窗
    var showAbnormalDetail by remember { mutableStateOf(false) }
    // 长按操作的卡片引用 + 全局位置（全屏遮罩点击任意处收起）
    var actionRecord by remember { mutableStateOf<RecordWithDerived?>(null) }
    var actionRect by remember { mutableStateOf<Rect?>(null) }
    // 待确认删除的记录（弹确认框后才真正删除）
    var pendingDelete by remember { mutableStateOf<RecordWithDerived?>(null) }

    // snapshotFlow 监听 LazyList 滚动位置，仅在"是否显示返回顶部"翻转时更新状态，
    // 不用 LaunchedEffect(scrollState.value) 每次像素变化都触发
    LaunchedEffect(listState) {
        snapshotFlow {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 300
        }
            .distinctUntilChanged()
            .collect { showBackToTop = it }
    }

    // 月分组与 prevMap 只在 records 变化时重算，不每次重组都 groupBy/buildPrevMap
    val monthGroups = remember(uiState.records) {
        // take(7) 而非 substring(0, 7)：CSV/备份/历史数据可能混入短日期或空串，
        // substring 会在组合期抛异常，导致总览页稳定复现地崩溃且用户无法自行恢复。
        uiState.records.groupBy { it.record.reoilDate.take(7) }.toList()
    }
    val prevMap = remember(uiState.records) { buildPrevMap(uiState.records) }
    // 每条记录的「近 N 次油耗均值」（含自身，往旧最多取 5 条有效油耗），供展开明细对比条
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
    // 趋势图以摘要卡内 sparkline 呈现，不设油号筛选胶囊——进入页面统一回「全部」
    LaunchedEffect(Unit) {
        if (uiState.trendGrade != "all") viewModel.setTrendGrade("all")
    }
    // 长按相关回调稳定（remember 一次），配合 items(key=id) 让未变化卡片可跳过重组
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
            // 吸顶月份标题覆盖层——位于 backdrop 录制树外，可安全 textureBlur(backdrop)
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

            // 范围筛选器并入摘要卡顶部；六指标摘要（带图标 + 环比）
            item {
                SummaryGrid(
                    uiState = uiState,
                    rangeLabels = rangeLabels,
                    onRangeChange = viewModel::setRange,
                    // 无异常时把「油耗稳定」并进摘要卡底部，不单占一张卡
                    stableNote = if (uiState.abnormalRecords.isEmpty()) {
                        stringResource(
                            when (uiState.range) {
                                "year" -> R.string.koil_ov_stable_year
                                "6m" -> R.string.koil_ov_stable_6m
                                "3m" -> R.string.koil_ov_stable_3m
                                else -> R.string.koil_ov_stable_all
                            }
                        )
                    } else {
                        null
                    },
                )
            }
            item { Spacer(Modifier.height(12.dp)) }


            // 异常卡：只有真有异常才占位；整卡可点击查看明细
            if (uiState.abnormalRecords.isNotEmpty()) {
                item {
                    AbnormalAlertCard(
                        count = uiState.abnormalRecords.size,
                        peakConsumption = uiState.abnormalRecords.mapNotNull { it.oilConsumption }.maxOrNull(),
                        latestConsumption = uiState.abnormalRecords
                            .maxByOrNull { it.record.reoilDate }?.oilConsumption,
                        onClick = { showAbnormalDetail = true },
                    )
                }
                item { Spacer(Modifier.height(12.dp)) }
            }

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
                    Spacer(Modifier.weight(1f))
                    // 補錄用胶囊按钮（主色浅底 + 加号图标），点击区域加大
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

            // LazyColumn 惰性渲染——月份标题分组展示，卡片仅组合可见项；
            // 无记录时显示空态引导。不设手动按月分页（visibleMonths）与"滚动加载更多"提示
            // 月份标题用普通 item——吸顶由覆盖层（StickyMonthOverlay，
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
                    // key=记录 id，回调稳定；未变化的滚出/滚入卡片可被跳过/回收
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
            // 底部留白取 bottomPadding+80.dp——回顶按钮占 bottomPadding+16 起的高度，留白不足会盖住末卡内容
            item { Spacer(Modifier.height(bottomPadding + 80.dp)) }
        }

        // 长按全屏操作层——点击任意处收起；圆按钮定位在长按卡片中央
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
                            // 先弹确认框，确认后才真正删除
                            rec?.let { pendingDelete = it }
                        }
                    }
                }
            }
        }

        // 返回顶部按钮——图标化 + 淡入缩放动效 + 智能回顶。
        // 智能回顶：可见项序号 > 60（几千条记录的深处）时先 scrollToItem 瞬移到近顶，
        // 再短动画收尾到 0；近处保持平滑 animateScrollToItem，不从深处慢速长飞。
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

    // 异常明细弹窗（有异常时点异常卡弹出）；KOiLGlassDialog 滚动容器是
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

    // 删除确认弹窗（防误删）
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

    // 编辑弹窗（复刻补录卡：日期只读 + 全量校验 + 保存置灰/变蓝）
    if (dlg.showEditDialog) {
        EditRecordDialog(
            uiState = dlg,
            onMileageChange = viewModel::setEditMileage,
            onOilAmountChange = viewModel::setEditOilAmount,
            onActualAmountChange = viewModel::setEditActualAmount,
            onGradeChange = viewModel::setEditGrade,
            onProvinceChange = viewModel::setEditProvince,
            onFullTankChange = viewModel::setEditFullTank,
            onPayableChange = {},
            onSave = viewModel::saveEdit,
            onDismiss = viewModel::cancelEdit,
        )
    }

    // 补录弹窗（source=backfill_modal）
    if (dlg.showBackfillDialog) {
        // 按窗口可用高度算（分屏/键盘弹出时 Configuration 仍是整屏）
        val maxH = with(LocalDensity.current) {
            (LocalWindowInfo.current.containerSize.height * 0.85f).toDp()
        }
        com.llpop.koil.ui.component.KOiLGlassDialog(
            show = true,
            onDismissRequest = viewModel::cancelBackfill,
            contentPadding = 0.dp,
            scrollable = true,
            maxHeight = maxH,
            widthFraction = 0.90f,
        ) {
            Column {
                Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 13.dp)) {
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
                // 选日期后该区间可输入里程范围提示（日期行与里程行之间）
                if (dlg.backfillRangeMin != null || dlg.backfillRangeMax != null) {
                    MileageRangeHint(
                        rangeMin = dlg.backfillRangeMin,
                        rangeMax = dlg.backfillRangeMax,
                    )
                    Spacer(Modifier.height(14.dp))
                }
                // 第二行：里程 + 加油量（图标/红色错误态对齐首页录入卡）
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
                // 第三行：应付（只读自动计算）+ 实付（对齐首页录入卡）
                Row {
                    EditField(
                        label = stringResource(R.string.koil_add_field_payable),
                        icon = Icons.Filled.Money,
                        value = dlg.backfillPayableAmount,
                        onValueChange = {},
                        keyboardType = KeyboardType.Decimal,
                        readOnly = true,
                        // 应付未计算（油量/参考价缺失）时显示占位
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
                }
                // 满箱标记（跳枪）：补录历史记录同样可标，默认勾选；整行铺满弹窗宽，
                // 横向 16dp / 纵向 13dp 都由行内 insideMargin 提供，与上一字段的间距也只由行内上距承担。
                com.llpop.koil.ui.component.FullTankSwitchRow(
                    checked = dlg.backfillIsFullTank,
                    onCheckedChange = viewModel::setBackfillFullTank,
                )
                Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 13.dp, bottom = 16.dp)) {
                Spacer(Modifier.height(10.dp))
                // 实付综合信息块（对齐 Web PaymentSummaryCard：优惠概览/实际单价/参考价对比）
                PaymentSummaryCard(
                    payable = dlg.backfillPayableAmount.toDoubleOrNull(),
                    actual = dlg.backfillActualAmount.toDoubleOrNull(),
                    fuelAmount = dlg.backfillOilAmount.toDoubleOrNull(),
                    referencePrice = dlg.backfillReferencePrice,
                )
                Spacer(Modifier.height(8.dp))
                // 补录校验提示（用户确认规则：日期边界/重复/里程链/偏差A+B/实付>应付/油箱容量）
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
                // 保存失败提示（如唯一约束冲突等）
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
                        // 全部校验通过 → 蓝色可保存；否则灰色置灰（对齐首页录入卡 addRecord）
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
}

@Composable
private fun monthTitle(monthKey: String): String {
    val parts = monthKey.split("-")
    val year = parts.getOrNull(0)?.toIntOrNull()
    val month = parts.getOrNull(1)?.toIntOrNull()
    return if (parts.size == 2 && year != null && month != null)
        stringResource(R.string.koil_ov_month_title, year, month)
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
    return pluralStringResource(
        R.plurals.koil_ov_month_summary,
        records.size,
        records.size,
        NumberFormat.money(totalAmount),
        NumberFormat.fixed2(totalOil),
    )
}

/** 月份标题行（列表 item 与吸顶覆盖层共用，保证两处布局/字号/间距完全一致，切换无跳变） */
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
 * 计算"当前吸顶月份"（等价 Compose stickyHeader 钉住语义，回滚连续无空档）：
 * - 取所有可见月份标题中 offset ≤ 顶栏高度的**最后一个**为吸顶月份（新标题顶替旧标题）；
 * - 无标题在顶栏区时：
 *   - 无可见标题：列表已回到顶部（未进入历史区）或已滚过历史区末尾 → 取消吸顶；
 *     否则（吸顶标题滚出视口上方、下一标题未到）→ 保持吸顶；
 *   - 可见标题都在顶栏下方：吸顶标题自然位置回到顶栏（回滚松开）→ **立即接替为上一个月份标题**
 *     （此时它必已处于吸顶位，即使视口外），实现回滚时顶栏标题连续切换、无空档；
 *     吸顶标题已滚出视口上方（上滚中）→ 保持吸顶。
 * 覆盖层据此渲染顶栏同款毛玻璃标题；列表内对应标题行由各自 pinned 判定透明占位。
 *
 * 信号源与判定树：
 * 不采用 snapshotFlow { visibleItemsInfo }：每帧发射一次完整可见项快照（60Hz 滚动下每秒
 * 数十个 List + LazyListItemInfo 快照分配），collect 内还要 mapNotNull 出中间列表。
 * 当前实现：
 * - 流信号取 `firstVisibleItemIndex to firstVisibleItemScrollOffset`——snapshotFlow 按结构
 *   相等去重，静止时完全不发射；滚动时发射体只有两个装箱 int；
 * - visibleItemsInfo 在收集体内直读（它本身仍是每帧新对象，但只被消费、不进流），
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
 * 吸顶月份标题覆盖层——位于 backdrop 录制树外（KOiLScreenScaffold overlay 插槽），
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
            // 空状态引导——可一键前往录入页（仅当宿主传入导航回调时显示）
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
    // 无异常时不单占一整张卡，收成卡片底部一行小字（null = 有异常，不显示）
    stableNote: String? = null,
) {
    val stats = uiState.stats
    val prev = uiState.prevStats
    fun pct(cur: Double, prevVal: Double?): Double? =
        if (prevVal != null && prevVal > 0) (cur - prevVal) / prevVal * 100 else null

    KOiLBlurBackground(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 20f,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // 范围筛选器放在摘要卡顶部（独立悬浮灰条会在卡片区多一块浮块）
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
            // —— 六指标 3×2（字号统一 21sp；里程/油量涨幅不判好坏用中性灰）——
            Row(modifier = Modifier.fillMaxWidth()) {
                StatCell(
                    Modifier.weight(1f),
                    icon = Icons.Filled.WaterDrop,
                    // 无满箱锚点时退化为「长期平均」，标签必须带标识，不能冒充区间平均
                    label = if (stats.longTermAvg) stringResource(R.string.koil_ov_long_term_avg)
                    else stringResource(R.string.koil_stat_avg_consumption),
                    value = NumberFormat.measure(stats.avgOilConsumption ?: 0.0),
                    unit = "L/100km",
                    comparePct = pct(stats.avgOilConsumption ?: 0.0, prev?.avgOilConsumption),
                )
                Spacer(Modifier.width(12.dp))
                StatCell(
                    Modifier.weight(1f),
                    icon = Icons.Filled.Money,
                    // 无满箱锚点时该值同样是长期平均兜底（总油/总里程），标签必须带标识，不能冒充区间值
                    label = if (stats.longTermAvg) stringResource(R.string.koil_ov_long_term_avg)
                    else stringResource(R.string.koil_stat_cost_per_km),
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
                // 总应付信息量低（与总实付几乎同值），用平均油价（含环比，可看油价走势）
                StatCell(
                    Modifier.weight(1f),
                    icon = Icons.Filled.Receipt,
                    label = stringResource(R.string.koil_stat_avg_price),
                    value = NumberFormat.measure(stats.avgUnitPriceActual ?: 0.0),
                    unit = stringResource(R.string.koil_stat_price_unit_long),
                    comparePct = pct(stats.avgUnitPriceActual ?: 0.0, prev?.avgUnitPriceActual),
                )
            }
            // 不设「實付 vs 應付 对比条」——按用户要求不显示节省提示
            Spacer(Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.12f))
            )
            Spacer(Modifier.height(10.dp))
            // 油耗走势 sparkline（不设独立趋势卡，无坐标轴无图例，只表达形状）
            val sparkValues = remember(uiState.trend) {
                // 短途闭合区间（<100km）单条失真风险大：趋势中隐藏（总账仍计入，见定稿）
                uiState.trend.mapNotNull { if (it.lowConfidence) null else it.oilConsumption }.takeLast(40)
            }
            if (sparkValues.size >= 2) {
                // 补参照——只给末值看不出当前算高还是低；给出样本数与区间
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.koil_ov_spark_label),
                        fontSize = 11.sp,
                        color = MiuixTheme.colorScheme.onBackgroundVariant,
                        maxLines = 1,
                        softWrap = false,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(
                            R.string.koil_ov_spark_range,
                            sparkValues.size,
                            NumberFormat.fixed2(sparkValues.min()),
                            NumberFormat.fixed2(sparkValues.max()),
                        ),
                        fontSize = 9.5.sp,
                        color = MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.7f),
                        maxLines = 1,
                        softWrap = false,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = NumberFormat.fixed2(sparkValues.last()),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MiuixTheme.colorScheme.onBackground,
                        maxLines = 1,
                        softWrap = false,
                    )
                    Spacer(Modifier.width(2.dp))
                    Text(
                        text = "L/100km",
                        fontSize = 9.5.sp,
                        color = MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.7f),
                        maxLines = 1,
                        softWrap = false,
                    )
                }
                Spacer(Modifier.height(6.dp))
                ConsumptionSparkline(
                    values = sparkValues,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(34.dp),
                )
                Spacer(Modifier.height(10.dp))
            }
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
            }
            // 无异常 → 「暂无异常」不单占整卡，收成这一行（省约 80dp）
            if (stableNote != null) {
                Spacer(Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.10f)),
                )
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(KOiLColors.Primary),
                    )
                    Spacer(Modifier.width(7.dp))
                    Text(
                        text = stringResource(R.string.koil_ov_stable_inline),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MiuixTheme.colorScheme.onBackground,
                        maxLines = 1,
                        softWrap = false,
                    )
                    Text(
                        text = " · $stableNote",
                        fontSize = 11.sp,
                        color = MiuixTheme.colorScheme.onBackgroundVariant,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
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
    // 里程/油量这类「涨了不代表变差」的指标不判好坏，只显示幅度（中性灰）
    neutral: Boolean = false,
) {
    // 六个指标统一 21sp（避免字号差异误导重要性）；深色主题用亮色变体
    val darkBg = MiuixTheme.colorScheme.background.luminance() < 0.5f
    val upColor = if (darkBg) Color(0xFFFF8A65) else Color(0xFFE65100)
    val downColor = if (darkBg) Color(0xFF66BB6A) else Color(0xFF2E7D32)
    // 单元格内容水平居中——start 对齐会让三列内容都挤在格子左侧、
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
                // 六个指标统一 21sp（不用 24/19sp 混排）
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
                // 「较上期」→「持平」，变化不足 0.5% 时说清是没变化，而不是让人猜在跟谁比
                Text(
                    text = stringResource(R.string.koil_ov_flat),
                    fontSize = 10.sp,
                    color = MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.5f),
                )
            }
        }
    }
}

/**
 * 摘要卡内的油耗 sparkline。
 * 只画折线 + 末点，不画坐标轴与网格——趋势的形状用一条线就能表达，
 * 不用双轴折线图（油耗 + 每公里成本同画布）：两套量纲不可比、轴标签 9sp 也读不清。
 */
@Composable
private fun ConsumptionSparkline(values: List<Double>, modifier: Modifier = Modifier) {
    val lineColor = KOiLColors.Primary
    Canvas(modifier = modifier) {
        if (values.size < 2) return@Canvas
        val minV = values.min()
        val maxV = values.max()
        val span = (maxV - minV).takeIf { it > 0.0001 } ?: 1.0
        val inset = 3.dp.toPx()
        val usableH = (size.height - inset * 2).coerceAtLeast(1f)
        val stepX = size.width / (values.size - 1)
        val pts = values.mapIndexed { i, v ->
            Offset(stepX * i, inset + usableH - ((v - minV) / span).toFloat() * usableH)
        }
        val path = Path().apply {
            moveTo(pts.first().x, pts.first().y)
            for (i in 1 until pts.size) lineTo(pts[i].x, pts[i].y)
        }
        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(
                width = 1.6.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )
        drawCircle(color = lineColor, radius = 2.4.dp.toPx(), center = pts.last())
    }
}

@Composable
private fun AbnormalAlertCard(
    count: Int,
    peakConsumption: Double?,
    latestConsumption: Double?,
    onClick: () -> Unit,
) {
    KOiLBlurBackground(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 20f,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = null,
                tint = Color(0xFFE65100),
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = pluralStringResource(R.plurals.koil_ov_abnormal_count, count, count),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MiuixTheme.colorScheme.onBackground,
                )
                // 这里用异常里最高/最近两次而非「平均油耗」（后者跟摘要卡同一个数），才是新增信息
                if (peakConsumption != null && latestConsumption != null) {
                    Text(
                        text = stringResource(
                            R.string.koil_ov_abnormal_peak,
                            NumberFormat.fixed2(peakConsumption),
                            NumberFormat.fixed2(latestConsumption),
                        ),
                        fontSize = 11.sp,
                        color = MiuixTheme.colorScheme.onBackgroundVariant,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            // 右侧不用「范围 + 条数」两行（跟顶部筛选器、摘要卡全重复），用明确的点击入口
            Text(
                text = stringResource(R.string.koil_ov_detail_go) + " ›",
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium,
                color = KOiLColors.Primary,
                maxLines = 1,
                softWrap = false,
            )
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
    // Modifier 放第一个可选参数位（Compose 约定，调用方全用命名参数，无兼容问题）
    modifier: Modifier = Modifier,
    // 展开明细用——本条起往旧最多 5 条的有效油耗均值与条数
    recentAvg: Double? = null,
    recentCount: Int = 0,
    onRect: (Rect) -> Unit = {},
) {
    val r = record.record
    // 点击卡片展开明细（LazyColumn 滚动回收后自动收起）
    var expanded by remember(record.record.id) { mutableStateOf(false) }
    val isAbnormal = record.consumptionStatus == ConsumptionStatus.HIGH || record.consumptionStatus == ConsumptionStatus.LOW
    val isHigh = record.consumptionStatus == ConsumptionStatus.HIGH
    val accentColor = when {
        isHigh -> Color(0xFFE65100)
        record.consumptionStatus == ConsumptionStatus.LOW -> Color(0xFFF59E0B)
        else -> KOiLColors.Primary
    }
    // 实付/油站单价与差额（明细优惠行、底部把手可见性共用）
    val actualPrice = record.unitPriceActual ?: r.oilPrice
    val stationPrice = record.unitPricePayable ?: r.oilPrice
    val stationSaved = if (actualPrice != null && stationPrice != null && r.oilAmount > 0) {
        (stationPrice - actualPrice) * r.oilAmount
    } else null
    // 有无可展开内容——决定底部「展开暗示把手」是否显示
    val hasDetail = (recentAvg != null && record.oilConsumption != null) ||
        (stationSaved != null && (stationSaved > 0.005 || stationSaved < -0.005))

    Box(
        modifier = modifier
            .fillMaxWidth()
            // 长按显示时上报自身全局位置（页面级全屏层据此定位圆按钮）
            .onGloballyPositioned { if (showMoreMenu) onRect(it.boundsInRoot()) },
    ) {
        KOiLBlurBackground(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 20f,
        ) {
            // 异常左竖条（异常卡唯一结构性信号，加宽到 4dp）
            if (isAbnormal) {
                // 外层 Box 是 wrap-content（滚动列表里高度约束无穷），fillMaxHeight 解析为 0 高导致竖条隐身；
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
                    // 不设按压涟漪（直角长方形），点击不进入编辑；长按=弹出中央圆按钮
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
                        // 用文字字形「←」，与里程行的「→」、环比的「↑」同一套字体风格
                        Text(
                            text = "←",
                            // 旧日期缩到 11sp 后，箭头同步缩小配套
                            fontSize = 12.sp,
                            color = MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.6f),
                        )
                        Spacer(Modifier.width(3.dp))
                        Text(
                            text = md(prevRecord.record.reoilDate),
                            // 旧日期弱化为 11sp 次要信息（用户定稿）
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MiuixTheme.colorScheme.onBackgroundVariant,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                    if (record.oilDays != null && !isInitial) {
                        Spacer(Modifier.width(6.dp))
                        // 设置页版本徽章同款；字号与里程胶囊统一 11sp
                        Pill(text = stringResource(R.string.koil_ov_oil_days, record.oilDays))
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = "¥" + NumberFormat.money(r.actualAmount),
                        // 与日期同字号 16sp，把视觉锚点让给油耗大数字
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
                    // 每公里成本放在「¥实付金额」正下方（第二行右侧），不占灰块右列
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

                // —— 油耗区（不设灰底，用淡分割线分组；数字左对齐、单价右对齐到内容线）——
                if (record.oilConsumption != null && record.spanDistance != null) {
                    // 14. 分割线：1dp、内容区全宽、文字色 10%
                    Spacer(Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.10f)),
                    )
                    Spacer(Modifier.height(10.dp))
                    // 深色主题下 2E7D32/E65100 对比度不足，按背景亮度切换亮色变体
                    val darkBg = MiuixTheme.colorScheme.background.luminance() < 0.5f
                    // 短途区间数字失真（标"短途参考"），环比 delta 不计算不显示——与异常判定口径一致
                    val delta = if (record.shortInterval) null else deltaText(
                        record.oilConsumption,
                        prevRecord?.oilConsumption,
                        lowerBetter = true,
                        goodColor = if (darkBg) Color(0xFF66BB6A) else Color(0xFF2E7D32),
                        badColor = if (darkBg) Color(0xFFFF8A65) else Color(0xFFE65100),
                    )
                    // 实付单价（实付金额/加油量）与油站单价（挂牌/应付单价）都固定两位小数
                    // （actualPrice / stationPrice 放在卡片顶层，与明细区共用）
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = NumberFormat.fixed2(record.oilConsumption),
                            // 字号取 33sp——数字墨迹与右列两行的墨迹等高（行高仍锁 31sp，不撑高卡片）
                            fontSize = 33.sp,
                            lineHeight = 31.sp,
                            fontWeight = FontWeight.ExtraBold,
                            // 异常时数字本身着色（偏高橙 / 偏低琥珀），正常用正文色
                            color = if (isAbnormal) accentColor else MiuixTheme.colorScheme.onBackground,
                            maxLines = 1,
                            softWrap = false,
                        )
                        // 按用户要求，数字右边不设异常圆点（异常由左竖条 + 数字着色表达）
                        Spacer(Modifier.width(8.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = "L/100km",
                                fontSize = 10.sp,
                                lineHeight = 13.sp,
                                color = MiuixTheme.colorScheme.onBackgroundVariant,
                            )
                            if (record.shortInterval) {
                                // 短途闭合区间：单条失真风险大，标"短途参考"（总账仍计入）
                                Pill(text = stringResource(R.string.koil_ov_short_interval_tag), neutral = true)
                            }
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
                } else if (record.distanceAnomaly && record.oilConsumption == null) {
                    // 满箱但区间里程非正（同点/回拨）——数据录错，明说而不是静默消失
                    Spacer(Modifier.height(9.dp))
                    HintLine(stringResource(R.string.koil_ov_distance_anomaly))
                } else if (record.consumptionStatus == ConsumptionStatus.PENDING && record.pendingOil != null) {
                    // 半箱/区间异常：待下次加满一次结算，显示自锚点以来的累计（不显示 km——
                    // 区间最终里程要等结算才确定，提前显示易与结算后的油耗区间混淆）
                    Spacer(Modifier.height(9.dp))
                    HintLine(
                        stringResource(
                            R.string.koil_ov_pending_settle,
                            record.pendingCount,
                            NumberFormat.fixed2(record.pendingOil),
                            NumberFormat.fixed2(record.pendingActual ?: 0.0),
                        )
                    )
                } else if (record.consumptionStatus == ConsumptionStatus.NO_BASE) {
                    // 无满箱锚点：首个满箱=基准已建立；半箱=暂无基准（上次满油时间未知，不显示累计）
                    Spacer(Modifier.height(9.dp))
                    if (r.isFullTank) {
                        HintLine(stringResource(R.string.koil_ov_base_set))
                    } else {
                        HintLine(stringResource(R.string.koil_ov_no_base))
                    }
                }

                // —— 点击展开明细——油耗对比条 + 较油站单价优惠 ——
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
                // 底部展开暗示把手（28×4dp 圆角条）——常驻：收起时提示可展开，展开后提示可收起
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

/** 展开明细的单行对比条（标签 + 比例条 + 数值） */
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

/** 无油耗记录（首次 / 短途 / 里程异常）的统一提示行 */
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

/** 长按弹出的圆形操作按钮（图标 + 文字标签） */
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

/** 环比 delta 文本（对齐 Web：↓0.26 (4.0%)；里程整数指标 digits=0 → ↓35 (5.7%)，不显示 35.00） */
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
        // 箭头放数字后面
        text = "$diffTxt (${NumberFormat.pct(abs(pct))}%) $arrow",
        color = color,
    )
}

/**
 * 设置页版本徽章同款胶囊——主色字 + 12% 主色底、圆角 20dp。
 * 顶行天数用 11sp（与旧日期一致），第二行两颗用 11sp 以便三样连排进一行。
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
    val month = parts.getOrNull(1)?.toIntOrNull()
    val day = parts.getOrNull(2)?.toIntOrNull()
    return if (parts.size == 3 && month != null && day != null)
        stringResource(R.string.koil_ov_md_date, month, day)
    else date
}

// ===== 编辑记录弹窗（复刻补录卡：字段结构/图标/红色错误态/里程区间提示/校验提示条/应付只读自动算/保存置灰变蓝；唯一差异：日期只读） =====
@Composable
private fun EditRecordDialog(
    uiState: DialogUiState,
    onMileageChange: (String) -> Unit,
    onOilAmountChange: (String) -> Unit,
    onActualAmountChange: (String) -> Unit,
    onGradeChange: (String) -> Unit,
    onProvinceChange: (String) -> Unit,
    onFullTankChange: (Boolean) -> Unit,
    onPayableChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    com.llpop.koil.ui.component.KOiLGlassDialog(
        show = true,
        onDismissRequest = onDismiss,
        contentPadding = 0.dp,
        scrollable = true,
        maxHeight = with(LocalDensity.current) {
            (LocalWindowInfo.current.containerSize.height * 0.85f).toDp()
        },
        widthFraction = 0.90f,
    ) {
        Column {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 13.dp)) {
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
                    // 应付未计算（油量/参考价缺失）时显示占位
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
            }
            // 满箱标记（跳枪）：历史记录可逐条改，改后整链实时重算；整行铺满弹窗宽，
            // 横向 16dp / 纵向 13dp 都由行内 insideMargin 提供，与上一字段的间距也只由行内上距承担。
            com.llpop.koil.ui.component.FullTankSwitchRow(
                checked = uiState.editIsFullTank,
                onCheckedChange = onFullTankChange,
            )
            Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 13.dp, bottom = 16.dp)) {
            Spacer(Modifier.height(10.dp))
            // 实付综合信息块（对齐 Web PaymentSummaryCard：优惠概览/实际单价/参考价对比）
            PaymentSummaryCard(
                payable = uiState.editPayableAmount.toDoubleOrNull(),
                actual = uiState.editActualAmount.toDoubleOrNull(),
                fuelAmount = uiState.editOilAmount.toDoubleOrNull(),
                referencePrice = uiState.editReferencePrice,
            )
            Spacer(Modifier.height(8.dp))
            // 编辑校验提示条（复刻补录卡：日期边界/同日重复/里程链/偏差A+B/实付>应付/油箱容量，通过绿色）
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
            // 保存失败提示（如唯一约束冲突等）
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
                    // 全部校验通过 → 蓝色可保存；否则灰色置灰（对齐补录卡/首页录入卡）
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

/** 补录校验提示（用户确认规则：早于第一条/同日重复/里程链/偏差A+B/实付>应付/油箱容量，通过绿色；readyText 支持编辑卡/补录卡差异化文案） */
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

/** 补录/编辑保存失败提示（红色），不静默吞异常 */
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

/** 选日期后该区间可输入里程范围提示（中性信息，不阻断） */
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

/** 补录弹窗字段（对齐首页录入卡 FormField：图标 + 标签行，红色错误态边框/图标/标签） */
@Composable
private fun EditField(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String,
    onValueChange: (String) -> Unit,
    // Modifier 放第一个可选参数位（Compose 约定）
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
        // miuix TextField 无 placeholder 参数，用 Box 叠加层实现"待计算"占位（value 为空时显示）
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
