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
import androidx.compose.ui.unit.dp
import com.llpop.koil.ui.component.effect.rememberEffectiveGlassSpec
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.material.GlassShape
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.SinkFeedback
import top.yukonga.miuix.kmp.utils.pressable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

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
    val capsuleShape = remember { GlassShape(CardCornerRadius) }
    // 底与边都走全 App 唯一原语（与分组卡片同一份采样参数、同一条描边）；
    // 不能玻璃时由这里给薄纱兜底 + 1dp 细线，轮廓不变。
    val panelSurface = Modifier.koilGlassPanelSurface(
        shape = capsuleShape,
        backdrop = backdrop,
        surfaceAlpha = surfaceAlpha,
    )
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
    val surface: Modifier = panelSurface ?: Modifier
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

    Row(
        modifier = modifier
            .then(surface)
            .then(clickableModifier)
            .padding(contentPadding),
        verticalAlignment = verticalAlignment,
        content = content,
    )
}
