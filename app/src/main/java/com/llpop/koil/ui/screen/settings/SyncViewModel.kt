package com.llpop.koil.ui.screen.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.llpop.koil.data.local.dao.KOiLRecordDao
import com.llpop.koil.data.settings.AppSettings
import com.llpop.koil.data.sync.CloudSyncManager
import com.llpop.koil.data.sync.CryptoHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 云同步配置区状态（密码不回显，仅暴露是否已设置 + 是否密文存储） */
data class SyncUiState(
    val enabled: Boolean = false,
    val url: String = AppSettings.DEFAULT_SYNC_URL,
    val username: String = "",
    val passwordSet: Boolean = false,
    val passwordEncrypted: Boolean = false,
    val lastStatus: String = "",          // 原始状态（ok@时间 / fail:原因 / 空）
    val busy: Boolean = false,
)

/** 恢复预览（UI 确认弹窗用） */
data class SyncRestorePreview(
    val valid: Int,
    val duplicates: Int,
    val errors: Int,
    val total: Int,
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

    val uiState: StateFlow<SyncUiState> = combine<Any, SyncUiState>(
        settings.syncEnabled,
        settings.syncUrl,
        settings.syncUsername,
        settings.syncAppPassword,
        settings.syncPasswordEncrypted,
        settings.syncLastStatus,
        _busy,
    ) { args ->
        val stored = args[3] as String
        SyncUiState(
            enabled = args[0] as Boolean,
            url = args[1] as String,
            username = args[2] as String,
            passwordSet = stored.isNotBlank(),
            passwordEncrypted = args[4] as Boolean,
            lastStatus = args[5] as String,
            busy = args[6] as Boolean,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, SyncUiState())

    fun setEnabled(on: Boolean) {
        viewModelScope.launch {
            settings.setSyncEnabled(on)
            if (on) {
                // 开启且本地空库 → 立即自动恢复（对齐 CloudSyncManager.startupCheck 语义）
                if (recordDao.count() == 0 && manager.isConfigured()) {
                    restoreNow()
                }
            }
        }
    }

    fun setUrl(url: String) {
        viewModelScope.launch { settings.setSyncUrl(url) }
    }

    fun setUsername(username: String) {
        viewModelScope.launch { settings.setSyncUsername(username) }
    }

    /** 保存应用密码：优先 Keystore 密文，失败明文回退并标记 */
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
        }
    }

    fun clearPassword() {
        viewModelScope.launch {
            settings.setSyncAppPassword("")
            settings.setSyncPasswordEncrypted(false)
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
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            try {
                persistDrafts(urlDraft, usernameDraft, passwordDraft)
                val result = manager.restorePreview()
                if (result == null) {
                    _message.value = "restore_no_cloud"
                } else {
                    _restorePreview.value = SyncRestorePreview(
                        valid = result.valid.size,
                        duplicates = result.duplicates,
                        errors = result.errors,
                        total = result.cloudRecordCount,
                        managerResult = result,
                    )
                }
            } catch (e: Exception) {
                _message.value = "restore_failed"
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

    /** 确认恢复：插入 valid + 应用设置 */
    fun confirmRestore() {
        val preview = _restorePreview.value ?: return
        val result = preview.managerResult ?: return
        viewModelScope.launch {
            _busy.value = true
            try {
                val inserted = manager.restoreCommit(result)
                _message.value = "restore_done"
            } catch (e: Exception) {
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
