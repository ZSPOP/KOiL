package com.llpop.koil

import com.llpop.koil.data.oil.Provinces
import com.llpop.koil.ui.component.gradeEnMapping
import com.llpop.koil.ui.component.gradeLabelFor
import com.llpop.koil.ui.component.provinceLabelFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/**
 * 省份 / 油号的显示名映射。
 *
 * 这两组是**数据库主键**（"广东"、"92号汽油"），显示层做本地化时只要有一个键漏了，
 * 英文界面里就会冒出中文（真机反馈过两次）。这里把"覆盖是否完整"变成断言。
 */
class ProvinceLocalizationTest {

    private val en = Locale.ENGLISH
    private val zhCn = Locale.SIMPLIFIED_CHINESE
    private val zhTw = Locale.TRADITIONAL_CHINESE

    private val cjk = Regex("[\\u4e00-\\u9fff]")

    @Test
    fun `每个省份都有英文显示名`() {
        val missing = Provinces.ALL.filter { provinceLabelFor(en, it) == it }
        assertTrue("英文映射缺失：$missing", missing.isEmpty())
        assertEquals(Provinces.ALL.size, Provinces.ALL.distinct().size)
    }

    @Test
    fun `英文省份名不含中文`() {
        Provinces.ALL.forEach { p ->
            val label = provinceLabelFor(en, p)
            assertFalse("英文省份名仍含中文：$p -> $label", cjk.containsMatchIn(label))
        }
    }

    @Test
    fun `繁体省份名不为空且不落到英文`() {
        Provinces.ALL.forEach { p ->
            val label = provinceLabelFor(zhTw, p)
            assertTrue("繁体省份名为空：$p", label.isNotBlank())
            assertNotEquals("繁中省份名落到了英文：$p", provinceLabelFor(en, p), label)
        }
    }

    @Test
    fun `简体省份名保持原键`() {
        Provinces.ALL.forEach { p ->
            assertEquals(p, provinceLabelFor(zhCn, p))
        }
    }

    @Test
    fun `油号英文显示名覆盖四种且无中文`() {
        assertEquals(4, gradeEnMapping.size)
        gradeEnMapping.forEach { (key, label) ->
            assertFalse("英文油号含中文：$key -> $label", cjk.containsMatchIn(label))
            assertEquals(label, gradeLabelFor(en, key))
        }
        // 英文下任何油号都不应回落到中文键
        gradeEnMapping.keys.forEach { key ->
            assertNotEquals(key, gradeLabelFor(en, key))
        }
    }

    @Test
    fun `未知键原样返回不崩溃`() {
        assertEquals("未知省份", provinceLabelFor(en, "未知省份"))
        assertEquals("未知油号", gradeLabelFor(zhTw, "未知油号"))
        assertEquals("", provinceLabelFor(en, ""))
    }
}
