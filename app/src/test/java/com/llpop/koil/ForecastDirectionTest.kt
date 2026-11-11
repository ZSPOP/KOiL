package com.llpop.koil

import com.llpop.koil.data.oil.ForecastDirection
import com.llpop.koil.data.oil.forecastDirection
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 搁浅判定（官方口径：调价幅度低于每吨 50 元时本轮不作调整）。
 *
 * 这条判断的意义不在算法，而在它是唯一挡住「预计下调约 0 元/吨」的闸门：
 * 幅度算成 0 或 47 时，界面上如果照直说「上调 47 元/吨」，用户以为会调，公告却是「不调」。
 * 25 轮里真正搁浅的轮次就是这样来的，所以边界必须钉死。
 */
class ForecastDirectionTest {

    @Test
    fun `幅度不足五十元每吨判为搁浅`() {
        assertEquals(ForecastDirection.STRANDED, forecastDirection(0.0))
        assertEquals(ForecastDirection.STRANDED, forecastDirection(49.9))
        assertEquals(ForecastDirection.STRANDED, forecastDirection(-49.9))
        assertEquals(ForecastDirection.STRANDED, forecastDirection(47.0))
        assertEquals(ForecastDirection.STRANDED, forecastDirection(-47.0))
        assertEquals(ForecastDirection.STRANDED, forecastDirection(1e-6))
    }

    @Test
    fun `正好五十元每吨要调不算搁浅`() {
        // 官方措辞是「低于每吨 50 元不作调整」→ 50 属于要调，边界取严格小于
        assertEquals(ForecastDirection.UP, forecastDirection(50.0))
        assertEquals(ForecastDirection.DOWN, forecastDirection(-50.0))
    }

    @Test
    fun `超过门槛按方向判定`() {
        assertEquals(ForecastDirection.UP, forecastDirection(170.0))
        assertEquals(ForecastDirection.DOWN, forecastDirection(-170.0))
        assertEquals(ForecastDirection.UP, forecastDirection(580.7))
        assertEquals(ForecastDirection.DOWN, forecastDirection(-580.7))
    }

    @Test
    fun `门槛可注入便于换口径`() {
        assertEquals(ForecastDirection.STRANDED, forecastDirection(80.0, threshold = 100.0))
        assertEquals(ForecastDirection.UP, forecastDirection(80.0, threshold = 50.0))
    }
}
