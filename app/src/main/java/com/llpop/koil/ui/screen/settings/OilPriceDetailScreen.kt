package com.llpop.koil.ui.screen.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.sp
import com.llpop.koil.R
import com.llpop.koil.domain.logic.NumberFormat
import com.llpop.koil.ui.component.AnchoredDropdownColumn
import com.llpop.koil.ui.component.AnchoredDropdownItemRow
import com.llpop.koil.ui.component.AnchoredPopup
import com.llpop.koil.ui.component.CardSegment
import com.llpop.koil.ui.component.KOiLScreenScaffold
import com.llpop.koil.ui.component.gradeLabel
import com.llpop.koil.ui.component.provinceLabel
import com.llpop.koil.ui.theme.KOiLColors
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import org.koin.androidx.compose.koinViewModel
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.util.Locale

/** 油价上调色（涨红） / 下调色（跌绿） */
private val UpColor = Color(0xFFE53935)
private val DownColor = Color(0xFF43A047)

/**
 * 油价明细页：省份（下拉）/油号切换 → 今年累计涨跌汇总 + 价格走势图 + 调价时间线。
 * 由设置页「油價數據庫」卡片入口进入；返回手势/返回按钮回到设置页。
 */
private const val CONTENT_KEY = "oil-price-detail-content"

