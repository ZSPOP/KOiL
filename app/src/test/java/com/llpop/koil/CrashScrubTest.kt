package com.llpop.koil

import com.llpop.koil.util.CrashReporter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 崩溃日志路径脱敏（[CrashReporter.scrubPaths]）。
 *
 * 覆盖点：本机私有目录、`/data/data` 软链写法、外部存储用户号、非本应用路径不误伤、幂等。
 * 这些是「用户把 crashes.txt 发出去求助」时唯一会泄露的信息面。
 */
class CrashScrubTest {

    private val appDir = "/data/user/0/com.llpop.koil/files"

    @Test
    fun `本机私有目录替换成占位符`() {
        val raw = "java.io.FileNotFoundException: $appDir/crashes.log (No such file or directory)"
        val out = CrashReporter.scrubPaths(raw, appDir)
        assertFalse(out.contains(appDir))
        assertTrue(out.contains("<app-data>/crashes.log"))
    }

    @Test
    fun `data data 形式的包路径也归一`() {
        val out = CrashReporter.scrubPaths(
            "at a.b(Foo.kt:1) open /data/data/com.llpop.koil/databases/KOiL.db",
            appDir,
        )
        assertFalse(out.contains("com.llpop.koil/databases"))
        assertTrue(out.contains("/data/<pkg>/databases/KOiL.db"))
    }

    @Test
    fun `外部存储用户号归一`() {
        val out = CrashReporter.scrubPaths("/storage/emulated/0/Download/KOiL-crash.txt", appDir)
        assertEquals("/storage/emulated/<user>/Download/KOiL-crash.txt", out)
    }

    @Test
    fun `appDir 为空时不崩也不改内容`() {
        val raw = "java.lang.IllegalStateException: boom"
        assertEquals(raw, CrashReporter.scrubPaths(raw, ""))
    }

    @Test
    fun `无路径文本原样返回`() {
        val raw = "java.lang.NullPointerException\n\tat com.llpop.koil.Foo.bar(Foo.kt:42)"
        assertEquals(raw, CrashReporter.scrubPaths(raw, appDir))
    }

    @Test
    fun `非应用沙箱的 data 路径不被误伤`() {
        // /data/local/tmp 是 shell 可写目录，不属于任何应用沙箱，保留原样
        val raw = "open failed: EACCES /data/local/tmp/x"
        assertTrue(CrashReporter.scrubPaths(raw, appDir).contains("/data/local/tmp/x"))
    }

    @Test
    fun `重复脱敏结果不变`() {
        val raw = "at x $appDir/a.txt"
        val once = CrashReporter.scrubPaths(raw, appDir)
        assertEquals(once, CrashReporter.scrubPaths(once, appDir))
    }
}
