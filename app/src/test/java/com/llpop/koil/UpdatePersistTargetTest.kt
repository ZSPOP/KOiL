package com.llpop.koil

import com.llpop.koil.data.update.UpdateTarget
import com.llpop.koil.data.update.resolvePersistTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 「一次检查结果该不该落库」的口径。
 *
 * 线上真实翻车：V1.1.16 在 Gitee 上「Release 已建、apk 附件还在传」的那 1~3 分钟里检查了一次，
 * 存下 (V1.1.16, 空直链)。之后点更新只会回退浏览器，而 45 分钟节流窗口内不会重查，
 * 用户看到的就是「显示可更新，怎么点都跳浏览器」。
 */
class UpdatePersistTargetTest {

    private val url = "https://gitee.com/ZSPOP/KOiL/releases/download/V1.1.16/KOiL-V1.1.16-vc233-release.apk"

    @Test
    fun `直链可用且确实更新时才落库`() {
        assertEquals(UpdateTarget("V1.1.16", url), resolvePersistTarget("V1.1.16", url, true))
    }

    @Test
    fun `直链为空不落库_否则会卡成点了只能跳浏览器`() {
        assertNull(resolvePersistTarget("V1.1.16", "", true))
        assertNull(resolvePersistTarget("V1.1.16", "   ", true))
    }

    @Test
    fun `不比本机新时即使有直链也不落库`() {
        assertNull(resolvePersistTarget("V1.1.15", url, false))
    }
}
