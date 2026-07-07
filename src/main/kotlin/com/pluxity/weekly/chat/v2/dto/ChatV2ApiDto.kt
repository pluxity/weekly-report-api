package com.pluxity.weekly.chat.v2.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank

// ── /chat/v2 요청/응답 ──

@Schema(description = "채팅 v2 요청 (tool calling PoC)")
data class ChatV2Request(
    @field:Schema(description = "자연어 메시지", example = "AA 태스크 진행률 80으로 수정해줘")
    @field:NotBlank
    val message: String,
)

@Schema(description = "채팅 v2 응답 — 최종 자연어 답변 + tool 실행 trace")
data class ChatV2Response(
    @field:Schema(description = "모델의 최종 자연어 답변")
    val reply: String,
    @field:Schema(description = "이번 턴에 실행된 tool 호출 내역 (디버깅/평가용)")
    val steps: List<ChatV2Step> = emptyList(),
    @field:Schema(description = "이번 턴 누적 입력 토큰")
    val inputTokens: Int = 0,
    @field:Schema(description = "이번 턴 누적 출력 토큰")
    val outputTokens: Int = 0,
    @field:Schema(description = "입력 토큰 중 캐시 히트분 (implicit caching 실할인 측정용)")
    val cachedTokens: Int = 0,
)

@Schema(description = "tool 실행 1건")
data class ChatV2Step(
    val tool: String,
    val arguments: String,
    val result: String,
)

// ── OpenAI 호환 tool calling wire DTO ──
// 기존 LlmApiDto의 Message는 content 필수라 tool_calls를 실을 수 없어 v2 전용으로 분리.

@JsonInclude(JsonInclude.Include.NON_NULL)
data class ToolChatRequest(
    val model: String,
    val messages: List<ToolMessage>,
    val temperature: Double,
    val tools: List<ToolDefinition>? = null,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
data class ToolMessage(
    val role: String,
    val content: String? = null,
    @param:JsonProperty("tool_calls")
    val toolCalls: List<ToolCall>? = null,
    @param:JsonProperty("tool_call_id")
    val toolCallId: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ToolCall(
    val id: String,
    val type: String = "function",
    val function: ToolCallFunction,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ToolCallFunction(
    val name: String,
    /** OpenAI 스펙상 arguments는 JSON "문자열"로 온다 */
    val arguments: String,
)

data class ToolDefinition(
    val type: String = "function",
    val function: FunctionDefinition,
)

data class FunctionDefinition(
    val name: String,
    val description: String,
    /** JSON Schema — Map으로 직렬화 */
    val parameters: Map<String, Any>,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ToolChatResponse(
    val choices: List<ToolChoice>? = null,
    val usage: ToolUsage? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ToolChoice(
    val message: ToolMessage? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ToolUsage(
    @param:JsonProperty("prompt_tokens") val promptTokens: Int = 0,
    @param:JsonProperty("completion_tokens") val completionTokens: Int = 0,
    @param:JsonProperty("total_tokens") val totalTokens: Int = 0,
    @param:JsonProperty("prompt_tokens_details") val promptTokensDetails: PromptTokensDetails? = null,
)

/** 프롬프트 토큰 중 캐시 히트분 — implicit caching 실할인 측정용 (OpenRouter가 제공할 때만 채워짐) */
@JsonIgnoreProperties(ignoreUnknown = true)
data class PromptTokensDetails(
    @param:JsonProperty("cached_tokens") val cachedTokens: Int = 0,
)
