package com.llpop.koil.ui.screen.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import top.yukonga.miuix.kmp.blur.layerBackdrop
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.sp
import com.llpop.koil.R
import com.llpop.koil.domain.logic.NumberFormat
import com.llpop.koil.ui.component.CardSegment
import com.llpop.koil.ui.component.LocalOnGlassSurface
import com.llpop.koil.ui.component.GlassPopupOverlay
import top.yukonga.miuix.kmp.glass.GlassPopupItem
import com.llpop.koil.ui.component.LocalWallpaperBackdrop
import com.llpop.koil.ui.component.swipeToSwitchTab
import com.llpop.koil.ui.component.KOiLHorizontalDivider
import com.llpop.koil.ui.component.BackNavigationIcon
import com.llpop.koil.ui.component.pageScrollModifiers
import com.llpop.koil.ui.component.KOiLScreenScaffold
import com.llpop.koil.ui.component.pageContentPadding
import com.llpop.koil.ui.component.blur.overScrollVertical
import com.llpop.koil.ui.component.blur.rememberBlurBackdrop
import com.llpop.koil.ui.component.gradeLabel
import com.llpop.koil.ui.component.provinceLabel
import com.llpop.koil.ui.theme.KOiLColors
import com.llpop.koil.util.AppClock
import kotlin.math.roundToInt
import org.koin.androidx.compose.koinViewModel
import top.yukonga.miuix.kmp.basic.Icon
import com.llpop.koil.ui.component.LocalPageBackdrop
import top.yukonga.miuix.kmp.basic.TabRowWithContour
import top.yukonga.miuix.kmp.glass.GlassSegmentedTabRow
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.llpop.koil.ui.theme.KOiLType

/** 油价上调色（涨红） / 下调色（跌绿） */
private val UpColor = KOiLColors.PriceUp
private val DownColor = KOiLColors.PriceDown

/**
 * 油价明细页：省份（下拉）/油号切换 → 今年累计涨跌汇总 + 价格走势图 + 调价时间线。
 * 由设置页「油價數據庫」卡片入口进入；返回手势/返回按钮回到设置页。
 */
private const val CONTENT_KEY = "oil-price-detail-content"

