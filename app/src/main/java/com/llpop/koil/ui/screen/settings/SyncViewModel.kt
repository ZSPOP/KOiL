package com.llpop.koil.ui.screen.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.llpop.koil.data.local.dao.KOiLRecordDao
import com.llpop.koil.data.settings.AppSettings
import com.llpop.koil.data.sync.CloudSyncManager
import com.llpop.koil.data.sync.CryptoHelper
import com.llpop.koil.data.sync.SyncConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 内置默认应用密码原在本文件（作者私有账号）。
 * 已迁到 [com.llpop.koil.data.sync.SyncConfig]：默认值只在数据层兜底、永不落盘，
 * 界面、开关与 `CloudSyncManager` 三处共用同一口径，避免再出现「显示有值却报未設定完整」。
 */

/** 云同步配置区状态（密码不回显，仅暴露是否已设置 + 是否密文存储） */
data class SyncUiState(
    val enabled: Boolean = false,
    val url: String = AppSettings.DEFAULT_SYNC_URL,
    val username: String = "",
    val passwordSet: Boolean = false,
    val passwordEncrypted: Boolean = false,
    // 是否**设置过**密码：从没设置过才允许回落内置默认；设置过又被清空 = 用户主动停用。
    val passwordEverSet: Boolean = false,
    // 密文存着但解不开（换机/重装后 Keystore 主密钥丢失）——此时 isConfigured() 为 false，
    // 推送/恢复全部静默跳过，开关却仍是"已开启"。UI 必须把这件事说出来，否则用户以为同步在跑。
    val passwordInvalid: Boolean = false,
    // 明文密码（Keystore 密文在这里解一次）。输入框按"眼睛"状态决定掩码/明文，
    // 需要真实字符才能回显，否则打开眼睛也是空的。只在本机内存里的 StateFlow 中存在。
    val password: String = "",
    val lastStatus: String = "",          // 原始状态（ok@时间 / fail:原因 / 空）
    val busy: Boolean = false,
    // 云同步配置异步加载完成标志（首值到达前不渲染开关，杜绝"关→开"闪烁）
    val loaded: Boolean = false,
    // 三项有效配置（含默认值兜底）——界面显示、状态判定、开关回调全部用这一份，不再各算各的
    val effectiveUrl: String = AppSettings.DEFAULT_SYNC_URL,
    val effectiveUsername: String = "",
    /** 三项都有效且密文可解。「已設定完整」与开关是否放行只认这一条 */
    val configured: Boolean = false,
) {
    /**
     * 界面与输入框共用的「有效密码草稿」：存过就回显，没设置过回落内置默认，
     * 设置过又被清空则为空（用户主动停用）。口径统一由 [SyncConfig] 提供。
     */
    val effectivePasswordDraft: String
        get() = SyncConfig.effectivePassword(password, passwordEverSet)
}

/** 恢复预览（UI 确认弹窗用） */
data class SyncRestorePreview(
    val valid: Int,
    val duplicates: Int,
    val errors: Int,
    val total: Int,
    val skippedByTombstone: Int = 0,
    val managerResult: CloudSyncManager.RestoreResult? = null,
)

