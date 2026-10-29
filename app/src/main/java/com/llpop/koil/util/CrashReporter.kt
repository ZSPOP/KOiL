package com.llpop.koil.util

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 全局未捕获异常捕获器（静默诊断兜底）：
 * - install：崩溃时把完整堆栈写入两个位置——公共「下载」目录 KOiL-crash.txt（用户可直接
 *   打开/转发给开发侧查阅）+ 应用私有 filesDir/crash.txt（兜底）；随后交给系统默认 handler。
 * - 不弹 Toast、不打扰用户；私有文件在下次启动时清理，只保留最近一次崩溃。
 */
object CrashReporter {
    private lateinit var crashFile: File
    private const val PUBLIC_FILE_NAME = "KOiL-crash.txt"

    fun install(context: Context) {
        crashFile = File(context.filesDir, "crash.txt")
        // 清理上一次的私有崩溃记录，只保留最近一次
        if (crashFile.exists()) crashFile.delete()
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                val sb = StringBuilder()
                sb.appendLine("=== ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())} ===")
                sb.appendLine("thread=${thread.name}")
                sb.append(sw.toString())
                val text = sb.toString()
                // 1) 应用私有目录（兜底）
                crashFile.writeText(text)
                // 2) 公共下载目录（用户可直接打开）：Android 10+ 用 MediaStore 免权限
                runCatching {
                    val resolver = context.contentResolver
                    val downloadsUri = MediaStore.Downloads.EXTERNAL_CONTENT_URI
                    // v1.5.36 P5：先清理历史同名崩溃文件（系统对同名 insert 会生成 (1)(2)… 堆积），只保留最新一份
                    resolver.query(
                        downloadsUri,
                        arrayOf(MediaStore.MediaColumns._ID),
                        "${MediaStore.MediaColumns.DISPLAY_NAME} = ?",
                        arrayOf(PUBLIC_FILE_NAME),
                        null,
                    )?.use { c ->
                        while (c.moveToNext()) {
                            val id = c.getLong(0)
                            resolver.delete(ContentUris.withAppendedId(downloadsUri, id), null, null)
                        }
                    }
                    val values = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, PUBLIC_FILE_NAME)
                        put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    }
                    resolver.insert(downloadsUri, values)?.let { uri ->
                        resolver.openOutputStream(uri)?.use { out ->
                            out.write(text.toByteArray())
                        }
                    }
                }
            }
            prev?.uncaughtException(thread, throwable) ?: run {
                throwable.printStackTrace()
            }
        }
    }
}
