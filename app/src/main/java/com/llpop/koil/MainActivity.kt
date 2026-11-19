package com.llpop.koil

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.ActivityCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import com.llpop.koil.data.settings.AppSettings
import com.llpop.koil.data.update.UpdateNotifier
import com.llpop.koil.ui.MainScreen
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext

class MainActivity : ComponentActivity() {

    /**
     * 「新版本提醒」通知点进来时置 true，MainScreen 消费后清掉。
     * 放成 Compose state 而不是直接读 intent：App 已在前台/后台时点通知走的是 onNewIntent，
     * 那一次 intent 不会触发重组，只有状态变化才会。
     */
    private var openAboutRequested by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        // 全局崩溃捕获统一在 KOiLApplication.onCreate 安装（覆盖启动期崩溃）
        // 系统 SplashScreen（Android 12+ 原生，低版本兼容库）：品牌水滴 + KOiL
        installSplashScreen()
        super.onCreate(savedInstanceState)
        openAboutRequested = intent?.getBooleanExtra(UpdateNotifier.EXTRA_OPEN_ABOUT, false) == true
        if (openAboutRequested) acknowledgeUpdateNotification(intent)
        // 调价推送通知权限（API 33+ 运行时申请；仅首次安装请求一次，拒绝后标记记忆，
        // 避免每次启动重复无效请求——Android 13+ 拒绝后系统静默不弹框；用户想开启时经设置页开关跳系统通知设置）
        // minSdk 33 起 POST_NOTIFICATIONS 恒存在，无需 SDK_INT 判断
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
            // **不能**自己造根 dispatcher。
            // rememberNavigationEventDispatcherOwner(true, null) 会 new 一个与平台返回链路
            // 完全隔离的 NavigationEventDispatcher；而 activity-compose 1.12 的 BackHandler 优先注册在
            // LocalNavigationEventDispatcherOwner 上，于是页面里的 BackHandler 永远收不到返回事件，
            // 手势一路下到 Activity 直接 finish —— 这就是明细页「侧滑直接退出应用，而不是返回上一级」。
            // ComponentActivity 自己就实现 NavigationEventDispatcherOwner，且
            // getNavigationEventDispatcher() 返回的正是 OnBackPressedDispatcher 的事件管道
            // （已在 onCreate 里与 window.onBackInvokedDispatcher 对接），直接把它交给组合即可。
            CompositionLocalProvider(
                LocalNavigationEventDispatcherOwner provides this@MainActivity,
            ) {
                MainScreen(
                    openAboutOnLaunch = openAboutRequested,
                    onOpenAboutHandled = { openAboutRequested = false },
                )
            }
        }
    }

    /** 通知点击（FLAG_ACTIVITY_CLEAR_TOP + SINGLE_TOP 复用同一实例）走这里 */
    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(UpdateNotifier.EXTRA_OPEN_ABOUT, false)) {
            openAboutRequested = true
            acknowledgeUpdateNotification(intent)
        }
    }

    /** 用户点开了「有新版本」通知：记成已知晓，别在 7 天后再追一次 */
    private fun acknowledgeUpdateNotification(intent: android.content.Intent) {
        val tag = intent.getStringExtra(UpdateNotifier.EXTRA_TAG).orEmpty()
        if (tag.isBlank()) return
        lifecycleScope.launch {
            runCatching { GlobalContext.get().get<UpdateNotifier>().markAcknowledged(tag) }
        }
    }

    // 首次启动通知权限框被拒时**不改开关**。
    //
    // 以前这里会 `setNotifyEnabled(false)`：用户手快点一次「不允许」，开关被永久写成关，
    // 之后哪怕他去系统设置里把通知放开，开关仍是关的，`notifyIfNeeded` 第一条就返回 false——
    // 这条链上所有补发路径全部失效，表现为「权限都给了，还是一条通知都收不到」。
    // 设置页的开关显示本来就与权限求与（`notifyEnabled && pushPermissionGranted`），
    // 拒绝时自然显示为关，授权后自然显示为开，不需要把用户的选择写死。
    // 使用 onRequestPermissionsResult 回调，抑制 DEPRECATION
    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100) {
            // 无论允许/拒绝都记录已请求（首次安装只弹一次框；拒绝则推送开关联动置关）
            val granted = grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
            runCatching {
                lifecycleScope.launch {
                    val settings = GlobalContext.get().get<AppSettings>()
                    settings.setNotificationPermissionRequested(true)
                    // granted 只用于判定「框已弹过」，不再据此改写用户开关
                    if (!granted) {
                        Log.i("KOiL", "notification permission denied; keep notifyEnabled untouched")
                    }
                }
            }
        }
    }
}
