package com.llpop.koil.ui.component

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.glass.GlassDialogButton
import top.yukonga.miuix.kmp.glass.GlassDialogButtonDefaults
import top.yukonga.miuix.kmp.glass.GlassDialogButtonRole
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 玻璃弹窗里的单行文字按钮。
 *
 * 官方只提供 [GlassDialogButton]（收 RowScope 内容），弹窗底部这类「取消 / 确定」
 * 到处都要重复一遍 `Text(style = textStyles.button)` 的写法，所以收成一层：
 * 文案单行、超出省略，其余颜色与按压反馈交给 [GlassDialogButton]。
 */
@Composable
fun GlassDialogTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    role: GlassDialogButtonRole = GlassDialogButtonRole.Normal,
    insideMargin: PaddingValues = GlassDialogButtonDefaults.InsideMargin,
) {
    GlassDialogButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        role = role,
        insideMargin = insideMargin,
    ) {
        Text(
            text = text,
            style = MiuixTheme.textStyles.button,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
