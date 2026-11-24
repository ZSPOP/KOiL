package com.llpop.koil.ui.component

import androidx.compose.runtime.compositionLocalOf
import top.yukonga.miuix.kmp.blur.Backdrop

/** 顶栏采样用的 backdrop：由 BlurredBar 提供，顶栏组件从这里取，不写死在调用点 */
val LocalTopAppBarBackdrop = compositionLocalOf<Backdrop?> { null }
