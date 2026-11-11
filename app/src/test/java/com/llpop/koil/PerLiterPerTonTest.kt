package com.llpop.koil

import com.llpop.koil.ui.screen.add.perLiterPerTon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 元/吨 → 元/升 的密度口径。
 *
 * 这条换算被四条路径共用（快照还原、后台刷新、手动切油号、油号自动跟随），
 * 之前只有前三条走它、自动跟随那两条把折合值停在旧油号上，所以单独钉住密度值。
 */
class PerLiterPerTonTest {

    private val delta = 1e-9

    @Test
    fun `汽油按 1316 升每吨折算`() {
        assertEquals(0.13, perLiterPerTon(170.0, "92"), delta)
        assertEquals(0.13, perLiterPerTon(170.0, "95"), delta)
        assertEquals(0.13, perLiterPerTon(170.0, "98"), delta)
    }

    @Test
    fun `柴油按 1205 升每吨折算`() {
        assertEquals(0.14, perLiterPerTon(170.0, "0"), delta)
    }

    @Test
    fun `切到柴油油号后折合值变大`() {
        assertTrue(perLiterPerTon(170.0, "0") > perLiterPerTon(170.0, "92"))
    }

    @Test
    fun `保留两位小数且四舍五入`() {
        // 345 / 1316 = 0.26215... → 0.26
        assertEquals(0.26, perLiterPerTon(345.0, "92"), delta)
        // -934 / 1316 = -0.70972... → -0.71
        assertEquals(-0.71, perLiterPerTon(-934.0, "92"), delta)
    }
}
