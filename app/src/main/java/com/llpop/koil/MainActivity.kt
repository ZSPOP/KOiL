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
        // minSdk 33 起 POST_NOTIFICATIONS 恒存在，无需 SDK_INT 判断（v1.6.3 清理死分支）
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            lifecycleScope.launch {
                val requested = GlobalContext.get().get<AppSettings>().notificationPermissionRequested.first()
                if (!requested) {
                    ActivityCompat.requestPermissions(this@MainActivity, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100)
                }
            }
        }
        enableEdgeToEdge()
        setContent {
            // v1.6.7：**不能**自己造根 dispatcher。
            // 旧写法 rememberNavigationEventDispatcherOwner(true, null) 会 new 一个与平台返回链路
            // 完全隔离的 NavigationEventDispatcher；而 activity-compose 1.12 的 BackHandler 优先注册在
            // LocalNavigationEventDispatcherOwner 上，于是页面里的 BackHandler 永远收不到返回事件，
            // 手势一路下到 Activity 直接 finish —— 这就是明细页「侧滑直接退出应用，而不是返回上一级」。
            // ComponentActivity 自己就实现 NavigationEventDispatcherOwner，且
            // getNavigationEventDispatcher() 返回的正是 OnBackPressedDispatcher 的事件管道
            // （已在 onCreate 里与 window.onBackInvokedDispatcher 对接），直接把它交给组合即可。
            CompositionLocalProvider(
                LocalNavigationEventDispatcherOwner provides this@MainActivity,
            ) {
                MainScreen()
            }
        }
    }

    // v1.5.28：首次启动通知权限框被拒 → 推送开关联动置关（设置页开关恒以系统权限状态为准，
    // 用户想开时点开关会再次请求权限）
    // v1.5.46：保留兼容旧权限回调路径，抑制 DEPRECATION（迁移 Activity Result API 属后续工程项）
    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
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
