package com.llpop.koil.ui.component

import android.animation.ValueAnimator
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.glass.GlassDropdownPopup
import top.yukonga.miuix.kmp.glass.GlassPopupDefaults
import top.yukonga.miuix.kmp.glass.GlassPopupAnchor
import top.yukonga.miuix.kmp.glass.GlassPopupSizing
import top.yukonga.miuix.kmp.utils.MiuixPopupUtils.Companion.PopupLayout

/**
 * 官方玻璃下拉的挂载层：把 `GlassDropdownPopup` 挂到根 Scaffold 的全屏层上。
 *
 * 与官方 `GlassDropdownPreference` 内部完全同一条路径：
 * `PopupLayout(renderInRootScaffold = true)` → 全屏 `Box` → `GlassDropdownPopup`。
 * 因此面板能盖在玻璃弹窗之上、不被弹窗圆角裁剪；面板的定位（按 anchorBounds/anchor）、
 * 展开/收起动画、模糊、点外部关闭全部由官方组件自己完成，这里只负责挂载与卸载时机。
 *
 * [anchor] 传官方 `rememberGlassPopupAnchor()` 并把值文本挂上 `glassPopupAnchorValue` 时，
 * 面板改以「值文本」为锚点展开（横向贴值、纵向从值处铺开），值本身在展开期间淡出，
 * 与官方 `GlassDropdownPreference`（设置页下拉）完全同一套行为。
 */
@Composable
fun GlassPopupOverlay(
    show: Boolean,
    anchorBounds: Rect,
    backdrop: Backdrop?,
    anchor: GlassPopupAnchor? = null,
    onDismissRequest: () -> Unit,
    maxHeight: Dp = 360.dp,
    minWidth: Dp = 200.dp,
    modifier: Modifier = Modifier,
    // 传 null 用官方默认内容内边距；日历这类需要"整块区域自己吞手势"的场景传 0，
    // 内边距由内容自己加，避免面板边缘那圈仍然能被官方 verticalScroll 拖动
    contentPadding: androidx.compose.foundation.layout.PaddingValues? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val mounted: MutableState<Boolean> = remember { mutableStateOf(false) }
    LaunchedEffect(show) {
        if (show) {
            mounted.value = true
        } else if (mounted.value) {
            // 等官方收起动画播完再卸载宿主（面板自身退出约 150ms，留余量到 260ms）。
            // 缩放系数当场读：无障碍/开发者选项把动画调慢时不截断官方动画，
            // 调到 0（关闭动画）时立即卸载，不多挂一段空层。
            val animatorScale = ValueAnimator.getDurationScale().coerceAtLeast(0f)
            delay((260L * animatorScale).toLong())
            mounted.value = false
        }
    }
    if (!mounted.value) return
    PopupLayout(
        visible = mounted,
        enterTransition = EnterTransition.None,
        exitTransition = ExitTransition.None,
        enableWindowDim = false,
        enableBackHandler = false,
        renderInRootScaffold = true,
    ) {
        Box(Modifier.fillMaxSize()) {
            GlassDropdownPopup(
                show = show,
                onDismissRequest = onDismissRequest,
                anchorBounds = anchorBounds,
                anchor = anchor,
                backdrop = backdrop,
                modifier = modifier,
                sizing = GlassPopupSizing(
                    minWidth = minWidth,
                    maxHeight = maxHeight,
                ),
                contentPadding = contentPadding
                    ?: androidx.compose.foundation.layout.PaddingValues(
                        vertical = GlassPopupDefaults.ContentPaddingVertical,
                    ),
                content = content,
            )
        }
    }
}
