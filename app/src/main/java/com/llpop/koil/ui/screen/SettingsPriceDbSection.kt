package com.llpop.koil.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.llpop.koil.R
import com.llpop.koil.data.oil.OilPriceDbSyncer
import com.llpop.koil.data.oil.PriceDbOutcome
import com.llpop.koil.ui.component.KOiLCheckMark
import com.llpop.koil.ui.screen.settings.SettingsUiState
import com.llpop.koil.ui.screen.settings.SettingsViewModel
import com.llpop.koil.ui.theme.KOiLColors
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.ProgressIndicatorDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.util.Locale
import com.llpop.koil.util.AppClock



/**
 * 「历史油价数据库」卡片（M-07 拆分）：库状态、灌库/补灌进度、手动更新、自动更新开关。
 * 从 SettingsScreen 主函数整体移出，行为不变。
 */
@Composable
internal fun PriceDbSection(
    viewModel: SettingsViewModel,
    uiState: SettingsUiState,
    onOpenPriceDetail: () -> Unit,
) {
        // ===== 历史油价数据库（Web 版大卡片排版） =====
            val priceDbStatus by viewModel.priceDbStatus.collectAsStateWithLifecycle()
            val priceDbUpdating by viewModel.priceDbUpdating.collectAsStateWithLifecycle()
            // 补灌进行中每 3 秒刷一次进度（原为 1 秒）。ViewModel 只在库行数变化时重算，
            // 而"已覆盖直接跳过"的省份不写库 → 进度会停在某个数字不动，看起来像卡死。
            // 3 秒足够看出推进；ViewModel 侧另有防重入，单次查询超时也不会堆叠并发。
            LaunchedEffect(priceDbStatus.seedState) {
                if (priceDbStatus.seedState == OilPriceDbSyncer.SeedState.RUNNING) {
                    while (true) {
                        delay(3000)
                        viewModel.loadPriceDbStatus()
                    }
                }
            }
            // —— 这一行（倒计时 + 上次检查）共用一个时钟 ——
            // 自走：原来只在重组时算一次，停在设置页跨过零点不会翻成「已开」。
            // 只在该行有下一个窗口日时启动，睡到下一整分钟再更新（这条是 12sp 副标题，
            // 最短一档是「Y 分」，秒级 tick 只会每分钟重组 60 次而不改一个像素）。
            // 截到整分钟再赋值：否则 value 每次都是全精度新对象，相邻分钟文本一样的也照重组一次。
            // 页面不可见时（按 Home / 切后台）挂起，不在后台空转——绑 STARTED，不绑组合本身。
            val lifecycleOwner = LocalLifecycleOwner.current
            val tickNow: java.time.LocalDateTime? =
                if (priceDbStatus.nextAdjustWindowDate != null) {
                    produceState(
                        initialValue = AppClock.nowLocal().withSecond(0).withNano(0),
                        key1 = priceDbStatus.nextAdjustWindowDate,
                    ) {
                        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                            // 回到前台先对齐一次：STOPPED 期间 block 被取消，value 冻在离开前的时刻，
                            // 不先赋一次就得等下一整分钟，「已开」态会晚 0~60 秒才翻
                            value = AppClock.nowLocal().withSecond(0).withNano(0)
                            while (true) {
                                delay(60_000L - AppClock.millis() % 60_000L)
                                // 落在整分钟上：跨零点判定等价（deadline 就是 00:00:00）
                                value = AppClock.nowLocal().withSecond(0).withNano(0)
                            }
                        }
                    }.value
                } else null
            val nowLocal = tickNow ?: AppClock.nowLocal()
            val todayLocal = nowLocal.toLocalDate()

            // 上次检查时间：今天只给时刻、昨天加前缀、更早带日期
            // （恒定 MM-dd HH:mm 会让"今天刚查过"和"三天前查的"看起来一样）
            // 与倒计时同源取 nowLocal，避免同一行里两个时钟在零点附近各说各话
            val uiLocale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
            val lastCheckedText: String? = if (priceDbStatus.lastCheckedAt > 0) {
                val zone = java.time.ZoneId.systemDefault()
                val at = java.time.LocalDateTime.ofInstant(
                    java.time.Instant.ofEpochMilli(priceDbStatus.lastCheckedAt),
                    zone,
                )
                val dayDiff = java.time.temporal.ChronoUnit.DAYS.between(at.toLocalDate(), todayLocal)
                val hm = java.time.format.DateTimeFormatter.ofPattern("HH:mm", uiLocale).format(at)
                when (dayDiff) {
                    0L -> hm
                    1L -> stringResource(R.string.koil_pdb_yesterday, hm)
                    else -> java.time.format.DateTimeFormatter.ofPattern("MM-dd HH:mm", uiLocale)
                        .format(at)
                }
            } else null

            // 距下次窗口：窗口日 24:00 生效 → 目标时刻取「窗口日次日 00:00」
            // 这里直接产出整句（而不是只给时长再套模板）：窗口已经过去时那句话不能带
            // 「距下轮调价日」前缀，否则会念成「距下轮调价日 本期窗口已开」，自相矛盾。
            val countdownText: String? = run {
                val d = priceDbStatus.nextAdjustWindowDate ?: return@run null
                val target = runCatching { java.time.LocalDate.parse(d) }.getOrNull() ?: return@run null
                val deadline = target.plusDays(1).atStartOfDay()
                val now = nowLocal
                when {
                    // 生效时刻已过：数据源还没翻到下一个窗口日，别再挂「今晚 24:00」骗人
                    !now.isBefore(deadline) -> stringResource(R.string.koil_pdb_countdown_passed)
                    // 三语的「今晚 24:00 …」本身都是完整句，直接出；
                    // 套 countdown 模板会念成「Next price change in Price change tonight at 24:00」
                    target == todayLocal -> stringResource(R.string.koil_pdb_countdown_today)
                    else -> {
                        val totalMin = java.time.Duration.between(now, deadline)
                            .toMinutes().coerceAtLeast(0)
                        val days = totalMin / (24 * 60)
                        val hours = (totalMin % (24 * 60)) / 60
                        val mins = totalMin % 60
                        val dur = when {
                            days > 0 -> stringResource(R.string.koil_pdb_dur_dh, days.toInt(), hours.toInt())
                            hours > 0 -> stringResource(R.string.koil_pdb_dur_hm, hours.toInt(), mins.toInt())
                            else -> stringResource(R.string.koil_pdb_dur_m, mins.toInt())
                        }
                        // 生效日 = 窗口日 + 1（与下面 deadline、行 1 的 effectiveDate 同口径）。
                        // 直接给绝对日期，用户不用从「还有几天」自己换算哪天变价。
                        val effectiveShort = target.plusDays(1).toString().takeLast(5)
                        stringResource(R.string.koil_pdb_countdown, effectiveShort, dur)
                    }
                }
            }

            // 行 1 = 结论（最近一次窗口发生了什么）；涨跌额紧随价格，涨红跌绿
            val w1Short = priceDbStatus.adjustWindowDate?.takeLast(5)
            // 生效日由 ViewModel 算好（窗口日 + 1，与查库同口径），这里只取 MM-dd 展示。
            // 直接用窗口日会被读成"当天就调了"：11-12 的窗口实际 11-13 生效（真机验收 A3 的不符点）。
            val w1EffectiveShort: String = priceDbStatus.adjustWindowEffectiveDate?.takeLast(5) ?: w1Short ?: ""
            val strandedKeptDate = priceDbStatus.strandedKeptDate
            val strandedKeptPrice = priceDbStatus.strandedKeptPrice
            val delta = priceDbStatus.adjustWindowDelta?.takeIf { kotlin.math.abs(it) >= 0.005 }
            val line1Base = when {
                // 缺数据必须排在搁浅前面：库内没有该窗口记录 ≠ 发改委确认维持，
                // 两者共用搁浅文案会把"还没补灌"说成"官方搁浅"。
                w1Short != null && priceDbStatus.windowRecordMissing ->
                    stringResource(R.string.koil_pdb_line_missing, w1Short)
                w1Short != null && priceDbStatus.windowStranded && strandedKeptDate != null && strandedKeptPrice != null ->
                    stringResource(R.string.koil_pdb_line_stranded, w1Short, strandedKeptDate, strandedKeptPrice)
                w1Short != null && priceDbStatus.windowStranded ->
                    stringResource(R.string.koil_pdb_line_stranded_short, w1Short)
                w1Short != null && priceDbStatus.adjustWindowPrice != null ->
                    stringResource(R.string.koil_pdb_line_adjusted, w1EffectiveShort, priceDbStatus.adjustWindowPrice!!)
                w1Short != null -> stringResource(R.string.koil_pdb_line_missing, w1Short)
                else -> stringResource(R.string.koil_pdb_not_checked)
            }
            val line1 = buildAnnotatedString {
                append(line1Base)
                if (delta != null) {
                    append("  ")
                    withStyle(
                        SpanStyle(
                            color = if (delta > 0) PriceUpColor else PriceDownColor,
                            fontWeight = FontWeight.Medium,
                        )
                    ) {
                        append(
                            String.format(
                                java.util.Locale.US,
                                if (delta > 0) "↑%.2f" else "↓%.2f",
                                kotlin.math.abs(delta),
                            )
                        )
                    }
                }
            }

            Column(modifier = Modifier.padding(16.dp)) {
                // —— 标题行：数据库 + 版本徽章 + 明细入口 ——
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.koil_pdb_db_title_short),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Medium,
                        color = MiuixTheme.colorScheme.onBackground,
                    )
                    if (priceDbStatus.versionLabel != "V——") {
                        Spacer(Modifier.width(8.dp))
                        // 与云同步「已连接」徽章同款：主色字 + 12% 主色底（版本正常时恒为蓝）
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(KOiLColors.Primary.copy(alpha = 0.12f))
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                        ) {
                            Text(
                                text = priceDbStatus.versionLabel,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = KOiLColors.Primary,
                            )
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    // 点击区只在左侧和纵向留内距：右侧不留，箭头右缘与下方
                    // 「导出与备份 / 崩溃日志」等 SettingRow 行的箭头同在
                    // 「卡片边 - 16dp」一条线上，纵向对齐。
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onOpenPriceDetail() }
                            .padding(start = 10.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.koil_pdb_view_detail),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                        )
                        Spacer(Modifier.width(2.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                // —— 完整性缺口提示（补灌未覆盖完的省，红色警示）——
                // 需要 total > 0 —— 库完全为空时四宫格已是 0、"数据库(待检测)"，
                // 再叠一句"31 省未拉取完整"会在首装头几秒闪屏，且此时信息量重复。
                if (priceDbStatus.incompleteProvinceCount > 0 && priceDbStatus.total > 0) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = pluralStringResource(R.plurals.koil_pdb_incomplete, priceDbStatus.incompleteProvinceCount, priceDbStatus.incompleteProvinceCount),
                        fontSize = 12.sp,
                        color = Color(0xFFE53935),
                    )
                    // 按真实补灌状态分文案——不用恒定一句"后台补灌中，完成后自动刷新"，
                    // 重试预算用尽后没任何东西在跑，恒定文案会让用户对着假进度干等。
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = when (priceDbStatus.seedState) {
                            OilPriceDbSyncer.SeedState.RUNNING -> stringResource(
                                R.string.koil_pdb_seeding_progress,
                                priceDbStatus.seedDone,
                                priceDbStatus.seedTotal,
                            )
                            OilPriceDbSyncer.SeedState.WAITING -> stringResource(R.string.koil_pdb_seeding_waiting)
                            OilPriceDbSyncer.SeedState.GAVE_UP ->
                                if (priceDbStatus.seedFailedCount > 0) {
                                    pluralStringResource(R.plurals.koil_pdb_seeding_failed, priceDbStatus.seedFailedCount, priceDbStatus.seedFailedCount)
                                } else {
                                    stringResource(R.string.koil_pdb_seeding)
                                }
                            else -> stringResource(R.string.koil_pdb_seeding)
                        },
                        fontSize = 11.sp,
                        color = MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.8f),
                    )
                }
                Spacer(Modifier.height(12.dp))

                // —— 统计四宫格：每格独立灰底圆角块，格间 8dp ——
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val statBox = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.05f))
                        .padding(vertical = 12.dp)
                    StatQuadItem(
                        modifier = statBox,
                        icon = Icons.Filled.Place,
                        value = priceDbStatus.provinceCount.toString(),
                        label = stringResource(R.string.koil_pdb_provinces),
                    )
                    StatQuadItem(
                        modifier = statBox,
                        icon = Icons.Filled.LocalGasStation,
                        value = priceDbStatus.gradeCount.toString(),
                        label = stringResource(R.string.koil_pdb_grades),
                    )
                    StatQuadItem(
                        modifier = statBox,
                        icon = Icons.Filled.CalendarMonth,
                        value = priceDbStatus.adjustmentCount.toString(),
                        label = stringResource(R.string.koil_pdb_adjustments),
                    )
                    StatQuadItem(
                        modifier = statBox,
                        icon = Icons.Filled.Storage,
                        value = priceDbStatus.total.toString(),
                        label = stringResource(R.string.koil_pdb_records),
                    )
                }
                Spacer(Modifier.height(14.dp))

                // —— 行 1：结论（最近一次窗口调价 / 搁浅 / 尚未检测）——
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (priceDbStatus.windowStranded && w1Short != null) {
                        Box(
                            Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(PriceUpColor)
                        )
                        Spacer(Modifier.width(6.dp))
                    } else if (priceDbStatus.windowRecordMissing && w1Short != null) {
                        // 缺数据用中性灰点：不是异常，也不能用搁浅的红点暗示"官方维持"
                        Box(
                            Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.45f))
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        text = line1,
                        fontSize = 13.sp,
                        lineHeight = 17.sp,
                        color = MiuixTheme.colorScheme.onBackgroundVariant,
                    )
                }
                // —— 行 2：接下来（倒计时） + 数据新鲜度（上次检查）——
                if (countdownText != null || lastCheckedText != null) {
                    Spacer(Modifier.height(4.dp))
                    // FlowRow：空间不足时整段换行，文案不会被从中间折断
                    @OptIn(ExperimentalLayoutApi::class)
                    FlowRow(
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        if (countdownText != null) {
                            Text(
                                text = countdownText,
                                fontSize = 12.sp,
                                lineHeight = 15.sp,
                                color = MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.75f),
                            )
                        }
                        if (countdownText != null && lastCheckedText != null) {
                            Text(
                                text = "·",
                                fontSize = 12.sp,
                                lineHeight = 15.sp,
                                color = MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.75f),
                            )
                        }
                        if (lastCheckedText != null) {
                            Text(
                                text = stringResource(R.string.koil_pdb_checked, lastCheckedText),
                                fontSize = 12.sp,
                                lineHeight = 15.sp,
                                color = MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.75f),
                            )
                        }
                    }
                }
                // —— 行 3：上次核对结论（仅异常/跳过/存疑类显示）——
                // 光有「最近检查 21:44」看不出那次到底怎么了：是入库了、搁浅了、源滞后跳过，
                // 还是直接失败。但正常的「已入库/无变化/搁浅」由行 1 + 行 2 已表达，再念一遍
                // 只是噪音——所以按 presentation.tier 折叠：失败类琥珀色 ⚠，跳过/存疑类中性 ⓘ，
                // 正常类整行不显示。这样常态只有两行，出问题时才冒出第三行。
                val lastOutcome = priceDbStatus.lastOutcome
                val presentation = lastOutcome?.let { presentOutcome(it) }
                if (presentation != null && presentation.tier != OutcomeTier.HIDDEN && presentation.text != null) {
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (presentation.tier == OutcomeTier.WARNING) Icons.Filled.Warning else Icons.Filled.Info,
                            contentDescription = null,
                            tint = if (presentation.tier == OutcomeTier.WARNING) KOiLColors.OutcomeWarning
                                   else MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.55f),
                            modifier = Modifier.size(13.dp),
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text = presentation.text,
                            fontSize = 12.sp,
                            lineHeight = 15.sp,
                            color = if (presentation.tier == OutcomeTier.WARNING) KOiLColors.OutcomeWarning
                                   else MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.75f),
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))

                // —— 底部双按钮：左「自动更新」（状态色，非实心）/ 右「立即更新」（卡内唯一实心主色）——
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    val autoOn = uiState.priceDbAutoUpdate
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                if (autoOn) KOiLColors.Primary.copy(alpha = 0.14f)
                                else MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.08f)
                            )
                            .clickable(enabled = uiState.loaded) {
                                viewModel.setPriceDbAutoUpdate(!autoOn)
                            }
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.koil_pdb_auto_short),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (autoOn) KOiLColors.Primary else MiuixTheme.colorScheme.onBackgroundVariant,
                            maxLines = 1,
                        )
                        Spacer(Modifier.width(6.dp))
                        // 纯状态标记：点击由整个按钮驱动，这里只表达「开 / 关」
                        KOiLCheckMark(
                            checked = autoOn,
                            activeColor = KOiLColors.Primary,
                            inactiveColor = MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.55f),
                            // 13sp 按钮文字旁边取 16dp：视觉高度与文字齐平；20dp 会顶满 pill 高度、
                            // 显得比文字重一级
                            iconSize = 16.dp,
                        )
                    }
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(KOiLColors.Primary)
                            .clickable(enabled = !priceDbUpdating) { viewModel.checkPriceDbNow() }
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (priceDbUpdating) {
                            CircularProgressIndicator(
                                size = 14.dp,
                                strokeWidth = 2.dp,
                                colors = ProgressIndicatorDefaults.progressIndicatorColors(
                                    foregroundColor = Color.White,
                                    backgroundColor = Color.Transparent,
                                ),
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = stringResource(if (priceDbUpdating) R.string.koil_pdb_updating else R.string.koil_pdb_update_now),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White,
                            maxLines = 1,
                        )
                    }
                }

            }
}

