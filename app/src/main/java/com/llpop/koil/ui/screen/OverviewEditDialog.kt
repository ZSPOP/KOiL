package com.llpop.koil.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Money
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.drop
import top.yukonga.miuix.kmp.glass.GlassTextField
import top.yukonga.miuix.kmp.glass.GlassTextFieldDefaults
import com.llpop.koil.ui.component.KOiLDecimalInput
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.llpop.koil.R
import com.llpop.koil.ui.component.sanitizeDecimalInput
import com.llpop.koil.domain.logic.NumberFormat
import com.llpop.koil.ui.component.gradeValueLabel
import com.llpop.koil.ui.component.fieldSurfaceColor
import com.llpop.koil.ui.screen.overview.DialogUiState
import com.llpop.koil.ui.theme.KOiLColors
import com.llpop.koil.ui.component.provinceLabel
import kotlin.math.abs
import top.yukonga.miuix.kmp.basic.DropdownArrowEndAction
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TextFieldDefaults
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.llpop.koil.ui.theme.KOiLType
import top.yukonga.miuix.kmp.glass.glassPopupAnchorValue
import top.yukonga.miuix.kmp.glass.rememberGlassPopupAnchor
import com.llpop.koil.ui.component.LocalPageBackdrop
import top.yukonga.miuix.kmp.glass.GlassPopupItem
import top.yukonga.miuix.kmp.glass.GlassDialogButton
import top.yukonga.miuix.kmp.glass.GlassDialogButtonRole
import com.llpop.koil.ui.component.GlassPopupOverlay
import com.llpop.koil.ui.component.LocalPageBackdrop



