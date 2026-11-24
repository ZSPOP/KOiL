package com.llpop.koil.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.unit.Density
import com.llpop.koil.ui.component.blur.LocalBlurEnabled
import top.yukonga.miuix.kmp.blur.Backdrop

/**
 * 标记当前组合位置的 `LocalPageBackdrop` 已经是「合成后的浮层采样源」。
 *
 * 弹层/弹窗内部会把合成结果重新下发成 `LocalPageBackdrop`（内容里的下拉、日历、
 * 玻璃卡片都吃这一份）。若内部再走一次 [rememberOverlayBackdrop]，页面层会被再压一次
 * 权重、壁纸层也会被再叠一遍 —— 结果既偏亮又偏淡。带上这个标记后直接复用已合成的那份。
 */
internal val LocalOverlayBackdropApplied = staticCompositionLocalOf { false }

/**
 * 底部弹层的页面层权重。
 *
 * 数据总览页的大块内容（实心主色按钮、成排小卡片）在 60dp 高斯下会被摊成整片色块，
 * 整页 1.0 采样挡不住；压下来之后底色连续、结构只剩一层。这是本工程对官方的显式偏离
 * （官方 `secondaryBackdrop` 是等权 1.0，没有这个旋钮）。
 */
internal const val SheetContentAlpha = 0.5f

/**
 * 确认弹窗的页面层权重。
 *
 * 弹窗的宿主页多半是设置页——六张 `surfaceContainer` 圆角卡片分组堆叠，卡与底的色差和
 * 圆角是一圈跨越上百像素的低频结构，60dp 高斯只会软化边缘、抹不掉形状。官方 demo 的
 * 宿主页是平铺行、没有卡片容器，所以官方参数搬到卡片页上必然透出矩形轮廓。
 * 压到一半之后卡片的层次还在、硬轮廓退成柔和过渡，同时保住“看得出背后有东西”。
 */
internal const val DialogContentAlpha = 0.5f

/**
 * 下拉面板的页面层权重。
 *
 * 下拉展开时面板正压在锚点那一行控件上（胶囊 pill、分段轨道、选中项亮底），这些是
 * 几十像素内的高对比结构，1.0 采样会让面板上半（叠在页面空白处）与下半（叠在控件上）
 * 分成明显两截，看起来像「只糊了一半」。与弹层同一口径压到一半，控件轮廓退成柔和过渡。
 */
internal const val PopupContentAlpha = 0.5f

/**
 * 浮层采样源：底色层（壁纸/表面色）与页面层按 [contentAlpha] 合成。
 *
 * 结构照官方 example `GlassPage.kt` 的 `secondaryBackdrop`：一个自定义 [Backdrop]，
 * 在同一个 `drawBackdrop` 里依次画多个录制层，坐标与残差全部透传首层。
 * 官方那处两层等权（等价于这里的 `contentAlpha = 1f`），这里多一个权重参数，
 * 让「底色连续、结构变淡」成为可能。
 *
 * [contentAlpha] 为 1f 时直接返回页面层，不生成合成对象（那条路径与官方逐位一致）。
 *
 * @param base 底色层（壁纸/表面色），全量绘制。
 * @param content 页面层（页面 UI），按 [contentAlpha] 叠上去。
 */
internal fun compositeBackdrop(
    base: Backdrop,
    content: Backdrop,
    contentAlpha: Float,
): Backdrop = object : Backdrop {
    override val isCoordinatesDependent: Boolean = true
    // 残差对齐取第一个绘制层，与官方 secondaryBackdrop 的口径一致
    override val offsetResidualX: Float get() = base.offsetResidualX
    override val offsetResidualY: Float get() = base.offsetResidualY

    override fun DrawScope.drawBackdrop(
        density: Density,
        coordinates: LayoutCoordinates?,
        layerBlock: (GraphicsLayerScope.() -> Unit)?,
        downscaleFactor: Float,
    ) {
        with(base) { drawBackdrop(density, coordinates, layerBlock, downscaleFactor) }
        with(content) {
            drawBackdrop(
                density = density,
                coordinates = coordinates,
                layerBlock = {
                    layerBlock?.invoke(this)
                    alpha *= contentAlpha
                },
                downscaleFactor = downscaleFactor,
            )
        }
    }
}

/**
 * 给官方组件自带浮层（如 `GlassDropdownPreference` 的下拉面板）用的页面采样源。
 *
 * 这些组件内部自己挂宿主，不走 [rememberOverlayBackdrop]，所以开关判断要单独给一份：
 * 关闭时返回 null，官方会落到不透明底，不会「开关关了、下拉还糊着」。
 */
@Composable
internal fun rememberPageBackdropForGlass(): Backdrop? = rememberPopupBackdrop()

/**
 * 下拉/浮层用的采样源：与弹层同一套合成，页面层按 [PopupContentAlpha] 压淡。
 */
@Composable
internal fun rememberPopupBackdrop(): Backdrop? = rememberOverlayBackdrop(PopupContentAlpha)

/**
 * 取浮层用的采样源：页面层 + 底色层按 [contentAlpha] 合成。
 *
 * 模糊开关关闭时返回 null —— 与 [rememberBlurBackdrop] 同一口径（「所有 backdrop 传 null」）。
 * 官方浮层在 `backdrop == null` 时由 `glassPanel` 走到 `fallback`：不透明 `containerColor`
 * 面板 + 原样保留的描边与阴影，这是官方唯一存在的「无玻璃」形态（`glassPanel` 不暴露
 * `enabled`，不可能出现「透光但没模糊」的中间态）。
 *
 * 宿主页面没有单独录制壁纸层时（例如关于页用本页自录层），退回页面层本身。
 */
@Composable
internal fun rememberOverlayBackdrop(contentAlpha: Float): Backdrop? {
    if (!LocalBlurEnabled.current) return null
    val page = LocalPageBackdrop.current
    // 已经处在弹层/弹窗内部：LocalPageBackdrop 就是合成结果，直接复用，避免二次压权重
    if (LocalOverlayBackdropApplied.current) return page
    val wallpaper = LocalWallpaperBackdrop.current
    return when {
        page == null -> wallpaper
        wallpaper == null || contentAlpha >= 1f -> page
        else -> remember(page, wallpaper, contentAlpha) {
            compositeBackdrop(wallpaper, page, contentAlpha)
        }
    }
}
