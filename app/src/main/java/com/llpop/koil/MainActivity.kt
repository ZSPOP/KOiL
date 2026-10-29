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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // 全局崩溃捕获统一在 KOiLApplication.onCreate 安装（v1.5.16 起，覆盖启动期崩溃）
        // 系统 SplashScreen（Android 12+ 原生，低版本兼容库）：品牌水滴 + KOiL
        installSplashScreen()
        super.onCreate(savedInstanceState)
        // 调价推送通知权限（API 33+ 运行时申请；仅首次安装请求一次，拒绝后标记记忆，
        // 不再每次启动重复无效请求——Android 13+ 拒绝后系统静默不弹框；用户想开启时经设置页开关跳系统通知设置）
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            lifecycleScope.launch {
                val requested = GlobalContext.get().get<AppSettings>().notificationPermissionRequested.first()
                if (!requested) {
                    ActivityCompat.requestPermissions(this@MainActivity, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100)
                }
            }
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
            // v1.5.36 P2：无论允许/拒绝都记录已请求（首次安装只弹一次框；拒绝则推送开关联动置关）
            val granted = grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
            runCatching {
                lifecycleScope.launch {
                    val settings = GlobalContext.get().get<AppSettings>()
                    settings.setNotificationPermissionRequested(true)
                    if (!granted) {
                        settings.setPricePush(false)
                    }
                }
            }
        }
    }
}
