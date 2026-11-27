package com.llpop.koil.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.llpop.koil.ui.component.KOiLSegmentedTabRow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 第二层：全 App 唯一的分段控件（数据总览范围筛选、油号切换、导入预览分类页签都用它）。
 *
 * 测它是因为它同时被多处共用：接口一改（参数顺序、回调语义）就是三处一起坏，
 * 而坏法往往很安静——标签还在、点击没反应，人工测要切三个页面才发现。
 *
 * 刻意不碰材质与动画：玻璃轨道在 Robolectric 下必然回落到薄纱路径，
 * 断言「看起来像玻璃」只会制造假覆盖。这里只钉参数到行为的映射。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class, qualifiers = "w411dp-h891dp")
class KOiLSegmentedTabRowTest {

    @get:Rule
    val rule = createComposeRule()

    private val tabs = listOf("A", "B", "C")

    @Test
    fun everyTabLabelIsRendered() {
        rule.setContent {
            MiuixTheme {
                KOiLSegmentedTabRow(tabs = tabs, selectedIndex = 0, onSelect = {})
            }
        }
        rule.waitForIdle()

        tabs.forEach { label ->
            rule.onNodeWithText(label).assertIsDisplayed()
        }
    }

    /** 点未选中项，回调拿到的必须是它在列表里的原始下标，而不是「切换了几次」 */
    @Test
    fun clickingUnselectedTabReportsItsOwnIndex() {
        val picked = mutableListOf<Int>()
        rule.setContent {
            MiuixTheme {
                KOiLSegmentedTabRow(tabs = tabs, selectedIndex = 0, onSelect = { picked += it })
            }
        }
        rule.waitForIdle()

        rule.onNodeWithText("C").performClick()

        rule.runOnIdle {
            assertEquals("点第三个标签应回调下标 2", listOf(2), picked)
        }
    }

    /** 回调驱动状态：点击后把 selectedIndex 写回控件，界面跟着换选中项且不崩 */
    @Test
    fun selectionFollowsUiState() {
        var selected by mutableIntStateOf(0)
        rule.setContent {
            MiuixTheme {
                KOiLSegmentedTabRow(
                    tabs = tabs,
                    selectedIndex = selected,
                    onSelect = { selected = it },
                )
            }
        }
        rule.waitForIdle()

        rule.onNodeWithText("B").performClick()
        rule.waitForIdle()

        rule.runOnIdle {
            assertEquals("点 B 后受控状态应为 1", 1, selected)
        }

        // 再点 A：验证状态是双向跟随，而不是只认第一次点击
        rule.onNodeWithText("A").performClick()
        rule.waitForIdle()
        rule.runOnIdle {
            assertEquals("再点 A 后受控状态应回到 0", 0, selected)
        }
    }

    /** 空列表是真实场景（范围筛选还没有可选值时），组件内部直接 return：既不崩，也不留下任何标签节点 */
    @Test
    fun emptyTabsComposesWithoutCrash() {
        rule.setContent {
            MiuixTheme {
                KOiLSegmentedTabRow(tabs = emptyList(), selectedIndex = 0, onSelect = {})
            }
        }
        rule.waitForIdle()

        rule.onAllNodesWithText("A").assertCountEquals(0)
    }
}