// ===== 编辑记录弹窗（复刻补录卡：字段结构/图标/红色错误态/里程区间提示/校验提示条/应付只读自动算/保存置灰变蓝；唯一差异：日期只读） =====
@Composable
internal fun EditRecordDialog(
    uiState: DialogUiState,
    onMileageChange: (String) -> Unit,
    onOilAmountChange: (String) -> Unit,
    onActualAmountChange: (String) -> Unit,
    onGradeChange: (String) -> Unit,
    onProvinceChange: (String) -> Unit,
    onFullTankChange: (Boolean) -> Unit,
    onPayableChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    com.llpop.koil.ui.component.KOiLFormSheet(
        show = true,
        onDismissRequest = onDismiss,
        title = stringResource(R.string.koil_ov_edit_title),
    ) {
        Column {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp)) {
            // 加油省份（与设置页同款下拉列表）
            ProvinceSelectField(province = uiState.editProvince, onProvinceChange = onProvinceChange)
            Spacer(Modifier.height(14.dp))
            // 第一行：日期（只读，不可修改）+ 油号（联动当日参考单价）
            Row(verticalAlignment = Alignment.Top) {
                com.llpop.koil.ui.component.KOiLDateField(
                    modifier = Modifier.weight(1f),
                    date = uiState.editDate,
                    recordDates = uiState.editRecordDates,
                    onDateSelected = null,
                )
                Spacer(Modifier.width(14.dp))
                GradePriceSelect(
                    grade = uiState.editGrade,
                    price = uiState.editReferencePrice,
                    onGradeSelected = onGradeChange,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(14.dp))
            // 打开即显示该日期（只读）的里程区间提示（对齐补录卡：日期行与里程行之间）
            if (uiState.editRangeMin != null || uiState.editRangeMax != null) {
                MileageRangeHint(
                    rangeMin = uiState.editRangeMin,
                    rangeMax = uiState.editRangeMax,
                )
                Spacer(Modifier.height(14.dp))
            }
            // 第二行：里程 + 加油量（红色错误态对齐补录卡）
            Row {
                EditField(
                    label = stringResource(R.string.koil_add_field_mileage),
                    icon = Icons.Filled.Speed,
                    value = uiState.editMileage,
                    onValueChange = onMileageChange,
                    keyboardType = KeyboardType.Number,
                    error = uiState.editMileageConflictType.isNotBlank() || uiState.editSameDayExists,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(14.dp))
                EditField(
                    label = stringResource(R.string.koil_add_field_oil_amount),
                    icon = Icons.Filled.WaterDrop,
                    value = uiState.editOilAmount,
                    onValueChange = onOilAmountChange,
                    keyboardType = KeyboardType.Decimal,
                    error = uiState.editOverTank,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(14.dp))
            // 第三行：应付（只读自动计算）+ 实付（对齐补录卡）
            Row {
                EditField(
                    label = stringResource(R.string.koil_add_field_payable),
                    icon = Icons.Filled.Money,
                    value = uiState.editPayableAmount,
                    onValueChange = onPayableChange,
                    keyboardType = KeyboardType.Decimal,
                    readOnly = true,
                    // 应付未计算（油量/参考价缺失）时显示占位
                    placeholder = stringResource(R.string.koil_ov_payable_pending),
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(14.dp))
                EditField(
                    label = stringResource(R.string.koil_add_field_actual),
                    icon = Icons.Filled.Money,
                    value = uiState.editActualAmount,
                    onValueChange = onActualAmountChange,
                    isLastField = true,
                    keyboardType = KeyboardType.Decimal,
                    error = uiState.editDeviationExceeded || uiState.editOverPayable,
                    modifier = Modifier.weight(1f),
                )
            }
            }
            // 满箱标记（跳枪）：历史记录可逐条改，改后整链实时重算；整行铺满弹窗宽。
            // 块模型：上方 12dp = ①「上一字段 → 块上缘」，由上面字段容器的 bottom 12dp 承担
            //（不另加 Spacer——原块外上距是容器底距 13 与 Spacer 12 叠加的 25dp，现收敛到 12；
                // 实测字段底→标题顶 34dp，改后约 21–24dp，含行内留白）；
            // 块内上距 8dp 在行内；bottomInset 维持默认 8dp → 高亮停在块下缘，
            // 块下缘 → 下方内容块的 16dp 由下面 Column 的 top 16dp 在高亮之外补。
            com.llpop.koil.ui.component.FullTankSwitchRow(
                checked = uiState.editIsFullTank,
                onCheckedChange = onFullTankChange,
            )
            // 跳枪行下方间距按「块是否真的渲染」分配：是否绘制只由下面两个谓词决定，
            // 卡片和提示条内部不再各判一次（否则改一处忘另一处就会「留了空档但没内容」）。
            // top 16dp = 块模型 ③「块下缘 → 下方内容块」，在高亮之外（弹窗高亮停在块下缘）。
            Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp)) {
                val hasSummary = hasPaymentSummary(
                    uiState.editPayableAmount.toDoubleOrNull(),
                    uiState.editActualAmount.toDoubleOrNull(),
                    uiState.editOilAmount.toDoubleOrNull(),
                    uiState.editReferencePrice,
                )
                val hasHint = hasPrecheckHint(
                    beforeFirst = uiState.editBeforeFirst,
                    sameDayExists = uiState.editSameDayExists,
                    mileageConflictType = uiState.editMileageConflictType,
                    deviationExceeded = uiState.editDeviationExceeded,
                    overPayable = uiState.editOverPayable,
                    overTank = uiState.editOverTank,
                    ready = uiState.editReady,
                )
                // 实付综合信息块（对齐 Web PaymentSummaryCard：优惠概览/实际单价/参考价对比）
                if (hasSummary) {
                    PaymentSummaryCard(
                        payable = uiState.editPayableAmount.toDoubleOrNull(),
                        actual = uiState.editActualAmount.toDoubleOrNull(),
                        fuelAmount = uiState.editOilAmount.toDoubleOrNull(),
                        referencePrice = uiState.editReferencePrice,
                    )
                    // 只有卡片真的画过，这段 8dp 才需要存在
                    if (hasHint) Spacer(Modifier.height(8.dp))
                }
                if (hasHint) {
                    // 编辑校验提示条（复刻补录卡：日期边界/同日重复/里程链/偏差A+B/实付>应付/油箱容量，通过绿色）
                    BackfillPrecheckHint(
                        beforeFirst = uiState.editBeforeFirst,
                        sameDayExists = uiState.editSameDayExists,
                        mileageConflictType = uiState.editMileageConflictType,
                        mileageConflictDate = uiState.editMileageConflictDate,
                        mileageConflictValue = uiState.editMileageConflictValue,
                        deviationPct = uiState.editDeviationPct,
                        deviationExceeded = uiState.editDeviationExceeded,
                        overPayable = uiState.editOverPayable,
                        overTank = uiState.editOverTank,
                        tankCapacity = uiState.editTankCapacity,
                        ready = uiState.editReady,
                        readyText = stringResource(R.string.koil_ov_edit_ready),
                    )
                }
                // 保存失败提示（如唯一约束冲突等）
                if (uiState.saveError) {
                    Spacer(Modifier.height(8.dp))
                    SaveErrorHint()
                }
                // 这 20dp 是「内容块 → 按钮行」的间距：有块真的渲染才画。空态时块下缘
                // 已由上方列的 top 16dp 撑到位（块模型 ③），再加 20dp 就变成 36dp——
                // 正是开发者最初抱怨的松散值，条件化后空态 = 16dp，与定稿图一致。
                if (hasSummary || hasHint || uiState.saveError) {
                    Spacer(Modifier.height(20.dp))
                }
                Row(modifier = Modifier.fillMaxWidth()) {
                    GlassDialogButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        role = GlassDialogButtonRole.Normal,
                    ) {
                        Text(
                            text = stringResource(R.string.koil_common_cancel),
                            style = MiuixTheme.textStyles.button,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    GlassDialogButton(
                        onClick = onSave,
                        modifier = Modifier.weight(1f),
                        enabled = uiState.editReady && !uiState.isSavingEdit,
                        // Primary 角色 = 主色底：校验通过才亮起，置灰语义与原来的蓝色可保存一致
                        role = GlassDialogButtonRole.Primary,
                    ) {
                        Text(
                            text = stringResource(R.string.koil_common_save),
                            style = MiuixTheme.textStyles.button,
                        )
                    }
                }
            }
        }
    }
}

