package com.llpop.koil.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import top.yukonga.miuix.kmp.basic.DividerDefaults
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 页面统一分隔线。
 *
 * 厚度与颜色只在组件内固定一次，调用处只决定内距（Modifier），
 * 不在各页面各自指定 thickness / color，避免同一个 App 里出现深浅不一的线。
 *
 * 画在玻璃卡内（[LocalOnGlassSurface] 为 true）时改用 onSurface 的低 alpha 中性线：
 * 主题的 dividerLine 是给实色 surfaceContainer 定的（亮色 #E0E0E0 / 暗色 #393939），
 * 铺在采样壁纸的玻璃上会一边看不见、一边发脏；onSurface 自带明暗自适应
 * （暗主题偏白、亮主题偏黑），随玻璃明度走最稳。
 */
@Composable
fun KOiLHorizontalDivider(modifier: Modifier = Modifier) {
    val onGlass = LocalOnGlassSurface.current
    HorizontalDivider(
        modifier = modifier,
        thickness = DividerDefaults.Thickness,
        color = if (onGlass) {
            MiuixTheme.colorScheme.onSurface.copy(alpha = 0.09f)
        } else {
            DividerDefaults.DividerColor
        },
    )
}
