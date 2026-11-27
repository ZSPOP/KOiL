package com.llpop.koil.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.llpop.koil.R
import com.llpop.koil.ui.component.FullTankSwitchRow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 「本次加满（跳枪）」开关行：录入页 / 编辑弹窗 / 补录弹窗三处共用。
 *
 * 它是油耗结算的开关——checked 传错会让「满箱→满箱」区间多算或少算一段，
 * 而界面上勾没勾看起来都对，是典型的静默错误。所以这里钉三件事：
 * 文案真的渲染出来、点击回调传的是取反值（不是常量）、语义状态与 checked 一致。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class, qualifiers = "w411dp-h891dp")
class FullTankSwitchRowTest {

    @get:Rule
    val rule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val title = context.getString(R.string.koil_add_full_tank)
    private val hint = context.getString(R.string.koil_add_full_tank_hint)

    @Test
    fun rendersTitleAndHint() {
        rule.setContent {
            MiuixTheme {
                FullTankSwitchRow(checked = false, onCheckedChange = {})
            }
        }
        rule.waitForIdle()

        rule.onNodeWithText(title).assertIsDisplayed()
        rule.onNodeWithText(hint).assertIsDisplayed()
    }

    /** 未勾选时点一下，回调必须是 true；勾选时点一下，必须是 false。传常量就会挂在这一条。 */
    @Test
    fun clickReportsInvertedValue() {
        var checked by mutableStateOf(false)
        val seen = mutableListOf<Boolean>()
        rule.setContent {
            MiuixTheme {
                FullTankSwitchRow(checked = checked, onCheckedChange = { seen += it; checked = it })
            }
        }
        rule.waitForIdle()

        rule.onNodeWithText(title).performClick()
        rule.waitForIdle()
        rule.runOnIdle { assertEquals("未勾选点击应上报 true", listOf(true), seen) }

        rule.onNodeWithText(title).performClick()
        rule.waitForIdle()
        rule.runOnIdle { assertEquals("已勾选再点应上报 false", listOf(true, false), seen) }
    }

    /** 无障碍状态：TalkBack 读的 toggleableState 必须跟着 checked 走，否则读出来是错的 */
    @Test
    fun toggleableStateReflectsChecked() {
        var checked by mutableStateOf(false)
        rule.setContent {
            MiuixTheme {
                FullTankSwitchRow(checked = checked, onCheckedChange = { checked = it })
            }
        }
        rule.waitForIdle()

        rule.onNodeWithText(title)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.Off))

        rule.onNodeWithText(title).performClick()
        rule.waitForIdle()

        rule.onNodeWithText(title)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.On))
    }
}
