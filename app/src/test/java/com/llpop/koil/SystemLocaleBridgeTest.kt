package com.llpop.koil

import com.llpop.koil.data.settings.AppSettings
import com.llpop.koil.util.SystemLocaleBridge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

/**
 * 系统「每应用语言」与 App 语言的映射。
 *
 * 这条线最容易错的是繁体判定：只看国家会把 `zh-Hant`（无地区）判成简体；
 * 只看语言会把香港/澳门判成简体。两种错法都会让用户在系统里选繁体、界面出简体。
 */
class SystemLocaleBridgeTest {

    @Test
    fun `简体与带地区的简体`() {
        assertEquals(AppSettings.Language.ZH_CN, SystemLocaleBridge.localeToLanguage(Locale.SIMPLIFIED_CHINESE))
        assertEquals(AppSettings.Language.ZH_CN, SystemLocaleBridge.localeToLanguage(Locale.forLanguageTag("zh-Hans-CN")))
        assertEquals(AppSettings.Language.ZH_CN, SystemLocaleBridge.localeToLanguage(Locale.forLanguageTag("zh")))
    }

    @Test
    fun `繁体按地区与按脚本都能认出`() {
        assertEquals(AppSettings.Language.ZH_TW, SystemLocaleBridge.localeToLanguage(Locale.TRADITIONAL_CHINESE))
        assertEquals(AppSettings.Language.ZH_TW, SystemLocaleBridge.localeToLanguage(Locale.forLanguageTag("zh-Hant")))
        assertEquals(AppSettings.Language.ZH_TW, SystemLocaleBridge.localeToLanguage(Locale.forLanguageTag("zh-HK")))
        assertEquals(AppSettings.Language.ZH_TW, SystemLocaleBridge.localeToLanguage(Locale.forLanguageTag("zh-MO")))
    }

    @Test
    fun `英文与其它语言`() {
        assertEquals(AppSettings.Language.EN, SystemLocaleBridge.localeToLanguage(Locale.ENGLISH))
        assertEquals(AppSettings.Language.EN, SystemLocaleBridge.localeToLanguage(Locale.US))
        // 认不出的语言返回 null：调用方据此不覆盖用户在 App 内的选择
        assertNull(SystemLocaleBridge.localeToLanguage(Locale.JAPANESE))
        assertNull(SystemLocaleBridge.localeToLanguage(Locale.forLanguageTag("fr-FR")))
    }

    @Test
    fun `标签与 localeConfig 声明一致`() {
        // 这三个值必须同时出现在 res/xml/locales_config.xml 里，否则 setApplicationLocales 会抛
        assertEquals("zh-CN", SystemLocaleBridge.tagOf(AppSettings.Language.ZH_CN))
        assertEquals("zh-TW", SystemLocaleBridge.tagOf(AppSettings.Language.ZH_TW))
        assertEquals("en", SystemLocaleBridge.tagOf(AppSettings.Language.EN))
    }

    @Test
    fun `往返一致`() {
        listOf(
            AppSettings.Language.ZH_CN to "zh-CN",
            AppSettings.Language.ZH_TW to "zh-TW",
            AppSettings.Language.EN to "en",
        ).forEach { (language, tag) ->
            assertEquals(language, SystemLocaleBridge.localeToLanguage(Locale.forLanguageTag(tag)))
        }
    }
}
