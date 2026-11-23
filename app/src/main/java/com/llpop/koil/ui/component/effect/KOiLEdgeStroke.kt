package com.llpop.koil.ui.component.effect

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
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
        derivedStateOf {
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
