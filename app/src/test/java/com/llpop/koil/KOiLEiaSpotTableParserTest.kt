package com.llpop.koil

import com.llpop.koil.data.oil.CrudeOilEstimator
import com.llpop.koil.data.oil.KOiLEiaSpotTableParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WPSR 现货价格表 JSON 的解析与合并契约。
 *
 * 这段代码的风险不在「解析不出来」，在**解析出来但错位**：日期与价格错开一格，
 * 预测会安静地算出个像模像样的错数，没有任何报错。所以下面一半用例是在验证
 * 「宁可返回空，也不返回错位数据」。
 *
 * fixture 是 2026-11-10 从
 * `https://www.eia.gov/petroleum/prices/data/12_series/D/PET_PRI_SPT_S1_D.json`
 * 原样抓下来的响应（约 2 KB，未修剪），其中 `RWTC` 排在 `RBRTE` 前面——
 * 正好用来证明代码是按 key 找行，不是按数组下标。
 */
class KOiLEiaSpotTableParserTest {

    private val realResponse = """{"table_name": "Spot Prices for Crude Oil and Petroleum Products", "table_units": "(Crude Oil in Dollars per Barrel, Products in Dollars per Gallon)", "release_date": "11/9/2026", "next_release_date": "11/10/2026", "thead": ["11/06/26", "11/05/26", "11/04/26", "11/03/26", "11/02/26", "10/30/26"], "tbody": [{"section": "Crude Oil", "data": {"RWTC": ["indent_0", "WTI - Cushing, Oklahoma", "1986 - 2026", "94.37", "94.05", "91.73", "99.64", "100.06", "107.56"], "RBRTE": ["indent_0", "Brent - Europe", "1987 - 2026", "97.83", "97.96", "91.84", "107.03", "107.39", "120.15"]}}, {"section": "Conventional Gasoline", "data": {"EER_EPMRU_PF4_Y35NY_DPG": ["indent_0", "New York Harbor, Regular", "1986 - 2026", "2.563", "2.568", "2.534", "2.728", "2.649", "2.968"], "EER_EPMRU_PF4_RGC_DPG": ["indent_0", "U.S. Gulf Coast, Regular", "1986 - 2026", "2.735", "2.713", "2.684", "2.903", "2.857", "3.238"]}}, {"section": "RBOB Regular Gasoline", "data": {"EER_EPMRR_PF4_Y05LA_DPG": ["indent_0", "Los Angeles", "2003 - 2026", "3.073", "3.123", "3.204", "3.433", "3.409", "3.766"]}}, {"section": "No. 2 Heating Oil", "data": {"EER_EPD2F_PF4_Y35NY_DPG": ["indent_0", "New York Harbor", "1986 - 2026", "4.252", "4.276", "4.127", "4.470", "4.322", "4.708"]}}, {"section": "Ultra-Low-Sulfur No. 2 Diesel Fuel", "data": {"EER_EPD2DXL0_PF4_Y35NY_DPG": ["indent_0", "New York Harbor", "2006 - 2026", "4.467", "4.639", "4.484", "4.823", "4.677", "4.876"], "EER_EPD2DXL0_PF4_RGC_DPG": ["indent_0", "U.S. Gulf Coast", "2006 - 2026", "4.305", "4.389", "4.187", "4.525", "4.397", "4.783"], "EER_EPD2DC_PF4_Y05LA_DPG": ["indent_0", "Los Angeles", "1996 - 2026", "4.535", "4.559", "4.494", "4.838", "4.690", "5.076"]}}, {"section": "Kerosene-Type Jet Fuel", "data": {"EER_EPJK_PF4_RGC_DPG": ["indent_0", "U.S. Gulf Coast", "1990 - 2026", "4.186", "4.162", "3.883", "4.198", "4.019", "4.379"]}}, {"section": "Propane", "data": {"EER_EPLLPA_PF4_Y44MB_DPG": ["indent_0", "Mont Belvieu, Texas", "1992 - 2026", "1.155", "1.128", "1.060", "1.195", "1.209", "1.285"]}}], "tfoot": []}"""

    /** 2026-10-01 ~ 2026-11-03 的真实 EIA 布伦特现货序列（v2 API 当天能给到的全部） */
    private val v2Points = listOf(
        "2026-10-01" to 114.82, "2026-10-02" to 135.51, "2026-10-05" to 125.51,
        "2026-10-06" to 125.44, "2026-10-07" to 126.00, "2026-10-08" to 130.18,
        "2026-10-09" to 130.03, "2026-10-12" to 129.95, "2026-10-13" to 125.78,
        "2026-10-14" to 126.55, "2026-10-15" to 126.35, "2026-10-16" to 119.33,
        "2026-10-19" to 118.82, "2026-10-20" to 115.22, "2026-10-21" to 117.37,
        "2026-10-22" to 125.39, "2026-10-23" to 128.30, "2026-10-26" to 128.12,
        "2026-10-27" to 123.10, "2026-10-28" to 119.20, "2026-10-29" to 115.28,
        "2026-10-30" to 120.15, "2026-11-02" to 107.39, "2026-11-03" to 107.03,
    )

