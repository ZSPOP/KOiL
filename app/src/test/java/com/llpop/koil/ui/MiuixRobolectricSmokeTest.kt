package com.llpop.koil.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * miuix 在 Robolectric 里的冒烟。
 *
 * 为什么保留它：全站 UI 测试都建立在「miuix 组件能在 Robolectric 下组合、能进语义树」这个前提上。
 * 这个前提靠 miuix AAR 与 Robolectric 图形桩互相兼容撑着，任何一方升级都可能悄悄破坏它——
 * 而破坏后最先红的不是这些用例本身，是后续所有 UI 测试，排查成本很高。
 * 所以留两个最便宜的探针：基础文本、以及设置页大量使用的 SwitchPreference。
 *
 * 刻意不碰 blur / RuntimeShader：Robolectric 下那套必然回落到不透明，
 * 留着只会制造「测试通过但其实什么都没测」的假覆盖。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class, qualifiers = "w411dp-h891dp")
class MiuixRobolectricSmokeTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun miuixTextRenders() {
        rule.setContent {
            MiuixTheme {
                Text(text = "probe")
            }
        }
        rule.waitForIdle()
        rule.onNodeWithText("probe").assertIsDisplayed()
    }

    /** SwitchPreference 是设置页里出现最多的行类型，它渲染不了等于设置页的 UI 测试全线失效 */
    @Test
    fun miuixSwitchPreferenceRenders() {
        rule.setContent {
            MiuixTheme {
                SwitchPreference(
                    checked = false,
                    onCheckedChange = {},
                    title = "probe-switch",
                    summary = "副标题",
                )
            }
        }
        rule.waitForIdle()
        rule.onNodeWithText("probe-switch").assertIsDisplayed()
    }
}
