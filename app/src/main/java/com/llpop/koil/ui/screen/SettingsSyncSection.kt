package com.llpop.koil.ui.screen

import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import kotlinx.coroutines.delay
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.glass.GlassTextField
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import top.yukonga.miuix.kmp.glass.GlassDialogButton
import top.yukonga.miuix.kmp.glass.GlassDialogButtonRole
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.drop
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.MutableState
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.llpop.koil.R
import com.llpop.koil.ui.component.KOiLNoWhitespaceInput
import com.llpop.koil.ui.component.KOiLPasswordMask
import com.llpop.koil.data.sync.SyncConfig
import com.llpop.koil.ui.component.KOiLGlassDialog
import com.llpop.koil.ui.component.rememberSnackbar
import com.llpop.koil.data.sync.SyncReconcile
import com.llpop.koil.ui.screen.settings.SyncViewModel
import com.llpop.koil.ui.theme.KOiLColors
import org.koin.androidx.compose.koinViewModel
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.SnackbarDuration
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.llpop.koil.ui.theme.KOiLType



/**
 * 云同步卡（坚果云 WebDAV：私有数据自动备份/恢复）（M-07 拆分）。
 * 从 SettingsScreen 主函数整体移出，行为不变；showDataIoDialog 上提到页面级供弹窗共享。
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
internal fun SyncPanelBody(
    tappedOnField: MutableState<Boolean>,
    focusManager: FocusManager,
) {
        val syncVm: SyncViewModel = koinViewModel()
        val syncUi by syncVm.uiState.collectAsStateWithLifecycle()
        // 三个草稿改用 TextFieldState（Compose 的 state 版文本 API）：
        // 值版 TextField(value, onValueChange) 已弃用，state 版才有 InputTransformation/OutputTransformation。
        val urlState = rememberTextFieldState(syncUi.effectiveUrl)
        val usernameState = rememberTextFieldState(syncUi.effectiveUsername)
        // 密码草稿 = 存储里的真实密码（异步到达后回填一次），眼睛关闭时掩码、点亮显示明文。
        // 没设置过时预填内置默认密码：口径统一由 SyncConfig 给，界面不再自己算一遍。
        val pwdState = rememberTextFieldState(syncUi.effectivePasswordDraft)

        // —— 回车跳下一项：三条信号统一收口 ——
        // 1) 输入法发了 ImeAction → onKeyboardAction
        // 2) 硬件/外接键盘的 Enter 键事件 → onPreviewKeyEvent
        // 3) 输入法把回车当成字符提交（中文补空格、英文补换行）→ 被 KOiLNoWhitespaceInput 抹掉时回调
        // 第 3 条是软键盘最常见的真实路径，也是此前「按回车没反应」的根因。
        val navGateAt = remember { mutableLongStateOf(0L) }
        val urlSubmitTick = remember { mutableIntStateOf(0) }
        val usernameSubmitTick = remember { mutableIntStateOf(0) }
        val pwdSubmitTick = remember { mutableIntStateOf(0) }

        fun navToNextField() {
            val now = System.currentTimeMillis()
            if (now - navGateAt.longValue < 350L) return // 同一次回车被两条信号同时捕获时只跳一次
            navGateAt.longValue = now
            focusManager.moveFocus(FocusDirection.Next)
        }

        fun submitWebDavPassword() {
            val now = System.currentTimeMillis()
            if (now - navGateAt.longValue < 350L) return
            navGateAt.longValue = now
            val v = pwdState.text.toString()
            if (v.isNotBlank() && v != syncUi.password) syncVm.setPassword(v)
            focusManager.clearFocus()
        }

        LaunchedEffect(Unit) {
            snapshotFlow { urlSubmitTick.intValue }.drop(1).collect { navToNextField() }
        }
        LaunchedEffect(Unit) {
            snapshotFlow { usernameSubmitTick.intValue }.drop(1).collect { navToNextField() }
        }
        LaunchedEffect(Unit) {
            snapshotFlow { pwdSubmitTick.intValue }.drop(1).collect { submitWebDavPassword() }
        }
        // 只有用户真实输入过的密码才允许被落盘：**绝不**用默认密码兜底。
        // 否则「清空密码 → 重新打开开关 → 点立即备份」会把默认密码写回存储，
        // 用户刚做的停用被自己一次点击静默撤销。这个变量原来自己又算了一遍口径，
        // 与 SyncConfig 分裂，所以现在只判「有没有输入」。
        val effectivePwd: String? = pwdState.text.toString().takeIf { it.isNotBlank() }
        // 存储里的值异步到达时回填到输入框；与当前内容一致就不动，避免打断正在输入的光标。
        LaunchedEffect(syncUi.effectiveUrl) {
            val v = syncUi.effectiveUrl
            if (urlState.text.toString() != v) urlState.setTextAndPlaceCursorAtEnd(v)
        }
        LaunchedEffect(syncUi.effectiveUsername) {
            val v = syncUi.effectiveUsername
            if (usernameState.text.toString() != v) usernameState.setTextAndPlaceCursorAtEnd(v)
        }
        LaunchedEffect(syncUi.password) {
            val v = syncUi.effectivePasswordDraft
            if (pwdState.text.toString() != v) pwdState.setTextAndPlaceCursorAtEnd(v)
        }
        var formExpanded by remember { mutableStateOf(false) }
        // 三个框各自的焦点句柄：回车（IME Next）显式跳到下一个框，不依赖默认的焦点查找
        val urlFocus = remember { FocusRequester() }
        val usernameFocus = remember { FocusRequester() }
        val pwdFocus = remember { FocusRequester() }
        // 三个框各自一个滚动请求：整块请求会让整块对齐到可视区顶部——矮视口（分屏/横屏）
        // 下三框总高超过可视区，整块顶上去后点第一个框也会跟着跳；改成谁聚焦只请求谁，
        // 滚动量最小，焦点框刚好露在键盘上方即可。
        val urlBringIntoView = remember { BringIntoViewRequester() }
        val usernameBringIntoView = remember { BringIntoViewRequester() }
        val pwdBringIntoView = remember { BringIntoViewRequester() }
        // 0=无焦点 / 1=URL / 2=账号 / 3=密码
        var focusedSyncField by remember { mutableStateOf(0) }
        val imeVisible = WindowInsets.isImeVisible
        LaunchedEffect(imeVisible, focusedSyncField) {
            if (!imeVisible || focusedSyncField == 0) return@LaunchedEffect
            delay(240)
            when (focusedSyncField) {
                1 -> urlBringIntoView
                2 -> usernameBringIntoView
                else -> pwdBringIntoView
            }.bringIntoView()
        }

        // 状态判定放在开关行之前：徽章挂在「云同步」标题右侧，与开关键同一行。
        // 只认数据层算好的 configured（三项有效配置），界面不再自己拼判定。
        val configured = syncUi.configured
        val statusText = when {
            // 密码解不开时其余状态都是假的（lastStatus 可能还停在上次成功备份）
            syncUi.passwordInvalid -> stringResource(R.string.koil_sync_status_password_invalid)
            !configured -> stringResource(R.string.koil_sync_status_not_configured)
            syncUi.busy -> stringResource(R.string.koil_sync_status_busy)
            syncUi.lastStatus.startsWith("restore@") ->
                stringResource(
                    R.string.koil_sync_status_restore,
                    syncUi.lastStatus.substringAfter("restore@"),
                )
            // inserted=0 的恢复：明确「无需恢复」，不与真实恢复混用同一文案
            syncUi.lastStatus.startsWith("restore-none@") ->
                stringResource(
                    R.string.koil_sync_status_restore_none,
                    syncUi.lastStatus.substringAfter("restore-none@"),
                )
            syncUi.lastStatus.startsWith("ok@") ->
                stringResource(R.string.koil_sync_status_ok, syncUi.lastStatus.substringAfter("ok@"))
            syncUi.lastStatus.startsWith("fail:") -> {
                // 数据层只存 key，人类语言在这里出（否则英/繁中用户会看到一句中文）
                val reason = syncUi.lastStatus.substringAfter("fail:")
                if (reason == "conflict") {
                    stringResource(R.string.koil_sync_reconcile_stale)
                } else if (reason == "corrupt") {
                    // 云端是别的版本写的：自动推送不会覆盖它，等用户在对账面板里决定
                    stringResource(R.string.koil_sync_status_corrupt)
                } else {
                    stringResource(R.string.koil_sync_status_fail, reason)
                }
            }
            else -> stringResource(R.string.koil_sync_status_never)
        }
        val healthy = configured && !syncUi.passwordInvalid
        val unhealthy = !healthy || syncUi.lastStatus.startsWith("fail:")
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                // —— 两个常用动作：常态可见 ——
                Row(modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(18.dp))
                            .background(KOiLColors.Primary)
                            .clickable(enabled = !syncUi.busy) {
                                syncVm.backupNow(urlState.text.toString(), usernameState.text.toString(), effectivePwd)
                            }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.Upload,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.koil_sync_backup_now),
                                fontSize = KOiLType.BodySmall,
                                fontWeight = FontWeight.Medium,
                                color = Color.White,
                            )
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(18.dp))
                            .background(MiuixTheme.colorScheme.secondaryContainer)
                            .clickable(enabled = !syncUi.busy) {
                                syncVm.restoreNow(urlState.text.toString(), usernameState.text.toString(), effectivePwd)
                            }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.Download,
                                contentDescription = null,
                                tint = MiuixTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.koil_sync_restore),
                                fontSize = KOiLType.BodySmall,
                                fontWeight = FontWeight.Medium,
                                color = MiuixTheme.colorScheme.onSecondaryContainer,
                            )
                        }
                    }
                }

                // 有副本时多给一行入口；没有就不占位（不给一个永远空的面板）
                val backupCount by syncVm.backupCount.collectAsStateWithLifecycle()
                if (backupCount > 0) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.koil_sync_backup_restore_entry, backupCount),
                        fontSize = KOiLType.BodySmall,
                        color = MiuixTheme.colorScheme.onBackgroundVariant,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { syncVm.loadLocalBackups() }
                            .padding(vertical = 4.dp),
                    )
                }

                Spacer(Modifier.height(10.dp))
                // —— 状态行：备份状态在左，管理/收起贴着同一行右侧 ——
                // 徽章挂在开关标题旁，这一行只留「文本 + 管理」，少一行高度。
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = statusText,
                        fontSize = KOiLType.BodySmall,
                        lineHeight = 15.sp,
                        color = if (unhealthy) KOiLColors.Warning
                        else MiuixTheme.colorScheme.onBackgroundVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    // 「管理 ⌄」与油价卡「详细 ›」同族——同字号 14sp、同图标 18dp，
                    // 且箭头右缘都贴到「卡片边 - 16dp」，与「导出与备份 / 崩溃日志」
                    // 等 SettingRow 行的箭头同一条线、纵向对齐。
                    // 命中区只包住「文字 + 箭头」：整行可点会让点状态文字也展开，不自然。
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { formExpanded = !formExpanded }
                            .padding(start = 8.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = if (formExpanded) stringResource(R.string.koil_sync_collapse)
                            else stringResource(R.string.koil_sync_manage),
                            fontSize = KOiLType.BodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                            maxLines = 1,
                        )
                        Spacer(Modifier.width(2.dp))
                        val arrowRotation by animateFloatAsState(
                            targetValue = if (formExpanded) 180f else 0f,
                            label = "syncManageArrow",
                        )
                        Icon(
                            imageVector = Icons.Filled.KeyboardArrowDown,
                            contentDescription = null,
                            tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
                            modifier = Modifier
                                .size(18.dp)
                                .rotate(arrowRotation),
                        )
                    }
                }

                // —— 表单：点「管理」才展开（改地址/账号/密码时用）——
                if (formExpanded) {
                    // 包住三框的容器只负责间距；滚动请求由各框自己承担（见上方 requester）
                    Column {
                    Spacer(Modifier.height(14.dp))
                    GlassTextField(
                        state = urlState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(urlFocus)
                            .bringIntoViewRequester(urlBringIntoView)
                            // IME 只给「字符」不给 action 时（部分中文输入法把回车当上屏/换行提交），
                            // onKeyboardAction 收不到，所以就地在键事件层面拦 Enter。
                            .onPreviewKeyEvent { event ->
                                if (event.type == KeyEventType.KeyDown &&
                                    (event.key == Key.Enter || event.key == Key.NumPadEnter)
                                ) {
                                    navToNextField()
                                    true
                                } else {
                                    false
                                }
                            }
                            .onFocusChanged { focusState ->
                                tappedOnField.value = focusState.isFocused
                                if (focusState.isFocused) focusedSyncField = 1
                                else if (focusedSyncField == 1) focusedSyncField = 0
                                val v = urlState.text.toString()
                                if (!focusState.isFocused && v != syncUi.url) syncVm.setUrl(v)
                            },
                        label = stringResource(R.string.koil_sync_url),
                        useLabelAsPlaceholder = true,
                        lineLimits = TextFieldLineLimits.SingleLine,
                        inputTransformation = KOiLNoWhitespaceInput { urlSubmitTick.intValue++ },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                            imeAction = androidx.compose.ui.text.input.ImeAction.Next,
                        ),
                        // 回车显式跳下一个框；不调 performDefaultAction，避免与手动移焦冲突
                        onKeyboardAction = { navToNextField() },
                    )
                    Spacer(Modifier.height(10.dp))
                    GlassTextField(
                        state = usernameState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(usernameFocus)
                            .bringIntoViewRequester(usernameBringIntoView)
                            .onPreviewKeyEvent { event ->
                                if (event.type == KeyEventType.KeyDown &&
                                    (event.key == Key.Enter || event.key == Key.NumPadEnter)
                                ) {
                                    navToNextField()
                                    true
                                } else {
                                    false
                                }
                            }
                            .onFocusChanged { focusState ->
                                tappedOnField.value = focusState.isFocused
                                if (focusState.isFocused) focusedSyncField = 2
                                else if (focusedSyncField == 2) focusedSyncField = 0
                                val v = usernameState.text.toString()
                                if (!focusState.isFocused && v != syncUi.username) syncVm.setUsername(v)
                            },
                        label = stringResource(R.string.koil_sync_username),
                        useLabelAsPlaceholder = true,
                        lineLimits = TextFieldLineLimits.SingleLine,
                        inputTransformation = KOiLNoWhitespaceInput { usernameSubmitTick.intValue++ },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Ascii,
                            imeAction = androidx.compose.ui.text.input.ImeAction.Next,
                        ),
                        onKeyboardAction = { navToNextField() },
                    )
                    Spacer(Modifier.height(10.dp))
                    var showPwd by remember { mutableStateOf(false) }
                    GlassTextField(
                        state = pwdState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(pwdFocus)
                            .bringIntoViewRequester(pwdBringIntoView)
                            .onPreviewKeyEvent { event ->
                                if (event.type == KeyEventType.KeyDown &&
                                    (event.key == Key.Enter || event.key == Key.NumPadEnter)
                                ) {
                                    submitWebDavPassword()
                                    true
                                } else {
                                    false
                                }
                            }
                            .onFocusChanged { focusState ->
                                tappedOnField.value = focusState.isFocused
                                if (focusState.isFocused) focusedSyncField = 3
                                else if (focusedSyncField == 3) focusedSyncField = 0
                                val v = pwdState.text.toString()
                                if (!focusState.isFocused && v.isNotBlank() && v != syncUi.password) {
                                    syncVm.setPassword(v)
                                }
                            },
                        label = stringResource(
                            when {
                                syncUi.passwordSet -> R.string.koil_sync_app_password_set
                                // 设置过又被清空 = 用户主动停用，有效密码为空，不会再回落内置默认。
                                // 这里若还说「将使用内置预设密码」，就是在骗他：填不填都能连的错觉。
                                syncUi.passwordEverSet -> R.string.koil_sync_app_password_unset
                                else -> R.string.koil_sync_app_password_unset_builtin
                            },
                        ),
                        useLabelAsPlaceholder = true,
                        lineLimits = TextFieldLineLimits.SingleLine,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = androidx.compose.ui.text.input.ImeAction.Done,
                        ),
                        // Done：落盘后收起键盘（不调 performDefaultAction，避免与 clearFocus 重复动作）
                        onKeyboardAction = { submitWebDavPassword() },
                        // 掩码由 OutputTransformation 承担（state 版没有 visualTransformation 参数）
                        outputTransformation = if (showPwd) null else KOiLPasswordMask,
                        // 同前两框：紧跟在 inputTransformation 之后补上回车识别
                        inputTransformation = KOiLNoWhitespaceInput { pwdSubmitTick.intValue++ },
                        trailingIcon = {
                            IconButton(onClick = { showPwd = !showPwd }) {
                                Icon(
                                    imageVector = if (showPwd) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                    // 读屏标签随当前状态切换：明文态读出「隐藏密码」，密文态读出「显示密码」
                                    contentDescription = stringResource(
                                        if (showPwd) R.string.koil_a11y_hide_secret else R.string.koil_a11y_show_secret,
                                    ),
                                    tint = MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.6f),
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        },
                    )
                    }
                    // 密码状态行 + 明文存储/失效警示
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val passwordWarn = syncUi.passwordInvalid || (syncUi.passwordSet && !syncUi.passwordEncrypted)
                        Text(
                            text = when {
                                !syncUi.passwordSet -> stringResource(R.string.koil_sync_app_password_unset)
                                syncUi.passwordInvalid -> stringResource(R.string.koil_sync_password_invalid)
                                !syncUi.passwordEncrypted -> stringResource(R.string.koil_sync_password_plain_warn)
                                else -> stringResource(R.string.koil_sync_app_password_set)
                            },
                            fontSize = KOiLType.CaptionLarge,
                            lineHeight = 14.sp,
                            color = if (passwordWarn && syncUi.passwordSet) KOiLColors.Warning
                            else MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.8f),
                            modifier = Modifier.weight(1f).padding(top = 4.dp),
                        )
                        if (syncUi.passwordSet) {
                            Text(
                                text = stringResource(R.string.koil_sync_password_clear),
                                fontSize = KOiLType.CaptionLarge,
                                color = KOiLColors.Primary,
                                modifier = Modifier
                                    .padding(top = 4.dp, start = 8.dp)
                                    .clickable {
                                        syncVm.clearPassword()
                                        pwdState.setTextAndPlaceCursorAtEnd("")
                                    },
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    // —— 保存并测试连接：配置草稿落盘 + PROPFIND 验证 ——
                    // 不用 surfaceVariant 0.6 淡底：它和卡片底色几乎同色，
                    // 真机上读起来像一行纯文字；用主色实底按钮（busy 时降透明度），
                    // 与上方「立即备份」同一套按钮语言，表单的提交动作才立得住。
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(
                                if (syncUi.busy) KOiLColors.Primary.copy(alpha = 0.45f)
                                else KOiLColors.Primary
                            )
                            .clickable(enabled = !syncUi.busy) {
                                syncVm.saveAndTest(urlState.text.toString(), usernameState.text.toString(), effectivePwd)
                            }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.koil_sync_save_test),
                                fontSize = KOiLType.Body,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White,
                            )
                        }
                    }
                }
            }

        // 云同步结果提示（备份/恢复）：走全局 Snackbar
        val syncMessage by syncVm.message.collectAsStateWithLifecycle()
        val syncSnackbar = rememberSnackbar(
            duration = SnackbarDuration.Long,
            withDismissAction = true,
        )
        val syncText = syncMessage?.let { formatSyncNotice(it) }
        LaunchedEffect(syncMessage) {
            if (syncText != null) {
                syncSnackbar(syncText)
                syncVm.consumeMessage()
            }
        }

        // 首次配对对账面板（PR ②）：云端已有快照、本机也非空时弹
        SyncReconcileDialog(syncVm)
        // 覆盖前副本的恢复入口（有副本才渲染）
        LocalBackupDialog(syncVm)
        LaunchedEffect(Unit) { syncVm.refreshBackupCount() }

        // 云同步恢复确认弹窗（预览 valid/dup/errors，确认后不删本地）
        val syncRestorePreview by syncVm.restorePreview.collectAsStateWithLifecycle()
        syncRestorePreview?.let { preview ->
            KOiLGlassDialog(
                show = true,
                onDismissRequest = syncVm::dismissRestorePreview,
                title = stringResource(R.string.koil_sync_restore_confirm_title),
            ) {
                Column {
                    Text(
                        text = pluralStringResource(
                            R.plurals.koil_sync_restore_confirm_msg,
                            preview.total,
                            preview.total, preview.valid, preview.duplicates, preview.errors,
                        ),
                        fontSize = KOiLType.Body,
                        lineHeight = 18.sp,
                        color = MiuixTheme.colorScheme.onBackgroundVariant,
                    )
                    if (preview.skippedByTombstone > 0) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = pluralStringResource(
                                R.plurals.koil_sync_restore_skipped_tombstone,
                                preview.skippedByTombstone,
                                preview.skippedByTombstone,
                            ),
                            fontSize = KOiLType.Body,
                            lineHeight = 18.sp,
                            color = MiuixTheme.colorScheme.onBackgroundVariant,
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        GlassDialogButton(
                            onClick = syncVm::dismissRestorePreview,
                            modifier = Modifier.weight(1f),
                            role = GlassDialogButtonRole.Normal,
                        ) {
                            Text(
                                text = stringResource(R.string.koil_common_cancel),
                                style = MiuixTheme.textStyles.button,
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        GlassDialogButton(
                            onClick = syncVm::confirmRestore,
                            modifier = Modifier.weight(1f),
                            role = GlassDialogButtonRole.Normal,
                        ) {
                            Text(
                                text = stringResource(R.string.koil_sync_restore_confirm, preview.valid),
                                style = MiuixTheme.textStyles.button,
                            )
                        }
                    }
                }
    }
    }
}

/**
 * 首次配对对账面板（PR ②）。
 *
 * 每个选项的文案自带后果（"云端独有 N 条会消失"），不靠用户去理解"以云端为准"是什么意思；
 * 后两个是破坏性的，点下去先弹二次确认——覆盖不可撤销，多一次点击比少一次损失便宜。
 *

 * **同一时刻只渲染一层弹窗**：对账面板 / 二次确认 / "云端备份读不懂"三态互斥。
 * 两个 KOiLGlassDialog 同时 show=true 会叠两层遮罩，视觉上像弹窗叠弹窗。
 */