/** 补录校验提示（用户确认规则：早于第一条/同日重复/里程链/偏差A+B/实付>应付/油箱容量，通过绿色；readyText 支持编辑卡/补录卡差异化文案） */
@Composable
internal fun BackfillPrecheckHint(
    beforeFirst: Boolean,
    sameDayExists: Boolean,
    mileageConflictType: String,
    mileageConflictDate: String,
    mileageConflictValue: Double,
    deviationPct: Double?,
    deviationExceeded: Boolean,
    overPayable: Boolean,
    overTank: Boolean,
    tankCapacity: Double,
    ready: Boolean,
    readyText: String,
) {
    // 「是否绘制」由调用方的 hasPrecheckHint 决定，这里不再前置判一次；
    // 下面 when 的 else 只是兜底（谓词已保证至少一条命中）
    val text = when {
        beforeFirst -> stringResource(R.string.koil_ov_bf_before_first)
        sameDayExists -> stringResource(R.string.koil_ov_bf_dup)
        mileageConflictType == "prev" -> stringResource(
            R.string.koil_ov_bf_mileage_prev, mileageConflictDate, mileageConflictValue
        )
        mileageConflictType == "next" -> stringResource(
            R.string.koil_ov_bf_mileage_next, mileageConflictDate, mileageConflictValue
        )
        deviationExceeded && deviationPct != null -> stringResource(
            R.string.koil_precheck_deviation, deviationPct
        )
        overPayable -> stringResource(R.string.koil_precheck_over_payable)
        overTank -> stringResource(R.string.koil_precheck_over_tank, tankCapacity.toInt())
        ready -> readyText
        else -> return
    }
    val blocking = beforeFirst || sameDayExists ||
        mileageConflictType.isNotBlank() || deviationExceeded || overPayable || overTank
    val (bg, fg) = if (blocking) KOiLColors.CautionContainer to KOiLColors.Caution
    else KOiLColors.SuccessContainer to KOiLColors.Success
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(text, fontSize = KOiLType.BodySmall, color = fg)
    }
}

