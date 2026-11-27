package com.llpop.koil.ui

import android.content.Context
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.llpop.koil.R
import com.llpop.koil.ui.component.KOiLDateField
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 日期字段：补录 / 编辑 / 首页录入三处共用，靠 `onDateSelected` 是否为 null 切换「可选」与「只读」两种形态。
 *
 * 这个分支是重构里最容易出问题的地方——传了回调的字段点不开、或者只读字段反而可点，
 * 人工测要逐个页面点才看得出来，所以钉住「回调 → 可点性」与「点日期 → 回传值」的对应关系。
 *
 * 注意：`clickable(enabled = false)` 依然会挂 OnClick 动作，只是节点 disabled，
 * 因此这里必须断言「可点的可用节点」数量，而不是「有没有 OnClick」。
 *
 * 宿主说明：日历面板走 `GlassPopupOverlay` → `PopupLayout(renderInRootScaffold = true)`，
 * 需要一个 miuix 根 `Scaffold` 作挂载宿主。只有 `MiuixTheme` 时面板整块不渲染（实测语义树里
 * 一个节点都没有），因此涉及面板的用例统一套 `MiuixTheme { Scaffold { ... } }`。
 *
 * 「今天」快捷按钮只移动选中（`selected = today`），回传由点日期格子触发，
 * 所以回调类用例点格子，不点该按钮。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class, qualifiers = "w411dp-h891dp")
class KOiLDateFieldTest {

    @get:Rule
    val rule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val label = context.getString(R.string.koil_add_date)
    private val todayLabel = context.getString(R.string.koil_cal_today)
    private val date = "2026-11-27"

    private fun host(content: @Composable () -> Unit) {
        rule.setContent {
            MiuixTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { _ ->
                    content()
                }
            }
        }
    }

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

    /** 点字段 → 日历面板真的挂载出来（锚定浮层，超出字段但仍是同一棵语义树） */
    @Test
    fun clickingFieldOpensCalendar() {
        var picked: String? = null
        host { KOiLDateField(date = date, onDateSelected = { picked = it }) }
        rule.waitForIdle()

        rule.onNodeWithText(date).performClick()
        rule.mainClock.advanceTimeBy(600)
        rule.waitForIdle()

        rule.onNodeWithText(todayLabel).assertIsDisplayed()
        rule.runOnIdle { assertEquals("仅打开面板不应回传日期", null, picked) }
    }

    /** 选一天：回传格式必须是 yyyy-MM-dd，且面板随后收起（不能留在屏幕上继续吞点击） */
    @Test
    fun pickingDayReportsThatDateAndClosesCalendar() {
        val picked = mutableListOf<String>()
        host { KOiLDateField(date = date, onDateSelected = { picked += it }) }
        rule.waitForIdle()

        rule.onNodeWithText(date).performClick()
        rule.mainClock.advanceTimeBy(600)
        rule.waitForIdle()

        // 11 月网格里 10–25 只属于本月，不会与上月尾部/下月头部的灰显数字重名
        rule.onNodeWithText("15").performClick()
        rule.mainClock.advanceTimeBy(600)
        rule.waitForIdle()

        rule.runOnIdle { assertEquals("点 15 号应回传该日期", listOf("2026-11-15"), picked) }
        rule.onAllNodes(hasClickAction() and isEnabled()).assertCountEquals(1)
    }
}
