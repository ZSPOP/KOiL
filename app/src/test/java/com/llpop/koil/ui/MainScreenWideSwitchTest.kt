package com.llpop.koil.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.llpop.koil.KOiLApplication
import com.llpop.koil.R
import com.llpop.koil.data.settings.AppSettings
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.koin.core.context.stopKoin
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 第三层试水：整屏 MainScreen 在窄↔宽之间切换会不会崩。
 *
 * 这是历史上真实闪过的场景（宽屏切换崩溃），前两层测不到它：
 * 宽屏判定本身对，但「判定翻转时整棵页面树重建」才是崩的地方。
 *
 * 试水阶段只断言一件事：切来切去不抛异常。崩就是红，不崩就是绿——不需要断言 rail 的视觉，
 * 因为 Robolectric 下玻璃组件本来就不出真实画面。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = KOiLApplication::class, qualifiers = "w411dp-h891dp")
class MainScreenWideSwitchTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    /**
     * 壁纸与模糊默认都是开，两个都得先关：
     * BgEffectBackground 在 Robolectric 下会跑 shader 动画帧，一条用例能耗到超时；
     * 模糊路径则全程回落到不透明，开着也测不到东西。
     */
    @Before
    fun disableHeavyVisuals() {
        val settings = GlobalContext.get().get<AppSettings>()
        runBlocking {
            settings.setWallpaperEnabled(false)
            settings.setBlurEnabled(false)
        }
    }

    @After
    fun tearDown() {
        runCatching { stopKoin() }
    }

    /**
     * 411 → 1200 → 411：单栏 → 双栏（Rail 创建）→ 单栏（Rail 销毁）。
     * 宽度靠外层 requiredWidth 喂，因为 MainScreen 内部是 fillMaxSize，读的是父级约束。
     */
    @Test
    fun switchingNarrowToWideAndBackDoesNotCrash() {
        var widthDp by mutableStateOf(411)

        rule.setContent {
            Box(Modifier.requiredWidth(widthDp.dp).fillMaxHeight()) {
                MainScreen(onOpenAboutHandled = {})
            }
        }

        rule.waitForIdle()

        // 窄屏：导航在底部悬浮栏。用它证明「窄屏态」真的成立，而不是宽度没喂进去
        val narrowCenterY = navItemCenterY()
        val rootHeight = rootHeight()
        assertTrue(
            "411dp 时导航项应落在下半屏（底部悬浮栏），实际 centerY=$narrowCenterY / rootH=$rootHeight",
            narrowCenterY > rootHeight * 0.6f,
        )

        rule.runOnIdle { widthDp = 1200 }
        rule.waitForIdle()

        // 宽屏：底部栏不组合（MainScreen 里 `if (railState == null)` 才画底栏），导航改由左侧 Rail 承担，
        // 所以同一导航项必须跑到上半屏。这条断言同时证明「宽度真的改变了布局分支」，
        // 否则整条用例只是「在 411dp 上来回晃了一次」，什么都测不到。
        val wideCenterY = navItemCenterY()
        assertTrue(
            "1200dp 时导航项应上移到左侧 Rail，实际 centerY=$wideCenterY / rootH=$rootHeight",
            wideCenterY in 0f..(rootHeight * 0.4f),
        )

        rule.runOnIdle { widthDp = 411 }
        rule.waitForIdle()

        // 切回来必须重新出现底部栏且不崩
        val backCenterY = navItemCenterY()
        assertTrue(
            "切回 411dp 后导航项应回到下半屏，实际 centerY=$backCenterY / rootH=$rootHeight",
            backCenterY > rootHeight * 0.6f,
        )
    }

    /** 取「录入」导航项的纵坐标（未合并语义树，Rail 与底栏的 item 都是独立文本节点） */
    private fun navItemCenterY(): Float {
        val label = ApplicationProvider.getApplicationContext<android.content.Context>()
            .getString(R.string.koil_nav_add)
        return rule.onAllNodesWithText(label, useUnmergedTree = true)
            .fetchSemanticsNodes()
            .firstOrNull()?.boundsInRoot?.center?.y ?: -1f
    }

    private fun rootHeight(): Float =
        rule.onRoot(useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.height
}
