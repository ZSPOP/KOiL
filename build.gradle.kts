plugins {
    alias(libs.plugins.android.application) apply false
    // com.android.test 由 AGP 提供：在根上声明一次版本，:baselineprofile 才能按 alias 请求，
    // 否则会报 "already on the classpath with an unknown version"
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}
