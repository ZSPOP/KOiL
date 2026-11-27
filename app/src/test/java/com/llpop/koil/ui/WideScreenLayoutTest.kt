package com.llpop.koil.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
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

/**
 * 宽屏判定的正式回归用例（取代原先的一次性探针）。
 *
 * `rememberIsWideScreen()` 是全站唯一的分栏开关：MainScreen 用它决定建不建 GlassNavigationRail、
 * AdaptiveTopAppBar 用它决定大标题展开与否。阈值 600dp 一旦被改动，窄屏会多出侧栏、宽屏会退回单栏，
 * 而这两类问题在真机上只有特定设备才复现，靠手测很难拦住，所以钉在这里。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class, qualifiers = "w411dp-h891dp")
class WideScreenLayoutTest {

    @get:Rule
    val rule = createComposeRule()

    /**
     * 阈值是「达到即宽屏」，所以 600dp 必须为 true、599dp 为 false。
     *
     * 用 requiredWidth 而不是 width：@Config 的 w411dp 把窗口最大约束定死在 411dp，
     * 普通 width 会被父约束截回 411px，测出来的永远是窄屏。
     */
    @Test
    fun thresholdTakesEffectExactlyAt600dp() {
        var widthDp by mutableStateOf(599)
        var wide by mutableStateOf(false)

        rule.setContent {
            Box(Modifier.requiredWidth(widthDp.dp).height(800.dp)) {
                ContainerSizeProvider(Modifier.fillMaxSize()) {
                    wide = rememberIsWideScreen()
                }
            }
        }

        rule.waitForIdle()
        assertFalse("599dp 差 1dp 不算宽屏", wide)

        rule.runOnIdle { widthDp = 600 }
        rule.waitForIdle()
        assertTrue("600dp 恰好达到阈值，应为宽屏", wide)

        rule.runOnIdle { widthDp = 601 }
        rule.waitForIdle()
        assertTrue("601dp 应为宽屏", wide)

        rule.runOnIdle { widthDp = 1200 }
        rule.waitForIdle()
        assertTrue("1200dp 应为宽屏", wide)

        rule.runOnIdle { widthDp = 411 }
        rule.waitForIdle()
        assertFalse("411dp 为窄屏", wide)
    }

    /**
     * 没有 ContainerSizeProvider 时回退读窗口尺寸。
     *
     * 这条路径是真实存在的：Dialog、独立窗口、以及任何不在页面根下的组合都没有根部注入，
     * 此时如果回退逻辑坏了（比如读到 0 或者读错来源），弹层会永远按窄屏渲染。
     */
    @Test
    @Config(qualifiers = "w1200dp-h891dp")
    fun fallsBackToWindowWidthWhenNoProviderIsPresent() {
        var wide by mutableStateOf(false)

        rule.setContent {
            wide = rememberIsWideScreen()
        }

        rule.waitForIdle()
        assertTrue("窗口 1200dp 且无容器注入时应回退为宽屏", wide)
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun fallsBackToNarrowWhenWindowIsNarrowAndNoProviderIsPresent() {
        var wide by mutableStateOf(true)

        rule.setContent {
            wide = rememberIsWideScreen()
        }

        rule.waitForIdle()
        assertFalse("窗口 411dp 且无容器注入时应回退为窄屏", wide)
    }
}
