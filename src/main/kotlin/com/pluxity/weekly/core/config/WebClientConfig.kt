package com.pluxity.weekly.config

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.WebClient

@Configuration
@ConditionalOnClass(WebClient::class)
class WebClientConfig {
    @Bean
    fun webClientBuilder(): WebClient.Builder =
        WebClient
            .builder()
            .defaultHeaders { it.accept = listOf(MediaType.APPLICATION_JSON) }
            .codecs { configurer ->
                configurer.defaultCodecs().maxInMemorySize(1024 * 1024)
            }
}