/** 通知参数里的第 n 个整数（plurals 的 count 必须是 Int，而 args 是 List<Any>） */
internal fun List<Any>.intAt(index: Int): Int = (getOrNull(index) as? Int) ?: (getOrNull(index) as? Number)?.toInt() ?: 0

/** 把 ViewModel 给出的结果通知 key 翻译为当前语言文案 */
@Composable
internal fun formatNotice(key: String, args: List<Any>): String = when (key) {
    "pdb_update_failed" -> stringResource(R.string.koil_msg_pdb_update_failed, args.getOrElse(0) { "" })
    "pdb_busy" -> stringResource(R.string.koil_msg_pdb_busy)
    "pdb_update_ok" -> pluralStringResource(
        R.plurals.koil_msg_pdb_update_ok, args.intAt(0), args.getOrElse(0) { 0 },
    )
    "pdb_update_none" -> stringResource(R.string.koil_msg_pdb_update_none)
    "price_db_empty" -> stringResource(R.string.koil_msg_price_db_empty)
    "price_exported" -> pluralStringResource(
        R.plurals.koil_msg_price_exported, args.intAt(0), args.getOrElse(0) { 0 }, args.getOrElse(1) { "" },
    )
    "export_failed" -> stringResource(R.string.koil_msg_export_failed, args.getOrElse(0) { "" })
    "no_records" -> stringResource(R.string.koil_msg_no_records)
    "records_exported" -> pluralStringResource(
        R.plurals.koil_msg_records_exported, args.intAt(0), args.getOrElse(0) { 0 }, args.getOrElse(1) { "" },
    )
    "cannot_read" -> stringResource(R.string.koil_msg_cannot_read)
    "file_empty" -> stringResource(R.string.koil_msg_file_empty)
    "missing_columns" -> stringResource(R.string.koil_msg_missing_columns)
    // 带乱码的"缺列"其实是编码问题，别让用户去核对列名
    "missing_columns_encoding" -> stringResource(R.string.koil_msg_missing_columns_encoding)
    "parse_failed" -> stringResource(R.string.koil_msg_parse_failed, args.getOrElse(0) { "" })
    "import_done" -> stringResource(R.string.koil_msg_import_done, args.getOrElse(0) { 0 }, args.getOrElse(1) { 0 })
    "backup_no_records" -> stringResource(R.string.koil_msg_backup_no_records)
    "restore_done" -> pluralStringResource(
        R.plurals.koil_msg_restore_done, args.intAt(0), args.getOrElse(0) { 0 }, args.getOrElse(1) { 0 },
    )
    "restore_failed" -> stringResource(R.string.koil_msg_restore_failed, args.getOrElse(0) { "" })
    "no_records_backup" -> stringResource(R.string.koil_msg_no_records_backup)
    "backup_done" -> pluralStringResource(
        R.plurals.koil_msg_backup_done, args.intAt(0), args.getOrElse(0) { 0 }, args.getOrElse(1) { "" },
    )
    "backup_failed" -> stringResource(R.string.koil_msg_backup_failed, args.getOrElse(0) { "" })
    else -> key
}