@Composable
fun OilPriceDetailScreen(
    onBack: () -> Unit,
    viewModel: OilPriceDetailViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val surfaceColor = MiuixTheme.colorScheme.surface
    val backdrop = rememberBlurBackdrop()

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    KOiLScreenScaffold(
        title = stringResource(R.string.koil_pdb_detail_title),
        ownWallpaper = true,
        backdrop = backdrop,
        listState = listState,
        navigationIcon = { BackNavigationIcon(onClick = onBack) },
    ) { innerPadding, topPadding, scrollBehavior, _ ->
        // —— 明细页整体滚动容器（LazyColumn + contentPadding 页面骨架）——
        // 时间线仅 20 条左右，整页收在一个 item 里一次性渲染；单 item 下
        // firstVisibleItemScrollOffset 就等于总滚动距离。
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                // 录制层由 KOiLScreenScaffold 的外层 Box 统一挂载；这里再挂一次同一个
                // backdrop 会造成绘制期重入 record → "Recording currently in progress" 闪退
                .pageScrollModifiers(
                    enableScrollEndHaptic = true,
                    showTopAppBar = true,
                    topAppBarScrollBehavior = scrollBehavior,
                ),
            // 横向边距由四张卡的 outerHorizontalPadding(12dp) 承担；
            // 顶部在顶栏下方再让 12dp 呼吸（extraTop），随顶栏折叠联动
            contentPadding = pageContentPadding(innerPadding, extraTop = 12.dp),
        ) {
            item(key = CONTENT_KEY) {
            Column(modifier = Modifier.fillMaxWidth()) {

            // —— 筛选区重排——
            // 行 1：省份 / 年份用下拉 pill（31 省、多年份是高基数项，若各占一行横滑 chips
            //       + 左右箭头，筛选区顶会占大半屏且 31 省里定位难）；下拉用官方玻璃 GlassPopupOverlay，
            //       视觉/动效与设置页省份选择一致。
            // 行 2：油号分段控件——固定 4 项（92/95/98/0）整行等宽分段，不横滑。
            var provinceMenuExpanded by remember { mutableStateOf(false) }
            val provinceAnchor = remember { mutableStateOf<IntRect?>(null) }
            var yearMenuExpanded by remember { mutableStateOf(false) }
            val yearAnchor = remember { mutableStateOf<IntRect?>(null) }
            // 点击已展开的 pill 时，Popup 先因「外部点击」触发 onDismissRequest 收起，
            // 同一击又落到 pill 的 toggle 上 → 立刻重新展开，表现为「收起又弹开」闪烁。
            // 记录 dismiss 时间戳，紧随其后（350ms 内）的 pill 点击视为同一次手势、不展开。
            var provinceDismissAt by remember { mutableLongStateOf(0L) }
            var yearDismissAt by remember { mutableLongStateOf(0L) }
            // 横向 12dp 与卡片外边距同值
            // （contentPadding 已不含横向，非卡内容自己补）
            Column(modifier = Modifier.padding(horizontal = 12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                // 省份下拉 pill（含展开浮层，Popup 锚定在 pill 所在 Box）
                // map 是 inline lambda，保留组合上下文，provinceLabel（@Composable）可直接调用
                val provinceItems = state.provinces.map { provinceLabel(it) }
                Box(modifier = Modifier.onGloballyPositioned { coordinates ->
                    val b = coordinates.boundsInWindow()
                    provinceAnchor.value = IntRect(b.left.toInt(), b.top.toInt(), b.right.toInt(), b.bottom.toInt())
                }) {
                    DropdownPill(
                        label = provinceLabel(state.province),
                        expanded = provinceMenuExpanded,
                        onToggle = {
                            val justDismissed = provinceDismissAt > 0 &&
                                AppClock.millis() - provinceDismissAt < 350
                            provinceDismissAt = 0L
                            if (!justDismissed) provinceMenuExpanded = !provinceMenuExpanded
                        },
                    )
                                        GlassPopupOverlay(
                        show = provinceMenuExpanded,
                        anchorBounds = provinceAnchor.value.toFloatRect(),
                        backdrop = LocalPageBackdrop.current,
                        onDismissRequest = {
                            provinceMenuExpanded = false
                            provinceDismissAt = AppClock.millis()
                        },
                        maxHeight = 360.dp,
                    ) {
                    provinceItems.forEachIndexed { idx, name ->
                        GlassPopupItem(
                            text = name,
                            selected = state.provinces[idx] == state.province,
                            onClick = {
                                provinceMenuExpanded = false
                                // 数据键是中文省名（DB 主键），这里只换显示名
                                viewModel.selectProvince(state.provinces[idx])
                            },
                        )
                    }
                
                    }
                }
                // 年份下拉 pill（无年份数据时占位，保持省份 pill 左对齐语义）
                if (state.years.isNotEmpty()) {
                    Box(modifier = Modifier.onGloballyPositioned { coordinates ->
                        val b = coordinates.boundsInWindow()
                        yearAnchor.value = IntRect(b.left.toInt(), b.top.toInt(), b.right.toInt(), b.bottom.toInt())
                    }) {
                        DropdownPill(
                            label = stringResource(R.string.koil_detail_year, state.year),
                            expanded = yearMenuExpanded,
                            onToggle = {
                                val justDismissed = yearDismissAt > 0 &&
                                    AppClock.millis() - yearDismissAt < 350
                                yearDismissAt = 0L
                                if (!justDismissed) yearMenuExpanded = !yearMenuExpanded
                            },
                        )
                                                GlassPopupOverlay(
                            show = yearMenuExpanded,
                            anchorBounds = yearAnchor.value.toFloatRect(),
                            backdrop = LocalPageBackdrop.current,
                            onDismissRequest = {
                                yearMenuExpanded = false
                                yearDismissAt = AppClock.millis()
                            },
                            maxHeight = 360.dp,
                        ) {
                        state.years.forEachIndexed { idx, y ->
                            GlassPopupItem(
                                text = stringResource(R.string.koil_detail_year, y),
                                selected = y == state.year,
                                onClick = {
                                    yearMenuExpanded = false
                                    viewModel.selectYear(y)
                                },
                            )
                        }
                    
                        }
                    }
                } else {
                    Spacer(Modifier.width(1.dp))
                }
            }
            Spacer(Modifier.height(10.dp))

            // —— 油号用官方 GlassSegmentedTabRow（一条轨道 + 轨道内指示块滑动）——
            val gradeIndex = state.grades.indexOf(state.grade).coerceAtLeast(0)
            val onGradeSelect: (Int) -> Unit = { idx -> viewModel.selectGrade(state.grades[idx]) }
            val wallpaper = LocalWallpaperBackdrop.current
            if (wallpaper != null) {
                GlassSegmentedTabRow(
                    tabs = state.grades.map { gradeLabel(it) },
                    selectedIndex = gradeIndex,
                    onSelect = onGradeSelect,
                    backdrop = wallpaper,
                )
            } else {
                TabRowWithContour(
                    tabs = state.grades.map { gradeLabel(it) },
                    selectedTabIndex = gradeIndex,
                    onTabSelected = onGradeSelect,
                    modifier = Modifier.swipeToSwitchTab(
                        tabCount = state.grades.size,
                        selectedIndex = gradeIndex,
                        onSelect = onGradeSelect,
                        itemSpacing = 5.dp,
                    ),
                )
            }
            }
            // 轨道与下方卡片之间留白：玻璃轨道贴边会显得和卡片粘在一起
            Spacer(Modifier.height(12.dp))
            // 年份筛选不出选项时补 2dp，避免控件区与下方内容贴在一起
            if (state.years.isEmpty()) {
                Spacer(Modifier.height(2.dp))
            }

            if (state.items.isEmpty()) {
                // —— 空态 ——
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 60.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.koil_pdb_detail_empty),
                        fontSize = KOiLType.BodyLarge,
                        color = MiuixTheme.colorScheme.onBackgroundVariant,
                    )
                }
            } else {
                // —— 涨跌汇总卡：两行（最新价与净涨跌 / 调价次数统计）合成一段卡片 ——
                // 别再拆成上下两段：分段拼接会在接缝处漏出一条贯穿卡片全宽的浅线
                // （两段各自 squircleSurface 抗锯齿的结果），它盖住真正的分割线，
                // 看起来就像「分割线顶到了卡片两边」。合并成单段后接缝不存在，
                // 分割线按行内边距内缩，与单元格文字左右对齐。
                CardSegment(
                    isFirst = true,
                    isLast = true,
                    outerBottomPadding = 12.dp,
                    glass = true,
                ) {
                    // 水平内距交给容器（与数据页一致）：线本身不写水平内距，跟着容器走
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                    ) {
                        // —— 第一行：最新价格（前，大号突出）+ 今年净涨跌（后）——
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                        ) {
                            SummaryCell(
                                modifier = Modifier.weight(1f),
                                label = stringResource(R.string.koil_pdb_latest_price),
                                value = state.latestPrice?.let { "¥${NumberFormat.measure(it)}" } ?: "--",
                                valueColor = MiuixTheme.colorScheme.onBackground,
                            )
                            SummaryCell(
                                modifier = Modifier.weight(1f),
                                label = stringResource(R.string.koil_pdb_net_change),
                                value = if (state.netChange >= 0) {
                                    stringResource(R.string.koil_pdb_net_up, state.netChange)
                                } else {
                                    stringResource(R.string.koil_pdb_net_down, -state.netChange)
                                },
                                valueColor = if (state.netChange >= 0) UpColor else DownColor,
                            )
                        }
                        // 两格（最新价 / 净涨跌）与三格（上调 / 下调 / 搁浅）之间的分割线：
                        // 水平内缩由上面的 Column 给（与数据页的分割线同一套用法）。
                        KOiLHorizontalDivider()
                        // —— 第二行：上调次数 / 下调次数 / 搁浅次数 ——
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                        ) {
                            SummaryCell(
                                modifier = Modifier.weight(1f),
                                label = stringResource(R.string.koil_pdb_up_count),
                                value = stringResource(R.string.koil_pdb_count_times, state.upCount),
                                valueColor = UpColor,
                            )
                            SummaryCell(
                                modifier = Modifier.weight(1f),
                                label = stringResource(R.string.koil_pdb_down_count),
                                value = stringResource(R.string.koil_pdb_count_times, state.downCount),
                                valueColor = DownColor,
                            )
                            SummaryCell(
                                modifier = Modifier.weight(1f),
                                label = stringResource(R.string.koil_pdb_stranded_count),
                                value = stringResource(R.string.koil_pdb_count_times, state.strandedCount),
                                valueColor = MiuixTheme.colorScheme.onBackgroundVariant,
                            )
                        }
                    }
                }

                // —— 价格走势图（套圆角卡片，与汇总卡视觉统一）——
                CardSegment(
                    isFirst = true,
                    isLast = true,
                    outerBottomPadding = 12.dp,
                    glass = true,
                ) {
                    PriceTrendCanvas(
                        items = state.items,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                    )
                }

                // —— 调价时间线（最新在上）——
                // 左轨道（涨红/跌绿/搁浅空心灰 + 淡色竖线串起节奏），
                // 右侧每行一条贯穿全宽的幅度导轨（彩段长度 = |幅度| / 区间最大幅度），
                // 避免「只有两行文字、中间一大片空」。
                val timeline = state.items.asReversed()
                // 归一化基准：当前筛选区间的最大单次幅度。取不到时退化为 1 元，
                // 避免全 0 导致除零（coerceAtLeast 兜底）。
                val maxAbsDiff = timeline.maxOfOrNull { kotlin.math.abs(it.diff ?: 0.0) }
                    ?.coerceAtLeast(0.01) ?: 1.0
                // 时间线整体作为一段卡片：组底距挂在 CardSegment 上，
                // 末项换成别的内容也不会丢
                CardSegment(
                    isFirst = true,
                    isLast = true,
                    outerBottomPadding = 12.dp,
                    insidePadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    // 与上方两张卡统一走全站玻璃参数；模糊/壁纸关闭或不支持 RuntimeShader 时回落实色
                    glass = true,
                ) {
                    timeline.forEachIndexed { idx, row ->
                        AdjustTimelineItem(
                            item = row,
                            isLatest = idx == 0,
                            isLast = idx == timeline.lastIndex,
                            maxAbsDiff = maxAbsDiff,
                        )
                    }
                }
            }
                }
            }

            item {
                Spacer(
                    Modifier
                        .height(24.dp)
                        .navigationBarsPadding()
                )
            }
        }
    }
}

