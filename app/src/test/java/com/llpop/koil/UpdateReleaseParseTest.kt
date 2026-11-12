package com.llpop.koil

import com.llpop.koil.data.update.UpdateDownloadState
import com.llpop.koil.data.update.parseGiteeLatestRelease
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Gitee `releases/latest` 解析。
 *
 * 线上只会跑到「正常响应」一条路径，而线上出错的恰恰是其它几种：
 * assets 里混着源码包时下到 zip、字段缺失时拿到半条数据、tag 与链接不同源。
 * 这些在这里全部覆盖。
 */
class UpdateReleaseParseTest {

    private val real = """
        {"id":1302471,"tag_name":"V1.1.9","target_commitish":"abc","prerelease":false,
         "name":"V1.1.9","body":"Gitee 镜像",
         "assets":[
           {"browser_download_url":"https://gitee.com/ZSPOP/KOiL/releases/download/V1.1.9/KOiL-V1.1.9-vc151-release.apk","name":"KOiL-V1.1.9-vc151-release.apk"},
           {"browser_download_url":"https://gitee.com/ZSPOP/KOiL/archive/refs/tags/V1.1.9.zip","name":"V1.1.9.zip"},
           {"browser_download_url":"https://gitee.com/ZSPOP/KOiL/archive/refs/tags/V1.1.9.tar.gz","name":"V1.1.9.tar.gz"}
         ]}
    """.trimIndent()

    @Test
    fun `真实响应取到 tag 与 apk 直链`() {
        val r = parseGiteeLatestRelease(real)!!
        assertEquals("V1.1.9", r.first)
        assertEquals(
            "https://gitee.com/ZSPOP/KOiL/releases/download/V1.1.9/KOiL-V1.1.9-vc151-release.apk",
            r.second,
        )
    }

    @Test
    fun `只有源码包时保留 tag 但没有直链`() {
        // 镜像链只传上源码包（APK 上传失败）时要仍能提示有新版本，只是点进去回退浏览器；
        // 若这里返回 null，用户会永远收不到更新提示。
        val onlySources = """
            {"tag_name":"V1.2.0","assets":[
              {"browser_download_url":"https://x/V1.2.0.zip","name":"V1.2.0.zip"},
              {"browser_download_url":"https://x/V1.2.0.tar.gz","name":"V1.2.0.tar.gz"}]}
        """.trimIndent()
        val r = parseGiteeLatestRelease(onlySources)!!
        assertEquals("V1.2.0", r.first)
        assertEquals("", r.second)
    }

    @Test
    fun `缺 tag 返回 null`() {
        assertNull(parseGiteeLatestRelease("""{"assets":[{"browser_download_url":"https://x/a.apk"}]}"""))
    }

    @Test
    fun `空 body 返回 null 而不是崩溃`() {
        // 限流/网关错误页会给 200 + HTML，正则取不到任何东西
        assertNull(parseGiteeLatestRelease("<html>rate limited</html>"))
        assertNull(parseGiteeLatestRelease(""))
    }

    @Test
    fun `下载进度 total 未知时 percent 为 -1`() {
        assertEquals(-1, UpdateDownloadState.Downloading(1024, -1).percent)
        assertEquals(-1, UpdateDownloadState.Downloading(1024, 0).percent)
    }

    @Test
    fun `下载进度百分比不超过 100`() {
        assertEquals(50, UpdateDownloadState.Downloading(50, 100).percent)
        // 服务端 Content-Length 偏小时不能显示 120%
        assertEquals(100, UpdateDownloadState.Downloading(120, 100).percent)
    }
}
