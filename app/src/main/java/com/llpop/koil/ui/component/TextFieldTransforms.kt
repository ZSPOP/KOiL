package com.llpop.koil.ui.component

import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.forEachChange

/**
 * 密码/密钥掩码：把整段替换成等长圆点。
 *
 * 长度不变，所以光标与选区映射不会错位——长度一变就会看到光标乱跳。
 * 对应旧的 `PasswordVisualTransformation()`；state 版文本 API 里没有 visualTransformation，
 * 由 [OutputTransformation] 承担。
 */
val KOiLPasswordMask: OutputTransformation = OutputTransformation {
    if (length > 0) replace(0, length, "•".repeat(length))
}

/**
 * 地址/账号类输入的过滤，同时把「回车被输入法当成字符提交」这件事识别出来。
 *
 * 只处理本次编辑插入的片段，不重排整串文本，所以光标不会跳位；粘贴带空白的整段内容也会被清掉空白。
 *
 * 为什么还要 [onSubmitAttempt]：软键盘的回车在不少输入法里既不发 ImeAction、也不发 KeyEvent，
 * 而是直接提交一个字符（中文常见补一个空格，英文常见提交一个换行）。这两个字符都会被本变换抹掉，
 * 于是界面表现为「按回车什么都没发生」。既然这类框本来就不该有空白，那么「提交里含空白」
 * 就等价于「用户按了回车」——用它补上第三条跳转信号。
 */
fun KOiLNoWhitespaceInput(onSubmitAttempt: () -> Unit = {}): InputTransformation =
    InputTransformation {
        var submitAttempted = false
        changes.forEachChange { range, _ ->
            val inserted = asCharSequence().subSequence(range.min, range.max)
            if (inserted.any { it.isWhitespace() }) {
                val cleaned = inserted.filterNot { it.isWhitespace() }
                replace(range.min, range.max, cleaned.toString())
                // 整段只提交了空白，或提交里带换行 —— 判定为一次回车提交
                if (cleaned.isEmpty() || inserted.contains('\n') || inserted.contains('\r')) {
                    submitAttempted = true
                }
            }
        }
        if (submitAttempted) onSubmitAttempt()
    }

/**
 * 小数金额输入：只保留数字与一个小数点，替代原先写在 `onValueChange` 里的
 * `String.sanitizeDecimalInput()`（state 版文本 API 的过滤必须走 [InputTransformation]）。
 */
fun KOiLDecimalInput(onSubmitAttempt: () -> Unit = {}): InputTransformation = InputTransformation {
    var submitAttempted = false
    changes.forEachChange { range, _ ->
        val inserted = asCharSequence().subSequence(range.min, range.max).toString()
        val cleaned = inserted.filter { it.isDigit() || it == '.' }
        if (cleaned != inserted) replace(range.min, range.max, cleaned)
        // 整段只提交了空白，或提交里带换行 —— 判定为一次回车（与地址/账号框同一套判据）
        if (cleaned.isEmpty() && inserted.isNotBlank() || inserted.contains('\n') || inserted.contains('\r')) {
            submitAttempted = true
        }
    }
    // 全串最多一个小数点：出现第二个就整串规整（只在异常输入时触发，正常键入不会走到）
    val full = asCharSequence().toString()
    if (full.count { it == '.' } > 1) {
        var seenDot = false
        val fixed = buildString {
            for (c in full) {
                if (c == '.') {
                    if (!seenDot) {
                        seenDot = true
                        append(c)
                    }
                } else {
                    append(c)
                }
            }
        }
        replace(0, length, fixed)
    }
    if (submitAttempted) onSubmitAttempt()
}

/**
 * 纯数字输入：步进器编辑态用，替代 `raw.filter { it.isDigit() }`。
 *
 * [onSubmitAttempt] 与 [KOiLNoWhitespaceInput] 同理：输入法把回车当字符提交时（这里补的是空格或换行），
 * 字符会被抹掉，靠这个回调顶上一次「回车提交」。
 */
fun KOiLDigitsOnlyInput(onSubmitAttempt: () -> Unit = {}): InputTransformation =
    InputTransformation {
        var submitAttempted = false
        changes.forEachChange { range, _ ->
            val inserted = asCharSequence().subSequence(range.min, range.max).toString()
            val cleaned = inserted.filter { it.isDigit() }
            if (cleaned != inserted) replace(range.min, range.max, cleaned)
            if (cleaned.isEmpty() && inserted.isNotBlank() ||
                inserted.contains('\n') || inserted.contains('\r')
            ) {
                submitAttempted = true
            }
        }
        if (submitAttempted) onSubmitAttempt()
    }
