package com.pluxity.weekly.chat.llm.dto

import com.fasterxml.jackson.annotation.JsonProperty

data class Message(
    val role: String,
    val content: String,
)

/** LLM 호출 결과 + 토큰 사용량을 함께 실어 나르는 캐리어. */
data class LlmResult<T>(
    val value: T,
    val usage: TokenUsage = TokenUsage(),
)

data class TokenUsage(
    val promptTokens: Int = 0,
    val completionTokens: Int = 0,
    val totalTokens: Int = 0,
) {
    operator fun plus(other: TokenUsage): TokenUsage =
        TokenUsage(
            promptTokens = promptTokens + other.promptTokens,
            completionTokens = completionTokens + other.completionTokens,
            totalTokens = totalTokens + other.totalTokens,
        )
}

// OpenAI 호환
data class OpenAiChatRequest(
    val model: String,
    val messages: List<Message>,
    val temperature: Double,
)

data class OpenAiChatResponse(
    val choices: List<OpenAiChoice>? = null,
    val usage: OpenAiUsage? = null,
)

data class OpenAiUsage(
    @param:JsonProperty("prompt_tokens") val promptTokens: Int = 0,
    @param:JsonProperty("completion_tokens") val completionTokens: Int = 0,
    @param:JsonProperty("total_tokens") val totalTokens: Int = 0,
)

data class OpenAiChoice(
    val message: OpenAiMessage? = null,
)

data class OpenAiMessage(
    val content: String? = null,
    val role: String? = null,
)
