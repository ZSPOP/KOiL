package com.llpop.koil.ui.component

/**
 * 数值输入框统一过滤：
 * 仅保留数字与至多一个小数点，杜绝 "1.2.3" 这类多小数点输入导致 toDoubleOrNull=null。
 * 录入卡 / 补录卡 / 编辑卡的小数输入字段统一调用。
 */
fun String.sanitizeDecimalInput(): String {
    var hasDot = false
    val sb = StringBuilder(length)
    for (c in this) {
        when {
            c.isDigit() -> sb.append(c)
            c == '.' && !hasDot -> {
                hasDot = true
                sb.append(c)
            }
            // 其余字符（多余小数点、字母、符号）一律丢弃
        }
    }
    return sb.toString()
}