/** 补录/编辑保存失败提示（红色），不静默吞异常 */
@Composable
internal fun SaveErrorHint() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(KOiLColors.ErrorContainer)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(
            text = stringResource(R.string.koil_ov_save_failed),
            fontSize = KOiLType.BodySmall,
            color = KOiLColors.Error,
        )
    }
}

/** 选日期后该区间可输入里程范围提示（中性信息，不阻断） */
@Composable
internal fun MileageRangeHint(rangeMin: Double?, rangeMax: Double?) {
    val text = when {
        rangeMin != null && rangeMax != null ->
            stringResource(R.string.koil_ov_bf_mileage_range, rangeMin.toInt(), rangeMax.toInt())
        rangeMin != null ->
            stringResource(R.string.koil_ov_bf_mileage_range_min, rangeMin.toInt())
        else ->
            stringResource(R.string.koil_ov_bf_mileage_range_max, rangeMax?.toInt() ?: 0)
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0x14FFFFFF))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(text, fontSize = KOiLType.BodySmall, color = Color(0xFF9AA0A6))
    }
}

/** 加油省份选择字段（锚定下拉列表：紧贴字段下方弹出、空间不足自动上翻、底色与首页下拉一致，联动参考价/应付/偏差预检） */
/** 加油省份选择字段（官方玻璃下拉：由 Scaffold 根级托管，玻璃面板 + 页面 backdrop 采样） */
/** 加油省份选择字段（灰底圆角框触发 + 官方玻璃下拉，面板挂在根 Scaffold 全屏层） */
@Composable
internal fun ProvinceSelectField(province: String, onProvinceChange: (String) -> Unit) {
    val showPicker = remember { mutableStateOf(false) }
    val anchorBounds = remember { mutableStateOf(androidx.compose.ui.geometry.Rect.Zero) }
    // 值文本作为锚点：面板从值处展开、值在展开期间淡出（与设置页下拉同款）
    val popupAnchor = rememberGlassPopupAnchor()
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.LocalGasStation,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.onBackgroundVariant,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = stringResource(R.string.koil_ov_field_province),
                fontSize = KOiLType.BodySmall,
                color = MiuixTheme.colorScheme.onBackgroundVariant,
            )
        }
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                // 与 GlassTextField 同构，高度随行高自然与输入框一致
                .clip(RoundedCornerShape(GlassTextFieldDefaults.CornerRadius))
                .background(fieldSurfaceColor())
                .onGloballyPositioned { anchorBounds.value = it.boundsInWindow() }
                .clickable { showPicker.value = true }
                .padding(
                    horizontal = GlassTextFieldDefaults.InsideMargin.width,
                    vertical = GlassTextFieldDefaults.InsideMargin.height,
                ),
            contentAlignment = Alignment.CenterStart,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (province.isBlank()) stringResource(R.string.koil_add_pick_province) else provinceLabel(province),
                    style = MiuixTheme.textStyles.main,
                    fontWeight = if (province.isBlank()) FontWeight.Normal else FontWeight.Bold,
                    color = if (province.isBlank()) MiuixTheme.colorScheme.onBackgroundVariant else MiuixTheme.colorScheme.onBackground,
                    maxLines = 1,
                )
                Spacer(Modifier.weight(1f))
                // 锚点只挂右侧箭头：值留在左边，面板按箭头这一组的右边缘对齐字段右边
                Row(
                    modifier = Modifier.glassPopupAnchorValue(popupAnchor),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DropdownArrowEndAction(actionColor = MiuixTheme.colorScheme.onBackgroundVariant)
                }
            }
        }
    }
    GlassPopupOverlay(
        show = showPicker.value,
        anchorBounds = anchorBounds.value,
        anchor = popupAnchor,
        backdrop = LocalPageBackdrop.current,
        onDismissRequest = { showPicker.value = false },
        maxHeight = 360.dp,
    ) {
        PROVINCES.forEach { p ->
            GlassPopupItem(
                text = provinceLabel(p),
                selected = p == province,
                onClick = {
                    onProvinceChange(p)
                    showPicker.value = false
                },
            )
        }
    }
}

