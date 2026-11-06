package com.llpop.koil.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
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
    // v1.6.5：Dialog 是独立窗口，**不继承** Activity 的 softInputMode/adjustResize。
    // 默认 decorFitsSystemWindows=true 时靠在系统收缩 dialog 窗口来避让键盘，实测 MIUI/HyperOS 上不收缩：
    // 弹窗高度不变、底部「保存」被键盘压住且滚不到（真机反馈）。
    // 改为 false —— 窗口铺到 IME 之下，由内容自己的 imePadding() 消费 IME inset（见下方 Box）。
    // v1.6.6：窗口属性三处，前两条决定宽度与键盘，第三条决定"逐级关闭"能不能生效：
    // 1) usePlatformDefaultWidth：只有"不指定比例"的弹窗才交给系统默认宽度；指定了 widthFraction 的必须为 false
    //    （窗口宽 MATCH_PARENT）。写成 widthFraction != null 就反了——指定了比例反而被系统默认宽度束住，
    //    实测编辑/补录窗明显变窄（真机反馈）。
    // 2) decorFitsSystemWindows = false：窗口铺到 IME / 系统栏之下，键盘避让交给内容自己的 imePadding()。
    // 3) dismissOnClickOutside = false：**这是「点一下全部一起关闭」的元凶**。默认 true 时，点击一旦落到
    //    Dialog 窗口之外，系统直接 onDismissRequest 关掉整个弹窗，绕过卡片外遮罩的逐级逻辑
    //    （有下拉先收下拉、再点才关卡片）。外部点击一律交给遮罩处理，这条捷径必须关掉。
    val props = DialogProperties(
        usePlatformDefaultWidth = widthFraction == null,
        decorFitsSystemWindows = false,
        dismissOnClickOutside = false,
    )
    androidx.compose.ui.window.Dialog(
        // 系统关闭（返回键/点击窗口外）直接关闭卡片；"点弹窗内容外的卡片外区域"由
        // overlay 遮罩处理逐层关闭（有下拉先收起、无下拉才关卡片）
        onDismissRequest = { onDismissRequest() },
        properties = props,
    ) {
        // v1.6.7：弹窗是独立窗口/组合根，Compose 会用窗口自己的 context+configuration
        // 重新提供 LocalContext/LocalConfiguration，把 MainScreen 的应用语言覆盖冲掉 ——
        // 表现为"主界面英文、补录窗/编辑窗整屏中文"。这里按应用语言重新覆盖一次。
        ProvideAppLocale(LocalAppLocale.current) {
        // 弹窗根包裹：内容 + 锚定下拉 overlay（同一窗口坐标系，位置自洽且不被卡片遮挡）
        AnchoredPopupDialogWrapper(
            hostState = hostState,
            onOutsideTap = onDismissRequest,
        ) {
            // v1.6.4：弹窗宽度按"窗口可用宽度"算，而不是整屏宽度——分屏/自由窗口下 Configuration
            // 给出的 screenWidthDp 可能是整块屏幕，弹窗会溢出窗口。
            // v1.6.6：改为直接取 BoxWithConstraints 的 maxWidth（= 本窗口内容可用宽度）。
            // 原来用 LocalWindowInfo.containerSize 反查，一旦窗口是 WRAP_CONTENT，containerSize 就由卡片宽度决定，
            // 和"卡片宽度由 containerSize 算"互为因果，会卡在一个偏小的值上（真机看到弹窗变窄）。
            // 用父约束没有这层循环，分屏/自由窗口/键盘弹出都天然正确。
            // v1.5.32：弹窗内点击空白失焦（composable 上下文取 focusManager 供 pointerInput 使用）
            val focusManager = LocalFocusManager.current
            // v1.6.2：高度上限不再只看屏幕高度——弹窗窗口在键盘弹出时会被系统压缩，
            // 若仍按 screenHeight*0.85 定高，超出窗口的部分（通常是底部"保存"按钮）会被裁掉且滚不到。
            // 这里用窗口实际可用高度做二次收敛，键盘弹起时弹窗自动变矮并保持内部可滚动。
            // v1.6.5：弹窗自己消费 IME inset（配合上面的 decorFitsSystemWindows=false）。
            // 注意放的位置：必须在 BoxWithConstraints **外层**——它下面的 maxHeight 会因此变矮，
            // 卡片 heightIn(max=cap) 自动收敛、内部 verticalScroll 可滚到「保存」按钮。
            Box(modifier = Modifier.imePadding()) {
                BoxWithConstraints {
                    // 注意：BoxWithConstraints 作用域自带 maxHeight，先取出参数值避免同名遮蔽
                    val requestedMax = maxHeight
                    val cap = if (requestedMax != null) minOf(requestedMax, this.maxHeight) else this.maxHeight
                    // 本窗口可用宽度（窗口已铺满，等于整屏/分屏后的窗口宽），直接作为比例基准
                    val widthMod = if (widthFraction != null)
                        Modifier.width(this.maxWidth * widthFraction)
                    else Modifier.fillMaxWidth()
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
    }
}