class SyncViewModel(
    private val settings: AppSettings,
    private val recordDao: KOiLRecordDao,
    private val manager: CloudSyncManager,
) : ViewModel() {

    private val _busy = MutableStateFlow(false)
    private val _restorePreview = MutableStateFlow<SyncRestorePreview?>(null)
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    val restorePreview: StateFlow<SyncRestorePreview?> = _restorePreview.asStateFlow()

    /** 解密后的密码 + 是否解不开（Keystore 主密钥丢失） */
    private data class PasswordInfo(val plain: String, val broken: Boolean)

    /**
     * 密文能否解开。Keystore 解密涉及 Cipher 初始化（有 IPC 成本），
     * 放 Default 且只在「密文/加密标志」变化时算一次——不能放在组合用的 combine 里，
     * 否则 busy 之类的开关抖动都会触发一次解密码。
     * 顺带把明文带出来给输入框回显，解密仍然只做一次。
     */
    private val passwordInfoFlow: Flow<PasswordInfo> = combine(
        settings.syncAppPassword,
        settings.syncPasswordEncrypted,
    ) { stored, encrypted ->
        when {
            stored.isBlank() -> PasswordInfo("", false)
            // 明文回退存储（Keystore 不可用时的降级路径）
            !encrypted -> PasswordInfo(stored, false)
            else -> {
                val plain = CryptoHelper.decrypt(stored).orEmpty()
                PasswordInfo(plain, plain.isBlank())
            }
        }
    }.flowOn(Dispatchers.Default)

    val uiState: StateFlow<SyncUiState> = combine<Any, SyncUiState>(
        settings.syncEnabled,
        settings.syncUrl,
        settings.syncUsername,
        settings.syncAppPassword,
        settings.syncPasswordEncrypted,
        settings.syncLastStatus,
        _busy,
        passwordInfoFlow,
        settings.syncPasswordEverSet,
    ) { args ->
        val stored = args[3] as String
        val pwd = args[7] as PasswordInfo
        val everSet = args[8] as Boolean
        val effUrl = SyncConfig.effectiveUrl(args[1] as String)
        val effUser = SyncConfig.effectiveUsername(args[2] as String)
        // 密文解不开时不给默认兜底：那属于「密码坏了」，不是「没设置过」
        val effPwd = if (pwd.broken) "" else SyncConfig.effectivePassword(pwd.plain, everSet)
        SyncUiState(
            enabled = args[0] as Boolean,
            url = args[1] as String,
            username = args[2] as String,
            passwordSet = stored.isNotBlank(),
            passwordEncrypted = args[4] as Boolean,
            passwordEverSet = everSet,
            passwordInvalid = pwd.broken,
            password = pwd.plain,
            lastStatus = args[5] as String,
            busy = args[6] as Boolean,
            loaded = true,
            effectiveUrl = effUrl,
            effectiveUsername = effUser,
            configured = SyncConfig.isComplete(effUrl, effUser, effPwd),
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, SyncUiState())

    /**
     * 开关切换。打开 = 立即同步：空库自动恢复 / 非空库立即推送。
     *
     * 不再接收地址/账号/密码草稿：有效配置由 [SyncConfig] 在数据层统一兜底，**默认值不落盘**。
     * 以前这里收三个兜底值再写进存储，正是「用户没填也能连、但界面说没配置完整」的来由。
     */
    fun setEnabled(on: Boolean) {
        // 进入即占位：只检查 _busy 而不先置位，连点开关会并发跑两轮
        if (!_busy.compareAndSet(false, true)) return
        viewModelScope.launch {
            try {
                settings.setSyncEnabled(on)
                if (!on) return@launch
                if (!manager.isConfigured()) return@launch
                if (recordDao.count() == 0) {
                    // 新机/空库：拉云端预览 → 确认弹窗（不直接写库）
                    loadRestorePreview()
                } else {
                    // 非空库：本地权威 → 立即推送到云端（镜像本地）
                    manager.pushNow(force = true)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _message.value = "backup_failed"
            } finally {
                _busy.value = false
            }
        }
    }

    fun setUrl(url: String) {
        viewModelScope.launch { settings.setSyncUrl(url) }
    }

    fun setUsername(username: String) {
        viewModelScope.launch { settings.setSyncUsername(username) }
    }

    /** 保存应用密码：优先 Keystore 密文，失败明文回退并标记。同时记为「设置过」 */
    fun setPassword(plain: String) {
        if (plain.isBlank()) return
        viewModelScope.launch {
            val encrypted = CryptoHelper.encrypt(plain)
            if (encrypted != null) {
                settings.setSyncAppPassword(encrypted)
                settings.setSyncPasswordEncrypted(true)
            } else {
                settings.setSyncAppPassword(plain)
                settings.setSyncPasswordEncrypted(false)
            }
            settings.setSyncPasswordEverSet(true)
        }
    }

    /**
     * 清空应用密码 = **主动停用云同步**。
     *
     * 两件事必须一起发生：清掉密码、把开关关掉。并把「设置过密码」记为 true——
     * 否则有效密码会回落内置默认密码，用户点了清空却还在用默认账号连，
     * 等于「停用」这个意图被系统吃掉。
     */
    fun clearPassword() {
        viewModelScope.launch {
            settings.setSyncAppPassword("")
            settings.setSyncPasswordEncrypted(false)
            settings.setSyncPasswordEverSet(true)
            settings.setSyncEnabled(false)
        }
    }

    /** 立即备份（手动强制推送，绕过 30s 频率闸）。draft 参数优先落盘（挂起等待完成）后推送 */
    fun backupNow(
        urlDraft: String? = null,
        usernameDraft: String? = null,
        passwordDraft: String? = null,
    ) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            try {
                persistDrafts(urlDraft, usernameDraft, passwordDraft)
                val ok = manager.pushNow(force = true)
                _message.value = if (ok) "backup_done" else "backup_failed"
            } catch (e: Exception) {
                _message.value = "backup_failed"
            } finally {
                _busy.value = false
            }
        }
    }

    /** 从云端恢复：先取预览（弹窗确认后再 commit）。draft 参数优先落盘 */
    fun restoreNow(
        urlDraft: String? = null,
        usernameDraft: String? = null,
        passwordDraft: String? = null,
    ) {
        if (!_busy.compareAndSet(false, true)) return
        viewModelScope.launch {
            try {
                persistDrafts(urlDraft, usernameDraft, passwordDraft)
                loadRestorePreview()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _message.value = "restore_failed"
            } finally {
                _busy.value = false
            }
        }
    }

    /** 拉取云端快照并挂到确认弹窗。不含 busy 闸——调用方（restoreNow / setEnabled）已占位 */
    private suspend fun loadRestorePreview() {
        val result = manager.restorePreview()
        if (result == null) {
            _message.value = "restore_no_cloud"
            return
        }
        _restorePreview.value = SyncRestorePreview(
            valid = result.valid.size,
            duplicates = result.duplicates,
            errors = result.errors,
            total = result.cloudRecordCount,
            skippedByTombstone = result.skippedByTombstone,
            managerResult = result,
        )
    }

    /** 保存配置并测试连接：草稿落盘（挂起等待）→ PROPFIND 验证，结果经 message 反馈 */
    fun saveAndTest(
        urlDraft: String? = null,
        usernameDraft: String? = null,
        passwordDraft: String? = null,
    ) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            try {
                persistDrafts(urlDraft, usernameDraft, passwordDraft)
                val ok = manager.testConnection()
                _message.value = if (ok) "config_saved" else "config_test_failed"
            } catch (e: Exception) {
                _message.value = "config_test_failed"
            } finally {
                _busy.value = false
            }
        }
    }

    /** 草稿落盘：挂起等待 DataStore 写入完成，保证后续 push/restore 读取到最新配置 */
    private suspend fun persistDrafts(
        urlDraft: String?,
        usernameDraft: String?,
        passwordDraft: String?,
    ) {
        if (urlDraft != null) settings.setSyncUrl(urlDraft)
        if (usernameDraft != null) settings.setSyncUsername(usernameDraft)
        if (!passwordDraft.isNullOrBlank()) {
            val encrypted = CryptoHelper.encrypt(passwordDraft)
            if (encrypted != null) {
                settings.setSyncAppPassword(encrypted)
                settings.setSyncPasswordEncrypted(true)
            } else {
                settings.setSyncAppPassword(passwordDraft)
                settings.setSyncPasswordEncrypted(false)
            }
        }
    }

    /** 确认恢复：插入 valid + 应用设置。带重入闸——连点会并发插入（幂等但会重复提示、重复网络） */
    fun confirmRestore() {
        val preview = _restorePreview.value ?: return
        val result = preview.managerResult ?: return
        if (!_busy.compareAndSet(false, true)) return
        viewModelScope.launch {
            try {
                val inserted = manager.restoreCommit(result)
                // 带上实际插入条数（算了不用，提示永远只说"已恢复"）。
                // inserted=0（云端无新增：一致/全被墓碑覆盖/全无效）改发 restore_none，
                // 不显示「已恢复 0 笔记录」这种自相矛盾的提示
                _message.value = if (inserted > 0) "restore_done:$inserted" else "restore_none"
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _message.value = "restore_failed"
            } finally {
                _restorePreview.value = null
                _busy.value = false
            }
        }
    }

    fun dismissRestorePreview() {
        _restorePreview.value = null
    }

    fun consumeMessage() {
        _message.value = null
    }
}