@Composable
fun OilPriceDetailScreen(
    onBack: () -> Unit,
    bottomPadding: androidx.compose.ui.unit.Dp = 0.dp,
    viewModel: OilPriceDetailViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    BackHandler(onBack = onBack)
    val surfaceColor = MiuixTheme.colorScheme.surface

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // —— 筛选区滚出屏幕后，浮出一条可点的胶囊条 ——
    // 阈值来源：filterBottomPx = 筛选区（pill 行 + 分段控件）底部在滚动内容坐标系里的 y；
    // topBarPx = 顶栏折叠后的高度（content 回调的 topPadding 随滚动递减，取历史最小值即折叠值）。
    // 筛选区被顶栏完全盖住 ⇔ scroll > filterBottomPx - topBarPx。
    // 两者都由布局测量驱动，不写死高度 → 换字号/语言、有无年份 pill 都不用改阈值。
    var filterBottomPx by remember { mutableFloatStateOf(0f) }
    var topBarPx by remember { mutableFloatStateOf(Float.MAX_VALUE) }
    // 判据与数据总览页一致：读 layoutInfo 里目标 item 的 offset，与顶栏高度比较。
    // content 是整页单 item（key = CONTENT_KEY），filterBottomPx 是筛选区底相对 item 顶的 y，
    // 二者相加即「筛选区底在视口中的位置」；≤ topBarPx 说明已被顶栏完全盖住 → 启用吸顶条。
    val showStickyFilter by remember {
        derivedStateOf {
            val contentInfo = listState.layoutInfo.visibleItemsInfo
                .firstOrNull { it.key == CONTENT_KEY }
            filterBottomPx > 0f &&
                topBarPx < Float.MAX_VALUE &&
                contentInfo != null &&
                contentInfo.offset + filterBottomPx <= topBarPx + 2f
        }
    }

    // 吸顶条上的下拉菜单状态（与页面内筛选区的菜单互不复用：两处锚点不同，同时只能开一个）
    var stickyProvinceExpanded by remember { mutableStateOf(false) }
    var stickyGradeExpanded by remember { mutableStateOf(false) }
    var stickyYearExpanded by remember { mutableStateOf(false) }
    // 同页面内 pill 的 350ms 去抖问题（Popup 因外部点击收起后，同一击又落到 pill 上 → 弹回）
    var stickyDismissAt by remember { mutableLongStateOf(0L) }
    fun toggleStickyMenu(expanded: Boolean, set: (Boolean) -> Unit) {
        val justDismissed = stickyDismissAt > 0 && System.currentTimeMillis() - stickyDismissAt < 350
        stickyDismissAt = 0L
        if (!justDismissed) set(!expanded)
    }

    KOiLScreenScaffold(
        title = stringResource(R.string.koil_pdb_detail_title),
        navigationIcon = {
            val layoutDirection = LocalLayoutDirection.current
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = MiuixIcons.Back,
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.onBackground,
                    modifier = Modifier.graphicsLayer {
                        scaleX = if (layoutDirection == LayoutDirection.Rtl) -1f else 1f
                    },
                )
            }
        },
        overlay = { backdrop, _, topPadding ->
            StickyFilterBar(
                visible = showStickyFilter,
                backdrop = backdrop,
                topPadding = topPadding,
                provinceText = provinceLabel(state.province),
                gradeText = gradeLabel(state.grade),
                yearText = if (state.years.isNotEmpty()) {
                    stringResource(R.string.koil_detail_year, state.year)
                } else {
                    null
                },
                provinces = state.provinces,
                selectedProvince = state.province,
                grades = state.grades,
                selectedGrade = state.grade,
                years = state.years,
                selectedYear = state.year,
                provinceExpanded = stickyProvinceExpanded,
                gradeExpanded = stickyGradeExpanded,
                yearExpanded = stickyYearExpanded,
                onToggleProvince = { toggleStickyMenu(stickyProvinceExpanded) { stickyProvinceExpanded = it } },
                onToggleGrade = { toggleStickyMenu(stickyGradeExpanded) { stickyGradeExpanded = it } },
                onToggleYear = { toggleStickyMenu(stickyYearExpanded) { stickyYearExpanded = it } },
                onDismissProvince = {
                    stickyProvinceExpanded = false
                    stickyDismissAt = System.currentTimeMillis()
                },
                onDismissGrade = {
                    stickyGradeExpanded = false
                    stickyDismissAt = System.currentTimeMillis()
                },
                onDismissYear = {
                    stickyYearExpanded = false
                    stickyDismissAt = System.currentTimeMillis()
                },
                onSelectProvince = {
                    stickyProvinceExpanded = false
                    viewModel.selectProvince(it)
                },
                onSelectGrade = {
                    stickyGradeExpanded = false
                    viewModel.selectGrade(it)
                },
                onSelectYear = {
                    stickyYearExpanded = false
                    viewModel.selectYear(it)
                },
                onBackToTop = { scope.launch { listState.animateScrollToItem(0) } },
            )
        },
    ) { _, topPadding, scrollBehavior, _ ->
        // 记录顶栏当前高度（随滚动折叠递减，取历史最小值即折叠后的高度）——供吸顶阈值使用。
        // 放在 SideEffect 里而不是组合体内直接赋值：避免「组合期间写状态、同帧又被 derivedStateOf 读到」
        // 引起的重复组合警告/来回翻转。
        SideEffect {
            if (topPadding.value < topBarPx) topBarPx = topPadding.value
        }
        // —— 明细页整体滚动容器（LazyColumn + contentPadding 页面骨架）——
        // 时间线仅 20 条左右，整页收在一个 item 里一次性渲染；单 item 下
        // firstVisibleItemScrollOffset 就等于总滚动距离，吸顶阈值判定与原 ScrollState 完全等价。
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            // 横向边距由四张卡的 outerHorizontalPadding(12dp) 承担
            contentPadding = PaddingValues(
                top = topPadding,
                bottom = bottomPadding,
            ),
        ) {
            item(key = CONTENT_KEY) {
            Column(modifier = Modifier.fillMaxWidth()) {
            Spacer(Modifier.height(4.dp))

            // —— 筛选区重排——
            // 行 1：省份 / 年份用下拉 pill（31 省、多年份是高基数项，若各占一行横滑 chips
            //       + 左右箭头，筛选区顶会占大半屏且 31 省里定位难）；下拉用自研 AnchoredPopup，
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
            // 包一层测量筛选区底部位置（吸顶阈值）；横向 12dp 与卡片外边距同值
            // （contentPadding 已不含横向，非卡内容自己补）
            Column(
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .onGloballyPositioned { coords ->
                        filterBottomPx = coords.positionInParent().y + coords.size.height
                    },
            ) {
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
                                System.currentTimeMillis() - provinceDismissAt < 350
                            provinceDismissAt = 0L
                            if (!justDismissed) provinceMenuExpanded = !provinceMenuExpanded
                        },
                    )
                    AnchoredPopup(
                        expanded = provinceMenuExpanded,
                        endAligned = false,
                        anchorBounds = provinceAnchor.value,
                        onDismissRequest = {
                            provinceMenuExpanded = false
                            provinceDismissAt = System.currentTimeMillis()
                        },
                        maxHeight = 380.dp,
                    ) {
                        AnchoredDropdownColumn {
                            provinceItems.forEachIndexed { idx, name ->
                                AnchoredDropdownItemRow(
                                    text = name,
                                    selected = state.provinces[idx] == state.province,
                                    isFirst = idx == 0,
                                    isLast = idx == provinceItems.lastIndex,
                                    onClick = {
                                        provinceMenuExpanded = false
                                        // 数据键是中文省名（DB 主键），这里只换显示名
                                        viewModel.selectProvince(state.provinces[idx])
                                    },
                                )
                            }
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
                                    System.currentTimeMillis() - yearDismissAt < 350
                                yearDismissAt = 0L
                                if (!justDismissed) yearMenuExpanded = !yearMenuExpanded
                            },
                        )
                        AnchoredPopup(
                            expanded = yearMenuExpanded,
                            anchorBounds = yearAnchor.value,
                            onDismissRequest = {
                                yearMenuExpanded = false
                                yearDismissAt = System.currentTimeMillis()
                            },
                        ) {
                            AnchoredDropdownColumn {
                                state.years.forEachIndexed { idx, y ->
                                    AnchoredDropdownItemRow(
                                        text = stringResource(R.string.koil_detail_year, y),
                                        selected = y == state.year,
                                        isFirst = idx == 0,
                                        isLast = idx == state.years.lastIndex,
                                        onClick = {
                                            yearMenuExpanded = false
                                            viewModel.selectYear(y)
                                        },
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Spacer(Modifier.width(1.dp))
                }
            }
            Spacer(Modifier.height(10.dp))

            // —— 油号分段控件（固定 4 项整行等宽；选中项蓝底白字）——
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.08f))
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                state.grades.forEach { g ->
                    val selected = g == state.grade
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(9.dp))
                            .background(if (selected) KOiLColors.Primary else Color.Transparent)
                            .clickable { viewModel.selectGrade(g) }
                            .padding(vertical = 9.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = gradeLabel(g),
                            fontSize = 12.sp,
                            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                            color = if (selected) Color.White else MiuixTheme.colorScheme.onBackground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            }
            if (state.years.isNotEmpty()) {
            } else {
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
                        fontSize = 14.sp,
                        color = MiuixTheme.colorScheme.onBackgroundVariant,
                    )
                }
            } else {
                // —— 涨跌汇总卡（卡片容器是 Box 布局，多个子项必须包 Column 防止重叠）——
                // 分两段：最新价与净涨跌 / 调价次数统计（段间不画线，靠行内距区分）
                CardSegment(
                    isFirst = true,
                    isLast = false,
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // —— 第一行：最新价格（前，大号突出）+ 今年净涨跌（后）——
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
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
                    }
                }
                CardSegment(
                    isFirst = false,
                    isLast = true,
                    outerBottomPadding = 12.dp,
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // —— 第二行：上调次数 / 下调次数 / 搁浅次数 ——
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
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
                ) {
                    PriceTrendCanvas(
                        items = state.items,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                    )
                }
                Spacer(Modifier.height(8.dp))

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
        }
    }
}