/**
 * 「上次核对结论」行的展示级别与文案。
 *
 * 这一行的价值是「价格可能不是最新的」这类需要用户留意的状态；正常入库的结论由行 1
 * （最近一次调价的生效日/价格）和行 2（倒计时/检查时间）已经表达，再念一遍只是噪音。
 * 分级：失败类用警示色 + ⚠，跳过/存疑类用中性灰 + ⓘ，正常类整行不显示。
 */
internal enum class OutcomeTier { HIDDEN, NOTICE, WARNING }

/** 一次 when 同时决定「显示什么文案」与「用什么级别显示」，避免两份 15 分支 when 各自维护。 */
internal data class OutcomePresentation(val text: String?, val tier: OutcomeTier)

/**
 * 上次核对结论 → 文案 + 展示级别，一个 when 同时产出两者。
 *

 * `Busy` / `AutoUpdateOff` / `Throttled` 是 Syncer 里「还没走到写缓存就 return」的早退路径，
 * 永远进不了「上次核对结论」，给 null text + HIDDEN，不配谁也读不到的字符串。
 * 其余结论即使 tier 为 HIDDEN 也照样生成文案：这些 string 只在本函数被引用，
 * 若 HIDDEN 分支返回 null 会触发 lint 的 UnusedResources，反而破坏 0 告警。
 * 分支语义（尤其「搁浅」与「非窗口日维持」的区别）由 Syncer 决定，界面只负责措辞与是否显示。
 */