/**
 * 紧凑下拉 pill（筛选区行 1：省份 / 年份）。
 * 视觉对齐分段控件（surface 底 8% + 圆角 10dp），点击后由调用处的 GlassPopupOverlay 展开选择列表。
 */

/** IntRect 锚点 -> 官方锚定弹层需要的 Rect（px 视口坐标） */
private fun IntRect?.toFloatRect(): Rect =
    if (this == null) Rect.Zero
    else Rect(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat())

@Composable
private fun DropdownPill(
    label: String,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.08f))
            .clickable(onClick = onToggle)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(
            text = label,
            fontSize = KOiLType.Body,
            fontWeight = FontWeight.Medium,
            color = MiuixTheme.colorScheme.onBackground,
            maxLines = 1,
            softWrap = false,
        )
        Spacer(Modifier.width(4.dp))
        Icon(
            imageVector = Icons.Filled.KeyboardArrowDown,
            contentDescription = null,
            tint = MiuixTheme.colorScheme.onBackgroundVariant,
            // 展开时箭头轻微上转，给出状态反馈
            modifier = Modifier
                .size(18.dp)
                .graphicsLayer { rotationZ = if (expanded) 180f else 0f },
        )
    }
}



/** 汇总单元格 */
@Composable
private fun SummaryCell(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    valueColor: Color,
    valueFontSize: TextUnit = 16.sp,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            fontSize = KOiLType.CaptionLarge,
            color = MiuixTheme.colorScheme.onBackgroundVariant,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = value,
            fontSize = valueFontSize,
            fontWeight = FontWeight.Bold,
            color = valueColor,
        )
    }
}

