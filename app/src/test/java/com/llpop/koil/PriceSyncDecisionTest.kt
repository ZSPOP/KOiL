package com.llpop.koil

import com.llpop.koil.data.oil.decideForceCheckOnLaunch
import com.llpop.koil.data.oil.isPastLastEffectiveWindow
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * 启动核验判定 / 窗口过期判定的回归。
 *
 * 这两条判据决定「装完新版本首启会不会自己拉到新价」和「8 小时节流会不会把确定过期的
 * 价格一直锁在屏幕上」，都属于用户能直接看见的行为，抽成纯函数后在这里钉住。
 */
class PriceSyncDecisionTest {

    // ---- decideForceCheckOnLaunch ----

    @Test
    fun `全新安装不强制`() {
        // lastVer = 0（从没记录过）且 lastCheck = 0（也从没检测过）= 真·首装
        assertEquals(false, decideForceCheckOnLaunch(lastVer = 0L, cur = 198L, lastCheck = 0L, autoOn = true))
    }

    @Test
    fun `旧版升级上来（无版本记录但有检测历史）强制`() {
        // V1.1.14 还没写过 last-run-version-code 这个字段，靠检测历史判断「不是首装」
        assertEquals(true, decideForceCheckOnLaunch(lastVer = 0L, cur = 198L, lastCheck = 1_700_000_000_000L, autoOn = true))
    }

    @Test
    fun `有版本记录且版本变了强制`() {
        assertEquals(true, decideForceCheckOnLaunch(lastVer = 197L, cur = 198L, lastCheck = 0L, autoOn = true))
    }

    @Test
    fun `同版本不强制`() {
        assertEquals(false, decideForceCheckOnLaunch(lastVer = 198L, cur = 198L, lastCheck = 1_700_000_000_000L, autoOn = true))
    }

    @Test
    fun `降级同样视为版本变化`() {
        // 用户手动装回旧版：版本号对不上就核验一次，与升级同一条分支（无害且合理）
        assertEquals(true, decideForceCheckOnLaunch(lastVer = 198L, cur = 197L, lastCheck = 0L, autoOn = true))
    }

    @Test
    fun `自动更新关着时升级也不强制`() {
        // 关键：force = true 会把开关判断一起绕过，所以判定层必须先收口
        assertEquals(false, decideForceCheckOnLaunch(lastVer = 197L, cur = 198L, lastCheck = 1_700_000_000_000L, autoOn = false))
        assertEquals(false, decideForceCheckOnLaunch(lastVer = 0L, cur = 198L, lastCheck = 1_700_000_000_000L, autoOn = false))
    }

    // ---- isPastLastEffectiveWindow ----

    /** 与实现同口径的生效时刻：窗口日次日 00:00（本地时区） */
    private fun effectiveAt(windowDay: String): Long =
        LocalDate.parse(windowDay).plusDays(1).atStartOfDay()
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    @Test
    fun `上次检测早于生效时刻判为过期`() {
        val eff = effectiveAt("2026-11-12")            // 11-13 00:00 生效
        assertEquals(true, isPastLastEffectiveWindow(eff - 1L, "2026-11-12"))
    }

    @Test
    fun `上次检测正好等于生效时刻不算过期`() {
        // 边界：00:00 那次检测本身就是拿到新价的那次
        val eff = effectiveAt("2026-11-12")
        assertEquals(false, isPastLastEffectiveWindow(eff, "2026-11-12"))
    }

    @Test
    fun `上次检测晚于生效时刻不算过期`() {
        val eff = effectiveAt("2026-11-12")
        assertEquals(false, isPastLastEffectiveWindow(eff + 60_000L, "2026-11-12"))
    }

    @Test
    fun `从未成功检测过一律算过期`() {
        assertEquals(true, isPastLastEffectiveWindow(0L, "2026-11-12"))
        assertEquals(true, isPastLastEffectiveWindow(-1L, null))
    }

    @Test
    fun `没有 prev 窗口存档时退回节流`() {
        // 首次同步 prev 还没形成的场景：不能因为缺存档就无限联网
        assertEquals(false, isPastLastEffectiveWindow(1_700_000_000_000L, null))
        assertEquals(false, isPastLastEffectiveWindow(1_700_000_000_000L, ""))
        assertEquals(false, isPastLastEffectiveWindow(1_700_000_000_000L, "   "))
    }

    @Test
    fun `prev 不是合法日期时退回节流`() {
        assertEquals(false, isPastLastEffectiveWindow(effectiveAt("2026-11-12") - 1L, "11月12日"))
    }
}
