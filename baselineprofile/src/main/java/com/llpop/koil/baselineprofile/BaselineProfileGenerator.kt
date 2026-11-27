package com.llpop.koil.baselineprofile

import android.os.ParcelFileDescriptor
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
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
 * 用法：
 *   本地有设备（`adb devices` 能看到）：./gradlew :baselineprofile:collectNonMinifiedReleaseBaselineProfile
 *   没有设备：走 .github/workflows/baseline-profile.yml，CI 起 API 33 模拟器录制并上传 artifact
 * 产物落在 app/src/release/generated/baselineProfiles/，由插件并入 release 包。
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
            // 录制环境是全新安装：首启必然弹权限请求，弹窗把前台抢走后首屏根节点就对不上了。
            // 先授予运行时权限，让录制起点等于「用户长期使用」的状态。
            grantRuntimePermissions()

            pressHome()
            startActivityAndWait()
            // 等 KOiL 自己的窗口真正出现，而不是系统桌面残留。
            // 冷启动在软件渲染模拟器上要十几秒（实测首帧 +11s233ms），5s 会误判成超时；
            // 不限定 depth，避免窗口被系统浮层部分遮挡时匹配不到。
            check(device.wait(Until.hasObject(By.pkg(PACKAGE)), 20_000)) {
                "KOiL 首屏 20s 内没出现：可能启动崩溃、包名不匹配或应用没装上"
            }
            device.waitForIdle(FOREGROUND_IDLE_MS)

            // 首页三段横滑：把另外两页的组合也录进去
            repeat(2) {
                device.swipe(
                    device.displayWidth * 4 / 5, device.displayHeight / 2,
                    device.displayWidth / 5, device.displayHeight / 2,
                    12,
                )
                device.waitForIdle(FOREGROUND_IDLE_MS)
            }

            // 总览列表滚动：记录卡展开、派生计算、玻璃面料都在这一段
            repeat(3) {
                device.swipe(
                    device.displayWidth / 2, device.displayHeight * 3 / 4,
                    device.displayWidth / 2, device.displayHeight / 4,
                    12,
                )
                device.waitForIdle(FOREGROUND_IDLE_MS)
            }
        }
    }

    /**
     * 用 shell 身份授予运行时权限。
     *
     * 目标包已由 benchmark 规则在 collect 开始时装好，所以在 lambda 里执行即刻生效。
     * 只处理 KOiL manifest 里那两个运行时权限，其余（INTERNET 等）安装即授予。
     */
    private fun grantRuntimePermissions() {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        RUNTIME_PERMISSIONS.forEach { permission ->
            // 读掉命令输出再关，避免缓冲区残留
            automation.executeShellCommand("pm grant $PACKAGE $permission").use { pfd ->
                ParcelFileDescriptor.AutoCloseInputStream(pfd).use { it.readBytes() }
            }
        }
    }

    private companion object {
        const val PACKAGE = "com.llpop.koil"
        // idle 等待上限：模拟器上 Compose 的持续动画/帧回调容易让默认 10s 卡在边界
        const val FOREGROUND_IDLE_MS = 10_000L
        // KOiL manifest 里仅有的两个运行时权限；不授就会在首启弹窗挡住前台
        val RUNTIME_PERMISSIONS = listOf(
            "android.permission.ACCESS_COARSE_LOCATION",
            "android.permission.POST_NOTIFICATIONS",
        )
    }
}
