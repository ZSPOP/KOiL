package com.llpop.koil

import com.llpop.koil.data.local.entity.KOiLRecordEntity
import com.llpop.koil.data.oil.OilPriceDbSyncer
import com.llpop.koil.data.oil.OilPriceDateUtil
import com.llpop.koil.data.oil.OilPriceSyncResult
import com.llpop.koil.data.sync.BackupCodec
import com.llpop.koil.data.sync.RecordMerger
import com.llpop.koil.domain.logic.ConsumptionStatus
import com.llpop.koil.domain.logic.DerivedCalculator
import com.llpop.koil.domain.logic.MileageChain
import com.llpop.koil.domain.logic.NumberFormat
import com.llpop.koil.domain.logic.RecordValidator
import com.llpop.koil.domain.logic.RecordWithDerived
import com.llpop.koil.domain.logic.SummaryMath
import com.llpop.koil.domain.logic.TrendSampler
import com.llpop.koil.domain.logic.inChronologicalOrder
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 严格口径回归测试：把「显示/派生计算」的边界行为固定下来。
 * 每条断言都是用户可见的正确行为，不是对现状的复述。
 */
class DomainRobustnessTest {

    // ===== NumberFormat：金额/度量/百分比 =====

    @Test
    fun `金额不出现负零`() {
        // -0.0001 四舍五入到分是 0.00，显示成 "-0.00" 会让用户以为扣了钱
        assertEquals("0.00", NumberFormat.money(-0.0001))
        assertEquals("0.00", NumberFormat.money(-0.0))
    }

    @Test
    fun `度量按十进制四舍五入而非二进制近似`() {
        // 7.855 的 double 实际是 7.85499999...，用 BigDecimal(double) 会错舍成 7.85
        assertEquals("7.86", NumberFormat.measure(7.855))
        assertEquals("2.68", NumberFormat.measure(2.675))
        assertEquals("1.01", NumberFormat.measure(1.005))
    }

    @Test
    fun `百分比按十进制四舍五入`() {
        assertEquals("20.6", NumberFormat.pct(20.55))
        assertEquals("20", NumberFormat.pct(20.0))
        assertEquals("-3.5", NumberFormat.pct(-3.45))
    }

    @Test
    fun `非有限值不得抛异常`() {
        // 任何 NaN/Infinity 走到这里都不能崩：崩溃点在最不起眼的文本格式化里
        NumberFormat.measure(Double.NaN)
        NumberFormat.measure(Double.POSITIVE_INFINITY)
        NumberFormat.money(Double.NEGATIVE_INFINITY)
        NumberFormat.pct(Double.NaN)
    }

    // ===== DerivedCalculator：里程/油耗派生 =====

    private fun rec(
        id: Long,
        date: String,
        mileage: Double,
        oil: Double = 40.0,
        payable: Double = 300.0,
        actual: Double = 300.0,
    ) = KOiLRecordEntity(
        id = id,
        reoilDate = date,
        mileage = mileage,
        oilAmount = oil,
        payableAmount = payable,
        actualAmount = actual,
        oilPrice = 7.5,
        oilGrade = "92",
        province = "广东",
        source = "entry_page",
    )

    @Test
    fun `首条满箱记录成为锚点且不产出油耗`() {
        val out = DerivedCalculator.compute(listOf(rec(1, "2026-01-01", 10_000.0)), 20.0)
        assertNull(out[0].distance)
        assertNull(out[0].oilConsumption)
        assertEquals(ConsumptionStatus.NO_BASE, out[0].consumptionStatus)
        assertNull(out[0].spanDistance)
    }

