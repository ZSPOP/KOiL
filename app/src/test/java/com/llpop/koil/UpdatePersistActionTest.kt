package com.llpop.koil

import com.llpop.koil.data.update.UpdatePersistAction
import com.llpop.koil.data.update.UpdateTarget
import com.llpop.koil.data.update.advancesLastCheckAt
import com.llpop.koil.data.update.resolvePersistAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「一次检查结果怎么落库」的三态口径（Write / Clear / Keep）。
 *
 * 1.1.15 的实测故障：V1.1.16 在 Gitee 上「Release 已建、apk 附件还在传」的那 1~3 分钟里
 * 检查了一次，存下 (V1.1.16, 空直链)；当时点更新只会回退浏览器，45 分钟节流窗口内又不重查，
 * 用户看到的就是「显示可更新，怎么点都跳浏览器」。现在点击会就地重查，但空直链仍不该落库——
 * 那会让已有可用直链的用户白点一次、且这轮本可以等下次检查补上。
 *
 * 三个分支必须各自独立断言：只测 Write 等于没测这次的核心改动（Keep 与 Clear 的区别全在另外两条）。
 */
class UpdatePersistActionTest {

    private val url = "https://gitee.com/ZSPOP/KOiL/releases/download/V1.1.16/KOiL-V1.1.16-vc233-release.apk"

    @Test
    fun `直链可用且确实更新时写入并推进节流`() {
        val action = resolvePersistAction("V1.1.16", url, true)

        assertEquals(UpdatePersistAction.Write(UpdateTarget("V1.1.16", url)), action)
        assertTrue(action.advancesLastCheckAt)
    }

    @Test
    fun `直链为空时保留上一次可用直链_不推进节流`() {
        // 保留：不清空库里已有的可用直链，横幅与红点不因撞窗口而消失
        val blank = resolvePersistAction("V1.1.16", "", true)
        assertEquals(UpdatePersistAction.Keep, blank)

        val spaces = resolvePersistAction("V1.1.16", "   ", true)
        assertEquals(UpdatePersistAction.Keep, spaces)

        // 不推进：否则下次冷启动本该重查补直链，却被 45 分钟窗口跳过
        assertFalse(blank.advancesLastCheckAt)
        assertFalse(spaces.advancesLastCheckAt)
    }

    @Test
    fun `不比本机新时清空并推进节流`() {
        // 即使直链可用也要清空：库里不留指向更低版本的入口
        val action = resolvePersistAction("V1.1.15", url, false)

        assertEquals(UpdatePersistAction.Clear, action)
        assertTrue(action.advancesLastCheckAt)
    }
}
