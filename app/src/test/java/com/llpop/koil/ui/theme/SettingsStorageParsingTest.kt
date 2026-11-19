package com.llpop.koil.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 新设置的存储值解析：脏值必须落回默认，不能抛异常、也不能把非法值当成合法档位。
 * 这两组键在 1.2.10 才加，此前没有任何测试覆盖。
 */
class SettingsStorageParsingTest {

    @Test
    fun floatingBottomBarStyle_acceptsStoredValues() {
        assertEquals(FloatingBottomBarStyle.IosLike, FloatingBottomBarStyle.fromStorage("ios_like"))
        assertEquals(FloatingBottomBarStyle.Miuix, FloatingBottomBarStyle.fromStorage("miuix"))
    }

    @Test
    fun floatingBottomBarStyle_fallsBackToMiuix_onDirtyValues() {
        assertEquals(FloatingBottomBarStyle.Miuix, FloatingBottomBarStyle.fromStorage(""))
        assertEquals(FloatingBottomBarStyle.Miuix, FloatingBottomBarStyle.fromStorage("IOS_LIKE"))
        assertEquals(FloatingBottomBarStyle.Miuix, FloatingBottomBarStyle.fromStorage("  ios_like  "))
        assertEquals(FloatingBottomBarStyle.Miuix, FloatingBottomBarStyle.fromStorage("floating"))
    }

    @Test
    fun pagerGestureMode_acceptsStoredValues() {
        assertEquals(PagerGestureMode.CrossAxis, PagerGestureMode.fromStorage("cross_axis"))
        assertEquals(PagerGestureMode.Native, PagerGestureMode.fromStorage("native"))
    }

    @Test
    fun pagerGestureMode_fallsBackToCrossAxis_onDirtyValues() {
        assertEquals(PagerGestureMode.CrossAxis, PagerGestureMode.fromStorage(""))
        assertEquals(PagerGestureMode.CrossAxis, PagerGestureMode.fromStorage("CROSS_AXIS"))
        assertEquals(PagerGestureMode.CrossAxis, PagerGestureMode.fromStorage("cross axis"))
        assertEquals(PagerGestureMode.CrossAxis, PagerGestureMode.fromStorage("swipe"))
    }

    @Test
    fun storageValues_areStable() {
        // 存储值是持久化契约，改名会让老用户的设置被静默重置
        assertEquals("ios_like", FloatingBottomBarStyle.IosLike.storageValue)
        assertEquals("miuix", FloatingBottomBarStyle.Miuix.storageValue)
        assertEquals("cross_axis", PagerGestureMode.CrossAxis.storageValue)
        assertEquals("native", PagerGestureMode.Native.storageValue)
    }
}