/** 价格走势折线图（横轴=调价次序，纵轴=价格）；点击数据点显示日期+价格 */
@Composable
private fun PriceTrendCanvas(items: List<PriceAdjustItem>, modifier: Modifier = Modifier) {
    val gridColor = MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.1f)
    val labelColor = MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.6f)
    val lineColor = KOiLColors.Primary
    // 数据点气泡：实色卡上 surfaceContainer 才和卡面对得上；卡片是采样玻璃时这里
    // 会变成一块实色贴片，换成 onSurface 的极低 alpha（描边和文字仍按主题走）。
    val bubbleBg = if (LocalOnGlassSurface.current) {
        MiuixTheme.colorScheme.onSurface.copy(alpha = 0.06f)
    } else {
        MiuixTheme.colorScheme.surfaceContainer
    }
    val bubbleBorder = MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.25f)
    val dotInnerColor = MiuixTheme.colorScheme.surface
    val onBgColor = MiuixTheme.colorScheme.onBackground
    val prices = items.map { it.price }
    var selectedIndex by remember { mutableStateOf<Int?>(null) }
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(170.dp)
            .pointerInput(prices.size) {
                fun nearestIndex(px: Float): Int? {
                    if (prices.size < 2) return if (prices.size == 1) 0 else null
                    val padLpx = 38.dp.toPx()
                    val wPx = size.width - padLpx - 12.dp.toPx()
                    val step = wPx / (prices.size - 1)
                    return ((px - padLpx) / step).roundToInt().coerceIn(0, prices.size - 1)
                }
                // 点击 + 横向拖动跟随：按住/横向拖动实时显示该点日期+价格；
                // 纵向拖动不消费，让给外层滚动容器正常滚页
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var dragging = false
                    val slop = viewConfiguration.touchSlop
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (change.isConsumed) break // 父级已消费（纵向滚动中）
                        val pos = change.position
                        if (!dragging) {
                            val dx = pos.x - down.position.x
                            val dy = pos.y - down.position.y
                            // 横向意图（|dx| >= |dy|）且越过 slop 才进入跟随
                            if (dx * dx + dy * dy >= slop * slop && kotlin.math.abs(dx) >= kotlin.math.abs(dy)) {
                                dragging = true
                                change.consume()
                                selectedIndex = nearestIndex(pos.x)
                            }
                        } else {
                            change.consume()
                            selectedIndex = nearestIndex(pos.x)
                        }
                        if (change.changedToUp()) {
                            if (!dragging) {
                                // 纯点击：显示该点
                                selectedIndex = nearestIndex(down.position.x)
                            }
                            break
                        }
                    }
                }
            },
    ) {
        if (prices.isEmpty()) return@Canvas
        val padL = 38.dp.toPx()
        val padR = 12.dp.toPx()
        val padT = 10.dp.toPx()
        val padB = 24.dp.toPx()
        val w = size.width - padL - padR
        val h = size.height - padT - padB

        val minP = prices.minOrNull() ?: 0.0
        val maxP = prices.maxOrNull() ?: 0.0
        val span = (maxP - minP).coerceAtLeast(0.1)
        // 上下留 15% 边距
        val lo = minP - span * 0.15
        val hi = maxP + span * 0.15
        val range = (hi - lo).coerceAtLeast(0.1)

        // 横向网格 + 价格刻度
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
            val pv = hi - range * frac
            drawContext.canvas.nativeCanvas.apply {
                val paint = android.graphics.Paint().apply {
                    color = android.graphics.Color.argb(
                        153,
                        (labelColor.red * 255).toInt(),
                        (labelColor.green * 255).toInt(),
                        (labelColor.blue * 255).toInt(),
                    )
                    textSize = 10.dp.toPx()
                }
                drawText(NumberFormat.measure(pv), 2.dp.toPx(), y + 3.dp.toPx(), paint)
            }
        }

        fun xAt(i: Int): Float = padL + (i.toFloat() / (prices.size - 1).coerceAtLeast(1)) * w
        fun yAt(v: Double): Float = padT + h - (((v - lo) / range).toFloat() * h)

        // 曲线下方面积渐变填充（提升图表可读性与专业观感）
        val fillPath = Path().apply {
            prices.forEachIndexed { i, v ->
                val x = xAt(i)
                val y = yAt(v)
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
            lineTo(xAt(prices.size - 1), padT + h)
            lineTo(xAt(0), padT + h)
            close()
        }
        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(lineColor.copy(alpha = 0.20f), lineColor.copy(alpha = 0.02f)),
                startY = padT,
                endY = padT + h,
            ),
        )

        // 折线
        val path = Path()
        prices.forEachIndexed { i, v ->
            val x = xAt(i)
            val y = yAt(v)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(
            path = path,
            color = lineColor,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()),
        )
        // 端点圆点
        drawCircle(color = lineColor, radius = 3.dp.toPx(), center = Offset(xAt(0), yAt(prices.first())))
        drawCircle(color = lineColor, radius = 4.dp.toPx(), center = Offset(xAt(prices.size - 1), yAt(prices.last())))

        // 选中点：竖虚线 + 放大圆点 + 气泡（日期 + 价格）
        val sel = selectedIndex
        if (sel != null && sel in prices.indices) {
            val sx = xAt(sel)
            val sy = yAt(prices[sel])
            drawLine(
                color = labelColor.copy(alpha = 0.8f),
                start = Offset(sx, padT),
                end = Offset(sx, size.height - padB),
                strokeWidth = 1.dp.toPx(),
            )
            drawCircle(color = lineColor, radius = 5.dp.toPx(), center = Offset(sx, sy))
            drawCircle(
                color = dotInnerColor,
                radius = 2.dp.toPx(),
                center = Offset(sx, sy),
            )
            // 气泡文字
            val bubbleText = "${items[sel].label}  ¥${NumberFormat.measure(items[sel].price)}"
            drawContext.canvas.nativeCanvas.apply {
                val paint = android.graphics.Paint().apply {
                    color = android.graphics.Color.argb(
                        255,
                        (onBgColor.red * 255).toInt(),
                        (onBgColor.green * 255).toInt(),
                        (onBgColor.blue * 255).toInt(),
                    )
                    textSize = 10.dp.toPx()
                    isAntiAlias = true
                }
                val textW = paint.measureText(bubbleText)
                val bubbleW = textW + 12.dp.toPx()
                val bubbleH = 20.dp.toPx()
                // 气泡优先放在点上方；上方空间不足则放下方（且不压日期刻度）
                val above = sy - bubbleH - 8.dp.toPx() > padT
                val bubbleY = if (above) {
                    sy - bubbleH - 8.dp.toPx()
                } else {
                    (sy + 10.dp.toPx()).coerceAtMost(size.height - padB - bubbleH)
                }
                val bubbleX = (sx - bubbleW / 2).coerceIn(padL, size.width - padR - bubbleW)
                // 画圆角矩形气泡
                drawRoundRect(
                    color = bubbleBg,
                    topLeft = Offset(bubbleX, bubbleY),
                    size = androidx.compose.ui.geometry.Size(bubbleW, bubbleH),
                    cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx()),
                )
                drawRoundRect(
                    color = bubbleBorder,
                    topLeft = Offset(bubbleX, bubbleY),
                    size = androidx.compose.ui.geometry.Size(bubbleW, bubbleH),
                    cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx()),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx()),
                )
                drawText(
                    bubbleText,
                    bubbleX + 6.dp.toPx(),
                    bubbleY + 14.dp.toPx(),
                    paint,
                )
            }
        }

        // 日期标签：首、尾 + 中间等距 2 个（点少时只画首尾，避免重叠）
        drawContext.canvas.nativeCanvas.apply {
            val paint = android.graphics.Paint().apply {
                color = android.graphics.Color.argb(
                    153,
                    (labelColor.red * 255).toInt(),
                    (labelColor.green * 255).toInt(),
                    (labelColor.blue * 255).toInt(),
                )
                textSize = 10.dp.toPx()
            }
            val n = prices.size
            val labelIdx = if (n >= 8) listOf(0, n / 4, n / 2, n * 3 / 4, n - 1).distinct() else listOf(0, n - 1)
            labelIdx.forEach { i ->
                val label = items[i].label
                val x = xAt(i)
                val textW = paint.measureText(label)
                // 首标签左对齐、尾标签右对齐、中间居中对齐，避免越界
                val drawX = when (i) {
                    0 -> padL
                    n - 1 -> (x - textW).coerceAtLeast(padL)
                    else -> (x - textW / 2).coerceIn(padL, padL + w - textW)
                }
                drawText(label, drawX, size.height - 6.dp.toPx(), paint)
            }
        }
    }
}