/** 实付综合信息块（对齐 Web PaymentSummaryCard：优惠概览 / 实际单价 · 参考价对比） */
@Composable
internal fun PaymentSummaryCard(
    payable: Double?,
    actual: Double?,
    fuelAmount: Double?,
    referencePrice: Double?,
) {
    // 「是否绘制」由调用方的 hasPaymentSummary 决定：两行都没有内容时调用方根本不会调它，
    // 这里不再自己判一次（否则就是同一条件抄两份，改一处忘另一处）。
    val hasPayable = payable != null && payable > 0
    val hasActual = actual != null && actual > 0
    val hasFuel = fuelAmount != null && fuelAmount > 0
    val hasReference = referencePrice != null && referencePrice > 0

    val green = KOiLColors.Success
    val amber = KOiLColors.Caution
    val gray = MiuixTheme.colorScheme.onBackgroundVariant

    // 第一行：优惠概览（应付/实付都有时显示）
    var savingsText: String? = null
    var savingsColor: Color = gray
    if (hasPayable && hasActual) {
        val diff = payable - actual
        val pct = if (payable > 0) diff / payable * 100 else 0.0
        when {
            diff > 0.005 -> { savingsText = stringResource(R.string.koil_ps_savings, abs(diff), abs(pct)); savingsColor = green }
            diff < -0.005 -> { savingsText = stringResource(R.string.koil_ps_overpay, abs(diff)); savingsColor = amber }
            else -> { savingsText = stringResource(R.string.koil_ps_no_savings); savingsColor = gray }
        }
    }

    // 第二行：实际单价对比（加油量>0 且 实付>0）
    val actualUnitPrice = if (hasFuel && hasActual) actual / fuelAmount else null
    var unitDiffText: String? = null
    var unitDiffColor: Color = gray
    if (actualUnitPrice != null && actualUnitPrice > 0 && hasReference) {
        val diff = referencePrice - actualUnitPrice
        val pct = if (referencePrice > 0) diff / referencePrice * 100 else 0.0
        when {
            diff > 0.005 -> { unitDiffText = stringResource(R.string.koil_ps_save_per_l, abs(diff), abs(pct)); unitDiffColor = green }
            diff < -0.005 -> { unitDiffText = stringResource(R.string.koil_ps_dear_per_l, abs(diff), abs(pct)); unitDiffColor = amber }
            else -> { unitDiffText = stringResource(R.string.koil_ps_match_ref); unitDiffColor = gray }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Column {
            if (savingsText != null && hasPayable && hasActual) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.koil_ps_payable_actual, payable, actual),
                        fontSize = KOiLType.BodySmall,
                        color = MiuixTheme.colorScheme.onBackgroundVariant,
                        modifier = Modifier.weight(1f),
                    )
                    Text(savingsText, fontSize = KOiLType.Body, fontWeight = FontWeight.SemiBold, color = savingsColor)
                }
                Spacer(Modifier.height(6.dp))
            }
            if (unitDiffText != null && actualUnitPrice != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = buildString {
                            append(stringResource(R.string.koil_ps_actual_unit, actualUnitPrice))
                            if (hasReference) append("  ·  ").append(stringResource(R.string.koil_ps_ref_price, referencePrice))
                        },
                        fontSize = KOiLType.BodySmall,
                        color = MiuixTheme.colorScheme.onBackgroundVariant,
                        modifier = Modifier.weight(1f),
                    )
                    Text(unitDiffText, fontSize = KOiLType.BodySmall, fontWeight = FontWeight.Medium, color = unitDiffColor)
                }
            }
        }
    }
}