    @Test
    fun `锚点前半箱不累计且无油耗`() {
        // 首条未满：上次满油时间未知，油量不可计入任何区间
        val out = DerivedCalculator.compute(
            listOf(
                rec(1, "2026-01-01", 10_000.0).copy(isFullTank = false),
                rec(2, "2026-01-08", 10_500.0),
            ), 20.0
        )
        assertEquals(ConsumptionStatus.NO_BASE, out[0].consumptionStatus)
        assertNull(out[0].pendingOil)
        // 第二条满箱成为新锚点，仍无结算区间
        assertNull(out[1].oilConsumption)
        assertEquals(ConsumptionStatus.NO_BASE, out[1].consumptionStatus)
    }

    @Test
    fun `里程倒退是异常不是短途`() {
        // 里程表被重置/填错：distance 为负。满箱区间为负时锚点不动、累计保留，
        // 等下一条正常里程的满箱记录对整个区间结算
        val out = DerivedCalculator.compute(
            listOf(rec(1, "2026-01-01", 10_000.0), rec(2, "2026-01-05", 9_000.0)), 20.0
        )
        assertEquals(-1_000.0, out[1].distance!!, 1e-9)
        assertNull(out[1].oilConsumption)
        assertTrue("里程倒退是数据异常", out[1].distanceAnomaly)
        assertEquals(ConsumptionStatus.PENDING, out[1].consumptionStatus)
        assertNull("负里程不得产出每公里成本", out[1].costPerKm)
    }

    @Test
    fun `短途恰好100公里边界不算短途参考`() {
        val out = DerivedCalculator.compute(
            listOf(rec(1, "2026-01-01", 10_000.0), rec(2, "2026-01-02", 10_100.0)), 20.0
        )
        assertEquals(40.0, out[1].oilConsumption!!, 1e-9) // 40L / 100km * 100
        assertFalse(out[1].shortInterval)
    }

    @Test
    fun `短途闭合区间仍产出数字并标短途参考`() {
        // 50km 闭合区间：单条数字失真风险大 → 标 shortInterval（UI 短途参考、趋势隐藏），
        // 但总账必须计入（Σ油/Σ里程 才严格等于 总油/总里程）
        val out = DerivedCalculator.compute(
            listOf(rec(1, "2026-01-01", 10_000.0), rec(2, "2026-01-02", 10_050.0)), 20.0
        )
        assertEquals(80.0, out[1].oilConsumption!!, 1e-9)
        assertTrue(out[1].shortInterval)
        assertEquals(50.0, out[1].spanDistance!!, 1e-9)
        assertEquals(40.0, out[1].spanOil!!, 1e-9)
    }

    @Test
    fun `油量为零不产生无穷单价`() {
        val out = DerivedCalculator.compute(
            listOf(
                rec(1, "2026-01-01", 10_000.0),
                rec(2, "2026-01-02", 10_500.0, oil = 0.0, payable = 0.0, actual = 0.0),
            ), 20.0
        )
        assertNull("油量 0 时单价应为空而不是 Infinity", out[1].unitPricePayable)
        assertNull(out[1].unitPriceActual)
        assertTrue(out[1].oilConsumption!!.isFinite())
    }

    @Test
    fun `半箱记录累计到下次加满一次结算`() {
        // F(500km,40L) P(400km,20L,未满) F(500km,40L)：
        // 区间2 = (20+40)L / 900km = 6.67 L/100km——与半箱比例无关，满箱法数学上严格成立
        val out = DerivedCalculator.compute(
            listOf(
                rec(1, "2026-01-01", 10_000.0),
                rec(2, "2026-01-08", 10_400.0).copy(isFullTank = false, oilAmount = 20.0),
                rec(3, "2026-01-15", 10_900.0),
            ), 20.0
        )
        // 半箱记录自身不产出（区间未闭合），只显示待结算累计
        assertNull(out[1].oilConsumption)
        assertEquals(ConsumptionStatus.PENDING, out[1].consumptionStatus)
        assertEquals(1, out[1].pendingCount)
        assertEquals(20.0, out[1].pendingOil!!, 1e-9)
        assertEquals(300.0, out[1].pendingActual!!, 1e-9)
        // 结算发生在下次加满：区间 = (20+40)L / 900km = 6.67——与半箱比例无关，数学严格成立
        assertEquals(6.67, out[2].oilConsumption!!, 0.01)
        assertEquals(900.0, out[2].spanDistance!!, 1e-9)
        assertEquals(60.0, out[2].spanOil!!, 1e-9)
        assertEquals(ConsumptionStatus.NORMAL, out[2].consumptionStatus)
    }

