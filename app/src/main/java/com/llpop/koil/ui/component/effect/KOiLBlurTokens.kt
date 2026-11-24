package com.llpop.koil.ui.component.effect

import androidx.compose.ui.graphics.Color
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurBlendMode

/**
 * 磨砂混色 token：与 miuix 官方 example 的 ColorBlendToken 同值。
 * 当前默认档为官方「Colored Thin」（Colored_Thin_Light / Colored_Thin_Dark）。
 */
object KOiLBlurTokens {
    private val ColoredThinLight = listOf(
        BlendColorEntry(Color(0x991C1C1C), BlurBlendMode.Overlay),
        BlendColorEntry(Color(0x802B2B2B), BlurBlendMode.SoftLight),
    )

    private val ColoredThinDark = listOf(
        BlendColorEntry(Color(0x1A9C9C9C), BlurBlendMode.ColorBurn),
        BlendColorEntry(Color(0x337A7A7A), BlurBlendMode.PlusDarker),
    )

    private val ColoredThickLight = listOf(
        BlendColorEntry(Color(0xE6BDBDBD), BlurBlendMode.Overlay),
        BlendColorEntry(Color(0x992B2B2B), BlurBlendMode.ColorDodge),
        BlendColorEntry(Color(0x339C9C9C), BlurBlendMode.SrcOver),
    )

    private val ColoredThickDark = listOf(
        BlendColorEntry(Color(0x667A7A7A), BlurBlendMode.ColorBurn),
        BlendColorEntry(Color(0x33747474), BlurBlendMode.Overlay),
        BlendColorEntry(Color(0x322B2B2B), BlurBlendMode.SrcOver),
    )

    // 官方 miuix-material `GlassMaterials.puredThinGlass(isDark)` 的两套材质：
    // 顶栏按钮（buttonMaterial）默认就用它，直接给的是「纯色底」上的口径。
    // 浅色档三层全在提亮（PlusDarker 5% 黑 → SoftLight 60% 白 → HardLight 40% 白），
    // 深色档三层在压暗 + 提灰（Luminosity/Overlay 把黑底推成 #6E 左右的玻璃面）。
    private val PuredThinLight = listOf(
        BlendColorEntry(Color(0x05000000), BlurBlendMode.PlusDarker),
        BlendColorEntry(Color(0x99FFFFFF), BlurBlendMode.SoftLight),
        BlendColorEntry(Color(0x66FFFFFF), BlurBlendMode.HardLight),
    )

    private val PuredThinDark = listOf(
        BlendColorEntry(Color(0x1A000000), BlurBlendMode.PlusDarker),
        BlendColorEntry(Color(0x66565656), BlurBlendMode.Luminosity),
        BlendColorEntry(Color(0x993F3F3F), BlurBlendMode.Overlay),
    )

    /** 官方 puredThinGlass 的模糊半径（20dp），纯色底口径固定用它。 */
    const val PURED_THIN_BLUR_RADIUS = 20f

    /** 官方 `GlassMaterials.puredThinGlass(isDark)` 的色层。 */
    fun puredThin(isDark: Boolean): List<BlendColorEntry> =
        if (isDark) PuredThinDark else PuredThinLight

    /** 官方「Colored Thin」档。 */
    fun coloredThin(isDark: Boolean): List<BlendColorEntry> =
        if (isDark) ColoredThinDark else ColoredThinLight

    /** 官方「Colored Thick」档（留作切换用）。 */
    fun coloredThick(isDark: Boolean): List<BlendColorEntry> =
        if (isDark) ColoredThickDark else ColoredThickLight
}
