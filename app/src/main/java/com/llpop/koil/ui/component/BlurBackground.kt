package com.llpop.koil.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 卡片容器 —— 对齐 Mishka Card 语义：**普通不透明实色卡片**（surfaceContainer 底 +
 * 16dp 圆角 + 高光描边），不跑 textureBlur shader。
 *
 * 性能对齐 Mishka：页面滑动/切换时卡片是普通绘制，无模糊计算，保证丝滑；
 * 悬浮导航条/底栏仍由 bottomBarBackdrop 提供真实模糊（见 MainScreen）。
 *
 * 【纪律】术语统一：柔光玻璃（不使用"毛玻璃"）。
 */
@Composable
fun KOiLBlurBackground(
    modifier: Modifier = Modifier,
    blurRadius: Float = 24f,
    cornerRadius: Float = 24f,
    showHighlight: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = RoundedCornerShape(cornerRadius.dp)
    val dark = isSystemInDarkTheme()
    // 对齐 Mishka Card：不透明 surfaceContainer 实色底
    val cardColor = MiuixTheme.colorScheme.surfaceContainer
    val highlightColor = if (dark) {
        Color.White.copy(alpha = 0.08f)
    } else {
        Color.White.copy(alpha = 0.6f)
    }

    var base: Modifier = modifier.clip(shape)
    if (showHighlight && cornerRadius > 0f) {
        base = base.border(1.dp, highlightColor, shape)
    }
    Box(
        modifier = base.background(cardColor),
    ) {
        content()
    }
}
