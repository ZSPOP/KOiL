plugins {
    alias(libs.plugins.android.test)
    // AGP 9 内置 Kotlin 支持，这里不能再挂 org.jetbrains.kotlin.android
    alias(libs.plugins.baselineprofile)
}

android {
    namespace = "com.llpop.koil.baselineprofile"

    // 与 :app 同一编译基准（miuix AAR 要求 compileSdk 37 的 minorApiLevel = 2）
    compileSdk {
        version = release(37) {
            minorApiLevel = 2
        }
    }

    defaultConfig {
        minSdk = 33
        targetSdk = 37
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // com.android.test 模块：不产出可安装 App，只对 :app 做仪器化测试
    targetProjectPath = ":app"
    experimentalProperties["android.experimental.self-instrumenting"] = true

    buildTypes {
        // 录制/基准要求 target 是非 debuggable 的 release 包，
        // 但测试模块自身必须 debuggable 才能被 instrumentation 启动
        create("benchmark") {
            isDebuggable = true
            signingConfig = getByName("debug").signingConfig
            matchingFallbacks += listOf("release")
        }
    }
}

dependencies {
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.test.runner)
    implementation(libs.androidx.test.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}

// baselineProfile 插件的默认行为即把录制结果写回 :app 的 generated 目录，
// 由 AGP 合并进 assets/dexopt/baseline.prof，这里不需要额外配置。
