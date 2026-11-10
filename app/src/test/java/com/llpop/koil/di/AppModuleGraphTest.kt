package com.llpop.koil.di

import androidx.lifecycle.ViewModel
import com.llpop.koil.data.sync.CloudSyncManager
import com.llpop.koil.data.sync.WebDavClient
import com.llpop.koil.ui.MainViewModel
import com.llpop.koil.ui.screen.add.AddViewModel
import com.llpop.koil.ui.screen.overview.OverviewViewModel
import com.llpop.koil.ui.screen.settings.OilPriceDetailViewModel
import com.llpop.koil.ui.screen.settings.SettingsViewModel
import com.llpop.koil.ui.screen.settings.SyncViewModel
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.koin.core.context.stopKoin
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Koin 容器可解析性回归（启动/切页闪退防护）。
 *
 * 背景：`singleOf(::WebDavClient)` 会让 Koin 去容器里解析构造参数 `HttpClientEngine?`——
 * 即使该参数有默认值，构造注入也不会用它，而工程从未注册该类型。
 * 结果是 SyncViewModel → CloudSyncManager → WebDavClient 整条链在建容器后**首次取用**时才失败：
 * 点「设置」崩；又因底部 Pager 设了 beyondViewportPageCount = 1，点「数据」会预组合相邻的
 * 设置页，于是「数据」也崩——两个页面同一条根因，且编译期与纯逻辑单测都发现不了。
 *
 * 这里把"容器里所有 ViewModel 与关键单例都取得到"固化成用例：
 * 依赖链上任何一环缺注册，都会在 CI 就失败，而不是等用户点页面时闪退。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = com.llpop.koil.KOiLApplication::class)
class AppModuleGraphTest {

    private val koin get() = GlobalContext.get()

    /** GlobalContext 是静态的：每个用例后必须停掉，否则下一个用例的 Application.onCreate 会重复 startKoin */
    @After
    fun tearDown() {
        runCatching { stopKoin() }
    }

    @Test
    fun `容器能创建关键单例`() {
        // WebDavClient 的 engine 参数只为单测注入 MockEngine，生产必须走无参构造；
        // 若有人改回 singleOf(::WebDavClient)，这里会先抛 NoDefinitionFoundException。
        assertTrue(koin.get<WebDavClient>() is WebDavClient)
        assertTrue(koin.get<CloudSyncManager>() is CloudSyncManager)
    }

    @Test
    fun `容器能创建全部ViewModel`() {
        // 逐个显式取：走与 koinViewModel() 相同的解析路径，依赖链缺一环即抛异常
        assertTrue(koin.get<MainViewModel>() is ViewModel)
        assertTrue(koin.get<AddViewModel>() is ViewModel)
        assertTrue(koin.get<OverviewViewModel>() is ViewModel)
        assertTrue(koin.get<SettingsViewModel>() is ViewModel)
        assertTrue(koin.get<OilPriceDetailViewModel>() is ViewModel)
        assertTrue(koin.get<SyncViewModel>() is ViewModel)
    }
}
