package com.llpop.koil.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.ProgressiveBlur
import top.yukonga.miuix.kmp.blur.progressiveTextureBlur
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.llpop.koil.ui.component.blur.LocalTopBarBlurStyle
import com.llpop.koil.ui.component.blur.TopBarBlurStyle
import com.llpop.koil.ui.component.blur.defaultBlurEffect
import com.llpop.koil.ui.component.blur.rememberBlurEnabled

/**
 * 顶栏模糊包装：
 * - Gaussian：默认不额外铺层，玻璃感由顶栏组件自身的渐进模糊承担；需 [forceTextureBlur] 时才铺均匀高斯
 * - Progressive：顶部最强、底部锐利的渐进模糊（progressiveTextureBlur）
 * 模糊不可用（开关关闭 / SDK<33 / 无 RuntimeShader）时 backdrop 为空，退化为普通内容，不包模糊层。
 */
@Composable
fun BlurredBar(
    backdrop: LayerBackdrop?,
    blurActive: Boolean = rememberBlurEnabled().value,
    blurStyle: TopBarBlurStyle = LocalTopBarBlurStyle.current,
    forceTextureBlur: Boolean = false,
    content: @Composable () -> Unit,
) {
    val drawBlur = blurActive && backdrop != null && forceTextureBlur
    val progressive = blurStyle == TopBarBlurStyle.Progressive
    CompositionLocalProvider(LocalTopAppBarBackdrop provides backdrop) {
        Box(
            modifier = if (drawBlur && !progressive) {
                Modifier.defaultBlurEffect(backdrop)
            } else {
                Modifier
            },
        ) {
            if (drawBlur && progressive) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .progressiveTextureBlur(
                            backdrop = backdrop,
                            shape = RectangleShape,
                            gradient = ProgressiveBlur.Top.copy(curve = 2.2f),
                            blurRadius = 10f,
                            colors = BlurColors(
                                blendColors = listOf(
                                    BlendColorEntry(color = MiuixTheme.colorScheme.surface.copy(alpha = 0.3f)),
                                ),
                            ),
                        ),
                )
            }
            content()
        }
    }
}
