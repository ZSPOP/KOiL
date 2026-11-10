package com.llpop.koil.ui.component

import androidx.compose.runtime.compositionLocalOf
import top.yukonga.miuix.kmp.blur.Backdrop

/**
 * 全局柔光玻璃 backdrop。
 * MainScreen 根容器用 rememberLayerBackdrop 创建并通过此 CompositionLocal 下发，
 * KOiLBlurBackground 读取后用 textureBlur 实现真实玻璃模糊。
 * 为 null 时降级为半透明纯色。
 *
 * 注：页面卡片采用普通实色（对齐 Mishka，不读此 backdrop），
 * 该 Local 保留仅供兼容；底栏/悬浮条玻璃直接使用 MainScreen 的 bottomBarBackdrop。
 */
val LocalKOiLBackdrop = compositionLocalOf<Backdrop?> { null }
