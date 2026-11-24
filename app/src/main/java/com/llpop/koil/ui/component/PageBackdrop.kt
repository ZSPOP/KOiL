package com.llpop.koil.ui.component

import androidx.compose.runtime.staticCompositionLocalOf
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.LayerBackdrop

/**
 * 页面 backdrop：玻璃弹窗（[KOiLGlassDialog]）与覆盖层用它采样页面内容做真模糊。
 * 下拉/菜单不再需要它——官方弹层组件自带 Popup 宿主。
 */
val LocalPageBackdrop = staticCompositionLocalOf<Backdrop?> { null }

/**
 * 壁纸/背景层 backdrop：只录制"页面底色"那一层，不录制列表与控件本身。
 * 官方 demo 的列表内玻璃标签行就是采样这种 backdrop——采样自己所在的录制层会崩。
 */
val LocalWallpaperBackdrop = staticCompositionLocalOf<LayerBackdrop?> { null }
