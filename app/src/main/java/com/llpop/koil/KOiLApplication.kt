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
        // v1.5.16：全局崩溃捕获统一在 Application 最早安装（覆盖启动期崩溃），写私有目录 + Download/KOiL-crash.txt；
        // 原 MainActivity 的重复安装已移除（原先两套 handler 各写一份文件）。
        CrashReporter.install(this)

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
                val settings = GlobalContext.get().get<AppSettings>()
                val db = AppDatabase.getInstance(this@KOiLApplication)
                SeedInitializer.seedIfEmpty(this@KOiLApplication, db, settings)
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
                // v1.5.26：全国补灌重试循环——窗口日历未就绪（锚点链启动瞬间失败、重装后缓存为空）时
                // 30 秒轮询重试，最多 6 次（约 3 分钟内自愈）；就绪即灌全国，无需手动点「立即更新」
                val syncer = GlobalContext.get().get<OilPriceDbSyncer>()
                repeat(6) { attempt ->
                    kotlinx.coroutines.delay(if (attempt == 0) 3000 else 30_000) // 首轮等当前省先就绪
                    if (syncer.fillRemainingProvincesIfCalendarReady()) return@repeat
                }
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
