package com.pluxity.weekly.config

import io.github.oshai.kotlinlogging.KotlinLogging
import io.netty.channel.ChannelOption
import io.netty.handler.timeout.ReadTimeoutHandler
import io.netty.handler.timeout.WriteTimeoutHandler
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientRequestException
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.netty.http.client.HttpClient
import java.time.Duration

private val log = KotlinLogging.logger {}

@Component
@ConditionalOnClass(WebClient::class)
class WebClientFactory {
    companion object {
        private const val DEFAULT_MAX_IN_MEMORY_SIZE = 50 * 1024 * 1024 // 50MB
    }

    fun createClient(
        baseUrl: String,
        connectionTimeoutMs: Int = 5000,
        responseTimeoutMs: Int = 30000,
        readTimeoutMs: Int = 30000,
        maxInMemorySize: Int = DEFAULT_MAX_IN_MEMORY_SIZE,
    ): WebClient {
        val httpClient =
            HttpClient
                .create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectionTimeoutMs)
                .responseTimeout(Duration.ofMillis(responseTimeoutMs.toLong()))
                .doOnConnected { conn ->
                    conn
                        .addHandlerLast(ReadTimeoutHandler(readTimeoutMs / 1000))
                        .addHandlerLast(WriteTimeoutHandler(readTimeoutMs / 1000))
                }

        return WebClient
            .builder()
            .baseUrl(baseUrl)
            .clientConnector(ReactorClientHttpConnector(httpClient))
            .codecs { it.defaultCodecs().maxInMemorySize(maxInMemorySize) }
            .filter { request, next ->
                next
                    .exchange(request)
                    .onErrorMap { error ->
                        when (error) {
                            is WebClientResponseException -> {
                                log.error {
                                    "HTTP 에러 응답 - Status: ${error.statusCode}, Body: ${error.responseBodyAsString}, URL: ${request.url()}"
                                }
                                error
                            }

                            is WebClientRequestException -> {
                                val cause = error.cause?.let { "${it::class.simpleName}: ${it.message ?: ""}" } ?: error.message
                                log.error(error) {
                                    "WebClient 요청 실패 - URL: ${request.url()}, 원인: $cause"
                                }
                                error
                            }

                            else -> {
                                log.error(error) {
                                    "예상치 못한 에러 - URL: ${request.url()}, 원인: ${error.message}"
                                }
                                error
                            }
                        }
                    }
            }.build()
    }
}
