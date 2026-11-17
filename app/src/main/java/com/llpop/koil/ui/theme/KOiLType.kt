package com.llpop.koil.ui.theme

import androidx.compose.ui.unit.sp

/**
 * 字号阶（type scale）。
 *
 * 现状是 208 处 `fontSize = N.sp` 直写，同一个视觉层级在不同页面各自写数字，
 * 想整体调一档字号得全仓找。这里把**实际用到的每一档**收成一处，值与原字面量 1:1，
 * 所以替换是零视觉变化的纯搬运。
 *
 * 命名按 Material 的角色名（Caption/Body/Title/Headline/Display），但**分档依据是尺寸**
 * 不是语义——先保证「值只有一处」，等以后真要按语义分组（标题/正文/辅助）时再改名字，
 * 那一步会牵动视觉，得单独评估。
 *
 * 刻意没有把 12/13/14 合成一档：它们在现有页面里确实是三种层级（辅助说明 / 正文 /
 * 正文偏大），现在合并会立刻改变观感。
 */
object KOiLType {
    /** 9.5sp：极小角标 */
    val Micro = 9.5.sp
    /** 10sp：角标 / 图例 */
    val Caption = 10.sp
    /** 11sp：次要说明 */
    val CaptionLarge = 11.sp
    /** 11.5sp：次要说明（半档，仅个别密集列表用） */
    val CaptionXLarge = 11.5.sp
    /** 12sp：辅助文案主力 */
    val BodySmall = 12.sp
    /** 13sp：正文 */
    val Body = 13.sp
    /** 14sp：正文偏大 */
    val BodyLarge = 14.sp
    /** 15sp：小标题 */
    val Subtitle = 15.sp
    /** 16sp：标题 */
    val Title = 16.sp
    /** 17sp：标题偏大 */
    val TitleLarge = 17.sp
    /** 18sp：区块标题 */
    val Headline = 18.sp
    /** 20sp：大号数字 */
    val Display = 20.sp
    /** 21sp：大号数字（半档） */
    val DisplayLarge = 21.sp
    /** 33sp：Hero 数字（首页油价主数字） */
    val Hero = 33.sp
}
