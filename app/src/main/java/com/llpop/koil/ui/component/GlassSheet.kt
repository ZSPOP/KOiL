package com.llpop.koil.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.glass.GlassBottomSheet
import top.yukonga.miuix.kmp.utils.overScrollVertical

/**
 * KOiL 玻璃底部弹层：直接委托官方 [GlassBottomSheet]。
 *
 * 与 [KOiLGlassDialog] 的分工：确认类、短内容用小弹窗；补录/编辑这类长表单用底部弹层，
 * 形态上更贴近手机原生（整宽拉起、顶部圆角、带拖拽把手，可下拉关闭）。
 *
 * 布局契约：`insideMargin` 传 0 —— 标题行由官方自己居中，正文的横向内距由各内容块自己给，
 * 这样 [FullTankSwitchRow] 才能落在零横向内距的容器里（它内部固定 16dp，外层再加会双重缩进）。
 *
 * 滚动：内容必须同时挂 `overScrollVertical` —— 面板的越界拉动/回弹靠 `LocalBottomSheetNestedScrollConnection`
 * 交接，普通 `verticalScroll` 只滚自己、不会把手势交给面板，表现为「内容拉不动」。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun KOiLFormSheet(
    show: Boolean,
    onDismissRequest: () -> Unit,
    title: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    // 在函数作用域（= 调用点）先取到页面 backdrop：弹层内容在根弹层宿主里组合，
    // 那里读 LocalPageBackdrop 是 null，必须在这里捕获后重新下发，否则里面的
    // 下拉面板/日历只能回落成纯色
    val backdrop = LocalPageBackdrop.current
    GlassBottomSheet(
        show = show,
        onDismissRequest = onDismissRequest,
        backdrop = backdrop,
        modifier = modifier,
        title = title,
        insideMargin = DpSize(0.dp, 0.dp),
        renderInRootScaffold = true,
        enableNestedScroll = true,
    ) {
        // 面板本身由库抬到键盘顶；键盘可见时底部只留 ImeGap，
        // 收起时才让出系统导航栏 + 16dp（那时它是真实占位）
        val imeVisible = WindowInsets.isImeVisible
        CompositionLocalProvider(LocalPageBackdrop provides backdrop) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // 越界修饰符必须挂在滚动容器外层（与页面列表同一写法）：
                // 放内层时它看不到滚动容器之外的手势，越界与交接都不会触发
                .overScrollVertical()
                .verticalScroll(rememberScrollState())
                .then(
                    if (imeVisible) {
                        Modifier.padding(bottom = ImeGap)
                    } else {
                        Modifier.navigationBarsPadding().padding(bottom = 16.dp)
                    },
                ),
            content = content,
        )
        }
    }
}
