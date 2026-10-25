package com.llpop.koil

import android.content.Context
import android.os.Process
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 全局崩溃捕获：将未捕获异常栈写入应用私有 files 目录，
 * 便于无 logcat 环境下定位闪退根因。
 * 崩溃文件：/sdcard/Android/data/com.llpop.koil/files/crash-yyyyMMdd-HHmmss.txt
 */
object CrashHandler {

    fun install(context: Context) {
        val filesDir = context.filesDir
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
                val file = File(filesDir, "crash-$timestamp.txt")
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                file.writeText(
                    "KOiL Crash Report\n" +
                        "Time: ${Date()}\n" +
                        "PID: ${Process.myPid()}\n" +
                        "Thread: ${thread.name}\n" +
                        "===== Stack Trace =====\n" +
                        sw.toString() + "\n" +
                        "===== Cause Chain =====\n" +
                        buildCauseChain(throwable)
                )
            } catch (_: Throwable) {
                // 兜底：崩溃处理器自身不能再抛
            } finally {
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }
    }

    private fun buildCauseChain(t: Throwable): String {
        val sb = StringBuilder()
        var cur: Throwable? = t
        var depth = 0
        while (cur != null && depth < 10) {
            sb.append("[$depth] ${cur.javaClass.name}: ${cur.message}\n")
            cur = cur.cause
            depth++
        }
        return sb.toString()
    }
}
