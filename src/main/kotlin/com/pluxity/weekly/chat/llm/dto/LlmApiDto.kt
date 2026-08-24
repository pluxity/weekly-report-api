package com.pluxity.weekly.chat.llm.dto

import com.fasterxml.jackson.annotation.JsonInclude
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
@JsonInclude(JsonInclude.Include.NON_NULL)
data class OpenAiChatRequest(
    val model: String,
    val messages: List<Message>,
    val temperature: Double,
    @param:JsonProperty("response_format")
    val responseFormat: ResponseFormat? = null,
    val provider: ProviderPreferences? = null,
)

/** 응답을 스키마에 맞는 JSON 하나로 강제한다 (OpenAI 호환 structured outputs). */
data class ResponseFormat(
    val type: String = "json_schema",
    @param:JsonProperty("json_schema")
    val jsonSchema: JsonSchemaSpec,
)

data class JsonSchemaSpec(
    val name: String,
    val strict: Boolean = true,
    val schema: Map<String, Any>,
)

/** 스키마를 지원하지 않는 엔드포인트로 라우팅되는 것을 막는다. */
data class ProviderPreferences(
    @param:JsonProperty("require_parameters")
    val requireParameters: Boolean = true,
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
