package com.llpop.koil.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.SnackbarDuration
import top.yukonga.miuix.kmp.basic.SnackbarHostState

/**
 * 全局 Snackbar 宿主状态：由 `MainScreen` 的根 Scaffold 创建并挂 `SnackbarHost`，
 * 各页面通过 [rememberSnackbar] 取到「弹一条提示」的能力，替代散落的 `Toast`。
 *
 * 为什么挂在根宿主而不是每页自己的 Scaffold：
 * - 提示要盖在页面与底部导航之上、弹层之下，根宿主是唯一在录制层之外的位置；
 * - 切页/切二级页时提示不会因为宿主被销毁而消失；
 * - 弹窗内的操作（如崩溃日志清理）也能共用同一条通道。
 */
val LocalSnackbarHostState = compositionLocalOf<SnackbarHostState?> { null }

/**
 * 返回一个「显示短提示」的函数；宿主不在（例如预览/测试环境）时是空操作，不崩。
 *
 * 用法：`val snackbar = rememberSnackbar()`，然后 `snackbar(context.getString(R.string.x))`。
 * 调用点若已在协程里（例如 Dialog 按钮的 `scope.launch`），直接调即可，内部已切到主线程并发。
 */
@Composable
fun rememberSnackbar(): (String) -> Unit {
    val hostState = LocalSnackbarHostState.current
    val scope = rememberCoroutineScope()
    return remember(hostState, scope) {
        { message ->
            if (hostState != null) {
                scope.launch {
                    hostState.showSnackbar(message = message, duration = SnackbarDuration.Short)
                }
            }
        }
    }
}