    // ---------- 解析 ----------

    @Test
    fun `真实响应按 key 取 RBRTE 行并按日期升序`() {
        val points = KOiLEiaSpotTableParser.parse(realResponse)
        assertEquals(
            listOf(
                "2026-10-30" to 120.15,
                "2026-11-02" to 107.39,
                "2026-11-03" to 107.03,
                "2026-11-04" to 91.84,
                "2026-11-05" to 97.96,
                "2026-11-06" to 97.83,
            ),
            points,
        )
    }

    @Test
    fun `不会取到排在前面的 WTI 行`() {
        val points = KOiLEiaSpotTableParser.parse(realResponse)
        // WTI 同列值是 94.37 / 99.64，一旦按下标取行就会混进来
        assertTrue(points.none { it.second == 94.37 || it.second == 99.64 })
    }

    @Test
    fun `价格列少于表头数量时整条丢弃`() {
        // 3 个描述列 + 2 个价格 = 5，配 6 个日期，列数对不上
        val body = """{"thead":["11/06/26","11/05/26","11/04/26","11/03/26","11/02/26","10/30/26"],""" +
            """"tbody":[{"data":{"RBRTE":["indent_0","Brent - Europe","1987 - 2026","97.83","97.96"]}}]}"""
        assertTrue(KOiLEiaSpotTableParser.parse(body).isEmpty())
    }

    @Test
    fun `价格列多于表头数量时整条丢弃`() {
        // 3 + 4 = 7 配 6 个日期：多出来的那一格会让日期和价格整体错开，
        // 这种错位不抛异常、只算错，必须整条丢弃而不是「尾部对齐凑合用」
        val body = """{"thead":["11/06/26","11/05/26","11/04/26","11/03/26","11/02/26","10/30/26"],""" +
            """"tbody":[{"data":{"RBRTE":["indent_0","Brent - Europe","1987 - 2026","97.83","97.96","91.84","107.03"]}}]}"""
        assertTrue(KOiLEiaSpotTableParser.parse(body).isEmpty())
    }

    @Test
    fun `描述列位置出现数字时整条丢弃`() {
        // 前 3 列本该是 indent 类名 / 展示名 / 年份范围；一旦它们是数字，
        // 说明列序变了，按位置取到的「价格」其实是别的东西
        val body = """{"thead":["11/06/26","11/05/26"],""" +
            """"tbody":[{"data":{"RBRTE":["1","2","3","97.83","97.96"]}}]}"""
        assertTrue(KOiLEiaSpotTableParser.parse(body).isEmpty())
    }

    @Test
    fun `单日空串只跳过当天不连坐整条`() {
        // 元素还在、只是值为空：这是「单日缺数据」，不是「结构对不上」，不该丢掉整段增量
        val body = realResponse.replace("\"91.84\"", "\"\"")
        val points = KOiLEiaSpotTableParser.parse(body)
        assertEquals(5, points.size)
        assertNull(points.firstOrNull { it.first == "2026-11-04" })
        assertEquals("2026-11-03" to 107.03, points.firstOrNull { it.first == "2026-11-03" })
    }

    @Test
    fun `单日非正数或非数字只跳过当天`() {
        // 这里的关键断言是 size == 5 且其余日期仍在。只断言「11-04 不存在」的话，
        // 整条被丢弃（返回空表）也能通过——上一版就是这么假绿的
        listOf("0", "-3.2", "n/a", "null").forEach { bad ->
            val points = KOiLEiaSpotTableParser.parse(realResponse.replace("\"91.84\"", "\"$bad\""))
            assertEquals("值 $bad 应只跳过 11-04，不该丢整条", 5, points.size)
            assertNull(points.firstOrNull { it.first == "2026-11-04" })
            assertEquals("2026-11-03" to 107.03, points.firstOrNull { it.first == "2026-11-03" })
            assertEquals("2026-11-06" to 97.83, points.firstOrNull { it.first == "2026-11-06" })
        }
    }

    @Test
    fun `表头只有一天时返回一条`() {
        val body = """{"thead":["11/06/26"],"tbody":[{"data":{"RBRTE":["indent_0","Brent","1987 - 2026","97.83"]}}]}"""
        assertEquals(listOf("2026-11-06" to 97.83), KOiLEiaSpotTableParser.parse(body))
    }