/**
 * 紧凑下拉 pill（筛选区行 1：省份 / 年份）。
 * 视觉对齐分段控件（surface 底 8% + 圆角 10dp），点击后由调用处的 AnchoredPopup 展开选择列表。
 */
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
            fontSize = 13.sp,
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

/**
 * 筛选区滚出屏幕后浮出的**可点**胶囊条。
 *
 * 与「顶栏副标题」的区别：副标题只能看，这条能改。三个胶囊各自带锚定下拉（复用页面内同一套
 * AnchoredPopup / AnchoredDropdownColumn），右侧圆形按钮回到顶部；滚回顶部、筛选区重新可见时
 * 自动收起（由 visible 驱动 AnimatedVisibility）。
 *
 * 位置在 KOiLScreenScaffold 的 overlay 槽位——backdrop 录制树之外，所以可以安全
 * textureBlur(backdrop) 采样整页内容，得到与顶栏一致的真毛玻璃（SDK<33 退化为 surface 0.82）。
 */
@Composable
private fun StickyFilterBar(
    visible: Boolean,
    backdrop: LayerBackdrop?,
    topPadding: Dp,
    provinceText: String,
    gradeText: String,
    yearText: String?,
    provinces: List<String>,
    selectedProvince: String,
    grades: List<String>,
    selectedGrade: String,
    years: List<String>,
    selectedYear: String,
    provinceExpanded: Boolean,
    gradeExpanded: Boolean,
    yearExpanded: Boolean,
    onToggleProvince: () -> Unit,
    onToggleGrade: () -> Unit,
    onToggleYear: () -> Unit,
    onDismissProvince: () -> Unit,
    onDismissGrade: () -> Unit,
    onDismissYear: () -> Unit,
    onSelectProvince: (String) -> Unit,
    onSelectGrade: (String) -> Unit,
    onSelectYear: (String) -> Unit,
    onBackToTop: () -> Unit,
) {
    val bd = backdrop
    // 吸顶条三个 pill 的窗口坐标（按锚点自动判向：左半左对齐、右半右对齐）
    val stickyProvinceAnchor = remember { mutableStateOf<IntRect?>(null) }
    val stickyGradeAnchor = remember { mutableStateOf<IntRect?>(null) }
    val stickyYearAnchor = remember { mutableStateOf<IntRect?>(null) }
    val canBlur = bd != null && isRuntimeShaderSupported()
    val barSurface = MiuixTheme.colorScheme.surface.copy(alpha = 0.82f)
    val lineColor = MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.12f)
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(140)) + slideInVertically(tween(200)) { -it },
        exit = fadeOut(tween(110)) + slideOutVertically(tween(150)) { -it },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = topPadding)
                .then(
                    if (canBlur) {
                        Modifier.textureBlur(
                            backdrop = bd,
                            shape = RectangleShape,
                            blurRadius = 25f,
                            colors = BlurColors(
                                blendColors = listOf(BlendColorEntry(color = barSurface)),
                            ),
                        )
                    } else {
                        Modifier.background(barSurface)
                    },
                )
                .drawBehind {
                    val y = size.height - 0.5.dp.toPx()
                    drawLine(
                        color = lineColor,
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 0.5.dp.toPx(),
                    )
                }
                .padding(horizontal = 12.dp, vertical = 7.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(modifier = Modifier.onGloballyPositioned { coordinates ->
                    val b = coordinates.boundsInWindow()
                    stickyProvinceAnchor.value = IntRect(b.left.toInt(), b.top.toInt(), b.right.toInt(), b.bottom.toInt())
                }) {
                    StickyPill(provinceText, provinceExpanded, onToggleProvince)
                    AnchoredPopup(
                        expanded = provinceExpanded,
                        endAligned = false,
                        anchorBounds = stickyProvinceAnchor.value,
                        onDismissRequest = onDismissProvince,
                        maxHeight = 380.dp,
                    ) {
                        AnchoredDropdownColumn {
                            provinces.forEachIndexed { idx, name ->
                                AnchoredDropdownItemRow(
                                    text = provinceLabel(name),
                                    selected = name == selectedProvince,
                                    isFirst = idx == 0,
                                    isLast = idx == provinces.lastIndex,
                                    onClick = { onSelectProvince(name) },
                                )
                            }
                        }
                    }
                }
                Box(modifier = Modifier.onGloballyPositioned { coordinates ->
                    val b = coordinates.boundsInWindow()
                    stickyGradeAnchor.value = IntRect(b.left.toInt(), b.top.toInt(), b.right.toInt(), b.bottom.toInt())
                }) {
                    StickyPill(gradeText, gradeExpanded, onToggleGrade)
                    AnchoredPopup(
                        expanded = gradeExpanded,
                        endAligned = false,
                        anchorBounds = stickyGradeAnchor.value,
                        onDismissRequest = onDismissGrade,
                    ) {
                        AnchoredDropdownColumn {
                            grades.forEachIndexed { idx, g ->
                                AnchoredDropdownItemRow(
                                    text = gradeLabel(g),
                                    selected = g == selectedGrade,
                                    isFirst = idx == 0,
                                    isLast = idx == grades.lastIndex,
                                    onClick = { onSelectGrade(g) },
                                )
                            }
                        }
                    }
                }
                if (yearText != null) {
                    Box(modifier = Modifier.onGloballyPositioned { coordinates ->
                        val b = coordinates.boundsInWindow()
                        stickyYearAnchor.value = IntRect(b.left.toInt(), b.top.toInt(), b.right.toInt(), b.bottom.toInt())
                    }) {
                        StickyPill(yearText, yearExpanded, onToggleYear)
                        AnchoredPopup(
                            expanded = yearExpanded,
                            endAligned = false,
                            anchorBounds = stickyYearAnchor.value,
                            onDismissRequest = onDismissYear,
                        ) {
                            AnchoredDropdownColumn {
                                years.forEachIndexed { idx, y ->
                                    AnchoredDropdownItemRow(
                                        text = stringResource(R.string.koil_detail_year, y),
                                        selected = y == selectedYear,
                                        isFirst = idx == 0,
                                        isLast = idx == years.lastIndex,
                                        onClick = { onSelectYear(y) },
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                // 回到顶部：与总览页一致的上箭头语义（列表本身不长，直接短动画回顶）
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.09f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onBackToTop,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowUp,
                        contentDescription = stringResource(R.string.koil_back_to_top),
                        tint = MiuixTheme.colorScheme.onBackgroundVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

/** 吸顶条上的紧凑胶囊（比页面内的 DropdownPill 更小一号，省高度） */
@Composable
private fun StickyPill(label: String, expanded: Boolean, onToggle: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(9.dp))
            .background(MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.09f))
            .clickable(onClick = onToggle)
            .padding(start = 10.dp, end = 7.dp, top = 5.dp, bottom = 5.dp),
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = MiuixTheme.colorScheme.onBackground,
            maxLines = 1,
            softWrap = false,
        )
        Spacer(Modifier.width(2.dp))
        Icon(
            imageVector = Icons.Filled.KeyboardArrowDown,
            contentDescription = null,
            tint = MiuixTheme.colorScheme.onBackgroundVariant,
            modifier = Modifier
                .size(16.dp)
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
            fontSize = 11.sp,
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
    val bubbleBg = MiuixTheme.colorScheme.surfaceContainer
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
            fontSize = 11.sp,
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
                        fontSize = 11.sp,
                        color = MiuixTheme.colorScheme.onBackgroundVariant,
                    )

                    diff == null -> Text(
                        text = stringResource(R.string.koil_pdb_first_base),
                        fontSize = 11.sp,
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
                        fontSize = 11.sp,
                        color = MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.8f),
                    )
                }
                if (isLatest) {
                    // 最新标签在「价格 ¥x + 涨跌额」的右侧（不在价格左侧，也不夹在两者之间）
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.koil_detail_latest),
                        fontSize = 10.sp,
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
