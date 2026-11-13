package com.llpop.koil

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.llpop.koil.data.oil.OilPriceDbSyncer
import com.llpop.koil.data.oil.OilPriceSyncWorker
import com.llpop.koil.data.oil.decideForceCheckOnLaunch
import com.llpop.koil.data.settings.AppSettings
import com.llpop.koil.data.sync.CloudSyncManager
import com.llpop.koil.data.update.UpdateChecker
import com.llpop.koil.di.appModule
import com.llpop.koil.util.CrashReporter
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.context.GlobalContext
import java.util.concurrent.TimeUnit

class KOiLApplication : Application() {

    /**
     * 本次启动是否需要绕过 8 小时节流做一次强制核验。
     *
     * 判定本身抽在 [decideForceCheckOnLaunch]（纯函数，有单测）：
     * - 已记录过 versionCode 且与当前不同 → 从有记录的版本升上来
     * - 没有记录（lastVer = 0）但库里有检测历史 → 从还没写这个字段的旧版升上来（V1.1.14 → V1.1.15）
     * - 全新安装两个条件都不成立，交给正常流程（首次灌库后本就要拉全量）
     * - **自动更新开关关着 → 一律不强制**：用户关掉就是不希望它自己联网，
     *   升级也不能替他改设置（force = true 会连开关判断一起绕过，这里必须先收口）
     */
    private suspend fun shouldForceCheckOnLaunch(): Boolean {
        val settings = GlobalContext.get().get<AppSettings>()
        val cur = BuildConfig.VERSION_CODE.toLong()
        val lastVer = runCatching { settings.lastRunVersionCode.first() }.getOrDefault(0L)
        val lastCheck = runCatching { settings.priceDbLastCheckAt.first() }.getOrDefault(0L)
        // 先落 versionCode 再判定：关掉开关的用户升级后，下次启动不该再判一次「刚升级」
        if (lastVer != cur) {
            runCatching { settings.setLastRunVersionCode(cur) }
        }
        val autoOn = runCatching { settings.priceDbAutoUpdate.first() }.getOrDefault(true)
        return decideForceCheckOnLaunch(lastVer, cur, lastCheck, autoOn)
    }