    @Test
    fun `缺少 RBRTE 行返回空`() {
        val body = """{"thead":["11/06/26"],"tbody":[{"data":{"RWTC":["indent_0","WTI","1986 - 2026","94.37"]}}]}"""
        assertTrue(KOiLEiaSpotTableParser.parse(body).isEmpty())
    }

    @Test
    fun `非 JSON 或空串返回空不抛异常`() {
        assertTrue(KOiLEiaSpotTableParser.parse("").isEmpty())
        assertTrue(KOiLEiaSpotTableParser.parse("<html>404</html>").isEmpty())
        assertTrue(KOiLEiaSpotTableParser.parse("""{"thead":[]}""").isEmpty())
    }

    // ---------- 发布日 ----------

    @Test
    fun `真实响应解析出源端发布日且不改动价格`() {
        val table = KOiLEiaSpotTableParser.parseWithMeta(realResponse)
        // fixture 原文 "release_date": "11/9/2026"，月日不补零，必须按 M/d/yyyy 解
        assertEquals("2026-11-09", table.releaseDate)
        // 发布日（11-09）晚于数据最后一天（11-06）：这正是要展示给用户的「源端滞后 3 天」
        assertEquals("2026-11-06", table.points.last().first)
        assertEquals(KOiLEiaSpotTableParser.parse(realResponse), table.points)
    }

    @Test
    fun `发布日等于数据最后一天时保留`() {
        // 当天发布当天数据：边界合法，不能因为「不够新」就丢掉
        val body = realResponse.replace("\"release_date\": \"11/9/2026\"", "\"release_date\": \"11/6/2026\"")
        assertEquals("2026-11-06", KOiLEiaSpotTableParser.parseWithMeta(body).releaseDate)
    }

    @Test
    fun `发布日早于数据最后一天时置空`() {
        // 发布不可能早于数据日。出现即说明字段语义被改了（比如换成「本期起始日」），
        // 照原样显示等于把一个未知说成一个具体日期
        val body = realResponse.replace("\"release_date\": \"11/9/2026\"", "\"release_date\": \"11/5/2026\"")
        val table = KOiLEiaSpotTableParser.parseWithMeta(body)
        assertNull(table.releaseDate)
        assertEquals(6, table.points.size) // 只丢发布日，不能连价格一起丢
    }

    @Test
    fun `发布日缺失或格式非法时置空但价格照常`() {
        val cases = listOf(
            realResponse.replace("\"release_date\": \"11/9/2026\", ", ""),        // 字段被删
            realResponse.replace("\"11/9/2026\"", "\"\""),                      // 空串
            realResponse.replace("\"11/9/2026\"", "\"n/a\""),                    // 脏值
            realResponse.replace("\"11/9/2026\"", "\"2026-11-09\""),             // 格式换成 ISO（解析器只认 M/d/yyyy）
        )
        cases.forEach { body ->
            val table = KOiLEiaSpotTableParser.parseWithMeta(body)
            assertNull("该响应的发布日应为 null", table.releaseDate)
            assertEquals(6, table.points.size)
        }
    }

    @Test
    fun `一个价格点都没解析出来时不给发布日`() {
        // 「这批数据」不存在，发布日没有指代对象；此时给日期反而像在说「数据是新的」
        val body = """{"release_date":"11/9/2026","thead":["11/06/26"],""" +
            """"tbody":[{"data":{"RBRTE":["indent_0","Brent - Europe","1987 - 2026",""]}}]}"""
        val table = KOiLEiaSpotTableParser.parseWithMeta(body)
        assertTrue(table.points.isEmpty())
        assertNull(table.releaseDate)
    }

    // ---------- 合并 ----------

    @Test
    fun `同日期以增量覆盖 v2`() {
        val base = listOf("2026-11-03" to 107.03, "2026-11-04" to 100.00)
        val merged = KOiLEiaSpotTableParser.merge(base, listOf("2026-11-04" to 91.84))
        assertEquals(listOf("2026-11-03" to 107.03, "2026-11-04" to 91.84), merged)
    }

    @Test
    fun `v2 缺失的日期被补上且结果升序无重复`() {
        val base = listOf("2026-11-03" to 107.03, "2026-11-02" to 107.39)
        val overrides = KOiLEiaSpotTableParser.parse(realResponse)
        val merged = KOiLEiaSpotTableParser.merge(base, overrides)
        assertEquals(merged.map { it.first }.distinct(), merged.map { it.first })
        assertEquals(merged.sortedBy { it.first }, merged)
        assertEquals(6, merged.size) // base 2 条 + 增量 6 条，其中 11-02/11-03 重叠
        assertEquals("2026-11-06" to 97.83, merged.last())
    }

