package com.llpop.koil.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.llpop.koil.ui.component.blur.LocalBlurEnabled
import com.llpop.koil.ui.component.effect.KOiLGlassEdgeTier
import com.llpop.koil.ui.component.effect.LocalKOiLGlassSpec
import com.llpop.koil.ui.component.effect.KOiLBlurTokens
import com.llpop.koil.ui.theme.LocalWallpaperEnabled
import com.llpop.koil.ui.component.effect.rememberKOiLGlassEdgeStroke
import com.llpop.koil.ui.theme.LocalKOiLDarkTheme
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.material.EdgeStroke
import top.yukonga.miuix.kmp.material.GlassShape
import top.yukonga.miuix.kmp.material.edgeStroke
import top.yukonga.miuix.kmp.material.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.squircle.squircleSurface
import top.yukonga.miuix.kmp.theme.LocalContentColor
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 一段独立 lazy item，靠分角 squircle 背景与相邻段拼成一张视觉连续的卡片：首段圆顶角、
 * 末段圆底角、中间段无圆角、段间严丝合缝。把整卡拆成多段后 LazyColumn 只组合/绘制可见段。
 *
 * 卡片语义（surfaceContainer 底 / onSurfaceContainer 内容色 / 16.dp 圆角）：
 * 有圆角的段用 [squircleSurface]（fill+clip，把 clickable 涟漪裁进圆角）；无圆角的中间段用纯 [background]（省一个 offscreen layer）。
 *
 * @param topCornerRadius 顶角半径，默认按 [isFirst] 推导；可覆写。
 * @param bottomCornerRadius 底角半径，默认按 [isLast] 推导；传动画值可让展开/收起时圆角平滑过渡而非突变。
 * @param insidePadding 段内留白（同 Card 的 `insideMargin` 语义）。
 */
@Composable
fun CardSegment(
    isFirst: Boolean,
    isLast: Boolean,
    modifier: Modifier = Modifier,
    color: Color = MiuixTheme.colorScheme.surfaceContainer,
    contentColor: Color = MiuixTheme.colorScheme.onSurfaceContainer,
    cornerRadius: Dp = 16.dp,
    topCornerRadius: Dp = if (isFirst) cornerRadius else 0.dp,
    bottomCornerRadius: Dp = if (isLast) cornerRadius else 0.dp,
    outerHorizontalPadding: Dp = 12.dp,
    outerTopPadding: Dp = 0.dp,
    outerBottomPadding: Dp = 0.dp,
    insidePadding: PaddingValues = PaddingValues(0.dp),
    /**
     * 是否做成官方式磨砂玻璃段：采样页面壁纸层（与内容层是兄弟，不会自采样）。
     * 半径 / 混色 / 噪声 / 描边档位 / 倾斜驱动 / 亮度统一由 [LocalKOiLGlassSpec] 给。
     */
    glass: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    // 玻璃段走官方 GlassShape（CornerBasedShape + smoothing = 连续角）：项目实色卡用
    // squircleSurface 的超椭圆，玻璃段统一到同一轮廓；同时 edgeStroke 要求 CornerBasedShape，
    // 且 GlassShape 的 smoothing 会被描边着色器采用（普通 RoundedCornerShape 只有 smoothing 0）。
    val glassShape = remember(topCornerRadius, bottomCornerRadius) {
        GlassShape(topCornerRadius, topCornerRadius, bottomCornerRadius, bottomCornerRadius)
    }
    val baseSpec = LocalKOiLGlassSpec.current
    // 底是纯色（动态壁纸关）时切官方 `puredThinGlass(isDark)` 口径：
    // ColoredThin 那套是给彩色壁纸调的（浅色档两层压暗 60%/50%，纯 #F7F7F7 底上会压成灰块，
    // brightness 0.18 补不回来）。puredThinGlass 是官方唯一针对纯色底给的材质，
    // 浅色档本身就在提亮，纯色底上直接呈现官方玻璃观感。
    val solidBackground = !LocalWallpaperEnabled.current
    val glassSpec = if (solidBackground) {
        remember(baseSpec) {
            baseSpec.copy(
                blurRadius = KOiLBlurTokens.PURED_THIN_BLUR_RADIUS,
                blendLight = KOiLBlurTokens.puredThin(false),
                blendDark = KOiLBlurTokens.puredThin(true),
                brightnessLight = 0f,
                brightnessDark = 0f,
                contrast = 1f,
                saturation = 1f,
            )
        }
    } else {
        baseSpec
    }
    val isDark = LocalKOiLDarkTheme.current
    // 玻璃只看「模糊总开关」：关掉动态壁纸只是底层不再流动，壁纸层仍是一块 surface 纯色，
    // 采样它得到的是一层薄纱 + 描边，玻璃照样成立（这是设计意图，不要把两个开关绑在一起）。
    // 只有当模糊总开关关掉、或老系统/GPU 不支持 RuntimeShader（textureBlur 画不出来，
    // 库内不做兜底）时才回落实色。
    val glassBackdrop = if (
        glass &&
        glassSpec.enabled &&
        LocalBlurEnabled.current &&
        isRuntimeShaderSupported()
    ) {
        LocalWallpaperBackdrop.current
    } else {
        null
    }
    // 官方描边：基准按档位取，双光源随设备倾斜偏移（绘制阶段读取，不触发重组）。
    // 只在「整卡就这一段」时画，多段逐段画会在卡片中间拉出一圈圈亮线。
    val edgeStrokeProvider: (() -> EdgeStroke?)? = if (
        glassBackdrop == null ||
        glassSpec.edgeTier == KOiLGlassEdgeTier.Disabled ||
        !(isFirst && isLast)
    ) {
        null
    } else {
        rememberKOiLGlassEdgeStroke(isDark = isDark, spec = glassSpec)
            .let { strokeState -> remember(strokeState) { { strokeState.value } } }
    }
    val background = when {
        // 毛玻璃段：不再自绘 surface（那层实色会把采样结果盖掉），交给 textureBlur 采样背景
        glassBackdrop != null -> Modifier
            .textureBlur(
                backdrop = glassBackdrop,
                shape = glassShape,
                blurRadius = glassSpec.blurRadius,
                noiseCoefficient = glassSpec.noiseCoefficient,
                colors = BlurColors(
                    blendColors = glassSpec.blendColors(isDark),
                    brightness = glassSpec.brightness(isDark),
                    contrast = glassSpec.contrast,
                    saturation = glassSpec.saturation,
                ),
                enabled = true,
            )
            .then(if (edgeStrokeProvider != null) Modifier.edgeStroke(shape = glassShape, stroke = edgeStrokeProvider) else Modifier)
        topCornerRadius == 0.dp && bottomCornerRadius == 0.dp -> Modifier.background(color)
        else -> Modifier.squircleSurface(color, topCornerRadius, topCornerRadius, bottomCornerRadius, bottomCornerRadius)
    }
    // 玻璃真正生效时向子级广播「在玻璃上」，让分割线等按实色卡设计的交界色自动换掉
    CompositionLocalProvider(
        LocalContentColor provides contentColor,
        LocalOnGlassSurface provides (glassBackdrop != null),
    ) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(
                    start = outerHorizontalPadding,
                    end = outerHorizontalPadding,
                    top = outerTopPadding,
                    bottom = outerBottomPadding,
                )
                .then(background)
                .padding(insidePadding),
            content = content,
        )
    }
}