    private val appScope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, throwable ->
            Log.e("KOiL", "AppScope uncaught exception", throwable)
        }
    )

    override fun onCreate() {
        super.onCreate()
        // 全局崩溃捕获统一在 Application 最早安装（覆盖启动期崩溃）；
        // 只安装一套 handler，避免多处安装各写一份文件。
        CrashReporter.install(this)
        // 崩溃现场只做一次本地追加写，日志裁剪放到 IO 线程，避免主线程阻塞
        appScope.launch { runCatching { CrashReporter.trim(this@KOiLApplication) } }

        startKoin {
            androidContext(this@KOiLApplication)
            modules(appModule)
        }

        // 方案X：油价库首次联网灌库——先灌当前省一整年曲线（即时可用），其余省份由 seedRemainingProvinces 后台补全。
        // 链式执行：灌库完成后再做启动调价检查（避免灌库 + 检查两个网络任务并发抢带宽拖慢首屏数据就绪）。
        appScope.launch {
            try {
                val syncer = GlobalContext.get().get<OilPriceDbSyncer>()
                syncer.initPriceDb()
                // 启动时非强制检查一次（8 小时节流 + 自动更新开关在服务内部判断）。
                // 例外：刚升级过就绕过节流强制核验——新版本可能改了窗口/价格口径，
                // 沿用旧版本留下的时间戳会让首屏一直显示上一轮价格（V1.1.14 装完仍显示 8.91）。
                syncer.checkAndUpdate(force = shouldForceCheckOnLaunch())
                // 窗口日当天"今晚24点生效"提醒——同步成功后重新排期单次 Worker（00:05 精准），
                // 再走一遍 notifyPriceChanges 兜底（当天启动/日历就绪即提醒；幂等，已提醒不会重复）
                syncer.scheduleWindowDayReminder(this@KOiLApplication)
                syncer.notifyPriceChanges(this@KOiLApplication)
            } catch (t: Throwable) {
                Log.e("KOiL", "Startup price sync failed", t)
            }
        }
        appScope.launch {
            try {
                // 全国补灌重试：
                // 采用退避递增（3s→10s→30s→60s→120s→180s）＋每轮独立容错，避免固定 30s×6（约 3 分钟）
                // 且任一轮异常即终止后续重试的短预算——弱网首装常在拿到窗口日历前就耗尽预算，之后没有任何
                // 触发点，只能靠杀进程冷启动才继续灌。预算用尽转 GAVE_UP，由设置页如实提示「点立即更新重试」，
                // 并由下方的回前台兜底接手。
                val syncer = GlobalContext.get().get<OilPriceDbSyncer>()
                syncer.runSeedRetryLoop()
            } catch (t: Throwable) {
                Log.e("KOiL", "PriceDb fill remaining failed", t)
            }
        }

        // 回前台兜底——补灌放弃后（GAVE_UP）或仍在等日历（WAITING）时，
        // 用户切回 App 再试一轮即可恢复，无需杀进程冷启动。
        registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            private var startedActivities = 0

            override fun onActivityStarted(activity: Activity) {
                // 0→1 视为真正的前台切换（Activity 间跳转不会触发）
                if (startedActivities++ != 0) return
                appScope.launch {
                    runCatching {
                        GlobalContext.get().get<OilPriceDbSyncer>().resumeSeedingIfNeeded()
                    }.onFailure { Log.w("KOiL", "Foreground seeding retry failed: ${it.message}") }
                }
                // 回前台也顺手看一眼更新（同样受 45 分钟节流约束）——
                // 冷启动与回前台都查，避免 App 常驻后台几天一直看不到新版本
                appScope.launch {
                    runCatching {
                        GlobalContext.get().get<UpdateChecker>().checkIfDue()
                    }.onFailure { Log.w("KOiL", "Foreground update check failed: ${it.message}") }
                }
            }

            override fun onActivityStopped(activity: Activity) {
                startedActivities = (startedActivities - 1).coerceAtLeast(0)
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })

        // 更新检查的「已读」落库交给应用级 scope：页面销毁不该让 markSeen 写不完
        // （点开链接后立刻退出设置页时，页面 scope 会被取消，DataStore 可能没落库，红点不灭）
        GlobalContext.get().get<UpdateChecker>().init(appScope)

        // 冷启动静默检查应用更新（45min 节流，回前台也查）——查到比本机更新的 release 就把 tag 落到 DataStore，
        // 设置页「检查更新」行据此挂红点，用户不用主动点也能知道有新版本。
        // 失败（离线 / 限流 / 超时）静默吞掉，保留上一次的 tag，不打扰、不显示假状态。
        appScope.launch {
            runCatching {
                GlobalContext.get().get<UpdateChecker>().checkIfDue()
            }.onFailure { Log.w("KOiL", "Startup update check failed: ${it.message}") }
        }

        // 云同步（坚果云 WebDAV，docs/KOiL云同步设计.md）：启动接入——
        // 空库自动恢复 / 上次失败补推 / 记录与设置变更订阅推送（内部幂等，不影响主流程）
        appScope.launch {
            runCatching {
                GlobalContext.get().get<CloudSyncManager>().init(appScope)
            }.onFailure { Log.e("KOiL", "CloudSync init failed", it) }
        }

        // 油价库每 8 小时自动检测（对齐 Web koil_price_auto_sync；受开关+节流约束）
        // 带网络约束 + 指数退避——无网时不会被唤醒空跑（Worker 内部 Result.retry 也会无限重试）
        // 整段放在 IO——WorkManager.getInstance() 首次调用要初始化单例、打开自己的 Room 库，
        // 放在 onCreate 主线程等于给冷启动首帧平白加几毫秒到十几毫秒。
        appScope.launch {
            val syncRequest = PeriodicWorkRequestBuilder<OilPriceSyncWorker>(
                OilPriceDbSyncer.CHECK_INTERVAL_MS, TimeUnit.MILLISECONDS
            )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            runCatching {
                WorkManager.getInstance(this@KOiLApplication).enqueueUniquePeriodicWork(
                    "KOiL_price_auto_sync",
                    ExistingPeriodicWorkPolicy.UPDATE,
                    syncRequest,
                )
            }
        }
    }
}