    @Test
    fun `同点满箱不闭合且累计保留到下一区间`() {
        // F(10000) F(10000 同点) F(10500)：span=0 不产出（除零），锚点不动，
        // 锚点之后累计的 r2 40L 并入下一区间：区间油量 = 40+40 = 80L / 500km = 16 L/100km
        // （锚点 r1 自身的 40L 属于上一区间，不参与累计；总账不丢油）
        val out = DerivedCalculator.compute(
            listOf(
                rec(1, "2026-01-01", 10_000.0),
                rec(2, "2026-01-05", 10_000.0),
                rec(3, "2026-01-10", 10_500.0),
            ), 20.0
        )
        assertEquals(ConsumptionStatus.PENDING, out[1].consumptionStatus)
        assertNull(out[1].oilConsumption)
        assertEquals(1, out[1].pendingCount)
        assertEquals(40.0, out[1].pendingOil!!, 1e-9)
        assertEquals(16.0, out[2].oilConsumption!!, 1e-9)
        assertEquals(500.0, out[2].spanDistance!!, 1e-9)
        assertEquals(80.0, out[2].spanOil!!, 1e-9)
    }

    @Test
    fun `油耗异常判定用均值上下浮动`() {
        // 三段 500km：8、8、12 L/100km → 均值 9.33，阈值 20% → 上界 11.2，第三条判偏高
        val out = DerivedCalculator.compute(
            listOf(
                rec(1, "2026-01-01", 10_000.0, oil = 40.0),
                rec(2, "2026-01-08", 10_500.0, oil = 40.0),
                rec(3, "2026-01-15", 11_000.0, oil = 40.0),
                rec(4, "2026-01-22", 11_500.0, oil = 60.0),
            ), 20.0
        )
        assertEquals(ConsumptionStatus.NORMAL, out[1].consumptionStatus)
        assertEquals(ConsumptionStatus.NORMAL, out[2].consumptionStatus)
        assertEquals(ConsumptionStatus.HIGH, out[3].consumptionStatus)
        assertTrue(out[3].deviationPct > 0)
    }

    @Test
    fun `短途区间不进异常判定均值且保持正常`() {
        // 500km/40L + 500km/40L + 50km/40L：正常区间均 8.0；
        // 短途 80.0 若计入均值会拉成 32，把正常 8.0 误判偏低、短途误判偏高——
        // 异常判定必须排除短途区间（复审打回项回归），短途保持 NORMAL/偏差 0，总账仍计入。
        val out = DerivedCalculator.compute(
            listOf(
                rec(1, "2026-01-01", 10_000.0, oil = 40.0),
                rec(2, "2026-01-08", 10_500.0, oil = 40.0),
                rec(3, "2026-01-15", 11_000.0, oil = 40.0),
                rec(4, "2026-01-22", 11_050.0, oil = 40.0),
            ), 20.0
        )
        // 正常闭合区间：8.0，参与均值判定，NORMAL、偏差 0
        assertEquals(8.0, out[1].oilConsumption!!, 1e-9)
        assertEquals(ConsumptionStatus.NORMAL, out[1].consumptionStatus)
        assertEquals(0.0, out[1].deviationPct, 1e-9)
        assertEquals(8.0, out[2].oilConsumption!!, 1e-9)
        assertEquals(ConsumptionStatus.NORMAL, out[2].consumptionStatus)
        assertEquals(0.0, out[2].deviationPct, 1e-9)
        // 短途：80.0 不进均值，保持 NORMAL / deviationPct=0 / shortInterval=true（不判 HIGH）
        assertTrue(out[3].shortInterval)
        assertEquals(80.0, out[3].oilConsumption!!, 1e-9)
        assertEquals(ConsumptionStatus.NORMAL, out[3].consumptionStatus)
        assertEquals(0.0, out[3].deviationPct, 1e-9)
    }

