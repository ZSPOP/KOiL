package com.llpop.koil.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import top.yukonga.miuix.kmp.blur.Backdrop
import com.llpop.koil.ui.component.blur.defaultBlurEffect
import com.llpop.koil.ui.component.blur.rememberBlurEnabled

/**
 * 顶栏模糊包装：
 * 玻璃感由顶栏组件自身的渐进模糊承担；需 [forceTextureBlur] 时才额外铺一层均匀高斯。
 * 模糊不可用（开关关闭 / SDK<33 / 无 RuntimeShader）时 backdrop 为空，退化为普通内容，不包模糊层。
 */
@Composable
fun BlurredBar(
    backdrop: Backdrop?,
    blurActive: Boolean = rememberBlurEnabled().value,
    forceTextureBlur: Boolean = false,
    content: @Composable () -> Unit,
) {
    val drawBlur = blurActive && backdrop != null && forceTextureBlur
    CompositionLocalProvider(LocalTopAppBarBackdrop provides backdrop) {
        Box(
            modifier = if (drawBlur) {
                Modifier.defaultBlurEffect(backdrop)
            } else {
                Modifier
            },
        ) {
            content()
        }
    }
}
