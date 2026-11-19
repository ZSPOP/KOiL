package com.llpop.koil.data.oil

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 取价口径：界面卡片、应付金额、偏差校验共用同一套油号映射。
 * 未知油号必须回落 92#，否则「界面按 92# 算、校验拿原始字符串查不到」会分叉。
 */
class PriceLookupTest {

    @Test
    fun gradeLabel_mapsKnownGrades() {
        assertEquals("92号汽油", PriceLookup.gradeLabel("92"))
        assertEquals("95号汽油", PriceLookup.gradeLabel("95"))
        assertEquals("98号汽油", PriceLookup.gradeLabel("98"))
        assertEquals("0号柴油", PriceLookup.gradeLabel("0"))
    }

    @Test
    fun gradeLabelOrNull_onlyAcceptsKnownGrades() {
        assertEquals("92号汽油", PriceLookup.gradeLabelOrNull("92"))
        assertEquals("0号柴油", PriceLookup.gradeLabelOrNull("0"))
        assertEquals(null, PriceLookup.gradeLabelOrNull(""))
        assertEquals(null, PriceLookup.gradeLabelOrNull("95#"))
        assertEquals(null, PriceLookup.gradeLabelOrNull("unknown"))
    }

    @Test
    fun gradeLabel_fallsBackTo92_forUnknownGrade() {
        assertEquals(PriceLookup.DEFAULT_GRADE, PriceLookup.gradeLabel(""))
        assertEquals(PriceLookup.DEFAULT_GRADE, PriceLookup.gradeLabel("92#"))
        assertEquals(PriceLookup.DEFAULT_GRADE, PriceLookup.gradeLabel("biodiesel"))
    }
}
