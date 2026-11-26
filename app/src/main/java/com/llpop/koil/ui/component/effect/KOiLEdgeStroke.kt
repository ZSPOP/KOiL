package com.llpop.koil.ui.component.effect

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.computedStateOf
import androidx.compose.runtime.remember
import top.yukonga.miuix.kmp.material.EdgeStroke
import top.yukonga.miuix.kmp.material.sensor.rememberDeviceTilt
import top.yukonga.miuix.kmp.material.withTilt

/**
 * 对应官方 example `component/edge/ContainerEdgeStroke.kt` 的 `rememberContainerEdgeStrokeState`：
 * 按 [KOiLGlassSpec.edgeTier] 取基准描边，两个光源再按设备倾斜做偏移，玻璃边就像官方 demo
 * 那样随手机姿态移动。档位为 [KOiLGlassEdgeTier.Disabled] 时返回 null（等于不画边）。
 */
@Composable
fun rememberKOiLGlassEdgeStroke(
    isDark: Boolean,
    spec: KOiLGlassSpec = LocalKOiLGlassSpec.current,
): State<EdgeStroke?> {
    val baseStroke = spec.edgeTier.resolve(isDark)
    val tilt = rememberDeviceTilt()
    return remember(baseStroke, spec.tiltDriven, spec.primarySensitivity, spec.secondarySensitivity, tilt) {
        computedStateOf {
            when {
                baseStroke == null -> null
                !spec.tiltDriven -> baseStroke
                else -> {
                    val sample = tilt.value
                    baseStroke.copy(
                        primary = baseStroke.primary.withTilt(sample, spec.primarySensitivity),
                        secondary = baseStroke.secondary.withTilt(sample, spec.secondarySensitivity),
                    )
                }
            }
        }
    }
}

/**
 * 静态版：只按档位取基准描边，不接设备倾斜。给「一打开就整块重组」的覆盖层用
 * （确认弹窗、底部弹层、顶栏按钮、底栏）—— 它们的 stroke 是普通值参数，
 * 接 [rememberKOiLGlassEdgeStroke] 会在每次倾斜时重组整个弹层内容。
 */
@Composable
fun rememberKOiLGlassEdgeStrokeStatic(
    isDark: Boolean,
    spec: KOiLGlassSpec = LocalKOiLGlassSpec.current,
): EdgeStroke? = remember(isDark, spec.edgeTier) { spec.edgeTier.resolve(isDark) }
