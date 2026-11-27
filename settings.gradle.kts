pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "KOiL"
include(":app")
// Baseline Profile 录制模块（com.android.test，非可安装 App）：
// 用 macrobenchmark 跑一遍启动/滚动路径，把热点方法写进 app 的 baseline profile。
include(":baselineprofile")
