package com.llpop.koil.ui.component.blur

/** 顶栏模糊形态：均匀高斯 / 顶部渐进 */
enum class TopBarBlurStyle(val storageValue: String) {
    Gaussian("gaussian"),
    Progressive("progressive");

    companion object {
        fun fromStorage(value: String): TopBarBlurStyle =
            entries.firstOrNull { it.storageValue == value } ?: Gaussian
    }
}
