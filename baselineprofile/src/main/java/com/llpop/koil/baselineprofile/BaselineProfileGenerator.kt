package com.llpop.koil.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * KOiL 的热点路径录制：冷启动 → 首页 → 横滑三页 → 列表滚动。
 *
 * 只覆盖「每次打开都会走」的路径，不覆盖设置/关于这类低频页：
 * profile 记录的是方法级热点，低频页进了 profile 只会稀释启动部分的权重。
 *
 * 用法（需要一台 API 33+ 设备或模拟器，`adb devices` 能看到）：
 *   ./gradlew :baselineprofile:pixel6Api33BenchmarkRelease  （Gradle 托管设备，需 KVM）
 * 或连真机后：
 *   ./gradlew :baselineprofile:connectedBenchmarkRelease
 * 生成产物写在 app/src/main/baselineProfiles/ 之外的生成目录，由插件自动并入 release 包。
 *
 * 改动了启动路径或首页/总览的组合结构后需要重录，否则 profile 会与代码漂移。
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() {
        rule.collect(packageName = PACKAGE) {
            pressHome()
            startActivityAndWait()
            // 等到 KOiL 自己的窗口真正出现，而不是系统桌面残留
            device.wait(Until.hasObject(By.pkg(PACKAGE).depth(0)), 5_000)
            device.waitForIdle()

            // 首页三段横滑：把另外两页的组合也录进去
            repeat(2) {
                device.swipe(
                    device.displayWidth * 4 / 5, device.displayHeight / 2,
                    device.displayWidth / 5, device.displayHeight / 2,
                    12,
                )
                device.waitForIdle()
            }

            // 总览列表滚动：记录卡展开、派生计算、玻璃面料都在这一段
            repeat(3) {
                device.swipe(
                    device.displayWidth / 2, device.displayHeight * 3 / 4,
                    device.displayWidth / 2, device.displayHeight / 4,
                    12,
                )
                device.waitForIdle()
            }
        }
    }

    private companion object {
        const val PACKAGE = "com.llpop.koil"
    }
}
