package com.llpop.koil.ui.component

import androidx.compose.runtime.Composable
import top.yukonga.miuix.kmp.glass.GlassDialogDefaults
import top.yukonga.miuix.kmp.material.GlassMaterial

/**
 * 弹窗外观的唯一入口：材质。
 *
 * 底部弹层（[KOiLFormSheet] / [KOiLListSheet]）已改为直接吃官方 `GlassBottomSheetDefaults`，
 * 不再经这层转发（弹层唯一的自研项是描边）；这里只剩确认弹窗（[KOiLGlassDialog]）的材质，
 * 与弹层同档，视觉上不会出现两种玻璃。
 *
 * 这里刻意**不改任何参数**：直接用官方默认（`dialogGlass` + 0.72 不透明度，
 * tint 取 `surfaceContainerHigh`，遮罩用官方 `ScrimColor`）。官方这套参数是整块玻璃的质感来源，
 * 在上面叠自己的颜色层或加深遮罩，都会让浮层偏离官方素材的观感。
 */
object KOiLGlassOverlayMaterials {

    /** 确认弹窗材质：官方默认（0.72，tint = surfaceContainerHigh）。 */
    @Composable
    fun dialog(): GlassMaterial = GlassDialogDefaults.material()
}
