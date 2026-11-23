package com.llpop.koil.ui.component.blur

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.material.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical as miuixOverScrollVertical

/**
 * 背景模糊扩展：开关、backdrop 录制、顶栏模糊形态，以及 BlurredBar 所需的默认模糊修饰符。
 */

/**
 * 模糊效果总开关：用户在设置里关掉后，所有 backdrop 传 null，
 * 玻璃/模糊组件按组件自身规则回落为不透明底色。
 */
val LocalBlurEnabled = compositionLocalOf { true }

/** 顶栏起始侧额外内缩（宽屏/侧栏场景由外层写入；手机为 0，等同无操作） */
val LocalTopBarStartInset = staticCompositionLocalOf<() -> Dp> { { 0.dp } }

@Composable
fun rememberBlurEnabled(): State<Boolean> =
    rememberUpdatedState(isRuntimeShaderSupported() && LocalBlurEnabled.current)

/**
 * 页面内容录制层。
 *
 * 这里**不再给录制层垫 surface 底色**：壁纸层已经提到 MainScreen 里（三个一级页容器透明），
 * 内容层若铺一层 surface，顶栏采「壁纸 + 内容」的合并层时壁纸会被整片盖成 surface 色
 * （深色主题下就是一片黑块）。与官方 GlassPage 一致 —— 那里的 backdrop 就是一个纯
 * `rememberLayerBackdrop()`，录制内容本身、透明处透出下面的壁纸。
 */
@Composable
fun rememberBlurBackdrop(enabled: Boolean = LocalBlurEnabled.current): LayerBackdrop? {
    if (!enabled || !isRuntimeShaderSupported()) return null
    return rememberLayerBackdrop()
}

@Composable
fun Modifier.defaultBlurEffect(
    backdrop: Backdrop,
): Modifier = this.textureBlur(
    backdrop = backdrop,
    shape = RectangleShape,
    blurRadius = 25f,
    colors = BlurColors(
        blendColors = listOf(
            BlendColorEntry(color = MiuixTheme.colorScheme.surface.copy(0.8f)),
        ),
    ),
)

/**
 * 顶栏滚动接线：把容器弹性 overscroll 偏移写回 [scrollBehavior]，
 * 顶栏的折叠/渐变带随之跟手（与参考实现同一套写法，升级 miuix 时无需改动）。
 */
@Composable
fun Modifier.overScrollVertical(scrollBehavior: top.yukonga.miuix.kmp.basic.ScrollBehavior): Modifier {
    val state = scrollBehavior.state
    // 回调跨组合复用：否则每次重组都会换一个新 lambda，modifier 变化会让滚动容器重建接线
    val onOffsetChanged: (Float) -> Unit = remember(state) {
        { state.overscrollOffset = it }
    }
    return miuixOverScrollVertical(onOffsetChanged = onOffsetChanged)
}
