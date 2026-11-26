package com.llpop.koil.ui.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.computedStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.llpop.koil.R
import com.llpop.koil.domain.logic.NumberFormat
import com.llpop.koil.ui.screen.settings.SettingsViewModel
import com.llpop.koil.ui.component.KOiLGlassDialog
import com.llpop.koil.ui.theme.KOiLColors
import com.llpop.koil.ui.theme.KOiLType
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.glass.GlassDialogButton
import top.yukonga.miuix.kmp.glass.GlassDialogButtonRole
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.llpop.koil.ui.component.KOiLSegmentedTabRow
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

/**
 * CSV 导入预览弹窗（M-07 拆分）：valid/duplicate/conflict/error 四页签，不入库。
 * 从 SettingsScreen 主函数整体移出，行为不变。
 *
 * 页签与内容双向绑定：点页签滚到对应一页，在内容区左右拖动则回写当前页签。
 * 每页内部仍然是纵向滚动列表，横向手势交给 pager、纵向交给列表，两者不抢方向。
 */
@Composable
internal fun ImportPreviewDialog(viewModel: SettingsViewModel) {
    val importPreview by viewModel.importPreview.collectAsStateWithLifecycle()
    importPreview?.let { preview ->
        val tabs = listOf(
            stringResource(R.string.koil_import_tab_valid, preview.valid.size) to preview.valid,
            stringResource(R.string.koil_import_tab_dup, preview.duplicates.size) to preview.duplicates,
            stringResource(R.string.koil_import_tab_conflict, preview.mileageConflicts.size) to preview.mileageConflicts,
            stringResource(R.string.koil_import_tab_error, preview.errors.size) to preview.errors,
        )
        // 页签只放分类名：官方分段控件的左右内距是固定常量，带「(19)」的文案
        // 在四格布局里会被截成「重複 …」；计数改在每页顶部显示，信息不丢
        val shortLabels = listOf(
            stringResource(R.string.koil_import_tab_short_valid),
            stringResource(R.string.koil_import_tab_short_dup),
            stringResource(R.string.koil_import_tab_short_conflict),
            stringResource(R.string.koil_import_tab_short_error),
        )
        val pagerState = rememberPagerState(initialPage = 0) { tabs.size }
        val scope = rememberCoroutineScope()
        // pagerState.currentPage 只在越过中线的瞬间跳一档，滑动过程中读它拿不到连续位置；
        // 叠上 currentPageOffsetFraction 再取整，页签高亮就能跟着手指连续切换，不用等松手。
        val tabIndex by remember(pagerState) {
            computedStateOf {
                (pagerState.currentPage + pagerState.currentPageOffsetFraction)
                    .roundToInt()
                    .coerceIn(0, tabs.lastIndex)
            }
        }
        KOiLGlassDialog(
            show = true,
            onDismissRequest = viewModel::dismissImportPreview,
            title = stringResource(R.string.koil_import_title),
        ) {
            Column {
                // 页签条直接复用全 App 唯一的分段控件：轨道材质、描边、指示块的滑动/跟手/按压
                // 动画与数据总览、油价明细完全同一套实现，不再自绘一条
                // 点页签时先把选中值锁到目标格：指示块像数据总览那样一条弹簧滑过去。
                // 若直接喂 pager 的连续偏移，点击后指示块会被 pager 一路"拖着"走，
                // 观感偏软 —— 那不是点击跳格该有的手感。松手（pager 停下）再交还跟手值。
                var clickTarget by remember { mutableStateOf<Int?>(null) }
                val shownTabIndex = clickTarget ?: tabIndex
                LaunchedEffect(pagerState) {
                    snapshotFlow { pagerState.isScrollInProgress }.collect { scrolling ->
                        if (!scrolling) clickTarget = null
                    }
                }
                KOiLSegmentedTabRow(
                    tabs = shortLabels,
                    selectedIndex = shownTabIndex,
                    onSelect = { idx ->
                        clickTarget = idx
                        scope.launch { pagerState.animateScrollToPage(idx) }
                    },
                )
                Spacer(Modifier.height(8.dp))
                HorizontalPager(
                    state = pagerState,
                    beyondViewportPageCount = 1,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                ) { page ->
                    val rows = tabs[page].second
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                    ) {
                        Text(
                            text = tabs[page].first,
                            fontSize = KOiLType.Caption,
                            color = MiuixTheme.colorScheme.onBackgroundVariant,
                            modifier = Modifier.padding(bottom = 6.dp),
                        )
                        rows.forEach { r ->
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
                                        fontSize = KOiLType.BodySmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = MiuixTheme.colorScheme.onBackground,
                                        modifier = Modifier.weight(1.5f),
                                    )
                                    Text(
                                        text = "${r.mileage.toInt()}km",
                                        fontSize = KOiLType.CaptionLarge,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = MiuixTheme.colorScheme.onBackgroundVariant,
                                        modifier = Modifier.weight(1.3f),
                                    )
                                    Text(
                                        text = "${NumberFormat.measure(r.oilAmount)}L",
                                        fontSize = KOiLType.CaptionLarge,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = MiuixTheme.colorScheme.onBackgroundVariant,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Text(
                                        text = "¥${NumberFormat.money(r.actualAmount)}",
                                        fontSize = KOiLType.CaptionLarge,
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
                                        fontSize = KOiLType.Caption,
                                        color = KOiLColors.Alert,
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
                                        fontSize = KOiLType.Caption,
                                        color = KOiLColors.AttentionDay,
                                        modifier = Modifier.padding(top = 2.dp),
                                    )
                                }
                            }
                        }
                        if (rows.isEmpty()) {
                            Text(
                                text = stringResource(R.string.koil_import_no_data),
                                fontSize = KOiLType.BodySmall,
                                color = MiuixTheme.colorScheme.onBackgroundVariant,
                                modifier = Modifier.padding(vertical = 20.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    GlassDialogButton(
                        onClick = viewModel::dismissImportPreview,
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
                        onClick = viewModel::confirmImport,
                        modifier = Modifier.weight(1f),
                        role = GlassDialogButtonRole.Normal,
                    ) {
                        Text(
                            text = stringResource(R.string.koil_import_confirm, preview.valid.size),
                            style = MiuixTheme.textStyles.button,
                        )
                    }
                }
            }
        }
    }
}

