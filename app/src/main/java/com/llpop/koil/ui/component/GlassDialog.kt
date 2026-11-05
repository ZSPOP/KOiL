package com.llpop.koil.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * KOiL 柔光玻璃弹窗：系统 Dialog 容器 + 半透明玻璃底色 + 24dp 圆角。
 * （弹窗为独立窗口，无法采样 App 内容做真模糊，采用半透明玻璃观感；
 *  之前用 miuix OverlayDialog 在部分机型触发闪退，故回退系统 Dialog。）
 * 可选参数：contentPadding 内边距、scrollable+maxHeight 超高限高滚动、widthFraction 屏宽比例（>0 时加宽到该比例，默认填满系统 Dialog 可用宽）。
 */
@Composable
fun KOiLGlassDialog(
    show: Boolean,
    onDismissRequest: () -> Unit,
    title: String? = null,
    contentPadding: Dp = 20.dp,
    scrollable: Boolean = false,
    maxHeight: Dp? = null,
    widthFraction: Float? = null,
    content: @Composable () -> Unit,
) {
    if (!show) return
    val hostState = remember { AnchoredPopupHostState() }
    val props = if (widthFraction != null) DialogProperties(usePlatformDefaultWidth = false) else DialogProperties()
    androidx.compose.ui.window.Dialog(
        // 系统关闭（返回键/点击窗口外）直接关闭卡片；"点弹窗内容外的卡片外区域"由
        // overlay 遮罩处理逐层关闭（有下拉先收起、无下拉才关卡片）
        onDismissRequest = { onDismissRequest() },
        properties = props,
    ) {
        // 弹窗根包裹：内容 + 锚定下拉 overlay（同一窗口坐标系，位置自洽且不被卡片遮挡）
        AnchoredPopupDialogWrapper(
            hostState = hostState,
            onOutsideTap = onDismissRequest,
        ) {
            // v1.6.4：弹窗宽度按"窗口可用宽度"算，而不是整屏宽度——分屏/自由窗口下 Configuration
            // 给出的 screenWidthDp 可能是整块屏幕，弹窗会溢出窗口。
            val windowWidthDp = with(LocalDensity.current) {
                LocalWindowInfo.current.containerSize.width.toDp()
            }
            val widthMod = if (widthFraction != null)
                Modifier.width(windowWidthDp * widthFraction)
            else Modifier.fillMaxWidth()
            // v1.5.32：弹窗内点击空白失焦（composable 上下文取 focusManager 供 pointerInput 使用）
            val focusManager = LocalFocusManager.current
            // v1.6.2：高度上限不再只看屏幕高度——弹窗窗口在键盘弹出时会被系统压缩，
            // 若仍按 screenHeight*0.85 定高，超出窗口的部分（通常是底部"保存"按钮）会被裁掉且滚不到。
            // 这里用窗口实际可用高度做二次收敛，键盘弹起时弹窗自动变矮并保持内部可滚动。
            BoxWithConstraints {
                // 注意：BoxWithConstraints 作用域自带 maxHeight，先取出参数值避免同名遮蔽
                val requestedMax = maxHeight
                val cap = if (requestedMax != null) minOf(requestedMax, this.maxHeight) else this.maxHeight
                Box(
                    modifier = Modifier
                        .then(widthMod)
                        .heightIn(max = cap)
                        .clip(RoundedCornerShape(24.dp))
                        // v1.5.32：背景完全不透明，确保盖住弹窗背后内容（原 0.94 alpha 在矮弹窗时透出下层文字造成"叠加"观感）
                        .background(MiuixTheme.colorScheme.surface)
                        .padding(contentPadding)
                        // v1.5.32：点击弹窗内空白处取消输入框选中/失焦（子输入框消费点击，不误触）
                        .pointerInput(Unit) {
                            detectTapGestures(onTap = { focusManager.clearFocus() })
                        },
                ) {
                    if (scrollable) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState()),
                        ) { content() }
                    } else {
                        content()
                    }
                }
            }
        }
    }
}
