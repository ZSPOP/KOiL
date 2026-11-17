package com.llpop.koil

import com.llpop.koil.data.update.apkFileNameFor
import com.llpop.koil.data.update.shouldPrunePackage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 安装包命名与旧包清理的判据。
 *
 * 这两条都是「用户能不能一眼看出盘上是哪个版本、该删哪一个」的全部依据：
 * 之前文件名固定 `KOiL-update.apk`，盘上没有任何版本信息，旧包也永远认不出是谁的。
 */
class ApkPackageNameTest {

    @Test
    fun `文件名带大写 V 与版本号`() {
        assertEquals("KOiL-V1.2.6.apk", apkFileNameFor("V1.2.6"))
    }

    @Test
    fun `tag 不带 V 前缀时也归一成同一种写法`() {
        // Gitee 上个别历史 tag 是小写 v 或干脆没有前缀，不能因此生成两个不同的文件名
        assertEquals(apkFileNameFor("V1.2.6"), apkFileNameFor("1.2.6"))
        assertEquals(apkFileNameFor("V1.2.6"), apkFileNameFor("v1.2.6"))
    }

    @Test
    fun `不同版本的包名必须不同 —— 否则无从分辨该删哪个`() {
        assertTrue(apkFileNameFor("V1.2.5") != apkFileNameFor("V1.2.6"))
    }

    @Test
    fun `清理时保留当前 tag 那一份`() {
        val keep = apkFileNameFor("V1.2.6")
        assertFalse(shouldPrunePackage(keep, keep))
    }

    @Test
    fun `其它版本的 apk 一律删掉`() {
        val keep = apkFileNameFor("V1.2.6")
        assertTrue(shouldPrunePackage(apkFileNameFor("V1.2.5"), keep))
        assertTrue(shouldPrunePackage("KOiL-update.apk", keep))
    }

    @Test
    fun `临时文件不受清理影响 —— 它归下载自己管`() {
        val keep = apkFileNameFor("V1.2.6")
        assertFalse(shouldPrunePackage("KOiL-update.part", keep))
        assertFalse(shouldPrunePackage("KOiL-V1.2.6.apk.part", keep))
    }
}
