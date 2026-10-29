package com.llpop.koil.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

/**
 * KOiL 品牌色（对齐 Web 端视觉 token §14）：
 * - 主色 iOS 系统蓝 #0071E3（深色 #0A84FF）
 * - 背景 近白 hsl(40 22% 98%) / 深色 hsl(240 8% 5%)——Miuix 默认即接近，仅覆盖主色
 * - 柔光玻璃三档在组件层用 miuix-blur 实现（见 BlurBackground）
 */
object KOiLColors {
    val Primary = Color(0xFF0071E3)
    val PrimaryDark = Color(0xFF0A84FF)
    val PrimaryContainer = Color(0xFFE8F2FE)
    val OnPrimaryContainer = Color(0xFF004A99)
    /** v1.5.28：权限/异常提示色（红色系，浅深色下均醒目） */
    val Warning = Color(0xFFE5484D)
}

@Composable
fun KOiLTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    // 完全对齐 Mishka：useMonet=false 时零覆盖 Miuix 静态配色（Mishka 主题即如此）。
    // 官方色值（0.9.4 一致）：
    //   浅色 surface（页面/Scaffold 底色）=#F7F7F7、surfaceContainer（卡片）=#FFFFFF
    //   深色 surface=#000000（纯黑底）、surfaceContainer=#242424（深灰卡片）
    // 页面底色由 Miuix Scaffold 默认 containerColor=surface 提供，卡片用 surfaceContainer，
    // 无需任何 .copy 覆盖，仅替换主色即可还原 Mishka 观感。
    val colors = if (darkTheme) {
        darkColorScheme(primary = KOiLColors.PrimaryDark)
    } else {
        lightColorScheme(primary = KOiLColors.Primary)
    }
    MiuixTheme(colors = colors, content = content)
}
