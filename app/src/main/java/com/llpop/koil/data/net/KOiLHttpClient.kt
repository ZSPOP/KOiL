package com.llpop.koil.data.net

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * 全 App 唯一的 Ktor client 构造入口。
 *
 * ## 为什么要收这一层
 *
 * 之前四个网络模块（定位、汇率、油价、WebDAV）各自 `HttpClient { install(HttpTimeout) … }`，
 * 同一段超时配置写了四遍，且四个模块的超时口径本来就不同（4s/6s/8s/15s 混在各自的常量里）。重复
 * 本身不算错，错在**改超时策略要改四处**，漏一处就成了"看起来统一、实际不统一"。
 *
 * 收敛后：超时只从 [create] 的入参进来，参数仍由各模块自己的常量给出——口径依旧按业务分档，
 * 但写法只有一个。
 *
 * ## 没有做成"全 App 共用一个 client"
 *
 * 那需要把每个模块的超时改成请求级 `timeout { }`，四个模块的构造签名与九处单测注入点都要跟着改，
 * 而收益只是省掉几个空闲连接池——本 App 的并发请求是"油价 / 汇率 / 同步 / 定位各一路"级别，
 * 命中不了连接复用。风险大过收益，故这一轮只统一写法，不合并实例。
 *
 * ## engine 参数
 *
 * 仅用于单测注入 `MockEngine`；生产一律不传，走 Ktor 默认引擎。各模块保留这个可空参数，
 * 是因为测试要拿真实 client 行为而不是手工构造假响应。
 */
object KOiLHttpClient {

    /**
     * 构造一个带统一超时的 client。
     *
     * @param timeoutMs 请求 / 连接 / 套接字三个超时都用这一个值——四个模块原本就是三档取同一数，
     *   拆开只会让人以为它们分别可调。
     * @param engine 仅单测注入，生产不传。
     * @param json 需要 `ContentNegotiation` 的模块传它；传 null 则只装超时，响应体由调用方自己解。
     */
    fun create(
        timeoutMs: Long,
        engine: HttpClientEngine? = null,
        json: Json? = null,
    ): HttpClient {
        val config: HttpClientConfig<*>.() -> Unit = {
            install(HttpTimeout) {
                requestTimeoutMillis = timeoutMs
                connectTimeoutMillis = timeoutMs
                socketTimeoutMillis = timeoutMs
            }
            if (json != null) {
                install(ContentNegotiation) { json(json) }
            }
        }
        return if (engine == null) HttpClient(config) else HttpClient(engine, config)
    }
}
