package com.llpop.koil.util

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore

/**
 * 导出文件存储助手：把导出内容保存到**用户可浏览**的公共位置（不再写入应用私有 files 目录）。
 * minSdk 33：统一走 MediaStore 写入公共 Download/KOiL/（无需任何权限，系统文件管理器可见）。
 * 返回用户可浏览的路径描述（如 Download/KOiL/xxx.csv）；失败返回 null。
 */
object ExportHelper {

    fun saveToDownloads(context: Context, fileName: String, content: String): String? = try {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, mimeType(fileName))
            put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/KOiL")
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
        resolver.openOutputStream(uri)?.use { it.write(content.toByteArray(Charsets.UTF_8)) }
            ?: return null
        "Download/KOiL/$fileName"
    } catch (_: Exception) {
        null
    }

    private fun mimeType(fileName: String): String = when {
        fileName.endsWith(".csv", ignoreCase = true) -> "text/csv"
        fileName.endsWith(".json", ignoreCase = true) -> "application/json"
        else -> "application/octet-stream"
    }
}
