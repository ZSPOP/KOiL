package com.llpop.koil.util

import androidx.annotation.VisibleForTesting
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * 全仓唯一的「现在」入口。
 *
 * 为什么要有它：生产代码原先有 40 处 `LocalDate.now()` / `System.currentTimeMillis()` 直调，
 * 于是「跨零点」「跨年」「窗口日当天」这类边界只能靠人推，写不了单测 —— 测试进程永远在
 * 真实的今天跑，改不了被测函数的「现在」。这里把时钟收成一个可替换的点：
 * 生产走系统时钟，测试用 [setTestClock] 固定到某一天。
 *
 * 语义与原先的直调完全一致（系统默认时区），不是新口径：
 * `AppClock.today()` == `LocalDate.now()`，`AppClock.millis()` == `System.currentTimeMillis()`。
 */
object AppClock {

    @Volatile
    private var testClock: Clock? = null

    /** 当前时钟；未注入测试时钟时为系统默认时区时钟 */
    val clock: Clock get() = testClock ?: Clock.systemDefaultZone()

    fun zone(): ZoneId = clock.zone

    fun now(): Instant = clock.instant()

    /** 墙上时钟毫秒数（与 `System.currentTimeMillis()` 同义） */
    fun millis(): Long = clock.millis()

    fun today(): LocalDate = LocalDate.now(clock)

    fun nowLocal(): LocalDateTime = LocalDateTime.now(clock)

    fun nowZoned(): ZonedDateTime = ZonedDateTime.now(clock)

    /** 仅测试使用：传 null 恢复系统时钟 */
    @VisibleForTesting
    fun setTestClock(clock: Clock?) {
        testClock = clock
    }
}
