package com.llpop.koil.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import com.llpop.koil.domain.logic.NumberFormat
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 油号选择框里那一行值：`92# · ¥7.89/L` / `0# · ¥7.89/L`。
 *
 * 首页录入卡与补录/编辑弹窗原来各写一套（首页是「油号 中黑 + 价格 小字灰」两段，
 * 弹窗是「油号 · 价格」整段粗体，且柴油在弹窗里显示成 `0#`）。收成一处后两边同源。
 *
 * 油号沿用输入框正文的字号（`MiuixTheme.textStyles.main`，17sp），价格降一档作为次要信息：
 * 半宽字段里整段 17sp 粗体放不下（会被裁掉尾部的价格）。
 */
@Composable
@ReadOnlyComposable
internal fun gradeValueLabel(grade: String, price: Double?): AnnotatedString {
    // 统一只显示标号：`92#` / `0#`。原先柴油写成 `0# 柴油`，比汽油标号长一截，
    // 半宽字段里会把后面的价格挤没（卡片、明细、摘要本来就都显示 `0#`）。
    val label = "$grade#"
    val priceText = price?.let { "¥${NumberFormat.measure(it)}/L" } ?: "¥--/L"
    val mainSize = MiuixTheme.textStyles.main.fontSize
    val onSurface = MiuixTheme.colorScheme.onBackground
    // 分隔点与价格沿用油号同一套字号/字重/颜色，整行观感一致
    return buildAnnotatedString {
        withStyle(SpanStyle(fontSize = mainSize, fontWeight = FontWeight.Bold, color = onSurface)) {
            append(label)
            append(" · $priceText")
        }
    }
}

/**
 * 输入框 / 选择框共用的表面底色，与 `GlassTextFieldDefaults` 的私有默认实现同公式：
 * 深色 `onBackground` 36/255 白、浅色 15/255 白。
 *
 * 官方那个 `defaultBackgroundColor()` 是 private，项目里自己算一遍；
 * 否则选择框用 `secondaryContainer`、输入框用玻璃默认色，同屏并排时是两种灰。
 */
@Composable
@ReadOnlyComposable
internal fun fieldSurfaceColor(): Color {
    val scheme = MiuixTheme.colorScheme
    val isDark = scheme.background.luminance() < 0.5f
    return scheme.onBackground.copy(alpha = if (isDark) 36f / 255f else 15f / 255f)
}
