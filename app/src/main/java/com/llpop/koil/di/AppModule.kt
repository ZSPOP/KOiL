package com.llpop.koil.di

import android.app.Application
import com.llpop.koil.data.local.AppDatabase
import com.llpop.koil.data.local.dao.KOiLPriceDao
import com.llpop.koil.data.local.dao.KOiLPricePendingDao
import com.llpop.koil.data.local.dao.KOiLRecordDao
import com.llpop.koil.data.location.LocationHelper
import com.llpop.koil.data.oil.CrudeOilEstimator
import com.llpop.koil.data.oil.OilPriceDbSyncer
import com.llpop.koil.data.oil.OilPriceNetwork
import com.llpop.koil.data.settings.AppSettings
import com.llpop.koil.data.sync.CloudSyncManager
import com.llpop.koil.data.sync.WebDavClient
import com.llpop.koil.ui.MainViewModel
import com.llpop.koil.ui.screen.add.AddViewModel
import com.llpop.koil.ui.screen.overview.OverviewViewModel
import com.llpop.koil.ui.screen.settings.OilPriceDetailViewModel
import com.llpop.koil.ui.screen.settings.SettingsViewModel
import com.llpop.koil.ui.screen.settings.SyncViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val appModule = module {
    // v1.6.3：androidContext() 注册的是 Context 类型；显式再注册一次 Application，
    // 让"构造参数要 Application"的 ViewModel 能被 viewModelOf 解析（类型收紧以防 Context 泄漏）
    // 用 applicationContext 转 Application：这层转换恒成立（applicationContext 必为 Application），
    // 直接 `androidContext() as Application` 依赖 startKoin 传的实例类型，写法上更脆
    single<Application> { androidContext().applicationContext as Application }
    single { AppDatabase.getInstance(androidContext()) }
    singleOf(::AppSettings)
    singleOf(::OilPriceNetwork)
    singleOf(::CrudeOilEstimator)
    singleOf(::OilPriceDbSyncer)
    singleOf(::WebDavClient)
    singleOf(::CloudSyncManager)

    single<KOiLRecordDao> { get<AppDatabase>().KOiLRecordDao() }
    single<KOiLPriceDao> { get<AppDatabase>().koilPriceDao() }
    single<KOiLPricePendingDao> { get<AppDatabase>().KOiLPricePendingDao() }
    singleOf(::LocationHelper)

    viewModelOf(::MainViewModel)
    viewModelOf(::AddViewModel)
    viewModelOf(::OverviewViewModel)
    viewModelOf(::SettingsViewModel)
    viewModelOf(::OilPriceDetailViewModel)
    viewModelOf(::SyncViewModel)
}
