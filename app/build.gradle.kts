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
        versionCode = 204
        versionName = "1.5.4"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            // 正式签名（keystore.properties 存在时）；否则回退 debug 签名
            signingConfig = if (hasReleaseSigning) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
            isMinifyEnabled = false
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
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.runtime)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation("androidx.compose.material3:material3:1.3.1")
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
    testImplementation("junit:junit:4.13.2")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("org.robolectric:robolectric:4.14.1")
}