/** [groupedCardItems] 的单段定义：稳定 [key] + 段内容。 */
class CardItem(
    val key: String,
    val content: @Composable ColumnScope.() -> Unit,
)

/**
 * 把一张卡片的多行拆成独立 lazy item（视觉上仍是一张连续卡片），替换 `item { Card { row1(); row2(); … } }`
 * 这种"单 item 塞多组件"反模式，让 LazyColumn 只组合可见行、按行增量重组。
 *
 * 不加 item 动画，保持静态 `Card` 观感；需要展开/收起动画的（如代理组节点行）自行在 item 内 `Modifier.animateItem(...)`。
 *
 * @param keyPrefix 与各段 key 拼成全局唯一 key。
 * @param items 卡片内各行；空列表不产出任何 item。
 * @param insidePadding 段内留白（同 Card 的 `insideMargin` 语义，默认 0）。
 */
fun LazyListScope.groupedCardItems(
    keyPrefix: String,
    items: List<CardItem>,
    outerTopPadding: Dp = 0.dp,
    outerBottomPadding: Dp = 12.dp,
    outerHorizontalPadding: Dp = 12.dp,
    insidePadding: PaddingValues = PaddingValues(0.dp),
    /** 本组合卡是否整体走玻璃（默认开）；关掉则退回逐段实色卡。 */
    glassEnabled: Boolean = true,
) {
    if (items.isEmpty()) return

    // 玻璃组卡：整组收成一个 item，玻璃和描边只画一次 —— 一整块玻璃 + 一圈边。
    if (glassEnabled) {
        item(key = "$keyPrefix:glass") {
            CardSegment(
                isFirst = true,
                isLast = true,
                outerHorizontalPadding = outerHorizontalPadding,
                outerTopPadding = outerTopPadding,
                outerBottomPadding = outerBottomPadding,
                insidePadding = insidePadding,
                glass = true,
            ) {
                items.forEach { cardItem -> cardItem.content.invoke(this) }
            }
        }
        return
    }

    val lastIndex = items.lastIndex
    items.forEachIndexed { index, cardItem ->
        item(key = "$keyPrefix:${cardItem.key}") {
            CardSegment(
                isFirst = index == 0,
                isLast = index == lastIndex,
                outerHorizontalPadding = outerHorizontalPadding,
                outerTopPadding = if (index == 0) outerTopPadding else 0.dp,
                outerBottomPadding = if (index == lastIndex) outerBottomPadding else 0.dp,
                insidePadding = insidePadding,
                content = cardItem.content,
            )
        }
    }
}
