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
 * 全局未捕获异常捕获器（静默诊断兜底）。
 *
 * v1.6.3 改造（原实现在崩溃线程里做整套 MediaStore 读写）：
 * 崩溃线程只剩「拼字符串 + 写一个私有文件」——毫秒级，且不依赖 ContentResolver。
 * 原实现要在崩溃线程里 query → 逐条 delete → insert → openOutputStream 写下载目录（4 次跨进程 IPC），
 * 任一环节被系统拖住，进程先被回收，日志就整份丢了；而且在崩溃现场做磁盘清理本身就不该发生。
 * 现在下载目录那份（KOiL-crash.txt，用户可直接打开/转发）由**下次启动**在 IO 线程补写，成功才删私有副本。
 */
object CrashReporter {
    private const val PRIVATE_FILE_NAME = "crash.txt"
    private const val PUBLIC_FILE_NAME = "KOiL-crash.txt"

    /**
     * 安装全局 handler（放在 Application.onCreate 最早处，覆盖启动期崩溃）。
     * 注意：不再在这里删除上次的私有文件——那份是待导出队列，删了就丢日志。
     */
    fun install(context: Context) {
        val crashFile = File(context.applicationContext.filesDir, PRIVATE_FILE_NAME)
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                // 只做这一件事：一次小文件写。堆栈通常几十 KB，耗时在毫秒级
                crashFile.writeText(buildReport(thread, throwable))
            }
            prev?.uncaughtException(thread, throwable) ?: run {
                throwable.printStackTrace()
            }
        }
    }

    /**
     * 启动时调用（必须在后台线程/IO 协程里）：把上次崩溃留下的私有记录补写到公共「下载」目录。
     * 导出成功才删除私有副本；失败则保留，下次启动再试（下载目录只保留最新一份）。
     */
    fun flushPendingCrashLog(context: Context) {
        val crashFile = File(context.filesDir, PRIVATE_FILE_NAME)
        if (!crashFile.exists()) return
        val text = runCatching { crashFile.readText() }.getOrNull()?.takeIf { it.isNotBlank() }
        if (text == null) {
            crashFile.delete()
            return
        }
        if (writeToDownloads(context, text)) crashFile.delete()
    }

    private fun buildReport(thread: Thread, throwable: Throwable): String {
        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))
        return buildString {
            appendLine("=== ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())} ===")
            appendLine("thread=${thread.name}")
            append(sw.toString())
        }
    }

    /** 写公共下载目录（Android 10+ MediaStore 免权限）。返回是否写入成功 */
    private fun writeToDownloads(context: Context, text: String): Boolean {
        val resolver = context.contentResolver
        val downloadsUri = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        // 先清理历史同名崩溃文件（系统对同名 insert 会生成 (1)(2)… 堆积），只保留最新一份
        runCatching {
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
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, PUBLIC_FILE_NAME)
            put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }
        val uri = runCatching { resolver.insert(downloadsUri, values) }.getOrNull() ?: return false
        val written = runCatching {
            resolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) } != null
        }.getOrDefault(false)
        // 插入了行却写不进去：删掉这一行，避免下载目录堆积 0 字节占位
        if (!written) runCatching { resolver.delete(uri, null, null) }
        return written
    }
}