    @Test
    fun `增量为空时原样返回并保持升序`() {
        val base = listOf("2026-11-03" to 107.03, "2026-11-02" to 107.39)
        assertEquals(
            listOf("2026-11-02" to 107.39, "2026-11-03" to 107.03),
            KOiLEiaSpotTableParser.merge(base, emptyList()),
        )
    }

    // ---------- 合并后的发布日校验（不变量属于最终结果） ----------

    @Test
    fun `v2 反而更新时丢弃发布日但保留 v2 数据`() {
        // 复审指出的反向场景：增量到 11-06、release_date 11-09，增量自查通过（11-09 ≥ 11-06）；
        // 但 v2 已更新到 11-10，合并后若照原样挂 11-09，UI 会显示
        // 「截至 11-10 · 源端 11-09 发布」——发布日早于数据日，与本次改造的第三条不变量冲突。
        val base = listOf(
            "2026-11-06" to 100.00,
            "2026-11-09" to 99.00,
            "2026-11-10" to 98.00, // v2 比增量新
        )
        val table = KOiLEiaSpotTableParser.parseWithMeta(realResponse)
        assertEquals("2026-11-09", table.releaseDate) // 增量表自身校验是通过的
        assertEquals("2026-11-06", table.points.last().first)

        val merged = KOiLEiaSpotTableParser.merge(base, table.points)
        val release = KOiLEiaSpotTableParser.resolveReleaseDate(table.releaseDate, merged)

        assertNull("合并后最后一天 11-10 晚于发布日 11-09，发布日必须置空", release)
        // 只丢字段不丢数据：v2 更新的 11-10 必须还在，且值没被增量覆盖
        assertEquals("2026-11-10" to 98.00, merged.last())
        assertEquals(99.00, merged.first { it.first == "2026-11-09" }.second, 0.0)
    }

    @Test
    fun `v2 落后于发布日时保留发布日`() {
        // 正常态：v2 停在 11-03，增量补到 11-06，发布日 11-09 → 合法，不能误伤
        val merged = KOiLEiaSpotTableParser.merge(
            v2Points,
            KOiLEiaSpotTableParser.parse(realResponse),
        )
        assertEquals("2026-11-06", merged.last().first)
        assertEquals("2026-11-09", KOiLEiaSpotTableParser.resolveReleaseDate("2026-11-09", merged))
    }

    @Test
    fun `合并后发布日校验的边界`() {
        val points = listOf("2026-11-06" to 97.83)
        assertNull(KOiLEiaSpotTableParser.resolveReleaseDate(null, points)) // 没有发布日
        assertNull(KOiLEiaSpotTableParser.resolveReleaseDate("", points)) // 空串
        assertNull(KOiLEiaSpotTableParser.resolveReleaseDate("n/a", points)) // 脏值
        assertNull(KOiLEiaSpotTableParser.resolveReleaseDate("11/9/2026", points)) // 只接受已归一化的 ISO
        assertNull(KOiLEiaSpotTableParser.resolveReleaseDate("2026-11-09", emptyList())) // 没有数据就没有指代对象
        // 相等是合法边界：当天发布当天数据，不能因为「不够新」就丢掉
        assertEquals("2026-11-06", KOiLEiaSpotTableParser.resolveReleaseDate("2026-11-06", points))
        assertEquals("2026-11-09", KOiLEiaSpotTableParser.resolveReleaseDate("2026-11-09", points))
    }

    // ---------- 端到端 ----------

    @Test
    fun `补增量后本轮窗口幅度从 -464 修正到 -846`() {
        val adjustDates = listOf("2026-10-15", "2026-10-29")
        val today = "2026-11-06"

        val before = CrudeOilEstimator.calcFromEiaSeries(v2Points, adjustDates, null, today)
        assertNotNull(before)
        assertEquals("2026-11-03", before!!.dataDate)
        assertEquals(3, before.sampleDays)
        assertEquals(-463.7, before.amountPerTon, 1.0)

        val after = CrudeOilEstimator.calcFromEiaSeries(
            KOiLEiaSpotTableParser.merge(v2Points, KOiLEiaSpotTableParser.parse(realResponse)),
            adjustDates,
            null,
            today,
        )
        assertNotNull(after)
        // 补上 11-04（107.03 → 91.84，单日 -15.19 美元）后结论从「小幅下调」变「大幅下调」
        assertEquals("2026-11-06", after!!.dataDate)
        assertEquals(6, after.sampleDays)
        assertEquals(-846.1, after.amountPerTon, 2.0)
    }
}
