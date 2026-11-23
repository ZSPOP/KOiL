package com.llpop.koil.ui.component.effect

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.material.EdgeStroke
import top.yukonga.miuix.kmp.material.EdgeStrokes

/**
 * 玻璃卡的描边档位：与官方 example `EdgeStrokeConfig.Container` 同名同义。
 */
enum class KOiLGlassEdgeTier {
    Disabled,
    Large,
    Medium,
    Small;

    fun resolve(isDark: Boolean): EdgeStroke? = when (this) {
        Disabled -> null
        Large -> if (isDark) EdgeStrokes.BigDark else EdgeStrokes.BigLight
        Medium -> if (isDark) EdgeStrokes.MiddleDark else EdgeStrokes.MiddleLight
        Small -> if (isDark) EdgeStrokes.SmallDark else EdgeStrokes.SmallLight
    }
}

/**
 * 全站玻璃卡的唯一参数源（对应官方 Texture Blur demo 的参数）。
 *
 * 改观感只需要动这里一处，所有接了玻璃的卡片会一起变；运行时覆盖 [LocalKOiLGlassSpec] 也能立刻生效。
 *
 * @param blurRadius 模糊半径（dp）。官方 demo = 40f；当前按观感调到 30f。
 * @param noiseCoefficient 采样噪声系数。官方默认 `BlurDefaults.NoiseCoefficient` = 0.0045f。
 * @param blendLight / blendDark 混色色标，当前为官方 `ColorBlendToken` 的 Colored Thin 档
 *   （`Colored Thick` 留在 [KOiLBlurTokens] 里可换回）。
 * @param edgeTier 描边档位，官方 demo 默认 Small Container。
 * @param tiltDriven 描边双光源是否随设备倾斜偏移（官方 demo 默认 true）。
 * @param brightnessLight / brightnessDark 磨砂面亮度，取值 [-1, 1]，0 为不变。按两档压暗程度分别取值，见 [brightness]。
 * @param contrast 对比度倍数，1 为不变；1.3 让玻璃面更脆、更像玻璃。
 * @param saturation 饱和度倍数，1 为不变；1.2 让透出的底色更活。
 */
@Immutable
data class KOiLGlassSpec(
    val enabled: Boolean = true,
    val blurRadius: Float = 30f,
    val noiseCoefficient: Float = 0.0045f,
    val blendLight: List<BlendColorEntry> = KOiLBlurTokens.coloredThin(false),
    val blendDark: List<BlendColorEntry> = KOiLBlurTokens.coloredThin(true),
    val edgeTier: KOiLGlassEdgeTier = KOiLGlassEdgeTier.Small,
    val tiltDriven: Boolean = true,
    val primarySensitivity: Float = 0.15f,
    val secondarySensitivity: Float = 0.12f,
    val brightnessLight: Float = 0.18f,
    val brightnessDark: Float = 0.16f,
    val contrast: Float = 1.3f,
    val saturation: Float = 1.2f,
) {
    fun blendColors(isDark: Boolean): List<BlendColorEntry> = if (isDark) blendDark else blendLight

    /**
     * 磨砂面亮度。按两档的压暗程度分别取值：亮色档两层（Overlay 60% 黑 + SoftLight 50% 黑）
     * 都在压暗，浅色底上会被压出一层灰，需要抬亮；暗色档（ColorBurn alpha 0.10 + PlusDarker 0.20）
     * 本身压得很轻，保持 0。
     */
    fun brightness(isDark: Boolean): Float = if (isDark) brightnessDark else brightnessLight
}

/** 玻璃卡参数作用域；默认即 [KOiLGlassSpec] 的官方默认值。 */
val LocalKOiLGlassSpec = compositionLocalOf { KOiLGlassSpec() }
