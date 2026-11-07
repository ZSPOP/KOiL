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
import com.llpop.koil.data.local.AppDatabase
import com.llpop.koil.data.oil.OilPriceDbSyncer
import com.llpop.koil.data.oil.OilPriceSyncWorker
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

    private val appScope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, throwable ->
            Log.e("KOiL", "AppScope uncaught exception", throwable)
        }
    )

    override fun onCreate() {
        super.onCreate()
        // v1.5.16：全局崩溃捕获统一在 Application 最早安装（覆盖启动期崩溃）；
        // 原 MainActivity 的重复安装已移除（原先两套 handler 各写一份文件）。
        CrashReporter.install(this)
        // v1.6.3：上次崩溃的日志在崩溃线程里只落到私有目录，这里在 IO 线程补写到「下载」目录，
        // 避免崩溃现场被 MediaStore 的 4 次跨进程 IPC 拖住、进程先死导致日志丢失
        appScope.launch { runCatching { CrashReporter.migrateAndTrim(this@KOiLApplication) } }

        startKoin {
            androidContext(this@KOiLApplication)
            modules(appModule)
        }

        // 设置一次性迁移：历史 koil_settings → KOiL_settings（键前缀 koil- → KOiL-），保留全部设置。
        // 移入 IO 后台执行，避免冷启动主线程阻塞（首次迁移含文件读写）
        appScope.launch {
            runCatching {
                val settings = GlobalContext.get().get<AppSettings>()
                // 顺序不可换：先把老文件整体迁到 KOiL_settings，再把密码单独挪进 KOiL_secret
                settings.migrateLegacyIfNeeded()
                settings.migrateSyncSecretIfNeeded()
            }
        }

        // 首次启动一次性清理（油价库已改为联网灌库，见下两段；种子预置机制已移除）
        appScope.launch {
            try {
                val settings = GlobalContext.get().get<AppSettings>()
                val db = AppDatabase.getInstance(this@KOiLApplication)
                // v1.5.16：一次性回填历史 payable=0 记录（无参考价且未手填应付的旧数据，按实付入账），仅执行一次
                if (!settings.recordPayableZeroFixed.first()) {
                    val fixed = db.KOiLRecordDao().fixZeroPayable()
                    settings.setRecordPayableZeroFixed(true)
                    Log.i("KOiL", "v1.5.16 fixed $fixed record(s) with zero payableAmount")
                }
                // v1.5.21：一次性清理——① 删 apizero 时代残留的 forecast 键（功能已彻底移除）；
                // ② 删"未来生效日"脏记录（effectiveDate > 今天，窗口日 24 点生效边界修复：先清存量再防新增）
                if (!settings.priceDbForecastCleared.first()) {
                    settings.clearLegacyPriceDbForecast()
                    Log.i("KOiL", "v1.5.21 cleared legacy price-db-forecast key")
                }
                if (!settings.priceDbWindowDayCleared.first()) {
                    val cleared = db.koilPriceDao().deleteFutureRecords(java.time.LocalDate.now().toString())
                    settings.setPriceDbWindowDayCleared(true)
                    Log.i("KOiL", "v1.5.21 cleared $cleared future-effectiveDate record(s)")
                }
                // 种子机制移除（发布前清理）：一次性物理删除 KOiL-record-seeded 键（种子功能已彻底移除，
                // 已灌入的 16 条测试记录由用户在 App 内自行删除，删除后不再复活）
                if (!settings.recordSeededCleared.first()) {
                    settings.clearLegacyRecordSeeded()
                    Log.i("KOiL", "seed mechanism removed: cleared legacy KOiL-record-seeded key")
                }
            } catch (t: Throwable) {
                Log.e("KOiL", "Seed init failed", t)
            }
        }

        // 方案X：油价库首次联网灌库——先灌当前省一整年曲线（即时可用），其余省份由 seedRemainingProvinces 后台补全。
        // 链式执行：灌库完成后再做启动调价检查（避免灌库 + 检查两个网络任务并发抢带宽拖慢首屏数据就绪）。
        appScope.launch {
            try {
                val syncer = GlobalContext.get().get<OilPriceDbSyncer>()
                syncer.initPriceDb()
                // 启动时非强制检查一次（8 小时节流 + 自动更新开关在服务内部判断）
                syncer.checkAndUpdate(force = false)
                // v1.5.27：窗口日当天"今晚24点生效"提醒——同步成功后重新排期一次性 Worker（00:05 精准），
                // 再走一遍 notifyPriceChanges 兜底（当天启动/日历就绪即提醒；幂等，已提醒不会重复）
                syncer.scheduleWindowDayReminder(this@KOiLApplication)
                syncer.notifyPriceChanges(this@KOiLApplication)
            } catch (t: Throwable) {
                Log.e("KOiL", "Startup price sync failed", t)
            }
        }
        appScope.launch {
            try {
                // 全国补灌重试（v1.6.4 重构）：
                // 原实现固定 30s×6（约 3 分钟）且任一轮异常即终止后续全部重试——弱网首装常在拿到
                // 窗口日历前就耗尽预算，之后没有任何触发点，表现为"杀进程冷启动才继续灌"。
                // 改为退避递增（3s→10s→30s→60s→120s→180s）＋每轮独立容错，预算用尽转 GAVE_UP，
                // 由设置页如实提示「点立即更新重试」，并由下方的回前台兜底接手。
                val syncer = GlobalContext.get().get<OilPriceDbSyncer>()
                syncer.runSeedRetryLoop()
            } catch (t: Throwable) {
                Log.e("KOiL", "PriceDb fill remaining failed", t)
            }
        }

        // v1.6.4：回前台兜底——补灌放弃后（GAVE_UP）或仍在等日历（WAITING）时，
        // 用户切回 App 再试一轮，不再依赖"杀进程冷启动"才能恢复。
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

        // v1.6.5：冷启动静默检查应用更新（8h 节流）——查到比本机更新的 release 就把 tag 落到 DataStore，
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
        // v1.6.2：补网络约束 + 指数退避——无网时不再被唤醒空跑（Worker 内部 Result.retry 也会无限重试）
        // v1.6.2：整段移到 IO——WorkManager.getInstance() 首次调用要初始化单例、打开自己的 Room 库，
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
            // 历史任务名（迁移专用）：v1.4.50 及更早为小写 koil_price_auto_sync，注销后启用新名
            runCatching { WorkManager.getInstance(this@KOiLApplication).cancelUniqueWork("koil_price_auto_sync") }
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
