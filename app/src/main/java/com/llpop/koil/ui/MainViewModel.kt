package com.llpop.koil.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.llpop.koil.data.location.LocationHelper
import com.llpop.koil.data.settings.AppSettings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(
    private val settings: AppSettings,
    private val locationHelper: LocationHelper,
) : ViewModel() {

    init {
        // 启动时若定位开关已开且有权限，静默定位并更新默认省份（跨省自动切换）
        viewModelScope.launch {
            val enabled = settings.locationEnabled.first()
            if (enabled && locationHelper.hasPermission()) {
                val province = locationHelper.getCurrentProvince()
                if (!province.isNullOrBlank()) settings.setDefaultProvince(province)
            }
        }
    }

    /** 主题模式三档：跟随系统 / 浅色 / 深色（koil-theme-mode） */
    val themeMode: StateFlow<AppSettings.ThemeMode> = settings.themeMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings.ThemeMode.SYSTEM)

    /** 语言四档：跟随系统 / 简体中文 / 繁體中文 / English */
    val language: StateFlow<AppSettings.Language> = settings.language
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings.Language.SYSTEM)

    /** 悬浮导航条开关（参考 Mishka） */
    val floatingBottomBar: StateFlow<Boolean> = settings.floatingBottomBar
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    /** 悬浮样式：miuix / ios_like */
    val floatingBottomBarStyle: StateFlow<String> = settings.floatingBottomBarStyle
        .stateIn(viewModelScope, SharingStarted.Eagerly, "miuix")

    /** 底栏模式：icon_and_text / icon_only */
    val bottomBarMode: StateFlow<String> = settings.bottomBarMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, "icon_and_text")

    fun setThemeMode(mode: AppSettings.ThemeMode) {
        viewModelScope.launch { settings.setThemeMode(mode) }
    }

    fun setLanguage(lang: AppSettings.Language) {
        viewModelScope.launch { settings.setLanguage(lang) }
    }

    fun setFloatingBottomBar(on: Boolean) {
        viewModelScope.launch { settings.setFloatingBottomBar(on) }
    }

    fun setFloatingBottomBarStyle(style: String) {
        viewModelScope.launch { settings.setFloatingBottomBarStyle(style) }
    }

    fun setBottomBarMode(mode: String) {
        viewModelScope.launch { settings.setBottomBarMode(mode) }
    }
}
