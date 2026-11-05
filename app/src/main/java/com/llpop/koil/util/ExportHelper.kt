package com.llpop.koil.util

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore

/**
 * 导出文件存储助手：把导出内容保存到**用户可浏览**的公共位置（不再写入应用私有 files 目录）。
 * minSdk 33：统一走 MediaStore 写入公共 Download/KOiL/（无需任何权限，系统文件管理器可见）。
 * 返回用户可浏览的路径描述（如 Download/KOiL/xxx.csv）；失败返回 null。
 *
 * v1.6.2：
 * ① 用 IS_PENDING 标记写入中，避免文件管理器在写一半时看到 0 字节或半截文件；
 * ② 写入失败时删除刚插入的 MediaStore 行，不再遗留空文件（原实现每失败一次就多一个 0 字节条目）。
 */
object ExportHelper {

    fun saveToDownloads(context: Context, fileName: String, content: String): String? {
        val resolver = context.contentResolver
        var inserted: Uri? = null
        return try {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, mimeType(fileName))
                put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/KOiL")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
            inserted = uri
            val stream = resolver.openOutputStream(uri)
            if (stream == null) {
                resolver.delete(uri, null, null)
                return null
            }
            stream.use { it.write(content.toByteArray(Charsets.UTF_8)) }
            resolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
            "Download/KOiL/$fileName"
        } catch (_: Exception) {
            inserted?.let { runCatching { resolver.delete(it, null, null) } }
            null
        }
    }

    private fun mimeType(fileName: String): String = when {
        fileName.endsWith(".csv", ignoreCase = true) -> "text/csv"
        fileName.endsWith(".json", ignoreCase = true) -> "application/json"
        else -> "application/octet-stream"
    }
}
