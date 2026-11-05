import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties
import java.io.FileInputStream

// 读取正式签名配置（keystore.properties 不入库；缺失时回退 debug 签名，便于无签名环境构建）
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) FileInputStream(f).use { load(it) }
}
val hasReleaseSigning = keystoreProps.getProperty("storePassword")?.isNotBlank() == true

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.llpop.koil"
    compileSdk = 37

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile", "keystore/koil-release.keystore"))
                storePassword = keystoreProps.getProperty("storePassword", "")
                keyAlias = keystoreProps.getProperty("keyAlias", "koil")
                keyPassword = keystoreProps.getProperty("keyPassword", "")
            }
        }
    }

    defaultConfig {
        applicationId = "com.llpop.koil"
        minSdk = 33
        targetSdk = 37
        // v1.6.0：引入 CI（GitHub Actions）——versionCode 支持 CI 注入：
        // CI 侧传 KOIL_CI_RUN_NUMBER（github.run_number），此处 +251 偏移，与本地 252 基准衔接、全局单调递增；
        // （GitHub Actions 表达式不支持 +，故加法放在 gradle 侧做）
        // 兼容旧变量名 KOIL_CI_VERSION_CODE（直接用其值）；本地构建无环境变量时 fallback 到下方基准值，每轮手动 +1。
        versionCode = System.getenv("KOIL_CI_RUN_NUMBER")?.toIntOrNull()?.let { it + 251 }
            ?: System.getenv("KOIL_CI_VERSION_CODE")?.toIntOrNull() ?: 252
        versionName = "1.6.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            // 正式签名（keystore.properties 存在时）；否则回退 debug 签名
            signingConfig = if (hasReleaseSigning) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
            // v1.6.2：开启 R8 代码压缩 + 资源压缩。此前 isMinifyEnabled=false，dex 未压缩 58MB（4 个 dex），
            // 绝大多数体积是未被引用到的 material-icons-extended（2000+ 图标，实际只用 28 个）与调试符号。
            // 混淆在 proguard-rules.pro 里用 -dontobfuscate 关闭（只做 shrink/optimize）：
            // ① CrashReporter 会把堆栈写进文件给用户看，保留原类名才看得懂；
            // ② 枚举名（ThemeMode/Language 的 name()）已持久化进 DataStore，不做混淆规避改名风险。
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    // v1.6.3：App 内置运行时语言切换（MainScreen 覆盖 Configuration）。若按语言拆分 AAB，
    // 用户切到的语言包可能根本不在设备上，切了等于没切 —— 关掉语言拆分，全部语言随包下发。
    bundle {
        language {
            enableSplit = false
        }
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

// Room schema 导出：每次构建生成 schemas/<version>/xxx.json，
// 作为未来写 Migration 的基准对照（配合 AppDatabase 的 addMigrations，杜绝清库式升级）
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    // Compose
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    // v1.6.2：tooling.preview 只含 @Preview 等 IDE 预览注解，主源码零引用（已全量核对），
    // 移到 debugImplementation，release 不再打进包
    debugImplementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.runtime)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Miuix（含柔光玻璃模糊库）
    // miuix-icons：Dropdown/ListPopup 菜单项渲染依赖 MiuixIcons（Check/ArrowUpDown 等），
    // 缺它会 NoClassDefFoundError 点击闪退（对齐 Mishka 完整 miuix bundle）
    implementation(libs.miuix.ui)
    implementation(libs.miuix.preference)
    implementation(libs.miuix.blur)
    implementation(libs.miuix.icons)
    // navigationevent：miuix Dropdown 弹窗的返回手势依赖 LocalNavigationEventDispatcherOwner；
    // 必须用 1.2.0（Mishka 版本），1.1.2 无外部 dispatcher 时抛 IllegalStateException 闪退
    implementation(libs.androidx.navigationevent.compose)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Ktor
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)

    // Koin
    implementation(libs.koin.core)
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)

    // Coroutines & Serialization
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    // DataStore（设置存储，替代 Room 保存设置键值）
    implementation(libs.androidx.datastore.preferences)

    // WorkManager（油价库每 8 小时自动检测）
    implementation(libs.androidx.work.runtime.ktx)

    // Android 12+ 系统 SplashScreen
    implementation(libs.androidx.core.splashscreen)

    // Tests
    testImplementation(libs.junit)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.robolectric)
}
