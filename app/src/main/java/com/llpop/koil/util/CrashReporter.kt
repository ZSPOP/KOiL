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
 * 全局未捕获异常捕获器（本地崩溃日志）。
 *
 * **本地累积 + 设置页查看/导出**。
 * - 崩溃线程只做一件事：把本次报告**追加**到私有目录的 crashes.log（一次小文件写，毫秒级），
 *   不做任何清理/跨进程操作——那些在崩溃现场都可能被进程回收打断；
 * - 下次启动在 IO 线程把累积日志裁剪到最近 [MAX_ENTRIES] 条（旧的自动挤掉）；
 * - 不自动写下载目录（用户不可见还占地方）；由设置页
 *   「崩溃日志」入口主动查看 / 一键导出到下载目录 / 清空。
 * - 日志只存手机私有目录，不上传任何远端。
 */
object CrashReporter {
    private const val LOG_FILE_NAME = "crashes.log"
    private const val EXPORT_FILE_NAME = "KOiL-crash.txt"

    /** 本地保留的最大条数，超过后最早的被挤掉 */
    const val MAX_ENTRIES = 10

    private fun logFile(context: Context) = File(context.applicationContext.filesDir, LOG_FILE_NAME)

    /**
     * 安装全局 handler（放在 Application.onCreate 最早处，覆盖启动期崩溃）。
     * 崩溃现场只允许一次 appendText：堆栈通常几十 KB，毫秒级完成。
     */
    fun install(context: Context) {
        val file = logFile(context)
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                file.appendText(buildReport(thread, throwable))
            }
            prev?.uncaughtException(thread, throwable) ?: run {
                throwable.printStackTrace()
            }
        }
    }

    /** 启动维护（必须在后台线程/IO 协程里）：把累积日志裁剪到最近 MAX_ENTRIES 条。 */
    fun trim(context: Context) {
        val log = logFile(context)
        runCatching {
            if (!log.exists()) return
            val entries = splitEntries(log.readText())
            if (entries.size > MAX_ENTRIES) {
                log.writeText(entries.takeLast(MAX_ENTRIES).joinToString(""))
            }
        }
    }

    /** 读全部日志（可能为空串）。设置页查看用 */
    fun readLogs(context: Context): String =
        runCatching { logFile(context).takeIf { it.exists() }?.readText().orEmpty() }.getOrDefault("")

    /** 清空日志。设置页「清空」用 */
    fun clear(context: Context) {
        runCatching { logFile(context).delete() }
    }

    /** 统计日志条数（按 "=== 时间 ===" 头计） */
    fun countEntries(text: String): Int =
        if (text.isBlank()) 0 else splitEntries(text).size

    /**
     * 导出全部日志到公共「下载」目录（Android 10+ MediaStore 免权限），文件名 KOiL-crash.txt。
     * 返回是否写入成功；失败不动本地日志，用户可重试。
     */
    fun exportToDownloads(context: Context): Boolean {
        val text = readLogs(context)
        if (text.isBlank()) return false
        return writeToDownloads(context, text)
    }

    private fun splitEntries(text: String): List<String> =
        // split 以行首 "=== " 为分隔符并消费掉分隔文本，片段必然不带前缀——恒走补前缀分支，无需判断
        text.split(Regex("(?m)^=== ")).filter { it.isNotBlank() }.map { "=== $it" }

    private fun buildReport(thread: Thread, throwable: Throwable): String {
        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))
        return buildString {
            appendLine()
            appendLine("=== ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())} ===")
            appendLine("thread=${thread.name}")
            append(sw.toString())
        }
    }

    /** 写公共下载目录（Android 10+ MediaStore 免权限）。返回是否写入成功 */
    private fun writeToDownloads(context: Context, text: String): Boolean {
        val resolver = context.contentResolver
        val downloadsUri = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        // 先清理已有的同名导出文件（系统对同名 insert 会生成 (1)(2)… 堆积），只保留最新一份
        runCatching {
            resolver.query(
                downloadsUri,
                arrayOf(MediaStore.MediaColumns._ID),
                "${MediaStore.MediaColumns.DISPLAY_NAME} = ?",
                arrayOf(EXPORT_FILE_NAME),
                null,
            )?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getLong(0)
                    resolver.delete(ContentUris.withAppendedId(downloadsUri, id), null, null)
                }
            }
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, EXPORT_FILE_NAME)
            put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            // 与 ExportHelper 一致：写入期间置 IS_PENDING，避免其它应用/媒体库看到写了一半的文件
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = runCatching { resolver.insert(downloadsUri, values) }.getOrNull() ?: return false
        val written = runCatching {
            resolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) } != null
        }.getOrDefault(false)
        if (written) {
            runCatching {
                resolver.update(
                    uri,
                    ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                    null,
                    null,
                )
            }
        }
        // 插入了行却写不进去：删掉这一行，避免下载目录堆积 0 字节占位
        if (!written) runCatching { resolver.delete(uri, null, null) }
        return written
    }
}
