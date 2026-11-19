package com.llpop.koil.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier

/**
 * 页面级弹层栈：把玻璃下拉提升到**页面根 Box** 渲染。
 * 官方玻璃弹层（`GlassDropdownPopup` 等）要求放在页面根 BoxScope，否则会被卡片裁剪、
 * 或者在被无限高度约束的列表项里测量失败。
 */
@Stable
class GlassPopupLayerState {

    internal val entries = mutableStateListOf<Pair<Int, @Composable BoxScope.() -> Unit>>()

    private var nextId = 0

    internal fun allocateId(): Int = nextId++

    internal fun upsert(id: Int, content: @Composable BoxScope.() -> Unit) {
        val index = entries.indexOfFirst { it.first == id }
        if (index >= 0) entries[index] = id to content else entries.add(id to content)
    }

    internal fun remove(id: Int) {
        entries.removeAll { it.first == id }
    }
}

val LocalGlassPopupLayer = staticCompositionLocalOf<GlassPopupLayerState?> { null }

/** 把 [content] 提升到页面根层渲染；没有宿主时原地兜底。 */
@Composable
fun GlassPopupLayer(content: @Composable BoxScope.() -> Unit) {
    val state = LocalGlassPopupLayer.current
    if (state == null) {
        Box(Modifier.fillMaxSize()) { content() }
        return
    }
    val id = remember { state.allocateId() }
    SideEffect { state.upsert(id, content) }
    DisposableEffect(id) {
        onDispose { state.remove(id) }
    }
}

/** 页面根层渲染宿主：放在内容与覆盖层之后，保证画在最上层且尺寸有界 */
@Composable
internal fun GlassPopupLayerHost(state: GlassPopupLayerState, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize()) {
        state.entries.forEach { (id, content) ->
            key(id) { content() }
        }
    }
}
