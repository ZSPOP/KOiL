package com.llpop.koil.ui.component

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * 横向安全区：刘海/挖孔与横向导航栏只吃水平分量。
 * 竖屏手机上是 0，横屏与折叠屏展开时才有值。
 */
@Composable
fun Modifier.horizontalCutoutPadding(): Modifier = this
    .windowInsetsPadding(
        WindowInsets.displayCutout.union(WindowInsets.navigationBars).only(WindowInsetsSides.Horizontal),
    )
