package com.llpop.koil.ui.component

import androidx.compose.runtime.Composable
import top.yukonga.miuix.kmp.glass.GlassBottomSheetDefaults
import top.yukonga.miuix.kmp.glass.GlassDialogDefaults
import top.yukonga.miuix.kmp.material.GlassMaterial

/**
 * 浮层外观的唯一入口：材质 + 遮罩。
 *
 * 底部弹层（[KOiLFormSheet] / [KOiLListSheet]）与确认弹窗（[KOiLGlassDialog]）都从这里取值，
 * 不再各自写参数。收拢成一处后，「所有浮层同步」由结构保证，而不是靠人工对齐。
 *
 * 这里刻意**不改任何参数**：两者直接用官方默认（`dialogGlass` + 0.72 不透明度，
 * tint 分别取 `surfaceContainer` / `surfaceContainerHigh`，遮罩用官方 `ScrimColor`）。
 * 官方这套参数是整块玻璃的质感来源，在上面叠自己的颜色层或加深遮罩，
 * 都会让浮层偏离官方素材的观感。
 */
object KOiLGlassOverlayMaterials {

    /** 底部弹层材质：官方默认（0.72，tint = surfaceContainer）。 */
    @Composable
    fun sheet(): GlassMaterial = GlassBottomSheetDefaults.material()

    /** 确认弹窗材质：官方默认（0.72，tint = surfaceContainerHigh）。 */
    @Composable
    fun dialog(): GlassMaterial = GlassDialogDefaults.material()
}
