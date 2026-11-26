package com.llpop.koil.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import com.llpop.koil.ui.component.blur.LocalBlurEnabled
import com.llpop.koil.ui.component.effect.KOiLGlassEdgeTier
import com.llpop.koil.ui.component.effect.rememberEffectiveGlassSpec
import com.llpop.koil.ui.component.effect.rememberKOiLGlassEdgeStroke
import com.llpop.koil.ui.theme.LocalKOiLDarkTheme
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.material.GlassShape
import top.yukonga.miuix.kmp.material.edgeStroke
import top.yukonga.miuix.kmp.material.isRuntimeShaderSupported

/**
 * 「这一处到底成不成玻璃」的统一门槛，与 [koilGlassPanelSurface] 内部同一判据：
 * 采得到底（backdrop 非空）+ 模糊总开关 + 玻璃规格启用 + 设备能画 RuntimeShader。
 * 分段控件、rail、自绘面都从这里取，避免各写四条而少一条。
 */
@Composable
fun rememberGlassActive(
    backdrop: Backdrop? = LocalWallpaperBackdrop.current ?: LocalPageBackdrop.current,
): Boolean {
    val spec = rememberEffectiveGlassSpec()
    return backdrop != null &&
        LocalBlurEnabled.current &&
        spec.enabled &&
        isRuntimeShaderSupported()
}

/** 选中指示块口径（暗/亮 × 静止/按下），分段控件与 rail 收起态共用一份，避免两处硬编码漂开。 */
object KOiLIndicatorAlpha {
    const val Dark = 0.133f
    const val DarkPressed = 0.26f
    const val Light = 0.06f
    const val LightPressed = 0.14f

    fun rest(isDark: Boolean): Float = if (isDark) Dark else Light
    fun pressed(isDark: Boolean): Float = if (isDark) DarkPressed else LightPressed
}

/**
 * 全 App 唯一的「采样玻璃面」原语：分组卡片、玻璃胶囊都从这一处拿底 + 边。
 *
 * 存在的理由：卡片（自绘分段卡）、胶囊（補錄 / 下拉 pill）原本各写一遍
 * `rememberEffectiveGlassSpec` + `textureBlur` + 描边 + 兜底判定，参数一改就漂。
 * 这里把「能不能玻璃」「底怎么采」「边画不画」一次定死，调用方只给形状与背景层。
 *
 * 返回 `null` 表示玻璃不成立（关模糊 / 无背景层 / 规格关闭 / 设备不支持 RuntimeShader），
 * 由调用方走自己的实色或薄纱兜底 —— 不同表面兜底口径不同（卡片是实色 surface，
 * 胶囊是 8% 薄纱），所以这里不替调用方决定兜底长相。
 *
 * @param drawEdge 只在整块面是单一轮廓时传 true。多段拼成的卡片逐段画边会在卡中间
 *   拉出一圈圈亮线，所以卡片只在「首段且末段」时传 true。
 * @param surfaceAlpha 整块（底 + 边）的透明度，仅供展开时要让位的胶囊类使用。
 */
@Composable
fun Modifier.koilGlassPanelSurface(
    shape: GlassShape,
    backdrop: Backdrop? = LocalWallpaperBackdrop.current ?: LocalPageBackdrop.current,
    drawEdge: Boolean = true,
    surfaceAlpha: Float = 1f,
): Modifier? {
    val spec = rememberEffectiveGlassSpec()
    val isDark = LocalKOiLDarkTheme.current
    val edgeStroke = rememberKOiLGlassEdgeStroke(isDark = isDark, spec = spec)
    if (backdrop == null) return null
    if (!rememberGlassActive(backdrop)) return null
    val showsEdge = drawEdge && spec.edgeTier != KOiLGlassEdgeTier.Disabled
    return then(
        remember(spec, isDark, backdrop, showsEdge, surfaceAlpha) {
            Modifier
                .graphicsLayer { alpha = surfaceAlpha }
                .textureBlur(
                    backdrop = backdrop,
                    shape = shape,
                    blurRadius = spec.blurRadius,
                    noiseCoefficient = spec.noiseCoefficient,
                    colors = BlurColors(
                        blendColors = spec.blendColors(isDark),
                        brightness = spec.brightness(isDark),
                        contrast = spec.contrast,
                        saturation = spec.saturation,
                    ),
                    enabled = true,
                )
                .then(
                    if (showsEdge) {
                        Modifier.edgeStroke(shape = shape, stroke = { edgeStroke.value })
                    } else {
                        Modifier
                    },
                )
        },
    )
}
