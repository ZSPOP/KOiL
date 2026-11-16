package com.llpop.koil.data.sync

import com.llpop.koil.data.settings.AppSettings

/**
 * 云同步「有效配置」的唯一口径。
 *
 * 地址 / 账号 / 内置应用密码这三条默认值只在这里兜底，且**永不落盘**：
 * 存储为空时按这里算出来的有效值去连接，界面也按同一个口径显示。
 *
 * 之所以收成一个对象，是因为以前同一个口径散在四处——开关回调一处、输入框初始化一处、
 * 状态判定一处（只认「存储里有没有密码」）、`CloudSyncManager.isConfigured()` 一处。
 * 后果是全新安装时三格都显示有值、状态却报「未設定完整」，用户以为是默认值被删了。
 */
object SyncConfig {

    /** 内置默认账号（与默认地址、默认应用密码同级；只讨论「什么时候用」，不讨论内容） */
    const val DEFAULT_USERNAME = "Kay@LLPop.CoM"

    /** 内置默认应用密码（同上，内容不参与讨论） */
    const val DEFAULT_APP_PASSWORD = "aees8xuzs3dzsdz3"

    /** 内置默认目录，与 [AppSettings.DEFAULT_SYNC_URL] 同源，避免两处各写一份 */
    const val DEFAULT_URL = AppSettings.DEFAULT_SYNC_URL

    /** 有效地址：存储为空才回落默认（存过空串的老数据也一并兜住） */
    fun effectiveUrl(stored: String): String = stored.ifBlank { DEFAULT_URL }

    /** 有效账号：存储为空才回落默认 */
    fun effectiveUsername(stored: String): String = stored.ifBlank { DEFAULT_USERNAME }

    /**
     * 有效应用密码。
     *
     * @param stored 存储里的解密结果（密文解不开时调用方传空，并按未配置处理，不走这里的兜底）
     * @param everSet 是否**设置过**密码：从未设置 → 回落内置默认；设置过又被清空 → 返回空，
     *   即「用户主动停用」，不能偷偷再拿默认密码连上去。
     */
    fun effectivePassword(stored: String, everSet: Boolean): String =
        stored.ifBlank { if (everSet) "" else DEFAULT_APP_PASSWORD }

    /** 三项是否都有效（界面「已設定完整」与开关回调共用这一条判定） */
    fun isComplete(url: String, username: String, password: String): Boolean =
        url.isNotBlank() && username.isNotBlank() && password.isNotBlank()

    // ---- 落盘守卫：这三个值可以由兜底算出来，但绝不允许被当成「用户填的」写进存储 ----

    fun isBuiltInUrl(value: String): Boolean = value.trim() == DEFAULT_URL

    fun isBuiltInUsername(value: String): Boolean = value.trim() == DEFAULT_USERNAME

    fun isBuiltInPassword(value: String): Boolean = value == DEFAULT_APP_PASSWORD
}
