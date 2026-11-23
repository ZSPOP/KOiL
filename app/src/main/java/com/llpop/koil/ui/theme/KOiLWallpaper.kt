package com.llpop.koil.ui.theme

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * 动态壁纸开关：开启时页面底层跑 BgEffectBackground（官方 BgEffectConfig 三层流动色），
 * 关闭时底层就是 surface 纯色。只影响页面底层，不改顶栏与玻璃参数。
 */
val LocalWallpaperEnabled = staticCompositionLocalOf { true }

/**
 * 动态底色阶是否推进。每个 page 的脚手架里各画一份全屏流动底着色器，pager 又预载相邻页，
 * 静止时其实有 3 份在跑（当前页 + 前后预载页）。这里按可见性收口：只有屏幕上真能看见的
 * 页传 true，预载离屏页传 false —— 色彩停在上一次的相位、不每帧重画整屏着色器，
 * 滑到该页时由 LaunchedEffect 续上原相位（floor(stage)+1），不会跳色。
 */
val LocalWallpaperPlaying = compositionLocalOf { true }
