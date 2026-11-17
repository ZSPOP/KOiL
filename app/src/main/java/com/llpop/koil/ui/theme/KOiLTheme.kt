package com.llpop.koil.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
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
    /** 权限/密码等异常提示色（红色系，浅深色下均醒目） */
    val Warning = Color(0xFFE5484D)
    /**
     * 油价核验失败警示色（琥珀色）。
     *
     * 刻意与 [Warning] 的红色分开：红色在数据页表示「涨价」，若核验失败也用红，
     * 同一个 App 里红就同时承担「涨价」与「失败」两种语义。琥珀色同样醒目、色觉友好，
     * 又避开了涨跌语义。仅用于油价卡片「上次核对结论」的失败类，别处仍用 [Warning]。
     */
    val OutcomeWarning = Color(0xFFE8A33D)

    // ===== 涨跌（数据页 / 记录卡 / 油价详情）=====
    /** 涨价 / 上调（红）。原散落在 PriceUpColor、UpColor 等多处同名值 */
    val PriceUp = Color(0xFFE53935)
    /** 跌价 / 下调（绿） */
    val PriceDown = Color(0xFF43A047)

    // PriceUp 的红色 0xFFE53935 在仓库里还承担另外两种语义。同名不同义混用一个 token，
    // 以后想给"删除"换色就会顺手把"涨价"也改掉。值相同、语义分开，才是 token 该有的粒度。
    /** 危险操作（删除记录、删除确认）。与 PriceUp 同值，但语义是操作而非行情 */
    val Danger = Color(0xFFE53935)
    /** 提示/异常标记（红点、导入报错行、省份库不完整）。与 PriceUp 同值，语义是提醒 */
    val Alert = Color(0xFFE53935)

    // ===== 油耗好坏三档（记录卡 / 六指标）=====
    // 深浅两套分开是刻意的：深橙 0xFFE65100 落在深色页上对比不足，深色模式换浅一档。
    /** 偏高（差）：浅色模式 */
    val AttentionDay = Color(0xFFE65100)
    /** 偏高（差）：深色模式 */
    val AttentionNight = Color(0xFFFF8A65)
    /** 偏低（好）：浅色模式 */
    val GoodDay = Color(0xFF2E7D32)
    /** 偏低（好）：深色模式 */
    val GoodNight = Color(0xFF66BB6A)
    /** 居中档（既不好也不差，如预测提示） */
    val NeutralAmber = Color(0xFFF59E0B)

    // 记录卡左侧色条的**渐变深端**。取色条不是单色而是两段渐变（上浅下深），
    // 所以深端单独两个 token，别拿 AttentionDay 去替代——那会把渐变压成一块平色。
    /** 偏高色条渐变深端 */
    val AttentionDayDeep = Color(0xFFD84315)
    /** 居中色条渐变深端 */
    val NeutralAmberDeep = Color(0xFFD97706)

    // ===== 状态三色 + 10% 容器底 =====
    /** 错误 / 阻断 */
    val Error = Color(0xFFFF3B30)
    /** 错误容器底（10% alpha） */
    val ErrorContainer = Color(0x1AFF3B30)
    /** 警示 / 待确认 */
    val Caution = Color(0xFFFF9500)
    /** 警示容器底（10% alpha） */
    val CautionContainer = Color(0x1AFF9500)
    /** 通过 / 正常 */
    val Success = Color(0xFF34C759)
    /** 通过容器底（10% alpha） */
    val SuccessContainer = Color(0x1A34C759)
}

/**
 * 当前**实际生效**的主题明暗（用户设置 → 系统）。
 *
 * 组件不能用 `isSystemInDarkTheme()` 判断配色：主题是设置项（跟随系统/浅色/深色），
 * 系统浅色 + App 选深色时，读系统值会把卡片高光、四宫格底色画反（亮块落在纯黑页面上）。
 * 由 [KOiLTheme] 统一下发，组件一律读这里。
 */
val LocalKOiLDarkTheme = staticCompositionLocalOf { false }

@Composable
fun KOiLTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    // useMonet=false 时不覆盖 Miuix 静态配色。
    // 官方色值（0.9.4 一致）：
    //   浅色 surface（页面/Scaffold 底色）=#F7F7F7、surfaceContainer（卡片）=#FFFFFF
    //   深色 surface=#000000（纯黑底）、surfaceContainer=#242424（深灰卡片）
    // 页面底色由 Miuix Scaffold 默认 containerColor=surface 提供，卡片用 surfaceContainer，
    // 无需任何 .copy 覆盖，仅替换主色即可。
    val colors = if (darkTheme) {
        darkColorScheme(primary = KOiLColors.PrimaryDark)
    } else {
        lightColorScheme(primary = KOiLColors.Primary)
    }
    CompositionLocalProvider(LocalKOiLDarkTheme provides darkTheme) {
        MiuixTheme(colors = colors, content = content)
    }
}
