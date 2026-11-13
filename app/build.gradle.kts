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
        // versionCode 唯一规则：只增不减，必须高于任何已分发安装包，且任意两个产物不得同号。
        // 号由 CI 在构建前算好后注入 KOIL_CI_VERSION_CODE，两条流水线共用「已发布版本数 N」作偏移：
        // - Build（合 main）：github.run_number + N
        // - Release（打 V 标签）：Build 最大 run_number + N + 1
        // 两者严格交替、永不同号。不能直接用 run_number（重建仓库后它从 1 重来，会低于已发布版本），
        // 也不能用 max(已发布最大, run_number) + k（发版后已发布号领先，连续构建会落到同一个号上）。
        // 本地构建用 KOIL_CI_VERSION_CODE 显式指定号；无环境变量时回落到 1。
        versionCode = System.getenv("KOIL_CI_VERSION_CODE")?.toIntOrNull()
            ?: System.getenv("KOIL_CI_RUN_NUMBER")?.toIntOrNull() ?: 1
        // versionName 与 tag 严格对齐（CI 已有「tag 去 V == versionName」断言，不一致直接 fail）
        versionName = "1.1.13"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        debug {
            // 独立包名：debug 构建不再覆盖真机上的正式版（签名不同本来也装不上）。
            // 真机 instrumented 测试（androidTest）装的是 debug 变体，走独立包名互不干扰。
            applicationIdSuffix = ".debug"
        }
        release {
            // 正式签名（keystore.properties 存在时）；否则回退 debug 签名
            signingConfig = if (hasReleaseSigning) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
            // 开启 R8 代码压缩 + 资源压缩：material-icons-extended 有大量未引用图标与调试符号，
            // 不压缩会显著放大产物体积。混淆在 proguard-rules.pro 里用 -dontobfuscate 关闭（只做 shrink/optimize）：
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
    // App 内置运行时语言切换（MainScreen 覆盖 Configuration）。若按语言拆分 AAB，
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

// Room schema 导出：每次构建生成 schemas/<version>/xxx.json，作为当前库结构的基准快照
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
    // tooling.preview 只含 @Preview 等 IDE 预览注解，主源码零引用，
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
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.robolectric)

    // Instrumented tests（真机性能基线等）
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
}
