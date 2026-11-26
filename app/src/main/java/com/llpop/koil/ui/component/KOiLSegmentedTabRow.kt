package com.llpop.koil.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.llpop.koil.ui.component.blur.LocalBlurEnabled
import com.llpop.koil.ui.component.blur.rememberEmptyBackdrop
import com.llpop.koil.ui.component.effect.KOiLGlassEdgeTier
import com.llpop.koil.ui.component.effect.rememberEffectiveGlassSpec
import com.llpop.koil.ui.component.effect.rememberKOiLGlassEdgeStroke
import com.llpop.koil.ui.theme.LocalKOiLDarkTheme
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.glass.GlassSegmentedTabRow
import top.yukonga.miuix.kmp.glass.GlassSegmentedTabRowDefaults
import top.yukonga.miuix.kmp.material.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 全 App 唯一的分段控件：数据总览的范围筛选、油价明细的油号、导入预览的分类页签都用它。
 *
 * 存在的理由：分段控件一旦各处自己拼参数，材质、描边、按压手感就会各走各的 ——
 * 之前导入弹窗那套自研轨道就是这样漂出去的。这里把「采样底 + 描边 + 薄纱兜底」
 * 一次写死，页面只负责传标签、选中项和回调；动画（指示块双弹簧、跟手拖动、
 * 按下缩放与颜色）全部走官方 [GlassSegmentedTabRow] 内部那一套，各处必然一致。
 *
 * 玻璃口径：
 * - 能采到背景（壁纸层或页面录制层）、模糊开关开、规格启用、设备支持 RuntimeShader 时，
 *   轨道整条交给采样层去画（trackColor 透明），与分组卡片同一个观感；
 * - 否则退回 8% 薄纱轨道 + 16% 指示块 + 一条 1dp 细线，轮廓不变，不会出现一整块不透明灰。
 *
 * @param backdrop 采样目标，默认取壁纸层、回落到页面录制层；呼出层（弹窗）里也走这一条。
 * @param height 唯一允许按场景微调的尺寸参数 —— 弹窗里觉得高就调小，材质与动画不另设。
 */
@Composable
fun KOiLSegmentedTabRow(
    tabs: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    backdrop: Backdrop? = LocalWallpaperBackdrop.current ?: LocalPageBackdrop.current,
    height: Dp = GlassSegmentedTabRowDefaults.Height,
) {
    if (tabs.isEmpty()) return

    val colors = MiuixTheme.colorScheme
    val glassSpec = rememberEffectiveGlassSpec()
    val isDark = LocalKOiLDarkTheme.current
    val edgeStroke = rememberKOiLGlassEdgeStroke(isDark = isDark, spec = glassSpec)
    // 与分组卡片同一门槛：采得到底 + 模糊总开关 + 规格启用 + 设备能画 textureBlur
    val glassActive = rememberGlassActive(backdrop)
    val showsEdge = glassActive && glassSpec.edgeTier != KOiLGlassEdgeTier.Disabled
    val fallbackShape = GlassSegmentedTabRowDefaults.shape(height)

    GlassSegmentedTabRow(
        tabs = tabs,
        selectedIndex = selectedIndex,
        onSelect = onSelect,
        backdrop = backdrop ?: rememberEmptyBackdrop(),
        height = height,
        surfaceAlpha = if (glassActive) 1f else 0f,
        // 材质升起时整条透明，让采样层完全接管；静止态用薄纱而不是官方那个不透明的
        // resting 色（后者是为防内容穿透设计的，放在这里会比旁边的卡片实一大截）
        trackColor = if (glassActive) {
            Color.Transparent
        } else {
            colors.onBackgroundVariant.copy(alpha = 0.08f)
        },
        indicatorColor = if (glassActive) {
            GlassSegmentedTabRowDefaults.indicatorColor()
        } else {
            colors.onBackgroundVariant.copy(alpha = 0.16f)
        },
        stroke = if (showsEdge) edgeStroke.value else null,
        modifier = modifier.then(
            if (glassActive) {
                Modifier
            } else {
                Modifier.border(
                    BorderStroke(1.dp, colors.onSurface.copy(alpha = 0.06f)),
                    fallbackShape,
                )
            },
        ),
    )
}