@Composable
internal fun SyncReconcileDialog(viewModel: SyncViewModel) {
    val preview by viewModel.reconcile.collectAsStateWithLifecycle()
    val corrupt by viewModel.reconcileCorrupt.collectAsStateWithLifecycle()
    var pendingDestructive by remember { mutableStateOf<SyncReconcile.Mode?>(null) }
    // 委托属性读不出智能转换，先取局部快照供 when 分支使用（同一帧内取值，与原先判断一致）
    val reconcilePreview = preview

    when {
        // ① 云端那份读不懂：只说「没动数据」，覆盖入口放在次要位置
        corrupt != null -> KOiLGlassDialog(
            show = true,
            onDismissRequest = viewModel::dismissReconcileCorrupt,
            title = stringResource(R.string.koil_sync_reconcile_corrupt_title),
        ) {
            Column {
                Text(
                    stringResource(R.string.koil_sync_reconcile_corrupt_msg),
                    fontSize = KOiLType.Body,
                    lineHeight = 18.sp,
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                )
                Spacer(Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    GlassDialogButton(
                        onClick = viewModel::dismissReconcileCorrupt,
                        modifier = Modifier.weight(1f),
                        role = GlassDialogButtonRole.Normal,
                    ) {
                        Text(
                            text = stringResource(R.string.koil_common_cancel),
                            style = MiuixTheme.textStyles.button,
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    GlassDialogButton(
                        onClick = viewModel::confirmReconcileCorrupt,
                        modifier = Modifier.weight(1f),
                        role = GlassDialogButtonRole.Destructive,
                    ) {
                        Text(
                            text = stringResource(R.string.koil_sync_reconcile_corrupt_overwrite),
                            style = MiuixTheme.textStyles.button,
                        )
                    }
                }
            }
        }

        // ② 破坏性二次确认
        pendingDestructive != null -> KOiLGlassDialog(
            show = true,
            onDismissRequest = { pendingDestructive = null },
            title = stringResource(R.string.koil_sync_reconcile_destructive_title),
        ) {
            Column {
                Text(
                    stringResource(R.string.koil_sync_reconcile_destructive_msg),
                    fontSize = KOiLType.Body,
                    lineHeight = 18.sp,
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                )
                Spacer(Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    GlassDialogButton(
                        onClick = { pendingDestructive = null },
                        modifier = Modifier.weight(1f),
                        role = GlassDialogButtonRole.Normal,
                    ) {
                        Text(
                            text = stringResource(R.string.koil_common_cancel),
                            style = MiuixTheme.textStyles.button,
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    GlassDialogButton(
                        onClick = {
                            val mode = pendingDestructive
                            pendingDestructive = null
                            if (mode != null) viewModel.confirmReconcile(mode)
                        },
                        modifier = Modifier.weight(1f),
                        role = GlassDialogButtonRole.Destructive,
                    ) {
                        Text(
                            text = stringResource(R.string.koil_sync_reconcile_confirm),
                            style = MiuixTheme.textStyles.button,
                        )
                    }
                }
            }
        }

        // ③ 对账面板本体
        reconcilePreview != null -> {
            val st = reconcilePreview.stats
            KOiLGlassDialog(
                show = true,
                onDismissRequest = viewModel::dismissReconcile,
                title = stringResource(R.string.koil_sync_reconcile_title),
            ) {
                Column {
                    Text(
                        stringResource(R.string.koil_sync_reconcile_counts, st.localCount, st.cloudCount),
                        fontSize = KOiLType.BodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = MiuixTheme.colorScheme.onBackground,
                    )
                    Spacer(Modifier.height(6.dp))
                    if (st.bothCount > 0) {
                        ReconcileLine(stringResource(R.string.koil_sync_reconcile_both, st.bothCount, st.conflictCount))
                    }
                    if (st.cloudOnlyCount > 0) {
                        ReconcileLine(pluralStringResource(R.plurals.koil_sync_reconcile_cloud_only, st.cloudOnlyCount, st.cloudOnlyCount))
                    }
                    if (st.localOnlyCount > 0) {
                        ReconcileLine(stringResource(R.string.koil_sync_reconcile_local_only, st.localOnlyCount))
                    }
                    if (st.tombstoneSkippedCount > 0) {
                        ReconcileLine(pluralStringResource(R.plurals.koil_sync_reconcile_skipped, st.tombstoneSkippedCount, st.tombstoneSkippedCount))
                    }
                    Spacer(Modifier.height(12.dp))
                    // 默认选项（推荐）放最上：用户不进面板直接点第一个就是最安全的
                    GlassDialogButton(
                        onClick = { viewModel.confirmReconcile(SyncReconcile.Mode.MERGE) },
                        modifier = Modifier.fillMaxWidth(),
                        role = GlassDialogButtonRole.Normal,
                    ) {
                        // 数字为 0 时不说「补进 0 条」——按钮文案要能单独读懂
                        Text(
                            text = if (st.cloudOnlyCount > 0) {
                                pluralStringResource(R.plurals.koil_sync_reconcile_merge, st.cloudOnlyCount, st.cloudOnlyCount)
                            } else {
                                stringResource(R.string.koil_sync_reconcile_merge_plain)
                            },
                            style = MiuixTheme.textStyles.button,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    GlassDialogButton(
                        onClick = { pendingDestructive = SyncReconcile.Mode.LOCAL_WINS },
                        modifier = Modifier.fillMaxWidth(),
                        role = GlassDialogButtonRole.Destructive,
                    ) {
                        Text(
                            text = if (st.cloudOnlyCount > 0) {
                                pluralStringResource(R.plurals.koil_sync_reconcile_local_wins, st.cloudOnlyCount, st.cloudOnlyCount)
                            } else {
                                stringResource(R.string.koil_sync_reconcile_local_wins_plain)
                            },
                            style = MiuixTheme.textStyles.button,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    GlassDialogButton(
                        onClick = { pendingDestructive = SyncReconcile.Mode.CLOUD_WINS },
                        modifier = Modifier.fillMaxWidth(),
                        role = GlassDialogButtonRole.Destructive,
                    ) {
                        Text(
                            text = if (st.localOnlyCount > 0) {
                                pluralStringResource(R.plurals.koil_sync_reconcile_cloud_wins, st.localOnlyCount, st.localOnlyCount)
                            } else {
                                stringResource(R.string.koil_sync_reconcile_cloud_wins_plain)
                            },
                            style = MiuixTheme.textStyles.button,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.koil_common_cancel),
                        fontSize = KOiLType.Body,
                        color = MiuixTheme.colorScheme.onBackgroundVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.dismissReconcile() }
                            .padding(vertical = 10.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
        }
    }
}

/** 对账面板的一行说明（点号 + 一行字） */
@Composable
internal fun ReconcileLine(text: String) {
    Text(
        text = "· $text",
        fontSize = KOiLType.Body,
        lineHeight = 18.sp,
        color = MiuixTheme.colorScheme.onBackgroundVariant,
        modifier = Modifier.padding(bottom = 4.dp),
    )
}

/**
 * 「从本地副本恢复」（PR ② 评审 P1-4）。
 *

 * 覆盖云端前会自动另存一份快照，但如果用户在应用里找不到它，那份副本就只对开发者有意义。
 * 这里把它变成可点的一行：时间 · 条数，点一下按「只插入、不删本地」的口径恢复。
 */
@Composable
internal fun LocalBackupDialog(viewModel: SyncViewModel) {
    val backups by viewModel.localBackups.collectAsStateWithLifecycle()
    if (backups.isEmpty()) return
    KOiLGlassDialog(
        show = true,
        onDismissRequest = viewModel::dismissLocalBackups,
        title = stringResource(R.string.koil_sync_backup_restore_title),
    ) {
        Column {
            Text(
                stringResource(R.string.koil_sync_backup_restore_hint),
                fontSize = KOiLType.BodySmall,
                lineHeight = 17.sp,
                color = MiuixTheme.colorScheme.onBackgroundVariant,
            )
            Spacer(Modifier.height(10.dp))
            backups.forEach { b ->
                val label = if (b.recordCount < 0) {
                    stringResource(R.string.koil_sync_backup_restore_broken)
                } else {
                    pluralStringResource(R.plurals.koil_sync_backup_restore_item, b.recordCount, b.displayTime, b.recordCount)
                }
                Text(
                    text = label,
                    fontSize = KOiLType.Body,
                    color = if (b.recordCount < 0) MiuixTheme.colorScheme.onBackgroundVariant
                    else MiuixTheme.colorScheme.onBackground,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(enabled = b.recordCount >= 0) { viewModel.restoreLocalBackup(b) }
                        .padding(horizontal = 10.dp, vertical = 10.dp),
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.koil_common_cancel),
                fontSize = KOiLType.Body,
                color = MiuixTheme.colorScheme.onBackgroundVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.dismissLocalBackups() }
                    .padding(vertical = 10.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

/** 云同步状态徽章：已连接（主色）/ 未配置（灰） */
@Composable
internal fun SyncStatusBadge(healthy: Boolean) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(
                if (healthy) KOiLColors.Primary.copy(alpha = 0.12f)
                else MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.12f)
            )
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(
            text = if (healthy) stringResource(R.string.koil_sync_badge_on)
            else stringResource(R.string.koil_sync_badge_off),
            fontSize = KOiLType.CaptionLarge,
            fontWeight = FontWeight.Medium,
            color = if (healthy) KOiLColors.Primary
            else MiuixTheme.colorScheme.onBackgroundVariant,
        )
    }
}

/** 云同步结果通知翻译（backup_done / backup_failed / backup_corrupt / restore_corrupt / restore_no_cloud / restore_done[:N] / restore_none / restore_failed / config_saved / config_test_failed） */
@Composable
internal fun formatSyncNotice(key: String): String {
    // 支持 "restore_done:12" 这种带参数形式（恢复完成后提示实际插入条数）
    val base = key.substringBefore(':')
    val arg = key.substringAfter(':', "").toIntOrNull()
    return when (base) {
        "backup_done" -> stringResource(R.string.koil_sync_backup_done)
        "backup_failed" -> stringResource(R.string.koil_sync_backup_failed)
        // 云端是别的版本写的：不是网络问题，别让用户去查账号密码
        "backup_corrupt" -> stringResource(R.string.koil_sync_backup_corrupt)
        "restore_no_cloud" -> stringResource(R.string.koil_sync_restore_no_cloud)
        "restore_done" ->
            if (arg != null) pluralStringResource(R.plurals.koil_sync_restore_done_count, arg, arg)
            else stringResource(R.string.koil_sync_restore_done)
        // inserted=0 的恢复：与设置页状态行同口径，不伪报「已恢复」
        "restore_none" -> stringResource(R.string.koil_sync_restore_none)
        "restore_failed" -> stringResource(R.string.koil_sync_restore_failed)
        // 云端是别的版本写的：一条都没恢复，别让用户以为「云端是空的」
        "restore_corrupt" -> stringResource(R.string.koil_sync_restore_corrupt)
        "config_saved" -> stringResource(R.string.koil_sync_config_saved)
        "config_test_failed" -> stringResource(R.string.koil_sync_config_test_failed)
        // 未配置时点开关：复用状态行同一句（三语文案已齐，不新增条目）
        "not_configured" -> stringResource(R.string.koil_sync_status_not_configured)
        // 首次配对且云端还没快照：不弹面板，只提示一句再直接上传
        "first_upload" ->
            pluralStringResource(R.plurals.koil_sync_first_upload, arg ?: 0, arg ?: 0)
        "reconcile_stale" -> stringResource(R.string.koil_sync_reconcile_stale)
        // 重跑没成功：不能说「已重新对账」
        "reconcile_stale_retry" -> stringResource(R.string.koil_sync_reconcile_stale_retry)
        "reconcile_stale_gone" -> stringResource(R.string.koil_sync_reconcile_stale_gone)
        "local_backup_broken" -> stringResource(R.string.koil_local_backup_broken)
        "reconcile_done" -> stringResource(R.string.koil_sync_reconcile_done)
        "reconcile_failed" -> stringResource(R.string.koil_sync_reconcile_failed)
        else -> key
    }
}
