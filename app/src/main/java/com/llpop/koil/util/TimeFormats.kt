package com.llpop.koil.util

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 全仓统一的时间格式化。
 *
 * 原先 `data/sync` 用 `SimpleDateFormat`、`ui/` 用 `java.time`，两套 API 并存：
 * 同一行两个时钟源的毛病刚修过一轮，留着两套迟早复发；`SimpleDateFormat` 还非线程安全，
 * 放在伴生对象里复用会偶发串格式。这里只留 `DateTimeFormatter`（不可变、线程安全）。
 *
 * locale 固定 [Locale.US]：这些格式都是纯数字，不能跟着界面语言变成阿拉伯数字/其他分隔符，
 * 否则切一次语言，页面上「上次同步」和「上次检查」就像两种东西。
 * 文件名用 UTC（跨设备排序稳定），展示用本地时区（用户对得上自己刚操作的时间）。
 */
object TimeFormats {

    private val MINUTE = DateTimeFormatter.ofPattern("MM-dd HH:mm", Locale.US)
    private val SECOND = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss", Locale.US)
    private val FILE = DateTimeFormatter.ofPattern("uuuuMMdd-HHmmss", Locale.US)
    private val FILE_MILLIS = DateTimeFormatter.ofPattern("uuuuMMdd-HHmmss-SSS", Locale.US)

    /** `MM-dd HH:mm`（本地时区） */
    fun minute(instant: Instant = AppClock.now()): String =
        MINUTE.withZone(AppClock.zone()).format(instant)

    /** `yyyy-MM-dd HH:mm:ss`（本地时区；导出文件/崩溃日志的时间戳） */
    fun second(instant: Instant = AppClock.now()): String =
        SECOND.withZone(AppClock.zone()).format(instant)

    /** `yyyyMMdd-HHmmss`（本地时区；用户可见的导出文件名） */
    fun fileStamp(instant: Instant = AppClock.now()): String =
        FILE.withZone(AppClock.zone()).format(instant)

    /** `yyyyMMdd-HHmmss-SSS`（UTC；同步副本文件名，同一秒内两次也不撞名） */
    fun fileStampUtc(instant: Instant = AppClock.now()): String =
        FILE_MILLIS.withZone(ZoneOffset.UTC).format(instant)

    /** 解析 [fileStampUtc] 生成的 UTC 串；读不懂返回 null（调用方原样展示，不吞信息） */
    fun parseFileStampUtc(stamp: String): Instant? =
        runCatching { FILE_MILLIS.withZone(ZoneOffset.UTC).parse(stamp, Instant::from) }.getOrNull()
}
