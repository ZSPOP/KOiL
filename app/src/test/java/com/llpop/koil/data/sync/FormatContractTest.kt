package com.llpop.koil.data.sync

import com.llpop.koil.data.local.entity.KOiLRecordEntity
import com.llpop.koil.ui.screen.settings.CSV_RECORD_HEADER
import com.llpop.koil.ui.screen.settings.csvEscape
import com.llpop.koil.ui.screen.settings.parseCsvLine
import com.llpop.koil.ui.screen.settings.parseFullTankColumn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 格式契约回归（M-05/M-06/M-21）：
 * - CSV 导出转义与解析严格互逆（导出 → 再导入等价）；
 * - 备份 JSON 的 version 写入后能被解析读出；
 * - buildBackup 对乱序输入按 reoilDate 排序，派生字段不因调用方顺序失真。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class FormatContractTest {

    // ===== M-06：CSV 转义回环 =====

    private fun roundTrip(vararg fields: String): List<String> =
        parseCsvLine(fields.joinToString(",") { csvEscape(it) })

    @Test
    fun `普通字段回环不变`() {
        val row = listOf("2026-01-15", "92号", "89050", "正常", "广东省")
        assertEquals(row, roundTrip(*row.toTypedArray()))
    }

    @Test
    fun `含逗号字段被包裹且回环等价`() {
        val row = listOf("含,逗号", "含,两个,逗号", "普通")
        assertEquals(row, roundTrip(*row.toTypedArray()))
    }

    @Test
    fun `含引号字段被包裹且内部引号翻倍回环等价`() {
        val row = listOf("he said \"hi\"", "普通")
        assertEquals(row, roundTrip(*row.toTypedArray()))
    }

    @Test
    fun `含换行字段被包裹且回环等价`() {
        val row = listOf("第一行\n第二行", "普通")
        assertEquals(row, roundTrip(*row.toTypedArray()))
    }

    @Test
    fun `空字段与全特殊字段回环`() {
        val row = listOf("", ",", "\"", "\n")
        assertEquals(row, roundTrip(*row.toTypedArray()))
    }

    // ===== M-05：version 写入可读出 =====

    @Test
    fun `云快照版本可解析读出`() {
        val records = listOf(entity("2026-01-10", 100.0), entity("2026-02-01", 500.0))
        val json = BackupCodec.buildBackup(records, "test")
        val parsed = BackupCodec.parse(json)
        assertEquals(BackupCodec.BACKUP_VERSION, parsed.version)
        assertEquals(records.size, parsed.total)
        assertEquals(records.size, parsed.records.size)
    }

    @Test
    fun `缺 version 的旧格式按 null 宽松解析`() {
        val records = listOf(entity("2026-01-10", 100.0))
        val json = BackupCodec.buildBackup(records, "test")
        // 删掉 "version" 键，模拟缺版本字段的旧格式（版本号引用常量，避免硬编码与实现漂移）
        val withoutVersion = json.replace("\"version\":${BackupCodec.BACKUP_VERSION},", "")
        assertNull(BackupCodec.parse(withoutVersion).version)
    }

    @Test
    fun `版本白名单覆盖全部兼容格式`() {
        // 兼容集 1..3：更早格式的快照缺 isFullTank，按缺省 true 解析
        assertTrue(BackupCodec.isKnownBackupVersion(BackupCodec.BACKUP_VERSION))
        assertTrue(BackupCodec.isKnownBackupVersion(BackupCodec.MANUAL_BACKUP_VERSION))
        assertTrue("更早格式的快照必须仍可解析（缺 isFullTank 按缺省）", BackupCodec.isKnownBackupVersion(1))
        assertTrue(BackupCodec.isKnownBackupVersion(2))
        assertTrue(!BackupCodec.isKnownBackupVersion(0))
        assertTrue(!BackupCodec.isKnownBackupVersion(4))
    }

    // ===== 满箱标记：随备份往返，旧格式缺字段按 true =====

    @Test
    fun `满箱标记随备份往返`() {
        val records = listOf(
            entity("2026-01-10", 100.0),
            entity("2026-02-01", 500.0).copy(isFullTank = false),
        )
        val parsed = BackupCodec.parse(BackupCodec.buildBackup(records, "test"))
        assertEquals(2, parsed.records.size)
        assertTrue(parsed.records[0].isFullTank)
        assertFalse("isFullTank=false 必须原样往返", parsed.records[1].isFullTank)
    }

    @Test
    fun `旧格式缺isFullTank字段按缺省true解析`() {
        // 更早格式的快照没有该字段
        val json = """
            {"records":[{
              "date":"2026-03-01","mileage":1000,"oilAmount":40,
              "payableAmount":300,"actualAmount":300
            }]}
        """.trimIndent()
        val parsed = BackupCodec.parse(json)
        assertEquals(1, parsed.records.size)
        assertTrue("缺字段按 true（历史口径=全部视为加满）", parsed.records.single().isFullTank)
    }

    // ===== 满箱标记：CSV 尾列「本次加满」21 列契约（Issue #11 定稿第 9 项） =====

    @Test
    fun `CSV表头21列且尾列为本次加满`() {
        val cols = CSV_RECORD_HEADER.split(",")
        assertEquals("记录导出必须为 21 列", 21, cols.size)
        assertEquals("尾列必须是「本次加满」", "本次加满", cols.last())
        // 无重复列名（列定位按名匹配，重名列会导致导入侧歧义）
        assertEquals("表头列名不得重复", 21, cols.toSet().size)
    }

    @Test
    fun `CSV满箱尾列随导出导入往返是与否`() {
        // 按导出侧的行结构拼一列数据行（与 exportCsv 的 21 列字段一一对应，满箱分别为 是/否），
        // 走 csvEscape → parseCsvLine → parseFullTankColumn 的真实往返链路
        val base = listOf(
            "2026-01-01", "-", "92号", "40", "7.5", "300", "290",
            "10000", "-", "-", "-", "-", "-", "-", "-", "-", "-",
            "-", "广东", "entry_page",
        )
        val fullTankIdx = CSV_RECORD_HEADER.split(",").indexOf("本次加满")
        assertEquals(20, fullTankIdx)

        for (flag in listOf("是", "否")) {
            val cells = base + flag
            val line = cells.joinToString(",") { csvEscape(it) }
            val parsed = parseCsvLine(line)
            assertEquals("往返不得改变列数", 21, parsed.size)
            assertEquals("其余 20 列回环不变", base, parsed.dropLast(1))
            assertEquals(
                "满箱尾列语义必须往返一致（$flag）",
                flag == "是",
                parseFullTankColumn(parsed[fullTankIdx]),
            )
        }
    }

    @Test
    fun `旧20列CSV缺满箱列默认视为加满`() {
        // 旧 20 列格式：缺列 → true（历史口径=全部视为加满，派生数字与升级前一致）
        assertTrue("缺列按 true", parseFullTankColumn(null))
        assertTrue("空值按 true", parseFullTankColumn(""))
        assertTrue("空白按 true", parseFullTankColumn("  "))
        // 显式否定值 → false；其余任意值按加满（与导入侧宽松口径一致）
        assertFalse(parseFullTankColumn("否"))
        assertFalse(parseFullTankColumn("no"))
        assertFalse(parseFullTankColumn("false"))
        assertFalse(parseFullTankColumn("0"))
        assertTrue(parseFullTankColumn("是"))
        assertTrue(parseFullTankColumn("yes"))
    }

    // ===== M-21：buildBackup 内部排序 =====

    @Test
    fun `乱序输入不破坏派生字段`() {
        // 故意乱序传入：解析结果必须仍按日期升序、全部通过校验
        val records = listOf(
            entity("2026-03-01", 900.0),
            entity("2026-01-01", 100.0),
            entity("2026-02-01", 500.0),
        )
        val parsed = BackupCodec.parse(BackupCodec.buildBackup(records, "test"))
        val dates = parsed.records.map { it.reoilDate }
        assertEquals(listOf("2026-01-01", "2026-02-01", "2026-03-01"), dates)
        assertEquals(3, parsed.total)
    }

    private fun entity(date: String, mileage: Double) = KOiLRecordEntity(
        reoilDate = date,
        mileage = mileage,
        oilAmount = 40.0,
        payableAmount = 300.0,
        actualAmount = 300.0,
        oilPrice = null,
        oilGrade = null,
        province = null,
        source = "test",
    )
}