@Composable
internal fun presentOutcome(outcome: PriceDbOutcome): OutcomePresentation = when (outcome) {
    // 早退路径：不落盘、不显示，不引用任何文案资源
    PriceDbOutcome.Busy, PriceDbOutcome.AutoUpdateOff, is PriceDbOutcome.Throttled ->
        OutcomePresentation(null, OutcomeTier.HIDDEN)
    // 失败类：源挂了/省份空/检测异常——价格可能不是最新，必须醒目
    PriceDbOutcome.NoProvince ->
        OutcomePresentation(stringResource(R.string.koil_pdb_outcome_no_province), OutcomeTier.WARNING)
    is PriceDbOutcome.FetchFailed ->
        OutcomePresentation(stringResource(R.string.koil_pdb_outcome_fetch_failed, outcome.detail), OutcomeTier.WARNING)
    is PriceDbOutcome.Failed ->
        OutcomePresentation(stringResource(R.string.koil_pdb_outcome_failed, outcome.detail), OutcomeTier.WARNING)
    // 跳过/存疑类：数据源滞后、无法确认搁浅、窗口日 24h 后生效——中性提示
    is PriceDbOutcome.SourceLagging -> OutcomePresentation(
        if (outcome.repaired > 0) {
            pluralStringResource(R.plurals.koil_pdb_outcome_source_lagging_fixed, outcome.repaired, outcome.repaired)
        } else {
            stringResource(R.string.koil_pdb_outcome_source_lagging)
        },
        OutcomeTier.NOTICE,
    )
    is PriceDbOutcome.UnchangedUnconfirmed ->
        OutcomePresentation(stringResource(R.string.koil_pdb_outcome_unchanged_unconfirmed), OutcomeTier.NOTICE)
    is PriceDbOutcome.WindowDay ->
        OutcomePresentation(stringResource(R.string.koil_pdb_outcome_window_day, outcome.date), OutcomeTier.NOTICE)
    // 正常类：已入库/无变化/搁浅/维持等，行 1 + 行 2 已覆盖，不占行；文案仍生成以保留资源引用
    is PriceDbOutcome.Stranded ->
        OutcomePresentation(stringResource(R.string.koil_pdb_outcome_stranded, outcome.date), OutcomeTier.HIDDEN)
    is PriceDbOutcome.Unchanged ->
        OutcomePresentation(stringResource(R.string.koil_pdb_outcome_unchanged, outcome.date), OutcomeTier.HIDDEN)
    is PriceDbOutcome.RepairedOnly ->
        OutcomePresentation(stringResource(R.string.koil_pdb_outcome_repaired_only, outcome.repaired), OutcomeTier.HIDDEN)
    PriceDbOutcome.AlreadyAdjusted ->
        OutcomePresentation(stringResource(R.string.koil_pdb_outcome_already_adjusted), OutcomeTier.HIDDEN)
    is PriceDbOutcome.Updated -> OutcomePresentation(
        if (outcome.failedProvinces > 0) {
            stringResource(R.string.koil_pdb_outcome_updated_partial, outcome.records, outcome.failedProvinces)
        } else {
            stringResource(R.string.koil_pdb_outcome_updated, outcome.records)
        },
        OutcomeTier.HIDDEN,
    )
    PriceDbOutcome.NoNewRecords ->
        OutcomePresentation(stringResource(R.string.koil_pdb_outcome_no_new_records), OutcomeTier.HIDDEN)
}
