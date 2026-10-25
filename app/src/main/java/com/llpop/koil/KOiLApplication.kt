package com.llpop.koil

import android.app.Application
import android.util.Log
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.llpop.koil.data.local.AppDatabase
import com.llpop.koil.data.local.seed.SeedInitializer
import com.llpop.koil.data.oil.OilPriceDbSyncer
import com.llpop.koil.data.oil.OilPriceSyncWorker
import com.llpop.koil.data.settings.AppSettings
import com.llpop.koil.di.appModule
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
        // 全局崩溃捕获：无 logcat 环境下将异常栈写入 files/crash-*.txt
        CrashHandler.install(this)

        startKoin {
            androidContext(this@KOiLApplication)
            modules(appModule)
        }

        // 设置一次性迁移：历史 koil_settings → KOiL_settings（键前缀 koil- → KOiL-），保留全部设置。
        // 移入 IO 后台执行，避免冷启动主线程阻塞（首次迁移含文件读写）
        appScope.launch { runCatching { GlobalContext.get().get<AppSettings>().migrateLegacyIfNeeded() } }

        // 首次启动预置：仅历史加油记录（油价库已改为联网灌库，见下两段）
        appScope.launch {
            try {
                SeedInitializer.seedIfEmpty(this@KOiLApplication, AppDatabase.getInstance(this@KOiLApplication))
            } catch (t: Throwable) {
                Log.e("KOiL", "Seed init failed", t)
            }
        }

        // 方案X：油价库首次联网灌库——先灌当前省一整年曲线（即时可用），其余省份由 seedRemainingProvinces 后台补全。
        // 链式执行：灌库完成后再做启动调价检查（避免灌库 + 检查两个网络任务并发抢带宽拖慢首屏数据就绪）。
        appScope.launch {
            try {
                GlobalContext.get().get<OilPriceDbSyncer>().initPriceDb()
                // 启动时非强制检查一次（8 小时节流 + 自动更新开关在服务内部判断）
                GlobalContext.get().get<OilPriceDbSyncer>().checkAndUpdate(force = false)
            } catch (t: Throwable) {
                Log.e("KOiL", "Startup price sync failed", t)
            }
        }
        appScope.launch {
            try {
                kotlinx.coroutines.delay(3000) // 等当前省先就绪，再后台补全国（幂等：只补缺失省）
                GlobalContext.get().get<OilPriceDbSyncer>().seedRemainingProvinces()
            } catch (t: Throwable) {
                Log.e("KOiL", "PriceDb fill remaining failed", t)
            }
        }

        // 油价库每 8 小时自动检测（对齐 Web koil_price_auto_sync；受开关+节流约束）
        val syncRequest = PeriodicWorkRequestBuilder<OilPriceSyncWorker>(
            OilPriceDbSyncer.CHECK_INTERVAL_MS, TimeUnit.MILLISECONDS
        ).build()
        // 历史任务名（迁移专用）：v1.4.50 及更早为小写 koil_price_auto_sync，注销后启用新名
        runCatching { WorkManager.getInstance(this).cancelUniqueWork("koil_price_auto_sync") }
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "KOiL_price_auto_sync",
            ExistingPeriodicWorkPolicy.UPDATE,
            syncRequest,
        )
    }
}
