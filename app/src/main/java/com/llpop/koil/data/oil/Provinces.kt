package com.llpop.koil.data.oil

/**
 * 全国 31 个省级行政区（不含港澳台）——油价库、定位、省份选择器、导入校验共用同一份。
 *
 * 同一份列表统一由本常量提供（避免 AddScreen / OverviewScreen / SettingsScreen / OilPriceDbSyncer
 * 各写一遍共 4 份字面量）。这些字符串是**数据库主键的一部分**（province 字段），
 * 任意一处拼写漂移（如"内蒙古"写成"内蒙"）都会让该省油价永远查不到、四宫格全空，
 * 而且不会报错。单一常量令漂移面归零。
 */
object Provinces {

    val ALL: List<String> = listOf(
        "广东", "北京", "上海", "江苏", "浙江", "山东", "四川", "湖北", "湖南",
        "河南", "河北", "福建", "安徽", "江西", "辽宁", "陕西", "云南", "广西",
        "重庆", "天津", "山西", "贵州", "黑龙江", "吉林", "甘肃", "内蒙古",
        "新疆", "海南", "宁夏", "青海", "西藏",
    )

    private val SET = ALL.toSet()

    /** 是否为受支持的省份简称（定位/导入结果校验用） */
    fun isValid(name: String?): Boolean = name != null && name in SET
}