    @Test
    fun `时间序排序统一按日期再按id`() {
        val raw = listOf(
            rec(9, "2026-03-23", 11_000.0),
            rec(5, "2026-03-22", 10_500.0),
            rec(3, "2026-03-22", 10_000.0),
        )
        assertEquals(listOf(3L, 5L, 9L), raw.inChronologicalOrder().map { it.id })
    }

    @Test
    fun `同日多条记录即使DAO返回id降序也不算成里程倒退`() {
        // DAO observeAll 是 ORDER BY reoilDate DESC, id DESC：同日两次加油时，
        // 只按日期稳定排序会让 id 大的排前面，里程段变成负数 → 假「里程表回拨」+ 油耗丢失。
        val daoOrder = listOf(
            rec(5, "2026-03-22", 10_500.0),
            rec(3, "2026-03-22", 10_000.0),
        )
        val out = DerivedCalculator.compute(daoOrder, 20.0)
        assertEquals(listOf(3L, 5L), out.map { it.record.id })
        assertNull(out[0].distance)
        assertEquals(500.0, out[1].distance!!, 1e-9)
        assertFalse("同日两条不应误报里程异常", out[1].distanceAnomaly)
        assertEquals(8.0, out[1].oilConsumption!!, 1e-9) // 40L / 500km * 100
    }

    @Test
    fun `编辑最早记录不被 beforeFirst 拦截`() {
        val earliest = rec(1, "2026-03-01", 30_000.0)
        // 编辑最早记录自身：即使里程/日期看起来早于“第二条”，也不能判 beforeFirst
        assertFalse(MileageChain.isBeforeFirst("2026-03-01", 29_000.0, earliest, editingId = 1))
        // 编辑其它记录时仍要正常拦截
        assertTrue(MileageChain.isBeforeFirst("2026-03-01", 29_000.0, earliest, editingId = 2))
    }

    @Test
    fun `同日补录必须大于同日最大里程`() {
        // 同日已有 50000（id=2），补录新记录 id 必然更大、排在它之后；
        // 45000 虽然落在跨日 prev/next 的空隙里，仍会派生负段，必须拦截。
        val sameDay = listOf(rec(2, "2026-03-05", 50_000.0))
        val chain = MileageChain.forBackfill(
            sameDayById = sameDay,
            beforeDate = rec(1, "2026-03-01", 30_000.0),
            afterDate = rec(4, "2026-03-10", 60_000.0),
        )
        assertNotNull(MileageChain.conflict(45_000.0, chain))
        assertEquals("prev", MileageChain.conflict(45_000.0, chain)!!.type)
        assertNull(MileageChain.conflict(51_000.0, chain))
    }

    @Test
    fun `同日编辑按 id 前后邻居判定`() {
        // 编辑 id=2（同日 50000）→ prev 取上一天 30000，next 取同日 id=3 的 60000。
        // 45000 应放行（排序后为 30000, 45000, 60000，正向）；70000 应被 next 拦截。
        val sameDayExcluding = listOf(rec(3, "2026-03-05", 60_000.0))
        val chain = MileageChain.forEdit(
            sameDayById = sameDayExcluding,
            editingId = 2,
            beforeDate = rec(1, "2026-03-01", 30_000.0),
            afterDate = null,
        )
        assertNull(MileageChain.conflict(45_000.0, chain))
        assertNotNull(MileageChain.conflict(70_000.0, chain))
        assertEquals("next", MileageChain.conflict(70_000.0, chain)!!.type)
    }

