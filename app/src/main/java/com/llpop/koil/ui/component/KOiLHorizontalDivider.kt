package com.llpop.koil.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import top.yukonga.miuix.kmp.basic.DividerDefaults
import top.yukonga.miuix.kmp.basic.HorizontalDivider

/**
 * 页面统一分隔线。
 *
 * 厚度与颜色只在组件内固定一次，调用处只决定内距（Modifier），
 * 不在各页面各自指定 thickness / color，避免同一个 App 里出现深浅不一的线。
 */
@Composable
fun KOiLHorizontalDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier,
        thickness = DividerDefaults.Thickness,
        color = DividerDefaults.DividerColor,
    )
}
