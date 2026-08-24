package com.pluxity.weekly.chat.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.math.BigDecimal

@ConfigurationProperties(prefix = "llm")
data class LlmProperties(
    val openrouter: OpenRouterProperties = OpenRouterProperties(),
    val temperature: Double = 0.1,
    val timeoutMs: Int = 60000,
)

data class OpenRouterProperties(
    val baseUrl: String = "https://openrouter.ai/api",
    val apiKey: String = "",
    val model: String = "",
    val inputPricePerMillion: BigDecimal = BigDecimal("0.30"),
    val outputPricePerMillion: BigDecimal = BigDecimal("2.50"),
    val siteUrl: String = "",
    val siteName: String = "",
) {
    val isEnabled: Boolean
        get() = apiKey.isNotBlank() && model.isNotBlank()
}