    @Test
    fun `同日编辑中间记录时同日 id 邻居优先于跨日邻居`() {
        // 同日 id=2 50000、id=4 70000；编辑 id=3。前后邻居必须取 50000/70000，
        // 不能退回跨日的 30000/80000。
        val sameDayExcluding = listOf(
            rec(2, "2026-03-05", 50_000.0),
            rec(4, "2026-03-05", 70_000.0),
        )
        val chain = MileageChain.forEdit(
            sameDayById = sameDayExcluding,
            editingId = 3,
            beforeDate = rec(1, "2026-03-01", 30_000.0),
            afterDate = rec(5, "2026-03-10", 80_000.0),
        )
        assertEquals(50_000.0, chain.prev!!.mileage, 0.0)
        assertEquals(70_000.0, chain.next!!.mileage, 0.0)
        assertNotNull(MileageChain.conflict(45_000.0, chain))
        assertNull(MileageChain.conflict(65_000.0, chain))
        assertNotNull(MileageChain.conflict(75_000.0, chain))
    }

    // ===== RecordValidator：CSV / 手动备份 / 云备份共用的入库闸门 =====

    @Test
    fun `入库校验拒绝NaN伪日期与越界值`() {
        assertTrue(RecordValidator.isValidRecord("2026-03-22", 10_000.0, 40.0, 300.0, 290.0))
        // NaN 会让所有比较都返回 false，必须显式拦截
        assertFalse(RecordValidator.isValidRecord("2026-03-22", Double.NaN, 40.0, 300.0, 290.0))
        assertFalse(RecordValidator.isValidRecord("2026-03-22", Double.POSITIVE_INFINITY, 40.0, 300.0, 290.0))
        // 正则 \d{4}-\d{2}-\d{2} 能过，但不是真实存在的日期
        assertFalse(RecordValidator.isValidRecord("2026-02-30", 10_000.0, 40.0, 300.0, 290.0))
        assertFalse(RecordValidator.isValidRecord("2026-13-45", 10_000.0, 40.0, 300.0, 290.0))
        assertFalse(RecordValidator.isValidRecord("2026-03-22", 10_000_001.0, 40.0, 300.0, 290.0))
        assertFalse(RecordValidator.isValidRecord("2026-03-22", 10_000.0, 40.0, -1.0, 290.0))
        assertFalse(RecordValidator.isValidRecord("2026-03-22", 10_000.0, 40.0, 300.0, Double.NaN))
    }

    @Test
    fun `文本清洗去掉控制字符但保留内容`() {
        assertEquals("广东 深圳", RecordValidator.sanitizeText("广东\n深圳"))
        assertEquals("92", RecordValidator.sanitizeText("9\u00002"))
    }

    @Test
    fun `导入入口拒绝未来日期`() {
        // 显式传 today，避免测试结果随系统时间漂移
        val today = LocalDate.of(2026, 11, 9)
        assertTrue(
            RecordValidator.isValidRecord(
                "2026-11-09", 10_000.0, 40.0, 300.0, 290.0, today = today,
            ),
        )
        assertFalse(
            RecordValidator.isValidRecord(
                "2026-11-10", 10_000.0, 40.0, 300.0, 290.0, today = today,
            ),
        )
        assertFalse(
            RecordValidator.isValidRecord(
                "2099-01-01", 10_000.0, 40.0, 300.0, 290.0, today = today,
            ),
        )
        // 显式放宽时才允许未来（留给将来"预约加油"类需求，不放松全局口径）
        assertTrue(
            RecordValidator.isValidRecord(
                "2099-01-01", 10_000.0, 40.0, 300.0, 290.0, allowFuture = true, today = today,
            ),
        )
    }

