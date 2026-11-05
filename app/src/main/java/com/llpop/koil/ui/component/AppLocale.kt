package com.llpop.koil.ui.component

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

/**
 * 应用内所选语言（非系统语言）。
 *
 * 为什么需要它：MainScreen 在**自己的组合里**用 LocalConfiguration/LocalContext 覆盖语言，
 * 让 stringResource 立即跟着切换。但 Compose 的每个独立窗口（Dialog / Popup）都会新建一个
 * 组合根，`ProvideCompositionLocals` 会用**该窗口自己的** context/configuration 重新提供
 * LocalContext / LocalConfiguration —— 覆盖被冲掉。
 *
 * 结果就是真机上看到的现象：主界面已经是英文，点开补录窗/编辑窗，里面整屏又变回中文。
 *
 * 这个 CompositionLocal 由 MainScreen 提供；自定义 CompositionLocal 会随父组合继承进
 * 子组合（Dialog 的子组合带 parentCompositionContext），不会被窗口重建冲掉，
 * 所以窗口内的内容可以用它把语言重新盖回去（见 [ProvideAppLocale]）。
 */
val LocalAppLocale = staticCompositionLocalOf<Locale?> { null }

/**
 * 在**独立窗口**里重新接受应用语言：把 LocalConfiguration / LocalContext 换成目标 locale 的
 * 版本，`stringResource`（读 LocalResources，其默认值由这两个 Local 推导）随之生效。
 *
 * 只应在 Dialog/Popup 这类新组合根内部使用；主界面本身由 MainScreen 统一覆盖。
 */
@Composable
fun ProvideAppLocale(locale: Locale?, content: @Composable () -> Unit) {
    if (locale == null) {
        content()
        return
    }
    val baseContext = LocalContext.current
    val baseConfig = LocalConfiguration.current
    // 保留窗口自身的字号缩放 / 尺寸 / 方向，只替换 locale
    val localizedConfig = remember(locale, baseConfig) {
        Configuration(baseConfig).apply { setLocale(locale) }
    }
    // createConfigurationContext 会产生一个新的 ContextImpl（不是包装 Activity 的 ContextWrapper），
    // 但 LocalActivityResultRegistryOwner 已由 MainScreen 显式提供并随父组合继承，不受影响。
    val localizedContext = remember(locale, baseContext, localizedConfig) {
        baseContext.createConfigurationContext(localizedConfig)
    }
    CompositionLocalProvider(
        LocalAppLocale provides locale,
        LocalConfiguration provides localizedConfig,
        LocalContext provides localizedContext,
    ) {
        content()
    }
}
