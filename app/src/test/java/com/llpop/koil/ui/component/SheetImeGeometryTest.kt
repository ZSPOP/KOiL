package com.llpop.koil.ui.component

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 弹层键盘几何的边界回归：这几条对应真机上出现过的四种坏形态 ——
 * 收键盘回弹、弹起冲顶、键盘占半屏时面板盖住键盘、键盘留白一刀切。
 *
 * 尺寸全部用 px，取值按 1080p 竖屏（窗口高 2400px、状态栏 120px、导航栏 66px）折算。
 */
class SheetImeGeometryTest {

    private val windowPx = 2400
    private val topInsetPx = 120
    private val navBottomPx = 66
    private val topGapPx = 48 // 16dp @3x
    private val idleExtraPx = 48
    private val imeGapPx = 36 // 12dp @3x
    private val minContentPx = 600 // 200dp @3x
    private val keyboardPx = 1000

    private fun geometry(
        imePx: Int,
        imeVisible: Boolean = imePx > 0,
        maxImePx: Int = keyboardPx,
        windowPx: Int = this.windowPx,
        maxHeightPx: Int = Int.MAX_VALUE,
    ) = sheetImeGeometry(
        imePx = imePx,
        imeVisible = imeVisible,
        maxImePx = maxImePx,
        windowPx = windowPx,
        topInsetPx = topInsetPx,
        navBottomPx = navBottomPx,
        imeGapPx = imeGapPx,
        idleExtraPx = idleExtraPx,
        minContentPx = minContentPx,
        topGapPx = topGapPx,
        maxHeightPx = maxHeightPx,
    )

    @Test
    fun `keyboard fully shown caps content above keyboard`() {
        val g = geometry(imePx = keyboardPx)
        // 2400 - 1000 - 120 - 48 - 36
        assertEquals(1196, g.contentCapPx)
        assertEquals(36, g.bottomSpacePx)
    }

    @Test
    fun `keyboard hidden keeps bottom space at nav plus extra`() {
        val g = geometry(imePx = 0)
        assertEquals(Int.MAX_VALUE, g.contentCapPx)
        assertEquals(navBottomPx + idleExtraPx, g.bottomSpacePx)
    }

    @Test
    fun `ime zero while still visible must not bounce back`() {
        // 收起末尾：ime 先归零、isImeVisible 还挂 true，必须按收起算，
        // 否则会按键盘留白算一帧、下一帧再跳回来（先下收再上弹）
        val g = geometry(imePx = 0, imeVisible = true, maxImePx = keyboardPx)
        assertEquals(navBottomPx + idleExtraPx, g.bottomSpacePx)
        assertEquals(Int.MAX_VALUE, g.contentCapPx)
    }

    @Test
    fun `bottom space interpolates with keyboard progress`() {
        val half = geometry(imePx = keyboardPx / 2)
        // 留白在 36 与 114 之间取一半
        assertEquals(75, half.bottomSpacePx)
        assertTrue(half.contentCapPx > geometry(imePx = keyboardPx).contentCapPx)
    }

    @Test
    fun `cap never exceeds keyboard top on small landscape`() {
        // 键盘占掉半屏以上：可视区低于 200dp，下限不能把面板顶回 200dp 盖住键盘
        val smallWindow = 1200
        val g = geometry(imePx = 700, windowPx = smallWindow, maxImePx = 700)
        // 1200 - 700 - 120 - 48 - 36 = 296
        assertEquals(296, g.contentCapPx)
        assertTrue(g.contentCapPx < minContentPx)
    }

    @Test
    fun `cap keeps minimum when there is room`() {
        // 键盘很矮、可视区远大于 200dp，下限失效，几何按可视区算
        val g = geometry(imePx = 200, maxImePx = 200)
        // 2400 - 200 - 120 - 48 - 36 = 1996
        assertEquals(1996, g.contentCapPx)
    }

    @Test
    fun `unmeasurable keyboard falls back to ratio estimate once`() {
        // 整机从未报过非零 ime：可见即为键盘全出，按 0.45 屏高估算，至少不冲顶
        val g = geometry(imePx = 0, imeVisible = true, maxImePx = 0)
        val estimated = (windowPx * SheetImeFallbackRatio).toInt()
        assertEquals(36, g.bottomSpacePx)
        assertEquals(windowPx - estimated - topInsetPx - topGapPx - 36, g.contentCapPx)
    }

    @Test
    fun `progress clamps between zero and one`() {
        assertEquals(0f, keyboardProgressPx(imePx = 0, imeVisible = false, maxImePx = 0, windowPx = 2400))
        assertEquals(1f, keyboardProgressPx(imePx = 2000, imeVisible = true, maxImePx = 1000, windowPx = 2400))
    }
}
