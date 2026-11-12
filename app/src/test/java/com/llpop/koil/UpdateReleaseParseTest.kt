package com.llpop.koil

import com.llpop.koil.data.update.UpdateDownloadState
import com.llpop.koil.data.update.selectLatestRelease
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Gitee release 列表的挑选逻辑。
 *
 * 线上只会跑「三条正式版、排序正常」这一条路径，而真正会翻车的是另外几种：
 * 补传旧版本把"最新"顶掉、混进 prerelease、assets 里只有源码包、网关返回 HTML。
 * 每一条都在这里固定住，并在 PR 里说明对应的是哪种真实场景。
 */
class UpdateReleaseParseTest {

    /** 用拼接而不是三引号模板：assets 之间的逗号靠手写极易漏，漏了整段 JSON 就非法、断言全变 null */
    private fun release(
        tag: String,
        id: Int,
        apk: String? = "https://gitee.com/x/KOiL-$tag-release.apk",
        pre: Boolean = false,
    ): String {
        val assets = buildList {
            if (apk != null) {
                add("""{"browser_download_url":"$apk","name":"KOiL-$tag-release.apk"}""")
            }
            add("""{"browser_download_url":"https://gitee.com/ZSPOP/KOiL/archive/refs/tags/$tag.zip","name":"$tag.zip"}""")
        }.joinToString(",")
        return """{"id":$id,"tag_name":"$tag","prerelease":$pre,"name":"$tag","body":"notes","assets":[$assets]}"""
    }

    @Test
    fun `三条正式版取版本号最大的 V1_1_9`() {
        val json = "[" + listOf(
            release("V1.1.7", 1302003),
            release("V1.1.8", 1302198),
            release("V1.1.9", 1302471),
        ).joinToString(",") + "]"
        val r = selectLatestRelease(json)!!
        assertEquals("V1.1.9", r.tag)
        assertEquals("https://gitee.com/x/KOiL-V1.1.9-release.apk", r.apkUrl)
    }

    @Test
    fun `补传旧版本排在最后也不会顶掉最新版`() {
        // 这是评审指出的真实场景：补传 = 新 id + 新 created_at，
        // /releases/latest 会返回 V1.1.5（旧版）→ 与本机比较 not newer → 红点静默熄灭。
        // 这里列表顺序把旧版放在最后，模拟"最新创建"，断言仍挑 V1.1.9。
        val json = "[" + listOf(
            release("V1.1.7", 1302003),
            release("V1.1.8", 1302198),
            release("V1.1.9", 1302471),
            release("V1.1.5", 1302888),
        ).joinToString(",") + "]"
        assertEquals("V1.1.9", selectLatestRelease(json)?.tag)
    }

    @Test
    fun `prerelease 不覆盖正式版`() {
        val json = "[" + listOf(
            release("V1.1.9", 1302471),
            release("V1.2.0-beta", 1302900, pre = true),
        ).joinToString(",") + "]"
        assertEquals("V1.1.9", selectLatestRelease(json)?.tag)
    }

    @Test
    fun `只有源码包时保留 tag 但没有直链`() {
        // 镜像链只传上源码包（APK 上传失败）时要仍能提示有新版本，只是点进去回退浏览器；
        // 若这里返回 null，用户会永远收不到更新提示。
        val json = "[" + release("V1.1.9", 1302471, apk = null) + "]"
        val r = selectLatestRelease(json)!!
        assertEquals("V1.1.9", r.tag)
        assertEquals("", r.apkUrl)
    }

    @Test
    fun `body 里贴的 apk 链接不会被当成资源`() {
        // 按结构解析 assets，而不是正则扫全文：正文里的下载链接不能变成下载目标
        val json = """
            [{"id":1,"tag_name":"V1.1.9","prerelease":false,
              "body":"手动下载：https://evil.example.com/KOiL-fake.apk 或 https://gitee.com/a.zip",
              "assets":[{"browser_download_url":"https://gitee.com/real/KOiL-real.apk","name":"KOiL-real.apk"}]}]
        """.trimIndent()
        assertEquals("https://gitee.com/real/KOiL-real.apk", selectLatestRelease(json)?.apkUrl)
    }

    @Test
    fun `空列表与错误页返回 null 而不是崩溃`() {
        // 限流/网关错误会给 200 + HTML
        assertNull(selectLatestRelease("[]"))
        assertNull(selectLatestRelease("<html>rate limited</html>"))
        assertNull(selectLatestRelease(""))
        assertNull(selectLatestRelease("""{"message":"403 rate limit"}"""))
    }

    @Test
    fun `全是 prerelease 时返回 null —— 没有正式版就什么都不提示`() {
        val json = "[" + listOf(
            release("V1.2.0", 9001, pre = true),
            release("V1.1.9", 9002, pre = true),
        ).joinToString(",") + "]"
        assertNull(selectLatestRelease(json))
    }

    @Test
    fun `多个 apk 时优先 KOiL- 前缀的包，不靠服务端顺序`() {
        // keep=5 时每版只有一个包，这条是纯运气；把 KOiL 包排在**第二位**证明确实按名字挑
        val json = """[{"id":9,"tag_name":"V1.1.10","prerelease":false,"assets":[
            {"browser_download_url":"https://gitee.com/x/other.apk","name":"other.apk"},
            {"browser_download_url":"https://gitee.com/x/KOiL-V1.1.10-vc161-release.apk","name":"KOiL-V1.1.10-vc161-release.apk"}
        ]}]"""
        assertEquals(
            "https://gitee.com/x/KOiL-V1.1.10-vc161-release.apk",
            selectLatestRelease(json)?.apkUrl,
        )
    }

    @Test
    fun `下载进度 total 未知时 percent 为 -1 且不超过 100`() {
        assertEquals(-1, UpdateDownloadState.Downloading(1024, -1).percent)
        assertEquals(-1, UpdateDownloadState.Downloading(1024, 0).percent)
        assertEquals(50, UpdateDownloadState.Downloading(50, 100).percent)
        assertEquals(100, UpdateDownloadState.Downloading(120, 100).percent)
    }
}
