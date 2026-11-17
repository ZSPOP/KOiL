package com.llpop.koil.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.llpop.koil.R
import com.llpop.koil.domain.logic.NumberFormat
import com.llpop.koil.ui.component.KOiLGlassDialog
import com.llpop.koil.ui.screen.settings.SettingsViewModel
import com.llpop.koil.ui.theme.KOiLColors
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.compose.runtime.mutableIntStateOf



/**
 * CSV 导入预览弹窗（M-07 拆分）：valid/duplicate/conflict/error 四页签，不入库。
 * 从 SettingsScreen 主函数整体移出，行为不变。
 */
@Composable
internal fun ImportPreviewDialog(viewModel: SettingsViewModel) {
    val importPreview by viewModel.importPreview.collectAsStateWithLifecycle()
    importPreview?.let { preview ->
        val activeTab = remember { mutableIntStateOf(0) }
        val tabs = listOf(
            stringResource(R.string.koil_import_tab_valid, preview.valid.size) to preview.valid,
            stringResource(R.string.koil_import_tab_dup, preview.duplicates.size) to preview.duplicates,
            stringResource(R.string.koil_import_tab_conflict, preview.mileageConflicts.size) to preview.mileageConflicts,
            stringResource(R.string.koil_import_tab_error, preview.errors.size) to preview.errors,
        )
        com.llpop.koil.ui.component.KOiLGlassDialog(show = true, onDismissRequest = viewModel::dismissImportPreview) {
            Column {
                Text(stringResource(R.string.koil_import_title), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MiuixTheme.colorScheme.onBackground)
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                        .padding(2.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    tabs.forEachIndexed { idx, (label, _) ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (activeTab.intValue == idx) KOiLColors.Primary else Color.Transparent)
                                .clickable { activeTab.intValue = idx }
                                .padding(horizontal = 6.dp, vertical = 4.dp),
                        ) {
                            Text(label, fontSize = 10.sp, color = if (activeTab.intValue == idx) Color.White else MiuixTheme.colorScheme.onBackground)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Column {
                        tabs[activeTab.intValue].second.forEach { r ->
                            // 四列等分（weight）而不是固定 dp：导入预览是用来逐条核对的行，
                            // 列宽一旦固定，系统字体放大或里程到 6 位时数字会换行、四列错位，
                            // 用户核对时容易看串行。改用权重后所有行列宽一致（行内不再有
                            // 随内容伸缩的第五列），文字单行显示；错误/冲突提示另起一行。
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = r.date,
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = MiuixTheme.colorScheme.onBackground,
                                        modifier = Modifier.weight(1.5f),
                                    )
                                    Text(
                                        text = "${r.mileage.toInt()}km",
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = MiuixTheme.colorScheme.onBackgroundVariant,
                                        modifier = Modifier.weight(1.3f),
                                    )
                                    Text(
                                        text = "${NumberFormat.measure(r.oilAmount)}L",
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = MiuixTheme.colorScheme.onBackgroundVariant,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Text(
                                        text = "¥${NumberFormat.money(r.actualAmount)}",
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = MiuixTheme.colorScheme.onBackgroundVariant,
                                        modifier = Modifier.weight(1.2f),
                                    )
                                }
                                r.errorKey?.let { key ->
                                    Text(
                                        text = when (key) {
                                            "format_error" -> stringResource(R.string.koil_msg_format_error)
                                            "duplicate" -> stringResource(R.string.koil_msg_duplicate)
                                            else -> key
                                        },
                                        fontSize = 10.sp,
                                        color = Color(0xFFE53935),
                                        modifier = Modifier.padding(top = 2.dp),
                                    )
                                }
                                r.conflictKey?.let { key ->
                                    Text(
                                        text = when (key) {
                                            "lt_prev" -> stringResource(R.string.koil_msg_mileage_lt_prev, r.conflictArg)
                                            "gt_next" -> stringResource(R.string.koil_msg_mileage_gt_next, r.conflictArg)
                                            else -> key
                                        },
                                        fontSize = 10.sp,
                                        color = Color(0xFFE65100),
                                        modifier = Modifier.padding(top = 2.dp),
                                    )
                                }
                            }
                        }
                        if (tabs[activeTab.intValue].second.isEmpty()) {
                            Text(stringResource(R.string.koil_import_no_data), fontSize = 12.sp, color = MiuixTheme.colorScheme.onBackgroundVariant, modifier = Modifier.padding(vertical = 20.dp))
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    // 与恢复弹窗一致——不设固定 40dp 高度，防文字下缘被裁
                    Button(onClick = viewModel::dismissImportPreview, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.koil_common_cancel), fontSize = 13.sp) }
                    Spacer(Modifier.width(10.dp))
                    Button(onClick = viewModel::confirmImport, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.koil_import_confirm, preview.valid.size), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}
