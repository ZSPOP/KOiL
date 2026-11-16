package com.llpop.koil

import com.llpop.koil.data.update.UpdateTarget
import com.llpop.koil.data.update.pickNewestTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 两个更新源都返回结果时的取舍。
 *
 * 线上最常见的是「两源版本一致」（都取 Gitee），真正会出事的是镜像滞后：
 * Gitee 停在 V1.2.2、GitHub 已发 V1.2.3 —— 这时必须取 GitHub，否则红点永远不亮。
 */
class UpdateSourcePickTest {

    private fun gitee(tag: String) = UpdateTarget(tag, "https://gitee.com/ZSPOP/KOiL/$tag.apk")
    private fun github(tag: String) = UpdateTarget(tag, "https://github.com/ZSPOP/KOiL/$tag.apk")

    @Test
    fun `两源同版本时优先 Gitee`() {
        val picked = pickNewestTarget(gitee("V1.2.3"), github("V1.2.3"))
        assertEquals("https://gitee.com/ZSPOP/KOiL/V1.2.3.apk", picked?.apkUrl)
    }

    @Test
    fun `Gitee 镜像滞后时取 GitHub 的新版`() {
        val picked = pickNewestTarget(gitee("V1.2.2"), github("V1.2.3"))
        assertEquals("V1.2.3", picked?.tag)
        // 直链必须与 tag 同源，否则会下到上一版却把新版标成已读
        assertEquals("https://github.com/ZSPOP/KOiL/V1.2.3.apk", picked?.apkUrl)
    }

    @Test
    fun `GitHub 滞后时仍取 Gitee`() {
        val picked = pickNewestTarget(gitee("V1.2.4"), github("V1.2.3"))
        assertEquals("V1.2.4", picked?.tag)
    }

    @Test
    fun `只有一个源可用时用它`() {
        assertEquals("V1.2.3", pickNewestTarget(gitee("V1.2.3"), null)?.tag)
        assertEquals("V1.2.3", pickNewestTarget(null, github("V1.2.3"))?.tag)
    }

    @Test
    fun `两源都失败返回 null`() {
        assertNull(pickNewestTarget(null, null))
    }

    @Test
    fun `版本段数不同也能比出大小`() {
        // 1.2.10 > 1.2.9：按字符串比会得出相反结论
        assertEquals("V1.2.10", pickNewestTarget(gitee("V1.2.10"), github("V1.2.9"))?.tag)
        assertEquals("V1.3.0", pickNewestTarget(gitee("V1.2.10"), github("V1.3.0"))?.tag)
    }
}
