package com.llpop.koil

import com.llpop.koil.ui.screen.settings.csvEscape
import com.llpop.koil.ui.screen.settings.decodeTextSmart
import com.llpop.koil.ui.screen.settings.splitCsvRecords
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * CSV 导入的两条边界：编码探测与整文件切分。
 *
 * 两者都是「导出 → 用 Excel 改数 → 导回来」这条主路径上的坑：
 * 编码探测错了报「缺少必需列」（指向错误方向，用户查不到原因）；
 * 切分错了把一条记录劈成两行（前半截列数不足）。
 */
class CsvImportTest {

    // ===== 编码探测 =====

    @Test
    fun `带 BOM 的 UTF-8 正常解码且不留 BOM 字符`() {
        val text = "加油日期,本次里程\n2026-01-01,10000"
        val bytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + text.toByteArray(Charsets.UTF_8)
        val out = decodeTextSmart(bytes)
        assertEquals(text, out)
        assertTrue("BOM 不应残留", out.first() != '\uFEFF')
    }

    @Test
    fun `无 BOM 的 UTF-8 正常解码`() {
        val text = "加油日期,本次里程\n2026-01-01,10000"
        assertEquals(text, decodeTextSmart(text.toByteArray(Charsets.UTF_8)))
    }

    @Test
    fun `GBK 文件不会被当成 UTF-8 解成乱码`() {
        // Windows Excel「另存为 CSV」的默认落盘编码，也是最典型的用户文件
        val text = "加油日期,本次里程,实付金额\n2026-01-01,10000,300"
        val gbk = text.toByteArray(charset("GBK"))
        val out = decodeTextSmart(gbk)
        assertEquals(text, out)
        assertTrue("不应出现替换字符", !out.contains('\uFFFD'))
    }

    @Test
    fun `空文件返回空串不抛异常`() {
        assertEquals("", decodeTextSmart(ByteArray(0)))
    }

    @Test
    fun `UTF-16LE 带 BOM 也能解`() {
        val text = "加油日期"
        val bytes = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + text.toByteArray(Charsets.UTF_16LE)
        assertEquals(text, decodeTextSmart(bytes))
    }

    @Test
    fun `纯 ASCII 走 UTF-8 分支`() {
        assertEquals("date,mileage", decodeTextSmart("date,mileage".toByteArray(Charsets.UTF_8)))
    }

    // ===== 整文件切分 =====

    @Test
    fun `普通记录按行切分`() {
        val recs = splitCsvRecords("a,b,c\n1,2,3\n")
        assertEquals(2, recs.size)
        assertArrayEquals(arrayOf("a", "b", "c"), recs[0].toTypedArray())
        assertArrayEquals(arrayOf("1", "2", "3"), recs[1].toTypedArray())
    }

    @Test
    fun `引号内的换行不切行`() {
        // 关键回归：以前先 lines() 再逐行解析，这条记录会被劈成两行，
        // 前半截列数不足进 format_error —— 而 csvEscape 明明给字段加过引号
        val recs = splitCsvRecords("a,\"x\ny\",c\n1,2,3\n")
        assertEquals("应只有两条记录", 2, recs.size)
        assertArrayEquals(arrayOf("a", "x\ny", "c"), recs[0].toTypedArray())
    }

    @Test
    fun `引号内的逗号不切列`() {
        val recs = splitCsvRecords("a,\"x,y\",c")
        assertArrayEquals(arrayOf("a", "x,y", "c"), recs[0].toTypedArray())
    }

    @Test
    fun `双引号转义还原为一个引号`() {
        val recs = splitCsvRecords("a,\"he said \"\"hi\"\"\",c")
        assertArrayEquals(arrayOf("a", "he said \"hi\"", "c"), recs[0].toTypedArray())
    }

    @Test
    fun `CRLF 只算一个行尾`() {
        val recs = splitCsvRecords("a,b\r\n1,2\r\n")
        assertEquals(2, recs.size)
        assertArrayEquals(arrayOf("a", "b"), recs[0].toTypedArray())
        assertArrayEquals(arrayOf("1", "2"), recs[1].toTypedArray())
    }

    @Test
    fun `末尾没有换行时最后一条照常收下`() {
        val recs = splitCsvRecords("a,b\n1,2")
        assertEquals(2, recs.size)
        assertArrayEquals(arrayOf("1", "2"), recs[1].toTypedArray())
    }

    @Test
    fun `空行整条丢弃`() {
        val recs = splitCsvRecords("a,b\n\n\n1,2\n")
        assertEquals(2, recs.size)
    }

    @Test
    fun `空字段保留位置`() {
        val recs = splitCsvRecords("a,,c")
        assertArrayEquals(arrayOf("a", "", "c"), recs[0].toTypedArray())
    }

    @Test
    fun `转义与切分严格互逆_含换行与逗号`() {
        val record = listOf("2026-01-01", "备注：a,b", "含\n换行", "普通")
        val line = record.joinToString(",") { csvEscape(it) }
        val recs = splitCsvRecords(line)
        assertEquals(1, recs.size)
        assertEquals("导出再导入必须等价", record, recs[0])
    }
}
