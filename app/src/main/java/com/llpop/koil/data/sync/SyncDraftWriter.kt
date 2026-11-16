package com.llpop.koil.data.sync

import com.llpop.koil.data.settings.AppSettings
import kotlinx.coroutines.flow.first

/**
 * 配置草稿落盘：**只写用户真实输入**，内置默认值一律不落盘。
 *
 * 为什么单独成对象：界面上三格显示的是有效值（含默认兜底），草稿变量因此天然带着默认值。
 * 只要有一处落盘路径无条件写，就会出现「什么都不填、点一下保存」→ 默认地址/账号/密码
 * 三件套进存储 → `passwordSet=true`，与「用户真的填过」再也分不开，正是 PR ① 要修的病根换了个入口复发。
 * 收成一个对象，测试能直接跑真实 [AppSettings]，不必去构造整个 ViewModel。
 */
object SyncDraftWriter {

    /**
     * @param urlDraft 用户这轮输入的地址；null = 本轮没动过这个字段（不写）
     * @param usernameDraft 同上
     * @param passwordDraft 用户这轮输入的密码；null / 空 = 不写（空串代表「清空过」，清理走 clearPassword）
     */
    suspend fun persist(
        settings: AppSettings,
        urlDraft: String?,
        usernameDraft: String?,
        passwordDraft: String?,
    ) {
        if (urlDraft != null && !SyncConfig.isBuiltInUrl(urlDraft)) {
            settings.setSyncUrl(urlDraft)
        }
        if (usernameDraft != null && !SyncConfig.isBuiltInUsername(usernameDraft)) {
            settings.setSyncUsername(usernameDraft)
        }
        if (!passwordDraft.isNullOrBlank() && !SyncConfig.isBuiltInPassword(passwordDraft)) {
            val encrypted = CryptoHelper.encrypt(passwordDraft)
            if (encrypted != null) {
                settings.setSyncAppPassword(encrypted)
                settings.setSyncPasswordEncrypted(true)
            } else {
                settings.setSyncAppPassword(passwordDraft)
                settings.setSyncPasswordEncrypted(false)
            }
            // 经「保存并测试」写入的密码同样算「设置过」：否则换机后解密失败会静默回落默认密码
            settings.setSyncPasswordEverSet(true)
        }
    }

    /** 当前存储里是否有用户自己填的地址/账号（判断「用户真的配过」时用，不算默认） */
    suspend fun hasUserConfig(settings: AppSettings): Boolean {
        val url = settings.syncUrl.first()
        val username = settings.syncUsername.first()
        return !SyncConfig.isBuiltInUrl(url) || !SyncConfig.isBuiltInUsername(username)
    }
}
