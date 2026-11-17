package com.llpop.koil.util

import android.app.LocaleManager
import android.content.Context
import android.os.LocaleList
import com.llpop.koil.data.settings.AppSettings
import java.util.Locale

/**
 * App 语言 ↔ 系统「每应用语言」（Android 13+ `LocaleManager`）的桥。
 *
 * 为什么要接这条线：清单里声明 `android:localeConfig` 之后，系统设置会多出一个语言入口，
 * 但用户在那里改完，系统只改自己的记录；App 内的语言仍由 DataStore 说了算，
 * 下次进界面又被旧值盖回去 —— 表现为「系统里改了没用」。两边必须对账：
 *
 * - App 内改语言：写 DataStore 之后调 [apply]，把选择同步到系统（系统设置里显示的就是它）；
 * - 系统里改语言：系统会推一次配置变更（进程不重建），`KOiLApplication` 在
 *   `onConfigurationChanged` 里调 [read] 对账并把值写回 DataStore。
 *
 * 没有用 `LocaleManager.addOnLocaleChangedListener`：它不在公开 SDK 里（编译不过），
 * 配置变更回调是公开且等价的入口 —— 改「每应用语言」必然伴随一次 configuration change。
 *
 * 单向真相仍是 DataStore：系统侧的值只负责写回来，界面永远只读 DataStore。
 */
object SystemLocaleBridge {

    /** 把 App 语言写进系统（SYSTEM = 清空，即「跟随系统」）。系统不支持时静默跳过。 */
    fun apply(context: Context, language: AppSettings.Language) {
        val service = service(context) ?: return
        val locales = when (language) {
            AppSettings.Language.SYSTEM -> LocaleList.getEmptyLocaleList()
            else -> LocaleList.forLanguageTags(tagOf(language))
        }
        // 未在 localeConfig 里声明的语言会抛 IllegalArgumentException：
        // 这里不能让一次语言切换把设置页搞崩，失败了就退回「App 内自绘」的老路径
        runCatching { service.applicationLocales = locales }
    }

    /**
     * 读系统侧当前设置。
     *
     * - 空列表 = 系统里选的是「跟随系统」→ [AppSettings.Language.SYSTEM]
     * - 认不出的语言（比如系统给了 `ja-JP`，而本 App 没声明日语）→ null，调用方不要覆盖用户设置
     */
    fun read(context: Context): AppSettings.Language? {
        val service = service(context) ?: return null
        val locales = runCatching { service.applicationLocales }.getOrNull() ?: return null
        if (locales.isEmpty) return AppSettings.Language.SYSTEM
        return localeToLanguage(locales[0])
    }

    private fun service(context: Context): LocaleManager? =
        runCatching { context.getSystemService(LocaleManager::class.java) }.getOrNull()

    /** 系统用的 BCP-47 标签；必须与 `res/xml/locales_config.xml` 一致 */
    internal fun tagOf(language: AppSettings.Language): String = when (language) {
        // SYSTEM 在 apply 里已转成空 LocaleList，不会走到这里；列出来只是为了 when 穷尽
        AppSettings.Language.ZH_CN, AppSettings.Language.SYSTEM -> "zh-CN"
        AppSettings.Language.ZH_TW -> "zh-TW"
        AppSettings.Language.EN -> "en"
    }

    /**
     * 系统 Locale → App 语言。
     *
     * 繁体不能只看国家：`zh-Hant` 是显式繁体，「香港/澳门/台湾」按地区也是繁体；
     * 其余中文（含 `zh-Hans`、`zh-CN`、无地区）都按简体。
     */
    internal fun localeToLanguage(locale: Locale): AppSettings.Language? = when (locale.language) {
        "zh" -> when {
            locale.script.equals("Hant", ignoreCase = true) -> AppSettings.Language.ZH_TW
            locale.country in setOf("TW", "HK", "MO") -> AppSettings.Language.ZH_TW
            else -> AppSettings.Language.ZH_CN
        }
        "en" -> AppSettings.Language.EN
        else -> null
    }
}
