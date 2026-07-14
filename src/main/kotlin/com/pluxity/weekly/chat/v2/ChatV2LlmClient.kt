package com.pluxity.weekly.chat.v2

import com.pluxity.weekly.chat.config.LlmProperties
import com.pluxity.weekly.chat.llm.dto.TokenUsage
import com.pluxity.weekly.chat.v2.dto.JsonSchemaSpec
import com.pluxity.weekly.chat.v2.dto.ResponseFormat
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
 * v2 전용 OpenRouter 클라이언트 — 두 가지 호출 모양을 담당한다.
 * - [call]: tool calling (조회 루프 — tools 전송, tool_calls 응답)
 * - [callStructured]: structured output (주간보고 생성 — response_format: json_schema로
 *   스키마를 프로바이더가 강제, fence/다중블록 파싱 실패 원천 차단)
 * OpenRouter 전용 — 이 경로는 Gemini/Ollama 폴백 없음 (provider 락 감수, 결정 문서 참고).
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
        /** 입력 토큰 중 캐시 히트분 (제공사가 안 주면 0) */
        val cachedTokens: Int = 0,
    )

    data class StructuredResult(
        /** 스키마를 만족하는 JSON 문자열 (프로바이더 강제) */
        val content: String,
        val usage: TokenUsage,
    )

    fun call(
        messages: List<ToolMessage>,
        tools: List<ToolDefinition>,
    ): StepResult {
        val response = send(ToolChatRequest(model = model(), messages = messages, temperature = properties.temperature, tools = tools))
        val message = response.firstMessage()
        return StepResult(message, response.toUsage(), response.usage?.promptTokensDetails?.cachedTokens ?: 0)
    }

    fun callStructured(
        messages: List<ToolMessage>,
        schemaName: String,
        schema: Map<String, Any>,
    ): StructuredResult {
        val response =
            send(
                ToolChatRequest(
                    model = model(),
                    messages = messages,
                    temperature = properties.temperature,
                    responseFormat = ResponseFormat(jsonSchema = JsonSchemaSpec(name = schemaName, schema = schema)),
                ),
            )
        val content =
            response
                .firstMessage()
                .content
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: throw CustomException(ErrorCode.LLM_INVALID_RESPONSE)
        return StructuredResult(content, response.toUsage())
    }

    private fun model(): String = properties.openrouter.model

    private fun send(request: ToolChatRequest): ToolChatResponse {
        val props = properties.openrouter
        val webClient = client ?: throw CustomException(ErrorCode.LLM_SERVICE_UNAVAILABLE)
        return webClient
            .post()
            .uri("/v1/chat/completions")
            .header("Authorization", "Bearer ${props.apiKey}")
            .header("HTTP-Referer", props.siteUrl)
            .header("X-Title", props.siteName)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .retrieve()
            .bodyToMono<ToolChatResponse>()
            .block()
            ?: throw CustomException(ErrorCode.LLM_INVALID_RESPONSE)
    }

    private fun ToolChatResponse.firstMessage(): ToolMessage =
        choices?.firstOrNull()?.message ?: throw CustomException(ErrorCode.LLM_INVALID_RESPONSE)

    private fun ToolChatResponse.toUsage(): TokenUsage =
        usage?.let { TokenUsage(it.promptTokens, it.completionTokens, it.totalTokens) } ?: TokenUsage()
}
