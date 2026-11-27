package com.llpop.koil.ui.theme

import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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

/**
 * 滚动闸门：各页列表处于拖拽/惯性滚动时置 `scrolling = true`。
 *
 * 动态壁纸那份全屏着色器是**常驻** 60fps 帧循环，滚动期间它和整页重录、玻璃模糊抢同一份
 * GPU 带宽。滚动是用户注意力全在内容上的少数场景，这里在滚动期间把壁纸的时间推进停掉
 * （动效相位不丢，停下后从原相位续上），松手立即恢复。
 *
 * 多页同时挂在 pager 里，所以用引用计数而不是直接赋值：任一页还在滚就算「滚动中」。
 *
 * 已知边界：根层 `HorizontalPager` 自身的拖拽**不上报**（MainScreen 不向闸门 report
 * `pagerState`）。pager 滑动是整页位移，不产生列表滚动回调，因此切换页面期间闸门不置位
 * （`scrolling` 保持 false）、壁纸继续推进——这是当前有意保留的行为。若将来需要覆盖，应在 pager 的 nestedScroll
 * 连接上补 `report(...)`，而不是在别处加第二套开关。
 */
@Stable
class WallpaperScrollGate {

    private val activeSources = mutableSetOf<Any>()

    var scrolling by mutableStateOf(false)
        private set

    fun report(source: Any, isScrolling: Boolean) {
        val changed = if (isScrolling) activeSources.add(source) else activeSources.remove(source)
        if (!changed) return
        val next = activeSources.isNotEmpty()
        if (next != scrolling) scrolling = next
    }
}

val LocalWallpaperScrollGate = compositionLocalOf<WallpaperScrollGate?> { null }