/**
 * 「卡片是否绘制」的唯一判据，调用方据此决定要不要画卡片、要不要补 8dp 间距。
 * 条件内部与 `BackfillPrecheckHint` 使用同一套字段，卡片本身不再重复判断。
 */
internal fun hasPaymentSummary(payable: Double?, actual: Double?, fuelAmount: Double?, referencePrice: Double?): Boolean {
    val hasPayable = payable != null && payable > 0
    val hasActual = actual != null && actual > 0
    val hasFuel = fuelAmount != null && fuelAmount > 0
    val hasReference = referencePrice != null && referencePrice > 0
    return (hasPayable && hasActual) || (hasFuel && hasActual && hasReference)
}

/** 与 [BackfillPrecheckHint] 的渲染条件保持一致（无任何提示且未通过时不占高度） */
internal fun hasPrecheckHint(
    beforeFirst: Boolean,
    sameDayExists: Boolean,
    mileageConflictType: String,
    deviationExceeded: Boolean,
    overPayable: Boolean,
    overTank: Boolean,
    ready: Boolean,
): Boolean = beforeFirst || sameDayExists || mileageConflictType.isNotBlank() ||
    deviationExceeded || overPayable || overTank || ready

/** 补录弹窗字段（对齐首页录入卡 FormField：图标 + 标签行，红色错误态边框/图标/标签） */
@Composable
internal fun EditField(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String,
    onValueChange: (String) -> Unit,
    // Modifier 放第一个可选参数位（Compose 约定）
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    readOnly: Boolean = false,
    error: Boolean = false,
    placeholder: String? = null,
    /** 本窗口最后一个可编辑字段：回车=收键盘，而不是跳下一项 */
    isLastField: Boolean = false,
) {
    val errorColor = KOiLColors.Error
    val focusManager = LocalFocusManager.current
    val submitTick = remember { mutableIntStateOf(0) }
    val advanceFromField: () -> Unit = {
        if (isLastField) focusManager.clearFocus() else focusManager.moveFocus(FocusDirection.Next)
    }
    LaunchedEffect(Unit) {
        snapshotFlow { submitTick.intValue }.drop(1).collect { advanceFromField() }
    }
    val state = rememberTextFieldState(value)
    var fieldFocused by remember { mutableStateOf(false) }
    // 外部值变更时回填；只在未聚焦时覆盖，避免打断正在输入的光标
    LaunchedEffect(value, fieldFocused) {
        if (!fieldFocused && state.text.toString() != value) {
            state.setTextAndPlaceCursorAtEnd(value)
        }
    }
    LaunchedEffect(state) {
        snapshotFlow { state.text.toString() }.drop(1).collect { onValueChange(it) }
    }
    // 与设置页同一套配色：默认玻璃表面底色 + 聚焦主色边框，错误态只换红色
    val tfColors = if (error) {
        GlassTextFieldDefaults.textFieldColors(labelColor = errorColor, borderColor = errorColor)
    } else {
        GlassTextFieldDefaults.textFieldColors()
    }
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (error) errorColor else MiuixTheme.colorScheme.onBackgroundVariant,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = label,
                fontSize = KOiLType.BodySmall,
                color = if (error) errorColor else MiuixTheme.colorScheme.onBackgroundVariant,
            )
        }
        Spacer(Modifier.height(6.dp))
        // miuix TextField 无 placeholder 参数，用 Box 叠加层实现"待计算"占位（value 为空时显示）
        Box(modifier = Modifier.fillMaxWidth()) {
            GlassTextField(
                state = state,
                modifier = Modifier
                    .fillMaxWidth()
                    // 只读字段（自动计算的应付）不参与焦点序列
                    .focusProperties { canFocus = !readOnly }
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown &&
                            (event.key == Key.Enter || event.key == Key.NumPadEnter)
                        ) {
                            advanceFromField()
                            true
                        } else {
                            false
                        }
                    }
                    .onFocusChanged { fieldFocused = it.isFocused },
                readOnly = readOnly,
                lineLimits = TextFieldLineLimits.SingleLine,
                keyboardOptions = KeyboardOptions(
                    keyboardType = keyboardType,
                    imeAction = if (isLastField) ImeAction.Done else ImeAction.Next,
                ),
                onKeyboardAction = { advanceFromField() },
                colors = tfColors,
                inputTransformation = KOiLDecimalInput { submitTick.intValue++ },
            )
            if (placeholder != null && state.text.isEmpty()) {
                Text(
                    text = placeholder,
                    // 与框内输入文字同字号
                    style = MiuixTheme.textStyles.main,
                    color = MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.5f),
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = GlassTextFieldDefaults.InsideMargin.width),
                )
            }
        }
    }
}