    @Test
    fun `调价日期规范化拒绝伪日期`() {
        // 带年份分支只拼字符串的话，"2026-02-30" 会原样进入设置与价格库
        assertNull(OilPriceDateUtil.normalize("2026-02-30"))
        assertNull(OilPriceDateUtil.normalize("2026-13-01"))
        assertNull(OilPriceDateUtil.normalize("2026-00-10"))
        assertNull(OilPriceDateUtil.normalize("2026年2月30日"))
        // 合法日期保持既有输出格式（补零）
        assertEquals("2026-10-29", OilPriceDateUtil.normalize("2026-10-29"))
        assertEquals("2026-10-29", OilPriceDateUtil.normalize("2026年10月29日"))
        assertEquals("2026-02-03", OilPriceDateUtil.normalize("2026/2/3"))
    }

    // ===== 云同步推送前合并：不丢其它设备的新记录，也不把本机删除拉回来 =====

    private fun cloudRec(date: String, mileage: Double) = BackupCodec.RecordData(
        reoilDate = date,
        mileage = mileage,
        oilAmount = 40.0,
        payableAmount = 300.0,
        actualAmount = 300.0,
    )

    @Test
    fun `同步合并只补云端独有记录并去重`() {
        val local = listOf(rec(1, "2026-03-01", 1000.0))
        val cloud = listOf(
            cloudRec("2026-03-01", 1000.0),  // 本地已有
            cloudRec("2026-03-05", 1200.0),  // 另一台设备新增
            cloudRec("2026-03-05", 1200.0),  // 云端快照内部重复
        )
        val missing = RecordMerger.missing(cloud, local)
        assertEquals(1, missing.size)
        assertEquals("2026-03-05", missing[0].reoilDate)
        assertEquals(1200.0, missing[0].mileage, 0.0)
    }

    @Test
    fun `同步合并跳过本机删除墓碑`() {
        val cloud = listOf(
            cloudRec("2026-03-01", 1000.0),  // 本机刚删掉，云端旧快照还在
            cloudRec("2026-03-05", 1200.0),
        )
        val tombstones = setOf(RecordMerger.keyOf("2026-03-01", 1000.0))
        val missing = RecordMerger.missing(cloud, emptyList(), tombstones)
        assertEquals(1, missing.size)
        assertEquals(1200.0, missing[0].mileage, 0.0)
    }

    @Test
    fun `恢复预览过滤墓碑并保留可恢复记录`() {
        val cloud = listOf(
            cloudRec("2026-03-01", 1000.0),  // 本机已删，必须跳过
            cloudRec("2026-03-05", 1200.0),  // 仍可恢复
        )
        val tombstones = setOf(RecordMerger.keyOf("2026-03-01", 1000.0))
        val candidates = RecordMerger.withoutTombstones(cloud, tombstones)
        assertEquals(1, candidates.size)
        assertEquals("2026-03-05", candidates[0].reoilDate)
        assertEquals(1200.0, candidates[0].mileage, 0.0)
    }

    @Test
    fun `油价检测失败不写 8 小时节流时间戳`() {
        assertFalse(
            OilPriceDbSyncer.shouldWriteLastCheckAt(
                OilPriceSyncResult(success = false, message = "双源失败"),
            ),
        )
        assertTrue(
            OilPriceDbSyncer.shouldWriteLastCheckAt(
                OilPriceSyncResult(success = true, message = "成功"),
            ),
        )
    }

    @Test
    fun `同步合并空云端不产生写入`() {
        assertTrue(
            RecordMerger.missing(emptyList(), listOf(rec(1, "2026-03-01", 1000.0))).isEmpty()
        )
    }

    // ===== 备份编解码：脏值/null 不能把同步卡死 =====

    // 说明：org.json 在普通 JVM 单测里是 Android stub，真实序列化测试放在
    // BackupCodecRobolectricTest（Robolectric 提供 Android org.json 实现）。

    // ===== 省份白名单（定位/导入的合法性闸门） =====

    @Test
    fun `省份白名单完整且无重复`() {
        val all = com.llpop.koil.data.oil.Provinces.ALL
        assertEquals("应为 31 个省级行政区", 31, all.size)
        assertEquals("不得重复", all.size, all.toSet().size)
        assertTrue(all.all { it.isNotBlank() })
    }

