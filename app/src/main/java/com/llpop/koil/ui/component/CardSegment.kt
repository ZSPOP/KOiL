package com.llpop.koil.ui.component

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.llpop.koil.ui.component.blur.LocalBlurEnabled
import com.llpop.koil.ui.component.effect.KOiLGlassEdgeTier
import com.llpop.koil.ui.component.effect.rememberEffectiveGlassSpec
import com.llpop.koil.ui.component.effect.rememberKOiLGlassEdgeStroke
import com.llpop.koil.ui.theme.LocalKOiLDarkTheme
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.interfaces.HoldDownInteraction
import top.yukonga.miuix.kmp.material.EdgeStroke
import top.yukonga.miuix.kmp.material.GlassShape
import top.yukonga.miuix.kmp.material.edgeStroke
import top.yukonga.miuix.kmp.material.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.squircle.squircleSurface
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import top.yukonga.miuix.kmp.utils.SinkFeedback
import top.yukonga.miuix.kmp.utils.TiltFeedback
import top.yukonga.miuix.kmp.utils.pressable
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
 *
 * ## 接入要求
 * - 传 [onClick] / [onLongPress] 时必须同时指定 [pressFeedbackType] = `Sink`（或把 [showIndication] 打开），
 *   否则点击既无涟漪也无下沉，会被当成「点击坏了」。
 * - 内容层不要再挂自己的 `clickable` / `combinedClickable`：内层先消费，落在内容区的点击到不了本组件，
 *   `SinkFeedback` 也收不到 press，等于没有反馈。需要整卡可点时，把内层手势上移成本组件的
 *   `onClick` / `onLongPress`（顺序不能反）。
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
    /**
     * 按下反馈：`Sink` 整段下沉、`Tilt` 倾斜，与官方 `Card` / `GlassCard` 走同一套
     * `Modifier.pressable` + 官方 `SinkFeedback` / `TiltFeedback`，不是自绘缩放。
     */
    pressFeedbackType: PressFeedbackType = PressFeedbackType.None,
    /** 是否显示系统 indication。官方 `Card` 默认关（只靠 Sink/Tilt 表达按压）。 */
    showIndication: Boolean = false,
    /** 外部按住态（例如父级胶囊被按住时整卡同步下沉），镜像进 interactionSource。 */
    holdDownState: Boolean = false,
    onClick: (() -> Unit)? = null,
    onLongPress: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    // 玻璃段走官方 GlassShape（CornerBasedShape + smoothing = 连续角）：项目实色卡用
    // squircleSurface 的超椭圆，玻璃段统一到同一轮廓；同时 edgeStroke 要求 CornerBasedShape，
    // 且 GlassShape 的 smoothing 会被描边着色器采用（普通 RoundedCornerShape 只有 smoothing 0）。
    val glassShape = remember(topCornerRadius, bottomCornerRadius) {
        GlassShape(topCornerRadius, topCornerRadius, bottomCornerRadius, bottomCornerRadius)
    }
    // 按底材取参数：动态壁纸关（纯色底）时自动切官方 puredThinGlass 口径，
    // 与 補錄胶囊等其它自绘玻璃共用同一处判定（rememberEffectiveGlassSpec）。
    val glassSpec = rememberEffectiveGlassSpec()
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
    // 分段拼接防缝：非末段的底角为 0，两段边界上 0.5x 降采样会产生一条抗锯齿黑缝。
    // 按官方指导在实色段向下多画 2px 同色（玻璃段是采样绘制，不吃这条）。
    // 挂在最外层：实色/超椭圆那层内部会 clip，放内层会被裁掉。
    val seamExtension: Modifier =
        if (!isLast && bottomCornerRadius == 0.dp && color.alpha >= 1f) {
            Modifier.drawBehind {
                drawRect(
                    color = color,
                    topLeft = Offset(0f, size.height),
                    size = Size(size.width, 2.dp.toPx()),
                )
            }
        } else {
            Modifier
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
        topCornerRadius == 0.dp && bottomCornerRadius == 0.dp ->
            seamExtension.then(Modifier.background(color))
        else -> seamExtension.then(Modifier.squircleSurface(color, topCornerRadius, topCornerRadius, bottomCornerRadius, bottomCornerRadius))
    }
    // 交互链照官方 Card.kt：pressable 在卡片本体外层承担 Sink/Tilt 变换与按住态，
    // combinedClickable 在内容层承担点击；两者共用同一条 interactionSource。
    val isClickable = onClick != null || onLongPress != null
    // 与官方 Card.kt:99 一致：无条件创建，使用处再判空 —— 条件 remember 会在
    // needsInteractionSource 翻转时重建实例，把进行中的按住态/涟漪掐断。
    val interactionSource = remember { MutableInteractionSource() }
    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnLongPress by rememberUpdatedState(onLongPress)

    if (holdDownState) {
        LaunchedEffect(interactionSource) {
            val hold = HoldDownInteraction.HoldDown()
            interactionSource.emit(hold)
            try {
                awaitCancellation()
            } finally {
                withContext(NonCancellable) { interactionSource.emit(HoldDownInteraction.Release(hold)) }
            }
        }
    }
    val pressFeedback = remember(pressFeedbackType) {
        when (pressFeedbackType) {
            PressFeedbackType.None -> null
            PressFeedbackType.Sink -> SinkFeedback()
            PressFeedbackType.Tilt -> TiltFeedback()
        }
    }
    val indicationToUse = if (showIndication) LocalIndication.current else null
    val clickableModifier =
        remember(isClickable, onLongPress != null, interactionSource, indicationToUse) {
            if (isClickable) {
                Modifier.combinedClickable(
                    interactionSource = interactionSource,
                    indication = indicationToUse,
                    onClick = { currentOnClick?.invoke() },
                    onLongClick = if (onLongPress != null) {
                        { currentOnLongPress?.invoke() }
                    } else {
                        null
                    },
                )
            } else {
                Modifier
            }
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
                .pressable(
                    interactionSource = if (pressFeedback != null) interactionSource else null,
                    indication = pressFeedback,
                    delay = null,
                )
                .then(background)
                .then(clickableModifier)
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
