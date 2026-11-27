package com.llpop.koil.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.llpop.koil.util.ContainerSizeProvider
import com.llpop.koil.util.rememberIsWideScreen
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 可行性探针：只回答两个问题。
 * 1. Robolectric 下 Compose 组合能不能起、`rememberIsWideScreen()` 是否随容器宽度正确翻转；
 * 2. miuix 0.9.5-rc01 的组件在 Robolectric 里能不能实例化并进语义树。
 *
 * 两个用例都绿，才值得在 UI 自动化测试上继续投入；任何一个红，说明这套方案在此环境下不可行，
 * 应改走 instrumented（模拟器）路线。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class, qualifiers = "w411dp-h891dp")
class ComposeUiProbeTest {

    @get:Rule
    val rule = createComposeRule()

    /** 容器宽度 411 / 599 / 601 / 1200 四种：阈值 600dp 上下各取两点 */
    @Test
    fun wideScreenThresholdFollowsContainerWidth() {
        var widthDp by mutableStateOf(411)
        var wide by mutableStateOf(false)

        rule.setContent {
            Box(Modifier.width(widthDp.dp).height(800.dp)) {
                ContainerSizeProvider(Modifier.fillMaxSize()) {
                    wide = rememberIsWideScreen()
                }
            }
        }

        rule.waitForIdle()
        assertFalse("411dp 应为窄屏", wide)

        rule.runOnIdle { widthDp = 599 }
        rule.waitForIdle()
        assertFalse("599dp 应为窄屏", wide)

        rule.runOnIdle { widthDp = 601 }
        rule.waitForIdle()
        assertTrue("601dp 应为宽屏", wide)

        rule.runOnIdle { widthDp = 1200 }
        rule.waitForIdle()
        assertTrue("1200dp 应为宽屏", wide)
    }

    /** miuix 组件进 Robolectric：能组合、能被语义树找到，且不因 native/RS 依赖崩 */
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
}
