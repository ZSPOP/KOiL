package com.llpop.koil.ui.component

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * 当前是否画在玻璃表面上（由 [CardSegment] 在玻璃真正生效时 provide）。
 *
 * 用途：玻璃卡的底色是采样壁纸 + 混色得来，不是 surfaceContainer，按实色卡设计的交界颜色
 * （[top.yukonga.miuix.kmp.basic.DividerDefaults.DividerColor] 等）会失准——亮主题的浅灰线
 * 在明亮玻璃上几乎看不见，暗主题的灰线在深玻璃上发脏。子组件读这个 local 后换成
 * 「跟随 onSurface 的低 alpha 中性线」，才能和玻璃自身明暗同步。
 */
val LocalOnGlassSurface = staticCompositionLocalOf { false }