/**
 * 调价时间线单条：0 中轴双向幅度条，不设左轨道。
 * 真机反馈：加左轨道会让每行变成「左节点 + 满宽底轨 + 端点」三层线，
 * 视觉过乱、也不符合选定的设计稿。因此严格对齐设计稿 A：
 * - 日期独占最左一列（与设计稿同宽语义），价格行与幅度条都在右侧列；
 * - 行 1：最新标签（如有）—— 价格 · 涨跌额 · 百分比，右对齐；
 * - 行 2：浅灰底轨 + 0 轴竖线；涨 = 从中轴向右伸红段，跌 = 向左伸绿段，
 *   段长 = 半宽 × (|幅度| / 区间最大幅度)，末端小圆点；搁浅/基准只有中轴 + 灰点。
 * 不设节点/竖线装饰，线条数量与设计稿一致。
 */
@Composable
private fun AdjustTimelineItem(
    item: PriceAdjustItem,
    isLatest: Boolean,
    isLast: Boolean,
    maxAbsDiff: Double,
) {
    val diff = item.diff
    val stranded = item.isStranded
    val accent: Color = when {
        stranded -> MiuixTheme.colorScheme.onBackgroundVariant
        diff != null && diff > 0.001 -> UpColor
        diff != null && diff < -0.001 -> DownColor
        else -> MiuixTheme.colorScheme.onBackgroundVariant
    }
    // 彩段占比：以当前筛选区间的最大幅度为满格 → 任何数据量级下条长都有可比性（固定 1 元满格
    // 会导致全是很短的条）；非零幅度保底 3%，保证 0.01 元这种小调价也看得见。
    val absDiff = diff?.let { kotlin.math.abs(it) } ?: 0.0
    val hasBar = !stranded && absDiff > 0.001
    val barFrac = if (hasBar) (absDiff / maxAbsDiff).toFloat().coerceIn(0.03f, 1f) else 0f

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
    ) {
        // —— 时间左侧加「导线」——
        // 竖线把相邻节点串起来，节点按 涨红 / 跌绿 / 搁浅·基准（空心灰）着色：
        // 连续涨跌的节奏、搁浅的位置都能一路扫下来，比只有孤立圆点更有时间感；
        // 幅度信息仍由右侧中轴条承担，两者不重复（一条管「节奏」，一条管「大小」）。
        Box(
            modifier = Modifier
                .width(16.dp)
                .fillMaxHeight(),
        ) {
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .width(2.dp)
                        .fillMaxHeight()
                        // 日期左边的线取 2dp/0.32（真机反馈 1.5dp/0.18 几乎融进卡片底），
                        // 仍低于节点(0.7/1.0)保持从属关系。
                        .background(accent.copy(alpha = 0.32f)),
                )
            }
            val dotSize = if (isLatest) 9.dp else 7.dp
            val hollow = stranded || diff == null || kotlin.math.abs(diff) <= 0.001
            // 对齐：圆点中心 = 行 1 文字的垂直中心。最新行 17sp（行高约 21dp，中心约 10.5dp）→
            // 9dp 点 top 6dp；普通行 15sp（中心约 9.5dp）→ 7dp 点 top 6dp（10/8dp 偏低，真机反馈）。
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 6.dp)
                    .size(dotSize)
                    .then(
                        if (hollow) {
                            Modifier.border(BorderStroke(1.5.dp, accent.copy(alpha = 0.7f)), CircleShape)
                        } else {
                            Modifier.background(accent, CircleShape)
                        }
                    ),
            )
        }
        // —— 日期列：独占最左（设计稿 A 的 46px 列），价格与幅度条都在右侧列 ——
        Text(
            text = item.label,
            fontSize = KOiLType.CaptionLarge,
            color = MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.85f),
            modifier = Modifier
                .width(46.dp)
                .padding(top = 3.dp),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = if (isLast) 0.dp else 16.dp),
        ) {
            // —— 行 1：价格（左）· 最新标签（价格右侧）· 涨跌额 ····· 百分比（顶最右）——
            // 字号规则：单条内 价格/涨跌额/百分比 统一 14sp，只有「最新」那条整体大一号
            // （价格 17sp、涨跌额与百分比 16sp），层次只留给当前价，其余记录一律平权，字号一致。
            val numSize = if (isLatest) 16.sp else 14.sp
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "¥${NumberFormat.measure(item.price)}",
                    // 价格列定宽：涨跌额从固定 x 起排，纵向成一条列线。
                    // 真机反馈「价格与涨跌额间距太开」：价格列定宽 56dp（仍覆盖 17sp 粗体 ¥10.15 ≈54dp），
                    // 列后间距 4dp，普通行（15sp ¥7.89 ≈40dp）的空档约 15dp，列线保持。
                    // 用 widthIn 而非 width：真出现三位数价格时让它自然撑开，不裁切。
                    modifier = Modifier.widthIn(min = 56.dp),
                    fontSize = if (isLatest) 17.sp else 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (stranded) {
                        MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.7f)
                    } else {
                        MiuixTheme.colorScheme.onBackground
                    },
                )
                Spacer(Modifier.width(4.dp))
                // 右端：涨跌额 / 搁浅 / 基准价 / 持平
                when {
                    stranded -> Text(
                        text = stringResource(R.string.koil_detail_stranded),
                        fontSize = KOiLType.CaptionLarge,
                        color = MiuixTheme.colorScheme.onBackgroundVariant,
                    )

                    diff == null -> Text(
                        text = stringResource(R.string.koil_pdb_first_base),
                        fontSize = KOiLType.CaptionLarge,
                        color = MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.8f),
                    )

                    diff > 0.001 -> Text(
                        text = stringResource(R.string.koil_detail_delta_up, diff),
                        fontSize = numSize,
                        fontWeight = FontWeight.SemiBold,
                        color = UpColor,
                    )

                    diff < -0.001 -> Text(
                        text = stringResource(R.string.koil_detail_delta_down, -diff),
                        fontSize = numSize,
                        fontWeight = FontWeight.SemiBold,
                        color = DownColor,
                    )

                    else -> Text(
                        text = stringResource(R.string.koil_pdb_flat),
                        fontSize = KOiLType.CaptionLarge,
                        color = MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.8f),
                    )
                }
                if (isLatest) {
                    // 最新标签在「价格 ¥x + 涨跌额」的右侧（不在价格左侧，也不夹在两者之间）
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.koil_detail_latest),
                        fontSize = KOiLType.Caption,
                        fontWeight = FontWeight.Medium,
                        color = KOiLColors.Primary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(5.dp))
                            .background(KOiLColors.Primary.copy(alpha = 0.12f))
                            .padding(horizontal = 4.dp, vertical = 1.dp),
                    )
                }
                val pct = item.pctChange
                // 百分比顶到行最右（设计稿 A）；没有百分比时这格空着，保持价格行的右端干净
                Spacer(Modifier.weight(1f))
                if (pct != null) {
                    Text(
                        text = if (pct >= 0) {
                            stringResource(R.string.koil_detail_pct_up, NumberFormat.measure(pct * 100))
                        } else {
                            stringResource(R.string.koil_detail_pct_down, NumberFormat.measure(-pct * 100))
                        },
                        fontSize = numSize,
                        color = accent.copy(alpha = 0.9f),
                    )
                }
            }
            Spacer(Modifier.height(7.dp))
            // —— 行 2：0 中轴双向幅度条 ——
            // 不用「整行灰轨 + 彩段从左往右长」：方向靠颜色区分，涨跌都朝同一边长，读起来像进度条。
            // 以中轴为 0：涨从中轴向右伸红段、跌向左伸绿段，幅度 = 半宽 × (|幅度| / 区间最大幅度)。
            // 方向与大小同时可见，左右两侧都被利用；搁浅/基准价只有中轴 + 一个灰点，不会与「小幅涨跌」混淆。
            val axisColor = MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.3f)
            // 灰色底带（用户反馈：裸细线浮在黑底上显得乱，坐在一条圆角灰带上更归置）
            // 底带透明度 0.14：0.09 在黑色卡片上几乎看不出来
            val bandColor = MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.14f)
            val flatDotColor = MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.55f)
            // 底带 6dp，彩段 4dp——上下各留 1dp 灰边，条「嵌」在带里而不是把带填满。
            // 彩段与底带同高（8dp）真机看太粗壮、像进度条；
            // 「底带 8 + 彩段 5」真机反馈灰底带仍偏粗，故取「底带 6 + 彩段 4」。
            val bandDp = 6.dp
            val barDp = 4.dp
            val barFracHalf = (barFrac / 2f).coerceIn(0.01f, 0.5f)
            val isRise = diff != null && diff > 0.001
            // 用 Canvas 按中轴算绝对坐标，而不是 Box + fillMaxWidth(frac)：
            // 后者的 frac 是相对**整行**宽度的，再靠 align(CenterEnd) 贴边 → 彩段实际是
            // 「从右边缘往左长」，和设计稿的「从中轴往外伸」完全相反（真机实测）。
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp),
            ) {
                val cy = size.height / 2f
                val cx = size.width / 2f
                val bandH = bandDp.toPx()
                // 灰色底带：整行一条圆角浅灰「底」，幅度条坐在上面，行列感更强
                drawRoundRect(
                    color = bandColor,
                    topLeft = Offset(0f, cy - bandH / 2f),
                    size = Size(size.width, bandH),
                    cornerRadius = CornerRadius(bandH / 2f, bandH / 2f),
                )
                // 0 轴：中轴竖线（涨跌的分界），画在底带高度内；彩段会盖住它的一半
                drawLine(
                    color = axisColor,
                    start = Offset(cx, cy - bandH / 2f + 1.dp.toPx()),
                    end = Offset(cx, cy + bandH / 2f - 1.dp.toPx()),
                    strokeWidth = 1.5.dp.toPx(),
                )
                if (hasBar) {
                    // 彩段：严格从中轴出发，涨向右、跌向左，长度 = 半宽 × 幅度占比。
                    // 粗细 5dp（底带 8dp 内嵌、上下各留 1.5dp 灰边）；Round cap 让靠中轴那端
                    // 收成圆头，和中轴竖线衔接自然，外侧端由圆点盖住。
                    val len = barFracHalf * size.width
                    val endX = if (isRise) cx + len else cx - len
                    drawLine(
                        color = accent,
                        start = Offset(cx, cy),
                        end = Offset(endX, cy),
                        strokeWidth = barDp.toPx(),
                        cap = StrokeCap.Round,
                    )
                    // 端点：直径 7dp > 底带高 6dp，上下各探出 0.5dp 仍能锁住位置
                    drawCircle(accent, 3.5.dp.toPx(), Offset(endX, cy))
                } else {
                    // 搁浅 / 基准价：中轴上一个灰点——价格没动
                    drawCircle(flatDotColor, 3.dp.toPx(), Offset(cx, cy))
                }
            }
        }
    }
}
