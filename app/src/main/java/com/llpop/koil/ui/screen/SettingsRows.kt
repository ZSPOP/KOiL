package com.llpop.koil.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.llpop.koil.R
import com.llpop.koil.data.settings.AppSettings
import com.llpop.koil.data.sync.SyncConfig
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import com.llpop.koil.ui.theme.PagerGestureMode
import com.llpop.koil.ui.theme.FloatingBottomBarStyle
import com.llpop.koil.ui.theme.KOiLColors
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.NumberPicker
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.glass.GlassDialogButton
import top.yukonga.miuix.kmp.glass.GlassDialogButtonRole
import androidx.compose.runtime.mutableIntStateOf
import com.llpop.koil.ui.theme.KOiLType
import com.llpop.koil.ui.component.KOiLGlassDialog



internal val GRADES = listOf("92", "95", "98", "0")

/** 云同步的默认值统一在 com.llpop.koil.data.sync.SyncConfig 兜底，界面不再各存一份 */

@Composable
internal fun DataIoDialogItem(
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    dividerAbove: Boolean = false,
    insideMargin: PaddingValues = BasicComponentDefaults.InsideMargin,
) {
    // 弹窗内的动作行交回 ArrowPreference，不再自绘 0.5dp 分割线
    // （设置类页面无分割线，行与行靠 insideMargin 的 16dp 留白区分）。
    // 行节点横向吃满面板宽度（内容靠 insideMargin 内缩），按下高亮才会像
    // 设置页卡片里的行一样横向铺满、由外层按面板圆角裁切，而不是一块内缩的方角块
    ArrowPreference(
        title = title,
        summary = subtitle,
        insideMargin = insideMargin,
        startAction = icon?.let { vector ->
            {
                Icon(
                    imageVector = vector,
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(end = 16.dp),
                )
            }
        },
        onClick = onClick,
    )
}

@Composable
internal fun SettingRow(
    title: String,
    subtitle: String,
    value: String,
    onClick: () -> Unit,
    /**
     * 该行拉起的弹窗是否正开在它上面。为 true 时这一行保持按下高亮，
     * 让「我点的是这一行」在弹窗盖住页面之后仍有连续反馈。
     */
    holdDownState: Boolean = false,
    showArrow: Boolean = true,
    arrowIcon: androidx.compose.ui.graphics.vector.ImageVector =
        Icons.AutoMirrored.Filled.KeyboardArrowRight,
    badgeDot: Boolean = false,
    subtitleMaxLines: Int = 1,
) {
    // 行体交回 ArrowPreference（16dp insideMargin、主题字号、官方箭头），
    // value 放 endActions，红点作为提醒标记跟在 value 前。
    ArrowPreference(
        title = title,
        summary = subtitle.ifEmpty { null },
        onClick = onClick,
        holdDownState = holdDownState,
        endActions = {
            if (badgeDot) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(KOiLColors.Alert),
                )
                Spacer(Modifier.width(6.dp))
            }
            if (value.isNotEmpty()) {
                Text(
                    text = value,
                    fontSize = KOiLType.BodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
    )
}

/** 油价涨跌色（与明细页口径一致：涨红 / 跌绿） */
internal val PriceUpColor = KOiLColors.PriceUp

internal val PriceDownColor = KOiLColors.PriceDown

/** 四宫格统计项（蓝色图标 + 数值 + 标签，对齐截图样式） */
@Composable
internal fun StatQuadItem(
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String,
    label: String,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = KOiLColors.Primary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = value,
            fontSize = KOiLType.Display,
            fontWeight = FontWeight.Bold,
            color = MiuixTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            fontSize = KOiLType.CaptionLarge,
            color = MiuixTheme.colorScheme.onBackgroundVariant,
        )
    }
}

/**
 * 步进输入行：标题 + 副标题 + 右侧 [− 数字 单位 +]。
 * 交互：点击数字弹出官方 NumberPicker 滚轮（按 step 分档，选中即回写），± 按钮仍逐档步进并即时提交；
 * 滚轮确认走 onCommit（越界由 range 夹取），取消不改值。
 */
