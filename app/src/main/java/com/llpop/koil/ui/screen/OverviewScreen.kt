package com.llpop.koil.ui.screen

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Money
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.animation.CapturedAnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.computedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.llpop.koil.R
import com.llpop.koil.ui.component.CardItem
import com.llpop.koil.ui.component.groupedCardItems
import com.llpop.koil.ui.component.KOiLHorizontalDivider
import com.llpop.koil.ui.component.pageScrollModifiers
import com.llpop.koil.ui.component.KOiLScreenScaffold
import com.llpop.koil.ui.component.pageContentPadding
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.llpop.koil.domain.logic.ConsumptionStatus
import com.llpop.koil.domain.logic.NumberFormat
import com.llpop.koil.ui.screen.overview.OverviewViewModel
import com.llpop.koil.domain.logic.RecordWithDerived
import com.llpop.koil.ui.theme.KOiLColors
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.glass.GlassDialogButton
import top.yukonga.miuix.kmp.glass.GlassDialogButtonRole
import top.yukonga.miuix.kmp.glass.GlassIconButton
import top.yukonga.miuix.kmp.material.GlassShape
import com.llpop.koil.ui.component.effect.rememberKOiLGlassEdgeStroke
import com.llpop.koil.ui.component.effect.KOiLGlassEdgeTier
import com.llpop.koil.ui.component.effect.LocalKOiLGlassSpec
import com.llpop.koil.ui.component.effect.rememberEffectiveGlassSpec
import com.llpop.koil.ui.component.blur.LocalBlurEnabled
import top.yukonga.miuix.kmp.material.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.material.edgeStroke
import top.yukonga.miuix.kmp.material.EdgeStroke
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.blur.BlurColors
import com.llpop.koil.ui.component.LocalWallpaperBackdrop
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.llpop.koil.ui.theme.KOiLType
import com.llpop.koil.ui.theme.LocalKOiLDarkTheme
import com.llpop.koil.ui.component.LocalPageBackdrop
import com.llpop.koil.ui.component.FullTankSwitchRow
import com.llpop.koil.ui.component.KOiLDateField
import com.llpop.koil.ui.component.KOiLFormSheet
import com.llpop.koil.ui.component.KOiLGlassDialog
import com.llpop.koil.ui.component.KOiLListSheet
import com.llpop.koil.ui.component.blur.rememberBlurBackdrop
import com.llpop.koil.ui.component.blur.overScrollVertical
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.utils.scrollEndHaptic



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
    // 滚到底（列表无法继续前滚）时按钮不收起，而是降到半透明仍可点：
    // 末卡右下角的金额/单价不会被实心圆盖住，用户又保留一键回顶
    val atListBottom by remember(listState) { computedStateOf { !listState.canScrollForward } }
    var moreMenuFor by remember { mutableStateOf<Long?>(null) }
    // 异常卡点击 → 异常明细弹窗
    var showAbnormalDetail by remember { mutableStateOf(false) }
    // 长按操作的卡片引用 + 全局位置（全屏遮罩点击任意处收起）
    var actionRecord by remember { mutableStateOf<RecordWithDerived?>(null) }
    var actionRect by remember { mutableStateOf<Rect?>(null) }
    // 待确认删除的记录（弹确认框后才真正删除）
    var pendingDelete by remember { mutableStateOf<RecordWithDerived?>(null) }


    // snapshotFlow 监听 LazyList 滚动位置，仅在"是否显示返回顶部"翻转时更新状态，
    // 不用 LaunchedEffect(scrollState.value) 每次像素变化都触发。
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


    val pageBackdrop = rememberBlurBackdrop()
    KOiLScreenScaffold(
        title = stringResource(R.string.koil_ov_title),
        modifier = modifier,
        backdrop = pageBackdrop,
        listState = listState,
        // 返回顶部按钮放在覆盖层：这层在 backdrop 录制树之外，采样 overlayBackdrop
        // （壁纸 + 内容 + 顶栏三层合并）。放在 content 里的话按钮落在 pageBackdrop
        // 录制树内部，采自己那层会崩，只能传 null —— 玻璃就退化成填充 + 描边了。
        overlay = { overlayBackdrop, _, _ ->
            Box(Modifier.fillMaxSize()) {
                // 返回顶部按钮——图标化 + 淡入缩放动效 + 智能回顶。
                // 竖直位置 = 底栏留白 + 16.dp（按钮 44dp），滚到底时降到 alpha 0.4（仍可点）。
                // 智能回顶：可见项序号 > 60 时先 scrollToItem 瞬移到近顶，再短动画收尾到 0。
                CapturedAnimatedVisibility(
                    visible = showBackToTop,
                    enter = fadeIn() + scaleIn(initialScale = 0.6f),
                    exit = fadeOut() + scaleOut(targetScale = 0.6f),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 16.dp, bottom = bottomPadding + 16.dp),
                ) {
                    GlassIconButton(
                        onClick = {
                            scope.launch {
                                if (listState.firstVisibleItemIndex > 60) {
                                    listState.scrollToItem(60)
                                }
                                listState.animateScrollToItem(0)
                            }
                        },
                        modifier = Modifier.alpha(if (atListBottom) 0.4f else 1f),
                        backdrop = overlayBackdrop,
                        size = 44.dp,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.KeyboardArrowUp,
                            contentDescription = stringResource(R.string.koil_back_to_top),
                            tint = MiuixTheme.colorScheme.onSurface,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
            }
        },
    ) { innerPadding, topPadding, scrollBehavior, pageBackdrop ->
        Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .pageScrollModifiers(
                    enableScrollEndHaptic = true,
                    showTopAppBar = true,
                    topAppBarScrollBehavior = scrollBehavior,
                ),
            // 底部留白走 contentPadding；横向边距由卡片组件自己给（与添加页、设置页同一种写法）
            // bottom 只留底栏槽高，与添加页、设置页一致
            contentPadding = pageContentPadding(innerPadding, extraBottom = bottomPadding),
        ) {

            // 范围筛选器 / 六指标 / 走势图：一组一卡
            groupedCardItems(
                keyPrefix = "ov_summary",
                outerBottomPadding = 12.dp,
                items = listOf(
                    CardItem("summary") {
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
                    },
                ),
            )

            // 异常卡：只有真有异常才占位；整卡可点击查看明细
            if (uiState.abnormalRecords.isNotEmpty()) {
                groupedCardItems(
                    keyPrefix = "ov_abnormal",
                    outerBottomPadding = 12.dp,
                        items = listOf(
                        CardItem("abnormal") {
                            AbnormalAlertCard(
                        count = uiState.abnormalRecords.size,
                        peakConsumption = uiState.abnormalRecords.mapNotNull { it.oilConsumption }.maxOrNull(),
                        latestConsumption = uiState.abnormalRecords
                            .maxByOrNull { it.record.reoilDate }?.oilConsumption,
                                onClick = { showAbnormalDetail = true },
                            )
                        },
                    ),
                )
            }

            // 加油历史区（对齐 Web：标题 + 共N条 + 补录）
            item {
                // 标题行不是卡片，横向 12dp 与卡片外边距同值（contentPadding 已不再给横向）
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SmallTitle(
                        text = stringResource(R.string.koil_ov_history),
                        modifier = Modifier.weight(1f),
                        insideMargin = PaddingValues(start = 4.dp, end = 0.dp, top = 8.dp, bottom = 8.dp),
                    )
                    // 補錄胶囊的玻璃：不走顶栏按钮那套材质（在页面内容里会被顶栏的
                    // dynamic 材质带成实心色块），而是与卡片完全同一套 —— 同一个
                    // KOiLGlassSpec + textureBlur + Colored Thin 混色，只把形状换成 16dp 胶囊。
                    // 采样目标同样是壁纸层（按钮在页面录制层内，不能采自己那层）。
                    // 与卡片同一处判定：关壁纸（纯色底）时切官方 puredThinGlass 口径
                    val glassSpec = rememberEffectiveGlassSpec()
                    val darkTheme = LocalKOiLDarkTheme.current
                    val wallpaperBackdrop = LocalWallpaperBackdrop.current
                    // 与卡片同一条门槛：只看模糊总开关，动态壁纸开关不参与（关壁纸时
                    // 采到的是 surface 纯色，薄纱效果仍成立）
                    val backfillGlassActive = wallpaperBackdrop != null &&
                        glassSpec.enabled &&
                        LocalBlurEnabled.current &&
                        isRuntimeShaderSupported()
                    val backfillShape = remember { GlassShape(16.dp) }
                    val backfillStroke: (() -> EdgeStroke?)? = if (
                        !backfillGlassActive || glassSpec.edgeTier == KOiLGlassEdgeTier.Disabled
                    ) {
                        null
                    } else {
                        rememberKOiLGlassEdgeStroke(isDark = darkTheme, spec = glassSpec)
                            .let { st -> remember(st) { { st.value } } }
                    }
                    val backfillSurface = if (backfillGlassActive) {
                        Modifier
                            .textureBlur(
                                backdrop = wallpaperBackdrop,
                                shape = backfillShape,
                                blurRadius = glassSpec.blurRadius,
                                noiseCoefficient = glassSpec.noiseCoefficient,
                                colors = BlurColors(
                                    blendColors = glassSpec.blendColors(darkTheme),
                                    brightness = glassSpec.brightness(darkTheme),
                                    contrast = glassSpec.contrast,
                                    saturation = glassSpec.saturation,
                                ),
                                enabled = true,
                            )
                            .then(
                                if (backfillStroke != null) {
                                    Modifier.edgeStroke(shape = backfillShape, stroke = backfillStroke)
                                } else {
                                    Modifier
                                },
                            )
                    } else {
                        Modifier.clip(backfillShape).background(KOiLColors.Primary.copy(alpha = 0.12f))
                    }
                    Row(
                        modifier = Modifier
                            .then(backfillSurface)
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
                            fontSize = KOiLType.BodySmall,
                            fontWeight = FontWeight.Medium,
                            color = KOiLColors.Primary,
                        )
                    }
                }
            }
            // LazyColumn 惰性渲染——月份标题分组展示，卡片仅组合可见项；
            // 无记录时显示空态引导。不设手动按月分页（visibleMonths）与"滚动加载更多"提示。
            // 月份标题是普通 item，跟内容一起滚走（不做吸顶）
            if (uiState.records.isEmpty()) {
                item { EmptyHistoryBox(onAddClick = onNavigateToAdd) }
            } else {
                monthGroups.forEach { (monthKey, monthRecords) ->
                    item(key = "header-$monthKey") {
                        Box(Modifier.padding(horizontal = 12.dp)) {
                            MonthHeaderRow(
                                monthKey = monthKey,
                                count = monthRecords.size,
                                summary = monthSummaryText(monthRecords),
                            )
                        }
                    }
                    // key=记录 id，回调稳定；未变化的滚出/滚入卡片可被跳过/回收
                    itemsIndexed(items = monthRecords, key = { _, it -> it.record.id }) { _, record ->
                        RecordCard(
                            record = record,
                            prevRecord = prevMap[record.record.id],
                            isInitial = prevMap[record.record.id] == null,
                            showMoreMenu = moreMenuFor == record.record.id,
                            onMoreMenu = onMoreMenuCard,
                            onRect = onActionRect,
                            recentAvg = recentAvgMap[record.record.id]?.first,
                            recentCount = recentAvgMap[record.record.id]?.second ?: 0,
                            // 卡间距与组底距同一个值：12dp
                            modifier = Modifier.padding(bottom = 12.dp),
                        )
                    }
                }
            }
        }

        // 长按全屏操作层——点击任意处收起；圆按钮定位在长按卡片中央
        CapturedAnimatedVisibility(
            visible = moreMenuFor != null,
            enter = fadeIn() + scaleIn(initialScale = 0.95f, animationSpec = tween(150)),
            exit = fadeOut() + scaleOut(targetScale = 0.98f, animationSpec = tween(120)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    // 遮罩（屏蔽罩）压到 0.40：0.22 压不住、长按菜单聚焦感不足，
                    // 0.34 实测仍偏亮，0.40 能把底压到只剩一层氛围又不至于死黑
                    .background(Color.Black.copy(alpha = 0.40f))
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
                            color = if (LocalKOiLDarkTheme.current) KOiLColors.PrimaryDark else KOiLColors.Primary,
                            // 必须采「壁纸层」而不是 pageBackdrop：长按浮层本身就在
                            // pageBackdrop 的录制层内部，玻璃去采自己所在的层会直接崩
                            //（回顶按钮同样传 null 规避）。壁纸层在页面之下，安全且同材质。
                            backdrop = LocalWallpaperBackdrop.current,
                        ) {
                            moreMenuFor = null
                            actionRecord?.let { viewModel.startEdit(it.record) }
                            actionRecord = null
                            actionRect = null
                        }
                        LongPressCircleAction(
                            icon = Icons.Filled.Delete,
                            label = stringResource(R.string.koil_ov_delete),
                            color = KOiLColors.Danger,
                            backdrop = LocalWallpaperBackdrop.current,
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

    }
    }

    // 异常明细弹层（有异常时点异常卡拉起）：从玻璃弹窗换成底部弹层——明细条数不定，
    // 弹层里内容区自己滚、底部「关闭」钉住，长列表不用把整页撑到顶；偏差取绝对值。
    // 弹层由 MainScreen 的根宿主渲染、在页面录制层之外，采样源直接用外层的整页 backdrop，
    // 这里不需要再补一份 CompositionLocal。
    if (showAbnormalDetail && uiState.abnormalRecords.isNotEmpty()) {
        KOiLListSheet(
            show = true,
            onDismissRequest = { showAbnormalDetail = false },
            title = stringResource(R.string.koil_ov_abnormal_detail_title, uiState.abnormalRecords.size),
            maxContentHeight = 480.dp,
            footer = {
                GlassDialogButton(
                    onClick = { showAbnormalDetail = false },
                    modifier = Modifier.weight(1f),
                    role = GlassDialogButtonRole.Normal,
                ) {
                    Text(
                        text = stringResource(R.string.koil_common_close),
                        style = MiuixTheme.textStyles.button,
                    )
                }
            },
        ) {
            // 面板 insideMargin 为 0，横向内距由内容块自己给；末行不画分隔线
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                uiState.abnormalRecords.forEachIndexed { index, d ->
                    val rec = d.record
                    val isHigh = d.consumptionStatus == ConsumptionStatus.HIGH
                    val accentColor = if (isHigh) KOiLColors.AttentionDay else KOiLColors.NeutralAmber
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
                            fontSize = KOiLType.Body,
                            fontWeight = FontWeight.Medium,
                            color = MiuixTheme.colorScheme.onBackground,
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "${rec.oilGrade ?: "-"}#",
                            fontSize = KOiLType.CaptionLarge,
                            fontWeight = FontWeight.Medium,
                            color = KOiLColors.Primary,
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = "${NumberFormat.measure(d.oilConsumption ?: 0.0)} L/100km",
                            fontSize = KOiLType.Body,
                            fontWeight = FontWeight.Bold,
                            color = MiuixTheme.colorScheme.onBackground,
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "$statusTxt ${NumberFormat.pct(abs(d.deviationPct))}%",
                            fontSize = KOiLType.CaptionLarge,
                            fontWeight = FontWeight.Medium,
                            color = accentColor,
                        )
                    }
                    if (index < uiState.abnormalRecords.lastIndex) {
                        KOiLHorizontalDivider(Modifier.padding(vertical = 8.dp))
                    }
                }
            }
        }
    }

    // 删除确认弹窗（防误删）
    pendingDelete?.let { rec ->
        KOiLGlassDialog(
            show = true,
            onDismissRequest = { pendingDelete = null },
            contentPadding = 20.dp,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = stringResource(R.string.koil_ov_delete_confirm_title),
                    fontSize = KOiLType.TitleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MiuixTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = stringResource(R.string.koil_ov_delete_confirm_sub),
                    fontSize = KOiLType.Body,
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                )
                Spacer(Modifier.height(20.dp))
                // 两个按钮交回官方 GlassDialogButton：取消 = Normal（半透明玻璃底），
                // 删除 = Destructive（危险色），底色/按下态/圆角都跟弹窗面板同一套 material
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    GlassDialogButton(
                        onClick = { pendingDelete = null },
                        modifier = Modifier.weight(1f),
                        role = GlassDialogButtonRole.Normal,
                    ) {
                        Text(
                            text = stringResource(R.string.koil_common_cancel),
                            style = MiuixTheme.textStyles.button,
                        )
                    }
                    GlassDialogButton(
                        onClick = {
                            pendingDelete = null
                            viewModel.deleteRecord(rec.record.id)
                        },
                        modifier = Modifier.weight(1f),
                        role = GlassDialogButtonRole.Destructive,
                    ) {
                        Text(
                            text = stringResource(R.string.koil_ov_delete_confirm),
                            style = MiuixTheme.textStyles.button,
                        )
                    }
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
        KOiLFormSheet(
            show = true,
            onDismissRequest = viewModel::cancelBackfill,
            title = stringResource(R.string.koil_ov_backfill_title),
        ) {
            Column {
                Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp)) {
                // 加油省份（与设置页同款下拉列表）
                ProvinceSelectField(province = dlg.backfillProvince, onProvinceChange = viewModel::setBackfillProvince)
                Spacer(Modifier.height(14.dp))
                // 第一行：日期（日历可改）+ 油号（联动当日参考单价）
                Row(verticalAlignment = Alignment.Top) {
                    KOiLDateField(modifier = Modifier.weight(1f), date = dlg.backfillDate, recordDates = dlg.backfillRecordDates, onDateSelected = viewModel::setBackfillDate)
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
                // 满箱标记（跳枪）：补录历史记录同样可标，默认勾选；整行铺满弹窗宽。
                // 块模型：上方 12dp = ①「上一字段 → 块上缘」，由上面字段容器的 bottom 12dp 承担
                //（不另加 Spacer——原块外上距是容器底距 13 与 Spacer 12 叠加的 25dp，现收敛到 12；
                // 实测字段底→标题顶 34dp，改后约 21–24dp，含行内留白）；
                // 块内上距 8dp 在行内；bottomInset 维持默认 8dp → 高亮停在块下缘，
                // 块下缘 → 下方内容块的 16dp 由下面 Column 的 top 16dp 在高亮之外补。
                FullTankSwitchRow(
                    checked = dlg.backfillIsFullTank,
                    onCheckedChange = viewModel::setBackfillFullTank,
                )
                // 跳枪行下方间距按「块是否真的渲染」分配：是否绘制只由下面两个谓词决定，
                // 卡片和提示条内部不再各判一次（否则改一处忘另一处就会「留了空档但没内容」）。
                // top 16dp = 块模型 ③「块下缘 → 下方内容块」，在高亮之外（弹窗高亮停在块下缘）。
                Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp)) {
                    val hasSummary = hasPaymentSummary(
                        dlg.backfillPayableAmount.toDoubleOrNull(),
                        dlg.backfillActualAmount.toDoubleOrNull(),
                        dlg.backfillOilAmount.toDoubleOrNull(),
                        dlg.backfillReferencePrice,
                    )
                    val hasHint = hasPrecheckHint(
                        beforeFirst = dlg.backfillBeforeFirst,
                        sameDayExists = dlg.backfillSameDayExists,
                        mileageConflictType = dlg.backfillMileageConflictType,
                        deviationExceeded = dlg.backfillDeviationExceeded,
                        overPayable = dlg.backfillOverPayable,
                        overTank = dlg.backfillOverTank,
                        ready = dlg.backfillReady,
                    )
                    // 实付综合信息块（对齐 Web PaymentSummaryCard：优惠概览/实际单价/参考价对比）
                    if (hasSummary) {
                        PaymentSummaryCard(
                            payable = dlg.backfillPayableAmount.toDoubleOrNull(),
                            actual = dlg.backfillActualAmount.toDoubleOrNull(),
                            fuelAmount = dlg.backfillOilAmount.toDoubleOrNull(),
                            referencePrice = dlg.backfillReferencePrice,
                        )
                        // 只有卡片真的画过，这段 8dp 才需要存在
                        if (hasHint) Spacer(Modifier.height(8.dp))
                    }
                    if (hasHint) {
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
                    }
                    // 保存失败提示（如唯一约束冲突等）
                    if (dlg.saveError) {
                        Spacer(Modifier.height(8.dp))
                        SaveErrorHint()
                    }
                    // 这 20dp 是「内容块 → 按钮行」的间距：有块真的渲染才画。空态时块下缘
                    // 已由上方列的 top 16dp 撑到位（块模型 ③），再加 20dp 就变成 36dp——
                    // 正是开发者最初抱怨的松散值，条件化后空态 = 16dp，与定稿图一致。
                    if (hasSummary || hasHint || dlg.saveError) {
                        Spacer(Modifier.height(20.dp))
                    }
                    Row(modifier = Modifier.fillMaxWidth()) {
                        GlassDialogButton(
                            onClick = viewModel::cancelBackfill,
                            modifier = Modifier.weight(1f),
                            role = GlassDialogButtonRole.Normal,
                        ) {
                            Text(
                                text = stringResource(R.string.koil_common_cancel),
                                style = MiuixTheme.textStyles.button,
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        GlassDialogButton(
                            onClick = viewModel::saveBackfill,
                            modifier = Modifier.weight(1f),
                            enabled = dlg.backfillReady && !dlg.isSavingBackfill,
                            // Primary 角色 = 主色底：录满才亮起，与原来「蓝色可保存」的语义一致
                            role = GlassDialogButtonRole.Primary,
                        ) {
                            Text(
                                text = stringResource(R.string.koil_common_save),
                                style = MiuixTheme.textStyles.button,
                            )
                        }
                    }
                }
            }
        }
    }
}
