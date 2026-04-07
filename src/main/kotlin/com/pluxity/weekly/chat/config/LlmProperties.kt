package com.pluxity.weekly.chat.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "llm")
data class LlmProperties(
    val ollama: OllamaProperties = OllamaProperties(),
    val gemini: GeminiProperties = GeminiProperties(),
    val openrouter: OpenRouterProperties = OpenRouterProperties(),
    val temperature: Double = 0.1,
    val timeoutMs: Int = 60000,
)

data class OllamaProperties(
    val baseUrl: String = "",
    val model: String = "",
) {
    val isEnabled: Boolean
        get() = baseUrl.isNotBlank() && model.isNotBlank()
}

data class GeminiProperties(
    val apiKey: String = "",
    val model: String = "",
) {
    val isEnabled: Boolean
        get() = apiKey.isNotBlank() && model.isNotBlank()
}

data class OpenRouterProperties(
    val baseUrl: String = "https://openrouter.ai/api",
    val apiKey: String = "",
    val model: String = "",
) {
    val isEnabled: Boolean
        get() = apiKey.isNotBlank() && model.isNotBlank()
}