    @Test
    fun `省份校验拒绝空值与未知地区`() {
        assertTrue(com.llpop.koil.data.oil.Provinces.isValid("广东"))
        assertTrue(com.llpop.koil.data.oil.Provinces.isValid("内蒙古"))
        assertFalse(com.llpop.koil.data.oil.Provinces.isValid(null))
        assertFalse(com.llpop.koil.data.oil.Provinces.isValid(""))
        // 定位/接口可能返回英文或带后缀的全称，未归一化前必须拒绝，否则四宫格查不到价
        assertFalse(com.llpop.koil.data.oil.Provinces.isValid("Guangdong"))
        assertFalse(com.llpop.koil.data.oil.Provinces.isValid("广东省"))
    }

    // ===== 天数差（导出/明细用） =====

    @Test
    fun `天数差计算与非法日期容错`() {
        assertEquals(7L, DerivedCalculator.daysBetween("2026-01-01", "2026-01-08"))
        assertEquals(0L, DerivedCalculator.daysBetween("2026-02-28", "2026-02-28"))
        // 跨闰年 2 月
        assertEquals(1L, DerivedCalculator.daysBetween("2024-02-28", "2024-02-29"))
        assertNull(DerivedCalculator.daysBetween("2026-02-30", "2026-03-01"))
        assertNull(DerivedCalculator.daysBetween("bad", "2026-03-01"))
    }

    // ===== 趋势抽样：超过上限时必须保留最后一条且不重复 =====

    @Test
    fun `趋势抽样保留首尾且不丢最后一条`() {
        val sixtyOne = (0 until 61).toList()
        val sampled = TrendSampler.sample(sixtyOne, 60)
        assertEquals("不超过上限", 60, sampled.size)
        assertEquals("保留第一条", 0, sampled.first())
        assertEquals("必须保留最后一条", 60, sampled.last())
        assertEquals("下标不得重复", sampled.size, sampled.toSet().size)

        val hundred = (0 until 100).toList()
        val sampled100 = TrendSampler.sample(hundred, 60)
        assertEquals(60, sampled100.size)
        assertEquals(99, sampled100.last())
        assertTrue("保持时间升序", sampled100 == sampled100.sorted())
    }

    @Test
    fun `趋势抽样不超过上限时原样返回`() {
        val ten = (0 until 10).toList()
        assertEquals(ten, TrendSampler.sample(ten, 60))
    }

    // ===== 统计口径：累计里程必须包含短途段，油耗仍只用长段 =====

    @Test
    fun `累计里程包含短途段而油耗口径不受影响`() {
        val rows = listOf(
            row(distance = 50.0),
            row(distance = 120.0),
            row(distance = null),
            row(distance = -8.0), // 里程回拨，负段不计入
        )
        // 50km 短途真实行驶过，累计里程必须计入
        assertEquals(170.0, SummaryMath.totalDistance(rows), 1e-9)
        // 平均单次里程按「每次加油」：50 与 120 两段
        val avg = SummaryMath.avgDistancePerReoil(rows)
        assertNotNull(avg)
        assertEquals(85.0, avg!!, 1e-9)
    }

    @Test
    fun `没有有效里程段时平均里程为空而不是零`() {
        assertNull(SummaryMath.avgDistancePerReoil(listOf(row(distance = null))))
    }

    private fun row(distance: Double?): RecordWithDerived = RecordWithDerived(
        record = KOiLRecordEntity(
            reoilDate = "2026-03-01",
            mileage = 1000.0,
            oilAmount = 40.0,
            payableAmount = 300.0,
            actualAmount = 300.0,
            oilPrice = 7.5,
            oilGrade = "92",
            province = "广东",
            source = "entry_page",
        ),
        distance = distance,
    )
}
