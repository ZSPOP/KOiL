package com.llpop.koil.data.update

/**
 * 把编译期写死的 ISO 日期（`2026-11-15`）转成关于页展示文案（`2026年11月15日`）。
 *
 * **日期格式一律中文**，不随系统语言变：发布日期是给用户核对「这个包是哪天发的」用的，
 * 换个 locale 就变成 `Nov 15, 2026` 会让同一张关于页在不同语言下对不上；
 * 且 `日/月` 前后缀本身就是这套文案的一部分，不做 locale 分支是明确决定，不是漏掉。
 *
 * 解析失败时**原样返回**：关于页是一屏静态信息，宁可显示一串 ISO，
 * 也不要在这一行开天窗（写死值改了格式而没人更新解析逻辑时，最坏结果是难看，不是空白）。
 */
fun formatReleaseDate(iso: String): String {
    val parts = iso.split("-")
    if (parts.size != 3) return iso
    val year = parts[0].toIntOrNull() ?: return iso
    val month = parts[1].toIntOrNull() ?: return iso
    val day = parts[2].toIntOrNull() ?: return iso
    if (month !in 1..12 || day !in 1..31) return iso
    return "%d年%d月%d日".format(year, month, day)
}
