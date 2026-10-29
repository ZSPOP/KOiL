package com.llpop.koil

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.core.app.ActivityCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.rememberNavigationEventDispatcherOwner
import com.llpop.koil.data.settings.AppSettings
import com.llpop.koil.ui.MainScreen
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // 全局崩溃捕获统一在 KOiLApplication.onCreate 安装（v1.5.16 起，覆盖启动期崩溃）
        // 系统 SplashScreen（Android 12+ 原生，低版本兼容库）：品牌水滴 + KOiL
        installSplashScreen()
        super.onCreate(savedInstanceState)
        // 调价推送通知权限（API 33+ 运行时申请；拒绝则通知静默跳过，可在系统设置重开）
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100)
        }
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

    // v1.5.28：首次启动通知权限框被拒 → 推送开关联动置关（设置页开关恒以系统权限状态为准，
    // 用户想开时点开关会再次请求权限）
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100) {
            val granted = grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                runCatching {
                    lifecycleScope.launch {
                        GlobalContext.get().get<AppSettings>().setPricePush(false)
                    }
                }
            }
        }
    }
}
