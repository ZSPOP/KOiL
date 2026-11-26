package com.llpop.koil.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
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
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.SinkFeedback
import top.yukonga.miuix.kmp.utils.pressable

/**
 * 全 App 唯一的玻璃胶囊（数据页「補錄」、油价明细两个下拉 pill 都用它）。
 *
 * 之前每个胶囊各写一遍采样 + 描边 + 兜底，参数一改就漂；这里收成一处：
 * 同一个 [GlassShape]（[CardCornerRadius]）、同一套 [rememberEffectiveGlassSpec] 参数、
 * 同一条全站描边、同一条按下推送反馈。调用方只管内容和内距。
 *
 * @param surfaceAlpha 整块（底 + 边）的透明度，只给「展开时胶囊要让位给锚面」这类场景用。
 * @param fallbackColor 玻璃不可用（关模糊 / 无背景层 / 不支持 RuntimeShader）时的兜底底色。
 * @param onClick 传 null 则是纯展示胶囊，不参与点击。
 */
@Composable
fun KOiLGlassCapsule(
    modifier: Modifier = Modifier,
    backdrop: Backdrop? = LocalWallpaperBackdrop.current,
    surfaceAlpha: Float = 1f,
    fallbackColor: Color = MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.08f),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    verticalAlignment: Alignment.Vertical = Alignment.CenterVertically,
    onClick: (() -> Unit)? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val glassSpec = rememberEffectiveGlassSpec()
    val isDark = LocalKOiLDarkTheme.current
    val edgeStroke = rememberKOiLGlassEdgeStroke(isDark = isDark, spec = glassSpec)
    val capsuleShape = remember { GlassShape(CardCornerRadius) }
    val glassActive = backdrop != null &&
        LocalBlurEnabled.current &&
        glassSpec.enabled &&
        isRuntimeShaderSupported()
    val showsEdge = glassActive && glassSpec.edgeTier != KOiLGlassEdgeTier.Disabled

    val interaction = remember { MutableInteractionSource() }
    val pressFeedback = remember { SinkFeedback() }
    val clickableModifier = if (onClick != null) {
        Modifier
            .pressable(
                interactionSource = interaction,
                indication = pressFeedback,
                delay = null,
            )
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
    } else {
        Modifier
    }

    val surface: Modifier = if (glassActive) {
        Modifier
            .graphicsLayer { alpha = surfaceAlpha }
            .textureBlur(
                backdrop = backdrop!!,
                shape = capsuleShape,
                blurRadius = glassSpec.blurRadius,
                noiseCoefficient = glassSpec.noiseCoefficient,
                colors = BlurColors(
                    blendColors = glassSpec.blendColors(isDark),
                    brightness = glassSpec.brightness(isDark),
                    contrast = glassSpec.contrast,
                    saturation = glassSpec.saturation,
                ),
                enabled = true,
            )
            .then(
                if (showsEdge) {
                    Modifier.edgeStroke(shape = capsuleShape, stroke = { edgeStroke.value })
                } else {
                    Modifier
                },
            )
    } else {
        Modifier
            .clip(capsuleShape)
            .background(fallbackColor.copy(alpha = fallbackColor.alpha * surfaceAlpha))
            .then(
                if (surfaceAlpha >= 1f) {
                    Modifier.border(
                        BorderStroke(1.dp, MiuixTheme.colorScheme.onSurface.copy(alpha = 0.06f)),
                        capsuleShape,
                    )
                } else {
                    Modifier
                },
            )
    }

    Row(
        modifier = modifier
            .then(surface)
            .then(clickableModifier)
            .padding(contentPadding),
        verticalAlignment = verticalAlignment,
        content = content,
    )
}
