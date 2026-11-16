package com.llpop.koil

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import com.llpop.koil.util.ExportHelper
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowContentResolver
import org.robolectric.annotation.Config
import java.io.File

/**
 * 导出到公共下载目录（Robolectric + 假 MediaStore provider）。
 *
 * 这条路径的两个真实故障都不是"文件没生成"：
 * ① 写入失败后残留 0 字节条目（每失败一次多一条垃圾）；
 * ② 同名文件被系统改名成 `xxx(1).csv` 后，提示里还是用户原来那个名字，
 *    用户按提示去 Download 里找不到。
 * 所以这里既验证写入内容，也验证「失败回滚」与「取回真实文件名」。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class ExportHelperRobolectricTest {

    /** 只实现导出用到的那几个方法；内容落到临时目录里的真实文件 */
    private class FakeMediaProvider(private val root: File) : ContentProvider() {
        val names = mutableMapOf<Uri, String>()
        var failOpen = false
        private var seq = 0

        override fun onCreate() = true

        override fun insert(uri: Uri, values: ContentValues?): Uri? {
            seq++
            val u = Uri.withAppendedPath(uri, seq.toString())
            names[u] = values?.getAsString(MediaStore.Downloads.DISPLAY_NAME).orEmpty()
            pending[u] = values?.getAsInteger(MediaStore.Downloads.IS_PENDING) ?: 0
            return u
        }

        val pending = mutableMapOf<Uri, Int>()

        override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
            if (failOpen) throw java.io.FileNotFoundException("boom")
            val f = File(root, "f${uri.lastPathSegment}")
            return ParcelFileDescriptor.open(
                f,
                ParcelFileDescriptor.MODE_READ_WRITE or ParcelFileDescriptor.MODE_CREATE or
                    ParcelFileDescriptor.MODE_TRUNCATE,
            ).also { files[uri] = f }
        }

        val files = mutableMapOf<Uri, File>()

        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?,
        ): Cursor {
            val cursor = MatrixCursor(arrayOf(MediaStore.Downloads.DISPLAY_NAME))
            cursor.addRow(arrayOf(names[uri]))
            return cursor
        }

        override fun update(
            uri: Uri,
            values: ContentValues?,
            selection: String?,
            selectionArgs: Array<out String>?,
        ): Int {
            values?.getAsInteger(MediaStore.Downloads.IS_PENDING)?.let { pending[uri] = it }
            return 1
        }

        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
            names.remove(uri)
            files.remove(uri)?.delete()
            return 1
        }

        override fun getType(uri: Uri): String? = null
    }

    private lateinit var provider: FakeMediaProvider
    private lateinit var context: Context
    private lateinit var root: File

    /**
     * 注册假 provider。
     *
     * 必须**先** attachInfo 再注册：ContentProvider 处理请求时会校验「请求 uri 的 authority
     * 与自身 authority 是否一致」，而 authority 只在 attachInfo 时从 ProviderInfo 拷进来。
     * 只调 registerProviderInternal 时 provider 的 authority 是 null，
     * openOutputStream 会在校验处抛 SecurityException（现象是导出恒返回 null）。
     */
    private fun register(provider: ContentProvider) {
        provider.attachInfo(
            context,
            ProviderInfo().apply {
                authority = MediaStore.AUTHORITY
                exported = true
            },
        )
        ShadowContentResolver.registerProviderInternal(MediaStore.AUTHORITY, provider)
    }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        root = File(context.cacheDir, "media").apply { mkdirs() }
        provider = FakeMediaProvider(root)
        register(provider)
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun `写入成功返回可浏览路径且内容落盘`() {
        val path = ExportHelper.saveToDownloads(context, "KOiL-records-1.csv", "日期,里程\n")

        assertEquals("Download/KOiL/KOiL-records-1.csv", path)
        val written = provider.files.values.single().readText()
        assertEquals("日期,里程\n", written)
        // IS_PENDING 必须复位，否则文件管理器里看不到（系统把 pending 的文件隐藏）
        assertTrue(provider.pending.values.all { it == 0 })
    }

    @Test
    fun `系统改名后提示里用真实文件名`() {
        // 模拟 MediaStore 同名自动改名：插入时把 DISPLAY_NAME 换成 xxx(1).csv
        val renamed = object : ContentProvider() {
            override fun onCreate() = true
            override fun insert(uri: Uri, values: ContentValues?): Uri {
                val u = Uri.withAppendedPath(uri, "99")
                provider.names[u] = "KOiL-records-1(1).csv"
                return u
            }
            override fun openFile(uri: Uri, mode: String) = provider.openFile(uri, mode)
            override fun query(
                uri: Uri, projection: Array<out String>?,
                selection: String?, selectionArgs: Array<out String>?, sortOrder: String?,
            ): Cursor = MatrixCursor(arrayOf(MediaStore.Downloads.DISPLAY_NAME)).apply {
                addRow(arrayOf("KOiL-records-1(1).csv"))
            }
            override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 1
            override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 1
            override fun getType(uri: Uri) = null
        }
        register(renamed)

        val path = ExportHelper.saveToDownloads(context, "KOiL-records-1.csv", "x")

        assertEquals("Download/KOiL/KOiL-records-1(1).csv", path)
    }

    @Test
    fun `打开输出流失败时回滚刚插入的条目`() {
        provider.failOpen = true

        assertNull(ExportHelper.saveToDownloads(context, "KOiL-records-1.csv", "x"))
        // 不能留下 0 字节的行：这正是「每失败一次多一个空文件」的来源
        assertTrue(provider.names.isEmpty())
    }

    @Test
    fun `插入直接失败时返回 null 不抛异常`() {
        val empty = object : ContentProvider() {
            override fun onCreate() = true
            override fun insert(uri: Uri, values: ContentValues?): Uri? = null
            override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?) = null
            override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
            override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
            override fun getType(uri: Uri) = null
            override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor = throw java.io.FileNotFoundException()
        }
        register(empty)

        assertNull(ExportHelper.saveToDownloads(context, "x.json", "{}"))
    }
}
