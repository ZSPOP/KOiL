package com.llpop.koil.util

import com.llpop.koil.data.settings.AppSettings
import java.util.Locale

/**
 * 语言解析：把设置里的 Language 档映射为 JVM/Android Locale。
 * SYSTEM 跟随系统默认；简中/繁中/英文显式指定，供运行时覆盖 Configuration 使用。
 */
object LocaleManager {

    fun resolve(lang: AppSettings.Language): Locale = when (lang) {
        AppSettings.Language.ZH_CN -> Locale.SIMPLIFIED_CHINESE
        AppSettings.Language.ZH_TW -> Locale("zh", "TW")
        AppSettings.Language.EN -> Locale.ENGLISH
        AppSettings.Language.SYSTEM -> Locale.getDefault()
    }

    /** 用于资源目录匹配：简体 zh-rCN（即默认 values）、繁体 zh-rTW、英文 en */
    fun bcp47(lang: AppSettings.Language): String = when (lang) {
        AppSettings.Language.ZH_CN -> "zh-rCN"
        AppSettings.Language.ZH_TW -> "zh-rTW"
        AppSettings.Language.EN -> "en"
        AppSettings.Language.SYSTEM -> Locale.getDefault().language
    }
}