@Composable
internal fun StepperInputRow(
    title: String,
    summary: String,
    value: Int,
    range: IntRange,
    step: Int,
    suffix: String,
    horizontalPadding: Dp = 16.dp,
    onFieldTap: () -> Unit = {},
    onCommit: (Int) -> Unit,
) {
    val clamped = value.coerceIn(range)
    // 滚轮按步长分档：5..200 step 5 → 40 档，滚一格正好一个步长，不会滚出 5 的碎值
    val safeStep = step.coerceAtLeast(1)
    val tickCount = ((range.last - range.first) / safeStep).coerceAtLeast(0)
    val tickOfValue = ((clamped - range.first) / safeStep).coerceIn(0, tickCount)
    var pickerOpen by remember { mutableStateOf(false) }
    var pendingTick by remember { mutableStateOf(0) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = KOiLType.TitleLarge,
                fontWeight = FontWeight.Medium,
                color = MiuixTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = summary,
                fontSize = KOiLType.BodyLarge,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
        Spacer(Modifier.width(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            // − 蓝圆（22dp）——纯符号按钮，补读屏动作标签（TalkBack 否则只读出「−」不知用途）
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(KOiLColors.Primary)
                    .clickable(
                        onClickLabel = stringResource(R.string.koil_stepper_decrease, title),
                    ) {
                        onCommit((clamped - safeStep).coerceIn(range))
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text("−", fontSize = KOiLType.BodyLarge, fontWeight = FontWeight.Bold, color = Color.White)
            }
            Spacer(Modifier.width(8.dp))
            // 数值区：点击弹出官方 NumberPicker 滚轮（原「点数字原地键入」已被滚轮取代）
            Text(
                text = clamped.toString(),
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(
                        onClickLabel = stringResource(R.string.koil_stepper_picker, title),
                    ) {
                        pendingTick = tickOfValue
                        pickerOpen = true
                        onFieldTap()
                    }
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                fontSize = KOiLType.Title,
                fontWeight = FontWeight.Bold,
                color = MiuixTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = suffix,
                fontSize = KOiLType.BodyLarge,
                color = MiuixTheme.colorScheme.onBackgroundVariant,
            )
            Spacer(Modifier.width(8.dp))
            // + 蓝圆（22dp）——纯符号按钮，补读屏动作标签（TalkBack 否则只读出「+」不知用途）
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(KOiLColors.Primary)
                    .clickable(
                        onClickLabel = stringResource(R.string.koil_stepper_increase, title),
                    ) {
                        onCommit((clamped + safeStep).coerceIn(range))
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text("+", fontSize = KOiLType.BodyLarge, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }
    if (pickerOpen) {
        KOiLGlassDialog(
            show = true,
            onDismissRequest = { pickerOpen = false },
            title = title,
            summary = summary,
        ) {
            Column {
                NumberPicker(
                    value = pendingTick,
                    onValueChange = { pendingTick = it },
                    modifier = Modifier.fillMaxWidth(),
                    range = 0..tickCount,
                    label = { idx -> "${range.first + idx * safeStep}" },
                    visibleItemCount = 5,
                    wrapAround = false,
                )
                Spacer(Modifier.height(16.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    GlassDialogButton(
                        onClick = { pickerOpen = false },
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
                            onCommit((range.first + pendingTick * safeStep).coerceIn(range))
                            pickerOpen = false
                        },
                        modifier = Modifier.weight(1f),
                        role = GlassDialogButtonRole.Primary,
                    ) {
                        Text(
                            text = stringResource(R.string.koil_common_confirm),
                            style = MiuixTheme.textStyles.button,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    trailingContent: (@Composable () -> Unit)? = null,
    /** 标题右侧的行内内容（状态徽章）。传了它就走自绘标题，因为 SwitchPreference 的 title 只收 String */
    titleTrailing: (@Composable () -> Unit)? = null,
) {
    if (titleTrailing == null) {
        // 开关行交回 SwitchPreference，附加内容挂在开关前的 endActions。
        SwitchPreference(
            checked = checked,
            onCheckedChange = onCheckedChange,
            title = title,
            summary = subtitle,
            endActions = {
                if (trailingContent != null) {
                    trailingContent()
                    Spacer(Modifier.width(8.dp))
                }
            },
        )
        return
    }
    // 徽章要贴在标题旁边，只能自绘标题区：用 BasicComponent 的无 title 重载，
    // 内边距、最小高度、整行点击涟漪仍由 miuix 提供，字号与颜色照 BasicComponent
    // 内部排版取值（headline1 / body2），保证与上面那些 SwitchPreference 行一致。
    BasicComponent(
        endActions = {
            if (trailingContent != null) {
                trailingContent()
                Spacer(Modifier.width(8.dp))
            }
            // Switch 自带 `Modifier.toggleable`（miuix 0.9.4 Switch.kt：`onCheckedChange != null` 时挂
            // toggleable，否则只加 semantics），它会消费点击事件，所以点开关只走这一条回调，
            // 不会和下面整行的 onClick 各触发一次变成「闪两下」。
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        },
        onClick = { onCheckedChange(!checked) },
        onClickLabel = title,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                fontSize = MiuixTheme.textStyles.headline1.fontSize,
                fontWeight = FontWeight.Medium,
                color = MiuixTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.width(6.dp))
            titleTrailing()
        }
        Text(
            text = subtitle,
            fontSize = MiuixTheme.textStyles.body2.fontSize,
            // 与 SwitchPreference 内部 summary 用同一个 token：这一行自带徽章时走自绘，
            // 之前写的是 onBackgroundVariant，比同页其它副标题亮一档，看着像另一套配色。
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
}

@Composable
internal fun themeLabel(mode: AppSettings.ThemeMode) = when (mode) {
    AppSettings.ThemeMode.SYSTEM -> stringResource(R.string.koil_theme_system)
    AppSettings.ThemeMode.LIGHT -> stringResource(R.string.koil_theme_light)
    AppSettings.ThemeMode.DARK -> stringResource(R.string.koil_theme_dark)
}

@Composable
internal fun langLabel(lang: AppSettings.Language) = when (lang) {
    AppSettings.Language.SYSTEM -> stringResource(R.string.koil_lang_system)
    AppSettings.Language.ZH_CN -> stringResource(R.string.koil_lang_zh_cn)
    AppSettings.Language.ZH_TW -> stringResource(R.string.koil_lang_zh_tw)
    AppSettings.Language.EN -> stringResource(R.string.koil_lang_en)
}

@Composable
internal fun styleLabel(style: FloatingBottomBarStyle) = when (style) {
    FloatingBottomBarStyle.Miuix -> stringResource(R.string.koil_style_miuix)
    FloatingBottomBarStyle.IosLike -> stringResource(R.string.koil_style_ios)
}

@Composable
internal fun pagerGestureLabel(mode: PagerGestureMode) = when (mode) {
    PagerGestureMode.CrossAxis -> stringResource(R.string.koil_pager_gesture_cross_axis)
    PagerGestureMode.Native -> stringResource(R.string.koil_pager_gesture_native)
}
