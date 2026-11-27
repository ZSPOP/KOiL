package com.llpop.koil.ui

import android.content.Context
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.llpop.koil.R
import com.llpop.koil.ui.component.KOiLDateField
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 日期字段：补录 / 编辑 / 首页录入三处共用，靠 `onDateSelected` 是否为 null 切换「可选」与「只读」两种形态。
 *
 * 这个分支是重构里最容易出问题的地方——传了回调的字段点不开、或者只读字段反而可点，
 * 人工测要逐个页面点才看得出来，所以钉住「回调 → 可点性」的对应关系。
 *
 * 注意：`clickable(enabled = false)` 依然会挂 OnClick 动作，只是节点 disabled，
 * 因此这里必须断言「可点的可用节点」数量，而不是「有没有 OnClick」。
 *
 * 边界说明：日期字段的日历面板走 `GlassPopupOverlay` →
 * `PopupLayout(renderInRootScaffold = true)`，需要一个真实的 miuix 根 Scaffold 做挂载宿主。
 * Robolectric 里单层 setContent 没有这个宿主，面板不会渲染，因此「打开面板 / 确认回传日期」
 * 这段只能在设备上验证，单元测试不假装覆盖它；下面只断言字段自身的可点性。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class, qualifiers = "w411dp-h891dp")
class KOiLDateFieldTest {

    @get:Rule
    val rule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val label = context.getString(R.string.koil_add_date)
    private val date = "2026-11-27"

    /** 只读形态（首页录入）：日期照常显示，但没有任何「可点的可用节点」 */
    @Test
    fun readOnlyFieldCannotBeClicked() {
        rule.setContent {
            MiuixTheme {
                KOiLDateField(date = date, onDateSelected = null)
            }
        }
        rule.waitForIdle()

        rule.onNodeWithText(label).assertIsDisplayed()
        rule.onNodeWithText(date).assertIsDisplayed()
        rule.onAllNodes(hasClickAction() and isEnabled()).assertCountEquals(0)
    }

    /** 可选形态（补录/编辑）：字段必须暴露且只暴露一个可点节点，否则用户点不开日历 */
    @Test
    fun editableFieldIsClickable() {
        rule.setContent {
            MiuixTheme {
                KOiLDateField(date = date, onDateSelected = {})
            }
        }
        rule.waitForIdle()

        rule.onNodeWithText(date).assertIsDisplayed()
        rule.onAllNodes(hasClickAction() and isEnabled()).assertCountEquals(1)
    }
}
