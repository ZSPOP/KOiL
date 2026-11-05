package com.llpop.koil.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration

/**
 * 油价库的**显示名**映射（v1.6.6）。
 *
 * 省份与油号的中文字面量是数据库主键的一部分（province / grade 字段），
 * 不能为了显示去改动，否则历史记录会查不到。所以本地化只发生在渲染这一步：
 * 键仍是"广东""92号汽油"，界面上按当前语言换成 Guangdong / 92 RON / 廣東。
 *
 * 背景：英文界面下来源数据被直接渲染，明细页顶部一排 chip 全是中文（真机反馈）。
 */
private val PROVINCE_EN = mapOf(
    "广东" to "Guangdong", "北京" to "Beijing", "上海" to "Shanghai", "江苏" to "Jiangsu",
    "浙江" to "Zhejiang", "山东" to "Shandong", "四川" to "Sichuan", "湖北" to "Hubei",
    "湖南" to "Hunan", "河南" to "Henan", "河北" to "Hebei", "福建" to "Fujian",
    "安徽" to "Anhui", "江西" to "Jiangxi", "辽宁" to "Liaoning", "陕西" to "Shaanxi",
    "云南" to "Yunnan", "广西" to "Guangxi", "重庆" to "Chongqing", "天津" to "Tianjin",
    "山西" to "Shanxi", "贵州" to "Guizhou", "黑龙江" to "Heilongjiang", "吉林" to "Jilin",
    "甘肃" to "Gansu", "内蒙古" to "Inner Mongolia", "新疆" to "Xinjiang", "海南" to "Hainan",
    "宁夏" to "Ningxia", "青海" to "Qinghai", "西藏" to "Xizang",
)

/** 只需列出简繁有差异的字形，其余省份原样返回 */
private val PROVINCE_TW = mapOf(
    "广东" to "廣東", "江苏" to "江蘇", "山东" to "山東", "辽宁" to "遼寧",
    "陕西" to "陝西", "云南" to "雲南", "广西" to "廣西", "重庆" to "重慶",
    "贵州" to "貴州", "黑龙江" to "黑龍江", "甘肃" to "甘肅", "内蒙古" to "內蒙古",
    "宁夏" to "寧夏",
)

// 油号统一成 "92# 汽油" 的写法（与总览/录入页的 "92# · ¥x/L" 同源，真机反馈要求统一）
private val GRADE_LABEL = mapOf(
    "92号汽油" to "92# 汽油",
    "95号汽油" to "95# 汽油",
    "98号汽油" to "98# 汽油",
    "0号柴油" to "0# 柴油",
)

private val GRADE_EN = mapOf(
    "92号汽油" to "92# Gasoline",
    "95号汽油" to "95# Gasoline",
    "98号汽油" to "98# Gasoline",
    "0号柴油" to "0# Diesel",
)

private val GRADE_TW = mapOf(
    "92号汽油" to "92# 汽油",
    "95号汽油" to "95# 汽油",
    "98号汽油" to "98# 汽油",
    "0号柴油" to "0# 柴油",
)

/**
 * MainScreen 会按用户所选语言覆盖 Configuration，所以这里读到的是**生效语言**
 * （而不是系统语言），与界面其它文案一致。
 */
@Composable
@ReadOnlyComposable
fun provinceLabel(name: String): String {
    val locale = LocalConfiguration.current.locales[0]
    return when {
        locale.language == "en" -> PROVINCE_EN[name] ?: name
        locale.language == "zh" && locale.country == "TW" -> PROVINCE_TW[name] ?: name
        else -> name
    }
}

@Composable
@ReadOnlyComposable
fun gradeLabel(name: String): String {
    val locale = LocalConfiguration.current.locales[0]
    return when {
        locale.language == "en" -> GRADE_EN[name] ?: name
        locale.language == "zh" && locale.country == "TW" -> GRADE_TW[name] ?: name
        else -> GRADE_LABEL[name] ?: name
    }
}
