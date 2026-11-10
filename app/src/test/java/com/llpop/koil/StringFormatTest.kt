package com.llpop.koil

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 全量字符串占位符格式化回归测试。
 *
 * 背景：明细页年份改用 `stringResource(R.string.koil_detail_year, y)`，
 * 但资源写的是 `%1$d`，而 y 是 String → String.format 抛
 * IllegalFormatConversionException: d != java.lang.String，
 * 用户点「油价数据库 · 查看」进明细页的**首帧就闪退**，且必现（库里有记录时同年份 chip 一定渲染）。
 * 这类错误编译期查不出来（vararg Any），只能运行时炸，所以在这里一次性扫全部 string 资源：
 * 按资源自己声明的占位符类型喂对应类型的假值，任何 key 抛异常即失败。
 *
 * 覆盖三种语言：values（默认）、values-en、values-zh-rTW。
 */
@RunWith(RobolectricTestRunner::class)
// SDK 34：Robolectric 4.17 在 SDK 35+ 会走 ApplicationSharedMemory，JDK 21 的 FileDescriptor
// 反射被模块系统拦截（RuntimeException: Failed to interact with raw FileDescriptor internals），
// 与本测试无关；这里的验证对象是资源字符串格式化，SDK 版本不影响结论
@Config(sdk = [34], application = Application::class)
class StringFormatTest {

    /** `%1$s` / `%2$.2f` / `%3$d` 等 positional 占位符 */
    private val placeholder = Regex("""%(\d+)\$[-#+0-9.]*([a-zA-Z])""")

    private fun checkAllStrings(context: Context, tag: String): List<String> {
        val failures = mutableListOf<String>()
        val fields = R.string::class.java.declaredFields
            .filter { it.type == Int::class.javaPrimitiveType && java.lang.reflect.Modifier.isStatic(it.modifiers) }
        fields.forEach { field ->
            val id = field.getInt(null)
            val raw = runCatching { context.getString(id) }.getOrNull() ?: return@forEach
            val matches = placeholder.findAll(raw).toList()
            if (matches.isEmpty()) return@forEach

            val argsCount = matches.maxOf { it.groupValues[1].toInt() }
            val args = arrayOfNulls<Any>(argsCount)
            for (m in matches) {
                val index = m.groupValues[1].toInt() - 1
                // %x / %c / %b 等项目未使用；出现即留空由下面统一报错，避免静默跳过
                args[index] = when (m.groupValues[2].lowercase()) {
                    "s" -> "示例"
                    "d" -> 7
                    "f" -> 7.5
                    else -> null
                }
            }
            if (args.any { it == null }) {
                failures += "[$tag] ${field.name}: 占位符序号不连续 → $raw"
                return@forEach
            }
            try {
                context.getString(id, *args)
            } catch (e: Throwable) {
                failures += "[$tag] ${field.name}: ${e::class.java.simpleName} ${e.message} → $raw"
            }
        }

        // plurals 同样扫一遍：quantity=1 校验 one/other 分支，quantity=2 校验 other 分支
        val pluralFields = R.plurals::class.java.declaredFields
            .filter { it.type == Int::class.javaPrimitiveType && java.lang.reflect.Modifier.isStatic(it.modifiers) }
        for (field in pluralFields) {
            val id = field.getInt(null)
            for (quantity in intArrayOf(1, 2)) {
                val raw = runCatching { context.resources.getQuantityString(id, quantity) }.getOrNull() ?: continue
                val matches = placeholder.findAll(raw).toList()
                if (matches.isEmpty()) continue

                val argsCount = matches.maxOf { it.groupValues[1].toInt() }
                val args = arrayOfNulls<Any>(argsCount)
                for (m in matches) {
                    val index = m.groupValues[1].toInt() - 1
                    args[index] = when (m.groupValues[2].lowercase()) {
                        "s" -> "示例"
                        "d" -> 7
                        "f" -> 7.5
                        else -> null
                    }
                }
                if (args.any { it == null }) {
                    failures += "[$tag] ${field.name}[q=$quantity]: 占位符序号不连续 → $raw"
                    continue
                }
                try {
                    context.resources.getQuantityString(id, quantity, *args)
                } catch (e: Throwable) {
                    failures += "[$tag] ${field.name}[q=$quantity]: ${e::class.java.simpleName} ${e.message} → $raw"
                }
            }
        }
        return failures
    }

    @Test
    fun `简体中文全部字符串可按占位符类型格式化`() {
        val failures = checkAllStrings(ApplicationProvider.getApplicationContext(), "zh-CN")
        assertTrue("存在格式化即崩溃的字符串：\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    @Config(sdk = [34], qualifiers = "en", application = Application::class)
    fun `英文全部字符串可按占位符类型格式化`() {
        val failures = checkAllStrings(ApplicationProvider.getApplicationContext(), "en")
        assertTrue("存在格式化即崩溃的字符串：\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    @Config(sdk = [34], qualifiers = "zh-rTW", application = Application::class)
    fun `繁体全部字符串可按占位符类型格式化`() {
        val failures = checkAllStrings(ApplicationProvider.getApplicationContext(), "zh-TW")
        assertTrue("存在格式化即崩溃的字符串：\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    /**
     * 明细页年份 chip 的直接回归：实参是年份字符串（state.years: List<String>），
     * 资源必须用 %s，且格式化结果要保留年份数字。
     */
    @Test
    fun `年份chip用字符串占位符格式化`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val text = context.getString(R.string.koil_detail_year, "2026")
        assertTrue("年份未被带入：$text", text.contains("2026"))
    }
}
