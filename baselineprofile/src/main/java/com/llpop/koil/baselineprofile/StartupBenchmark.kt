package com.llpop.koil.baselineprofile

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 冷启动量化：回答「baseline profile 到底值不值」。
 *
 * 三个测试是同一段测量的三个编译模式，用来夹出收益区间：
 * - [CompilationMode.None]：完全不 AOT 编译（解释执行 + JIT），代表「没有任何预热」的最差情况；
 * - [CompilationMode.Partial]：按设备上的 profile 做部分 AOT，也就是装了 baseline.prof 之后系统实际会走的路；
 * - [CompilationMode.Full]：全量 AOT，代表「所有方法都提前编译」的理论上限。
 *
 * 三者之差即为可优化空间；Partial 与 None 之差即 baseline profile 的实际收益。
 *
 * 跑法（需要 API 33+ 设备或模拟器，无人值守走 CI 的 macrobenchmark workflow）：
 *   ./gradlew :baselineprofile:connectedNonMinifiedReleaseAndroidTest \
 *     -Pandroid.testInstrumentationRunnerArguments.class=com.llpop.koil.baselineprofile.StartupBenchmark
 *
 * 结果在 baselineprofile/build/outputs/androidTest-results/ 与 additional_test_output 的 JSON 里。
 */
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun coldStartupNoCompilation() = measureColdStartup(CompilationMode.None())

    @Test
    fun coldStartupPartialCompilation() = measureColdStartup(CompilationMode.Partial())

    @Test
    fun coldStartupFullCompilation() = measureColdStartup(CompilationMode.Full())

    /**
     * 冷启动一次并采样 [StartupTimingMetric]。
     *
     * 迭代 3 次而不是默认 5：模拟器上每次迭代都要 force-stop 再冷启，
     * 3 次已能看出量级，多跑只会让 CI 时长翻倍。
     */
    private fun measureColdStartup(compilationMode: CompilationMode) {
        benchmarkRule.measureRepeated(
            packageName = PACKAGE,
            metrics = listOf(StartupTimingMetric()),
            iterations = 3,
            startupMode = StartupMode.COLD,
            compilationMode = compilationMode,
        ) {
            pressHome()
            startActivityAndWait()
        }
    }

    private companion object {
        const val PACKAGE = "com.llpop.koil"
    }
}
