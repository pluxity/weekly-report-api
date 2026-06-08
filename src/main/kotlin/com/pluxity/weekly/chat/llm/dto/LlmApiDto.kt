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

// Ollama
data class OllamaChatRequest(
    val model: String,
    val messages: List<Message>,
    val stream: Boolean,
    val options: OllamaOptions,
)

data class OllamaOptions(
    val temperature: Double,
)

data class OllamaChatResponse(
    val message: OllamaMessage? = null,
)

data class OllamaMessage(
    val content: String? = null,
    val role: String? = null,
)

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

// Gemini
data class GeminiRequest(
    val contents: List<GeminiContent>,
    val systemInstruction: GeminiContent? = null,
    val generationConfig: GeminiGenerationConfig? = null,
)

data class GeminiContent(
    val role: String? = null,
    val parts: List<GeminiPart>,
)

data class GeminiPart(
    val text: String,
)

data class GeminiGenerationConfig(
    val temperature: Double,
    val responseMimeType: String = "application/json",
    val thinkingConfig: GeminiThinkingConfig = GeminiThinkingConfig(),
)

data class GeminiThinkingConfig(
    // gemini-2.5-flash: 0 = thinking 비활성. 분류·추출 작업엔 추론이 불필요하고, 추론 토큰 제거로 지연 대폭 감소.
    val thinkingBudget: Int = 0,
)

data class GeminiResponse(
    val candidates: List<GeminiCandidate>? = null,
)

data class GeminiCandidate(
    val content: GeminiContent? = null,
)
