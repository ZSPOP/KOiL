package com.llpop.koil.ui.component

import androidx.compose.runtime.compositionLocalOf

/** 顶栏标题的整页透明度（搜索栏/弹出层过渡用，按需读取）；默认恒为 1f */
val LocalTopAppBarTitleAlpha = compositionLocalOf<() -> Float> { { 1f } }
