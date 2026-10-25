package com.llpop.koil

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.rememberNavigationEventDispatcherOwner
import com.llpop.koil.ui.MainScreen
import com.llpop.koil.util.CrashReporter

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // 全局崩溃捕获：崩溃堆栈静默落盘（私有目录 + 下载/KOiL-crash.txt），
        // 不弹 Toast、不打扰用户；需要排查时从文件取堆栈
        CrashReporter.install(this)
        // 系统 SplashScreen（Android 12+ 原生，低版本兼容库）：品牌水滴 + KOiL
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // navigationevent：miuix Overlay Dropdown 弹窗的返回手势（NavigationBackHandler）
            // 依赖 LocalNavigationEventDispatcherOwner；Mishka 由根组合自举提供，
            // KOiL 在此显式提供根 dispatcher（对齐 navigationevent 1.2.0）
            val navOwner = rememberNavigationEventDispatcherOwner(true, null)
            CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides navOwner) {
                MainScreen()
            }
        }
    }
}
