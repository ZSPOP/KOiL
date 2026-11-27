package com.llpop.koil.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.CapturedAnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.fillMaxHeight
import com.llpop.koil.R
import com.llpop.koil.ui.component.CardSegment
import com.llpop.koil.ui.component.KOiLHorizontalDivider
import com.llpop.koil.domain.logic.ConsumptionStatus
import com.llpop.koil.domain.logic.NumberFormat
import com.llpop.koil.domain.logic.RecordWithDerived
import com.llpop.koil.ui.theme.KOiLColors
import kotlin.math.abs
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.glass.GlassIconButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import com.llpop.koil.ui.theme.KOiLType

// ===== 三段式记录卡（对齐 Web） =====
@Composable
internal fun RecordCard(
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
        isHigh -> KOiLColors.AttentionDay
        record.consumptionStatus == ConsumptionStatus.LOW -> KOiLColors.NeutralAmber
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
        CardSegment(
            isFirst = true,
            isLast = true,
            outerBottomPadding = 0.dp,
            // 与各页分组卡同一套全站玻璃参数（LocalKOiLGlassSpec）；
            // 模糊/壁纸任一关闭或设备不支持 RuntimeShader 时自动回落实色
            glass = true,
            // 点击/长按由卡片本体承担（官方 Card 语义：手势在背景之内、内容之外），
            // 这样 Sink 下沉覆盖整段玻璃；内容层不再挂自己的 combinedClickable，
            // 否则内层先消费、Sink 收不到 press，表现为"点了有反应但没反馈"。
            pressFeedbackType = PressFeedbackType.Sink,
            onClick = { if (showMoreMenu) onMoreMenu(record) else expanded = !expanded },
            onLongPress = { if (!showMoreMenu) onMoreMenu(record) },
        ) {
            // CardSegment 内容槽是 ColumnScope；异常竖条要 matchParentSize 叠在内容上，
            // 因此显式包一层 Box 承接原来的 BoxScope 布局（列表项间距由调用处 padding(bottom) 给）。
            Box {
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
                                        if (isHigh) KOiLColors.AttentionDay else KOiLColors.NeutralAmber,
                                        if (isHigh) KOiLColors.AttentionDayDeep else KOiLColors.NeutralAmberDeep,
                                    )
                                )
                            )
                            .align(Alignment.CenterStart),
                    )
                }
            }
            // 手势已上移到 CardSegment 的 onClick/onLongPress（配合 pressFeedbackType = Sink）；
            // 这里再挂 clickable 会内层先消费，导致外层 Sink 收不到 press，等于没有按压反馈。
            Column(
                modifier = Modifier
                    .padding(start = if (isAbnormal) 16.dp else 16.dp, end = 16.dp, top = 13.dp, bottom = 13.dp),
            ) {
                // —— 顶行：日期链（新日期 ← 旧日期 + 天数胶囊） ····· 右侧实付金额 ——
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = md(r.reoilDate),
                        fontSize = KOiLType.Title,
                        fontWeight = FontWeight.Bold,
                        color = MiuixTheme.colorScheme.onBackground,
                    )
                    if (prevRecord != null) {
                        Spacer(Modifier.width(4.dp))
                        // 用文字字形「←」，与里程行的「→」、环比的「↑」同一套字体风格
                        Text(
                            text = "←",
                            // 旧日期缩到 11sp 后，箭头同步缩小配套
                            fontSize = KOiLType.BodySmall,
                            color = MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.6f),
                        )
                        Spacer(Modifier.width(3.dp))
                        Text(
                            text = md(prevRecord.record.reoilDate),
                            // 旧日期弱化为 11sp 次要信息（用户定稿）
                            fontSize = KOiLType.CaptionLarge,
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
                        fontSize = KOiLType.Title,
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
                                fontSize = KOiLType.CaptionLarge,
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
                                fontSize = KOiLType.CaptionLarge,
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
                            fontSize = KOiLType.Body,
                            fontWeight = FontWeight.SemiBold,
                            color = MiuixTheme.colorScheme.onBackground,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                }

                // —— 油耗区（不设灰底，用淡分割线分组；数字左对齐、单价右对齐到内容线）——
                if (record.oilConsumption != null && record.spanDistance != null) {
                    KOiLHorizontalDivider(Modifier.padding(vertical = 10.dp))
                    // 深色主题下 2E7D32/E65100 对比度不足，按背景亮度切换亮色变体
                    val darkBg = MiuixTheme.colorScheme.background.luminance() < 0.5f
                    // 短途区间数字失真（标"短途参考"），环比 delta 不计算不显示——与异常判定口径一致
                    val delta = if (record.shortInterval) null else deltaText(
                        record.oilConsumption,
                        prevRecord?.oilConsumption,
                        lowerBetter = true,
                        goodColor = if (darkBg) KOiLColors.GoodNight else KOiLColors.GoodDay,
                        badColor = if (darkBg) KOiLColors.AttentionNight else KOiLColors.AttentionDay,
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
                            fontSize = KOiLType.Hero,
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
                                fontSize = KOiLType.Caption,
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
                                    fontSize = KOiLType.BodySmall,
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
                                    fontSize = KOiLType.BodySmall,
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
                                fontSize = KOiLType.Caption,
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
                                fontSize = KOiLType.BodySmall,
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
                    // 两类 PENDING 分开说：半箱（没加满）与同点加油（加满了但区间里程为 0，不闭合、
                    // 锚点不动）。四种文案 = 两类 × 是否已被后续满箱吸收；并入段是短途时再标一句
                    // 「短途参考」——那段数字在满箱卡片上本来就标了，这里不标会显得它很可信。
                    Spacer(Modifier.height(9.dp))
                    val settledDate = record.settledByDate
                    val settledValue = record.settledConsumption
                    val settledShort = record.settledShortInterval
                    val samePoint = r.isFullTank
                    val text = if (settledDate != null && settledValue != null) {
                        stringResource(
                            when {
                                samePoint && settledShort -> R.string.koil_ov_same_point_merged_short
                                samePoint -> R.string.koil_ov_same_point_merged
                                settledShort -> R.string.koil_ov_pending_merged_short
                                else -> R.string.koil_ov_pending_merged
                            },
                            settledDate.takeLast(5),
                            NumberFormat.measure(settledValue),
                        )
                    } else if (samePoint) {
                        // 同点加油仍未被吸收：累计同样保留着，等下一条正常里程的满箱一次结算
                        stringResource(
                            R.string.koil_ov_same_point_settle,
                            record.pendingCount,
                            NumberFormat.fixed2(record.pendingOil),
                            NumberFormat.fixed2(record.pendingActual ?: 0.0),
                        )
                    } else {
                        // 仍待结算：显示自锚点以来的累计（不显示 km——
                        // 区间最终里程要等结算才确定，提前显示易与结算后的油耗区间混淆）
                        stringResource(
                            R.string.koil_ov_pending_settle,
                            record.pendingCount,
                            NumberFormat.fixed2(record.pendingOil),
                            NumberFormat.fixed2(record.pendingActual ?: 0.0),
                        )
                    }
                    HintLine(text)
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
                // 不用默认 expandIn/expandOut：它们带缩放成分，玻璃卡会"先缩再长"，
                // 与 Sink 松手回弹叠在一起显得黏滞。改成纯纵向展开 + 淡入，节奏更利落。
                CapturedAnimatedVisibility(
                    visible = expanded,
                    enter = expandVertically(
                        animationSpec = tween(240, easing = FastOutSlowInEasing),
                        expandFrom = Alignment.Top,
                    ) + fadeIn(animationSpec = tween(120)),
                    // fadeOut 与 shrinkVertically 同长：若淡出更短，最后一段会「内容已透明、
                    // 卡片还在空缩」，观感像内容先消失（第二轮评审 P3-1）。
                    exit = shrinkVertically(
                        animationSpec = tween(200, easing = FastOutSlowInEasing),
                        shrinkTowards = Alignment.Top,
                    ) + fadeOut(animationSpec = tween(200)),
                ) {
                    Column {
                        val darkBg = MiuixTheme.colorScheme.background.luminance() < 0.5f
                        val goodColor = if (darkBg) KOiLColors.GoodNight else KOiLColors.GoodDay
                        val badColor = if (darkBg) KOiLColors.AttentionNight else KOiLColors.AttentionDay
                        if (recentAvg != null && record.oilConsumption != null) {
                            Spacer(Modifier.height(10.dp))
                            val cur = record.oilConsumption
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
                                fontSize = KOiLType.CaptionLarge,
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
}

/** 展开明细的单行对比条（标签 + 比例条 + 数值） */
@Composable
internal fun DetailBarRow(
    label: String,
    value: String,
    valueColor: Color,
    fraction: Float,
    fillColor: Color,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            fontSize = KOiLType.CaptionLarge,
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
            fontSize = KOiLType.BodySmall,
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
internal fun HintLine(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.10f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("ℹ", fontSize = KOiLType.Body, color = MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.7f))
            Spacer(Modifier.width(6.dp))
            Text(
                text = text,
                fontSize = KOiLType.CaptionLarge,
                color = MiuixTheme.colorScheme.onBackgroundVariant,
            )
        }
    }
}

/**
 * 长按弹出的圆形操作按钮（玻璃圆 + 图标 + 文字标签）。
 *
 * 原来是主色/危险色的实心大圆 + 白字，压在玻璃页上像两块塑料片。现在直接走官方
 * [GlassIconButton]：真采样 backdrop（页面列表内容层）做折射，材质、描边、阴影、
 * 按下时内容 alpha 渐暗和按压叠加层都由框架统一给，与顶栏/回顶按钮同一套玻璃语言。
 * 语义（编辑/删除）仍留在图标与文字的颜色上，不靠整块实色喊。
 *
 * backdrop 为 null（模糊开关关闭 / 机型不支持 RuntimeShader）时，官方组件自动回落成
 * 填充 + 描边，视觉和玻璃态一致只是不折射。
 */
@Composable
internal fun LongPressCircleAction(
    icon: ImageVector,
    label: String,
    color: Color,
    backdrop: Backdrop?,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        GlassIconButton(
            onClick = onClick,
            backdrop = backdrop,
            size = 54.dp,
        ) {
            Icon(icon, contentDescription = label, tint = color, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = label,
            fontSize = KOiLType.BodySmall,
            fontWeight = FontWeight.Medium,
            color = color,
        )
    }
}

internal fun RecordWithDerived.isLow(): Boolean = consumptionStatus == ConsumptionStatus.LOW

/** 环比 delta 结果（文本 + 好坏颜色，渲染时按颜色展示，对齐 Web 绿涨/橙跌语义） */
internal data class DeltaText(val text: String, val color: Color)

/** 环比 delta 文本（对齐 Web：↓0.26 (4.0%)；里程整数指标 digits=0 → ↓35 (5.7%)，不显示 35.00） */
internal fun deltaText(
    cur: Double?,
    prev: Double?,
    lowerBetter: Boolean,
    goodColor: Color = KOiLColors.GoodDay,
    badColor: Color = KOiLColors.AttentionDay,
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
internal fun Pill(
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
internal fun md(date: String): String {
    val parts = date.split("-")
    val month = parts.getOrNull(1)?.toIntOrNull()
    val day = parts.getOrNull(2)?.toIntOrNull()
    return if (parts.size == 3 && month != null && day != null)
        stringResource(R.string.koil_ov_md_date, month, day)
    else date
}