/** 油号选择 + 当日参考单价（对齐首页录入 GradeField：显示 `92# · ¥X.XX/L`，点击弹锚定下拉） */
/** 油号选择 + 当日参考单价（官方玻璃下拉，价格走 summary 行） */
/** 油号选择 + 当日参考单价（灰底圆角框触发 + 官方玻璃下拉） */
@Composable
internal fun GradePriceSelect(
    grade: String,
    price: Double?,
    onGradeSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val gradeLabels = listOf("92", "95", "98", "0")
    val showMenu = remember { mutableStateOf(false) }
    val anchorBounds = remember { mutableStateOf(androidx.compose.ui.geometry.Rect.Zero) }
    val popupAnchor = rememberGlassPopupAnchor()
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.LocalGasStation,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.onBackgroundVariant,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(text = stringResource(R.string.koil_ov_grade), fontSize = KOiLType.BodySmall, color = MiuixTheme.colorScheme.onBackgroundVariant)
        }
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                // 与 GlassTextField 同构（同圆角 / 同内边距 / 同行高），高度自然相等
                .clip(RoundedCornerShape(GlassTextFieldDefaults.CornerRadius))
                .background(fieldSurfaceColor())
                .onGloballyPositioned { anchorBounds.value = it.boundsInWindow() }
                .clickable { showMenu.value = true }
                .padding(
                    horizontal = GlassTextFieldDefaults.InsideMargin.width,
                    vertical = GlassTextFieldDefaults.InsideMargin.height,
                ),
            contentAlignment = Alignment.CenterStart,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    // 与首页录入卡共用 gradeValueLabel：柴油不再显示成 `0#`
                    text = gradeValueLabel(grade, price),
                    style = MiuixTheme.textStyles.main,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                // 锚点只挂右侧箭头：值留在左边，面板右边缘对齐字段右边
                Row(
                    modifier = Modifier.glassPopupAnchorValue(popupAnchor),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DropdownArrowEndAction(actionColor = MiuixTheme.colorScheme.onBackgroundVariant)
                }
            }
        }
    }
    GlassPopupOverlay(
        show = showMenu.value,
        anchorBounds = anchorBounds.value,
        anchor = popupAnchor,
        backdrop = LocalPageBackdrop.current,
        onDismissRequest = { showMenu.value = false },
        maxHeight = 240.dp,
    ) {
        gradeLabels.forEach { g ->
            val label = if (g == "0") stringResource(R.string.koil_add_grade_diesel)
            else stringResource(R.string.koil_add_grade_gasoline, g)
            GlassPopupItem(
                text = label,
                selected = g == grade,
                onClick = {
                    onGradeSelected(g)
                    showMenu.value = false
                },
            )
        }
    }
}
