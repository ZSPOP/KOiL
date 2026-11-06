# ===== KOiL R8 规则（v1.6.2 起生效：isMinifyEnabled = true / isShrinkResources = true）=====
#
# 策略：只做 shrink + optimize，不做混淆。
#   - CrashReporter 会把异常堆栈写入 Download/KOiL-crash.txt 并展示给用户，
#     保留可读类名/方法名，线上排障才看得懂；
#   - ThemeMode / Language 等枚举的 name() 已持久化在 DataStore，
#     关闭混淆可从根本上规避"改名后旧值读不回来"的风险。
# 体积收益几乎全部来自 shrink（移除未引用类/字段/资源），混淆带来的额外收益很小。

-dontobfuscate

# 保留行号信息（堆栈定位需要）
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# 注解 / 泛型签名 / 内部类：kotlinx.serialization、Room、Compose 运行时都依赖
-keepattributes Signature,InnerClasses,EnclosingMethod,*Annotation*,RuntimeVisibleAnnotations,RuntimeInvisibleAnnotations

# ===== WorkManager =====
# WorkRequest 里只存 Worker 的全限定名字符串，运行时靠反射实例化；
# 若被 shrink 掉会直接抛 "Could not instantiate ... Worker"。
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-dontwarn androidx.work.**

# ===== kotlinx.serialization =====
# @Serializable 类由编译器插件生成 $serializer 单例；保留 serializer() 工厂与伴生对象成员，
# 避免 Json.decodeFromString 在优化后取不到序列化器。
-keepclassmembers class **$$serializer {
    *;
}
-keepclasseswithmembers class ** {
    kotlinx.serialization.KSerializer serializer(...);
}
-dontnote kotlinx.serialization.**

# ===== Koin（DI）=====
# singleOf(::X) 走构造函数引用，理论上不需要 keep；保留为好（Koin 内部仍有 KClass 查表路径）
-keep class org.koin.** { *; }
-dontwarn org.koin.**

# ===== Ktor / OkHttp =====
# Ktor 会按名称反射选择引擎与插件；可选依赖（Conscrypt、BouncyCastle、Kotlin-Reflect 等）未引入，忽略其缺失告警。
-keep class io.ktor.client.engine.okhttp.** { *; }
-keep class io.ktor.serialization.kotlinx.json.** { *; }
-dontwarn io.ktor.**
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn kotlin.reflect.**
-dontwarn kotlinx.coroutines.debug.**

# ===== Room =====
# Room 2.7 自带 consumer 规则；这里只兜住 schema/实体上的注解不被裁剪。
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-dontwarn androidx.room.paging.**

# ===== 其他常见误报 =====
-dontwarn javax.annotation.**
-dontwarn org.slf4j.**
