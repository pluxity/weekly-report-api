package com.pluxity.weekly.chat.v2

import com.pluxity.weekly.chat.config.LlmProperties
import com.pluxity.weekly.chat.llm.dto.TokenUsage
import com.pluxity.weekly.chat.v2.dto.ToolChatRequest
import com.pluxity.weekly.chat.v2.dto.ToolChatResponse
import com.pluxity.weekly.chat.v2.dto.ToolDefinition
import com.pluxity.weekly.chat.v2.dto.ToolMessage
import com.pluxity.weekly.config.WebClientFactory
import com.pluxity.weekly.core.constant.ErrorCode
import com.pluxity.weekly.core.exception.CustomException
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono

/**
 * tool calling 전용 OpenRouter 클라이언트 (PoC).
 * LlmService.callOpenRouter와 동일한 호출이지만 tools 필드와 tool_calls 응답을 다룬다.
 * PoC라 OpenRouter 전용 — Gemini/Ollama 폴백 없음.
 */
@Component
class ChatV2LlmClient(
    private val properties: LlmProperties,
    webClientFactory: WebClientFactory,
) {
    private val client: WebClient? =
        properties.openrouter.takeIf { it.isEnabled }?.let {
            webClientFactory.createClient(
                baseUrl = it.baseUrl,
                responseTimeoutMs = properties.timeoutMs,
                readTimeoutMs = properties.timeoutMs,
            )
        }

    data class StepResult(
        val message: ToolMessage,
        val usage: TokenUsage,
    )

    fun call(
        messages: List<ToolMessage>,
        tools: List<ToolDefinition>,
    ): StepResult {
        val props = properties.openrouter
        val webClient = client ?: throw CustomException(ErrorCode.LLM_SERVICE_UNAVAILABLE)

        val response =
            webClient
                .post()
                .uri("/v1/chat/completions")
                .header("Authorization", "Bearer ${props.apiKey}")
                .header("HTTP-Referer", props.siteUrl)
                .header("X-Title", props.siteName)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(
                    ToolChatRequest(
                        model = props.model,
                        messages = messages,
                        temperature = properties.temperature,
                        tools = tools,
                    ),
                ).retrieve()
                .bodyToMono<ToolChatResponse>()
                .block()
                ?: throw CustomException(ErrorCode.LLM_INVALID_RESPONSE)

        val message =
            response.choices
                ?.firstOrNull()
                ?.message
                ?: throw CustomException(ErrorCode.LLM_INVALID_RESPONSE)
        val usage =
            response.usage
                ?.let { TokenUsage(it.promptTokens, it.completionTokens, it.totalTokens) }
                ?: TokenUsage()
        return StepResult(message, usage)
    }
}
