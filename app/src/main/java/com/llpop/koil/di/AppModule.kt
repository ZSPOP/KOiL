package com.llpop.koil.di

import com.llpop.koil.data.local.AppDatabase
import com.llpop.koil.data.local.dao.FuelPriceDao
import com.llpop.koil.data.local.dao.KOiLPricePendingDao
import com.llpop.koil.data.local.dao.KOiLRecordDao
import com.llpop.koil.data.location.LocationHelper
import com.llpop.koil.data.oil.OilPriceDbSyncer
import com.llpop.koil.data.oil.OilPriceNetwork
import com.llpop.koil.data.settings.AppSettings
import com.llpop.koil.ui.MainViewModel
import com.llpop.koil.ui.screen.add.AddViewModel
import com.llpop.koil.ui.screen.overview.OverviewViewModel
import com.llpop.koil.ui.screen.settings.SettingsViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val appModule = module {
    single { AppDatabase.getInstance(androidContext()) }
    singleOf(::AppSettings)
    singleOf(::OilPriceNetwork)
    singleOf(::OilPriceDbSyncer)

    single<KOiLRecordDao> { get<AppDatabase>().KOiLRecordDao() }
    single<FuelPriceDao> { get<AppDatabase>().fuelPriceDao() }
    single<KOiLPricePendingDao> { get<AppDatabase>().KOiLPricePendingDao() }
    singleOf(::LocationHelper)

    viewModelOf(::MainViewModel)
    viewModelOf(::AddViewModel)
    viewModelOf(::OverviewViewModel)
    viewModelOf(::SettingsViewModel)
}
