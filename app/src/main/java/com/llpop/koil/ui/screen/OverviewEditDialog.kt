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
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.llpop.koil.R
import com.llpop.koil.ui.component.sanitizeDecimalInput
import com.llpop.koil.domain.logic.NumberFormat
import com.llpop.koil.ui.screen.overview.DialogUiState
import com.llpop.koil.ui.theme.KOiLColors
import com.llpop.koil.ui.component.provinceLabel
import kotlin.math.abs
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.DropdownArrowEndAction
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TextFieldDefaults
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.llpop.koil.ui.theme.KOiLType



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
    com.llpop.koil.ui.component.KOiLGlassDialog(
        show = true,
        onDismissRequest = onDismiss,
        contentPadding = 0.dp,
        scrollable = true,
        maxHeight = with(LocalDensity.current) {
            (LocalWindowInfo.current.containerSize.height * 0.85f).toDp()
        },
        widthFraction = 0.90f,
    ) {
        Column {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 12.dp)) {
            Text(
                text = stringResource(R.string.koil_ov_edit_title),
                fontSize = KOiLType.Headline,
                fontWeight = FontWeight.Bold,
                color = MiuixTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(16.dp))
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
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f).heightIn(min = 44.dp),
                    ) { Text(stringResource(R.string.koil_common_cancel), fontSize = KOiLType.BodyLarge) }
                    Spacer(Modifier.width(12.dp))
                    Button(
                        onClick = onSave,
                        modifier = Modifier.weight(1f).heightIn(min = 44.dp),
                        enabled = uiState.editReady && !uiState.isSavingEdit,
                        // 全部校验通过 → 蓝色可保存；否则灰色置灰（对齐补录卡/首页录入卡）
                        colors = ButtonDefaults.buttonColors(
                            color = KOiLColors.Primary,
                            contentColor = Color.White,
                            disabledColor = MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            disabledContentColor = MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.4f),
                        ),
                    ) { Text(stringResource(R.string.koil_common_save), fontSize = KOiLType.BodyLarge, fontWeight = FontWeight.Medium) }
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
@Composable
internal fun ProvinceSelectField(province: String, onProvinceChange: (String) -> Unit) {
    val showPicker = remember { mutableStateOf(false) }
    val anchorBounds = remember { mutableStateOf<androidx.compose.ui.unit.IntRect?>(null) }
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
                .heightIn(min = 44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MiuixTheme.colorScheme.secondaryContainer)
                .onGloballyPositioned {
                    val b = it.boundsInWindow()
                    anchorBounds.value = androidx.compose.ui.unit.IntRect(b.left.toInt(), b.top.toInt(), b.right.toInt(), b.bottom.toInt())
                }
                .clickable { showPicker.value = true }
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (province.isBlank()) stringResource(R.string.koil_add_pick_province) else provinceLabel(province),
                    fontSize = KOiLType.BodyLarge,
                    fontWeight = if (province.isBlank()) FontWeight.Normal else FontWeight.Bold,
                    color = if (province.isBlank()) MiuixTheme.colorScheme.onBackgroundVariant else MiuixTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                )
                DropdownArrowEndAction(actionColor = MiuixTheme.colorScheme.onBackgroundVariant)
            }
        }
    }
    if (showPicker.value) {
        // 弹窗根级锚定下拉：注册到 KOiLGlassDialog 的 overlay（窗口坐标定位）
        com.llpop.koil.ui.component.AnchoredPopupAnchor(
            show = true,
            id = "ov-province-picker",
            anchorBounds = anchorBounds.value,
            maxHeight = 320.dp,
            onDismiss = { showPicker.value = false },
        ) {
            com.llpop.koil.ui.component.AnchoredDropdownColumn {
                PROVINCES.forEachIndexed { index, p ->
                    com.llpop.koil.ui.component.AnchoredDropdownItemRow(
                        text = provinceLabel(p),
                        selected = p == province,
                        isFirst = index == 0,
                        isLast = index == PROVINCES.lastIndex,
                        onClick = {
                            onProvinceChange(p)
                            showPicker.value = false
                        },
                    )
                }
            }
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
) {
    val errorColor = KOiLColors.Error
    val tfColors = TextFieldDefaults.textFieldColors(
        backgroundColor = MiuixTheme.colorScheme.secondaryContainer,
        labelColor = if (error) errorColor else MiuixTheme.colorScheme.onSecondaryContainer,
        borderColor = if (error) errorColor else Color.Transparent,
    )
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
            TextField(
                value = value,
                onValueChange = { onValueChange(it.sanitizeDecimalInput()) },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                singleLine = true,
                readOnly = readOnly,
                colors = tfColors,
                cursorBrush = SolidColor(MiuixTheme.colorScheme.onBackground),
            )
            if (placeholder != null && value.isEmpty()) {
                Text(
                    text = placeholder,
                    fontSize = KOiLType.BodyLarge,
                    color = MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.5f),
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 14.dp),
                )
            }
        }
    }
}

/** 油号选择 + 当日参考单价（对齐首页录入 GradeField：显示 `92# · ¥X.XX/L`，点击弹锚定下拉） */
@Composable
internal fun GradePriceSelect(
    grade: String,
    price: Double?,
    onGradeSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val gradeLabels = listOf("92", "95", "98", "0")
    val showMenu = remember { mutableStateOf(false) }
    val anchorBounds = remember { mutableStateOf<androidx.compose.ui.unit.IntRect?>(null) }
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
                .heightIn(min = 52.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MiuixTheme.colorScheme.secondaryContainer)
                .onGloballyPositioned {
                    val b = it.boundsInWindow()
                    anchorBounds.value = androidx.compose.ui.unit.IntRect(b.left.toInt(), b.top.toInt(), b.right.toInt(), b.bottom.toInt())
                }
                .clickable { showMenu.value = true }
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "$grade# · ${price?.let { "¥${NumberFormat.measure(it)}/L" } ?: "¥--/L"}",
                    fontSize = KOiLType.BodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = MiuixTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                )
                DropdownArrowEndAction(actionColor = MiuixTheme.colorScheme.onBackgroundVariant)
            }
        }
    }
    if (showMenu.value) {
        // 弹窗根级锚定下拉：注册到 KOiLGlassDialog 的 overlay（窗口坐标定位，与 miuix 同款视觉）
        com.llpop.koil.ui.component.AnchoredPopupAnchor(
            show = true,
            id = "ov-grade-picker",
            anchorBounds = anchorBounds.value,
            maxHeight = 240.dp,
            onDismiss = { showMenu.value = false },
        ) {
            com.llpop.koil.ui.component.AnchoredDropdownColumn {
                gradeLabels.forEachIndexed { index, g ->
                    val label = if (g == "0") stringResource(R.string.koil_add_grade_diesel)
                    else stringResource(R.string.koil_add_grade_gasoline, g)
                    com.llpop.koil.ui.component.AnchoredDropdownItemRow(
                        text = label,
                        selected = g == grade,
                        isFirst = index == 0,
                        isLast = index == gradeLabels.lastIndex,
                        onClick = {
                            onGradeSelected(g)
                            showMenu.value = false
                        },
                    )
                }
            }
        }
    }
}
