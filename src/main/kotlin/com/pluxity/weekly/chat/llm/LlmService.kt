package com.pluxity.weekly.chat.llm

import com.pluxity.weekly.chat.config.LlmProperties
import com.pluxity.weekly.chat.dto.LlmAction
import com.pluxity.weekly.chat.llm.dto.IntentResult
import com.pluxity.weekly.chat.llm.dto.JsonSchemaSpec
import com.pluxity.weekly.chat.llm.dto.LlmResult
import com.pluxity.weekly.chat.llm.dto.Message
import com.pluxity.weekly.chat.llm.dto.OpenAiChatRequest
import com.pluxity.weekly.chat.llm.dto.OpenAiChatResponse
import com.pluxity.weekly.chat.llm.dto.ProviderPreferences
import com.pluxity.weekly.chat.llm.dto.ReasoningConfig
import com.pluxity.weekly.chat.llm.dto.ResponseFormat
import com.pluxity.weekly.chat.llm.dto.TokenUsage
import com.pluxity.weekly.chat.llm.dto.WeeklyReportClassifyResult
import com.pluxity.weekly.chat.llm.dto.WeeklyReportMatchResult
import com.pluxity.weekly.chat.llm.schema.ClassifySchema
import com.pluxity.weekly.config.WebClientFactory
import com.pluxity.weekly.core.constant.ErrorCode
import com.pluxity.weekly.core.exception.CustomException
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import tools.jackson.databind.ObjectMapper

private val log = KotlinLogging.logger {}

@Service
class LlmService(
    private val properties: LlmProperties,
    private val objectMapper: ObjectMapper,
    webClientFactory: WebClientFactory,
) {
    private val openRouterClient: WebClient? =
        properties.openrouter.takeIf { it.isEnabled }?.let {
            webClientFactory.createClient(
                baseUrl = it.baseUrl,
                responseTimeoutMs = properties.timeoutMs,
                readTimeoutMs = properties.timeoutMs,
            )
        }

    init {
        log.info { "LLM OpenRouter: ${properties.openrouter.isEnabled}" }
    }

    fun extractIntent(messages: List<Message>): LlmResult<IntentResult> = callWithRetry(messages, "Intent 추출", parse = ::parseIntent)

    /** answer 액션용 자연어 응답. 코드펜스 제거 후 평문 그대로 반환하고, 빈 응답은 LLM_INVALID_RESPONSE. */
    fun answerChat(messages: List<Message>): LlmResult<String> =
        callWithRetry(messages, "답변 생성") { raw ->
            stripCodeFence(raw).trim().ifBlank { throw CustomException(ErrorCode.LLM_INVALID_RESPONSE) }
        }

    fun generate(messages: List<Message>): LlmResult<List<LlmAction>> = callWithRetry(messages, "LLM 액션 생성", parse = ::parseActions)

    fun classifyWeeklyReport(messages: List<Message>): LlmResult<WeeklyReportClassifyResult> =
        callWithRetry(messages, "LLM classify", CLASSIFY_RESPONSE_FORMAT, REASONING_OFF, ::parseClassify)

    fun matchWeeklyReport(messages: List<Message>): LlmResult<WeeklyReportMatchResult> =
        callWithRetry(messages, "LLM match", parse = ::parseMatch)

    /**
     * LLM 호출 + 재시도 공통 골격. 타입별로 변하는 parse 만 주입받는다.
     * - CustomException 은 즉시 전파(재시도 안 함)
     * - 그 외 예외는 MAX_RETRIES 까지 지수 backoff 후 재시도, 모두 실패 시 LLM_SERVICE_UNAVAILABLE
     */
    private fun <T> callWithRetry(
        messages: List<Message>,
        label: String,
        responseFormat: ResponseFormat? = null,
        reasoning: ReasoningConfig? = null,
        parse: (String) -> T,
    ): LlmResult<T> {
        var lastException: Exception? = null
        repeat(MAX_RETRIES) { attempt ->
            try {
                val result = callOpenRouter(messages, responseFormat, reasoning)
                log.info { "$label 응답: ${result.value}" }
                return LlmResult(parse(result.value), result.usage)
            } catch (e: Exception) {
                // LLM_SERVICE_UNAVAILABLE 등 설정·가용성 문제는 재시도해도 소용없어 즉시 전파.
                // 그 외(LLM_INVALID_RESPONSE = 파싱 실패·빈 응답 포함)는 재샘플링으로 복구 가능 → 재시도.
                if (e is CustomException && e.code != ErrorCode.LLM_INVALID_RESPONSE) {
                    throw e
                }
                lastException = e
                log.warn { "$label 실패 (시도 ${attempt + 1}/$MAX_RETRIES): ${e.message}" }
                if (attempt < MAX_RETRIES - 1) {
                    Thread.sleep(retryBackoffMs(attempt))
                }
            }
        }
        log.error(lastException) { "$label $MAX_RETRIES 회 재시도 실패" }
        throw CustomException(ErrorCode.LLM_SERVICE_UNAVAILABLE)
    }

    private fun callOpenRouter(
        messages: List<Message>,
        responseFormat: ResponseFormat?,
        reasoning: ReasoningConfig?,
    ): LlmResult<String> {
        val props = properties.openrouter
        val request =
            OpenAiChatRequest(
                model = props.model,
                messages = messages,
                temperature = properties.temperature,
                responseFormat = responseFormat,
                provider = responseFormat?.let { ProviderPreferences() },
                reasoning = reasoning,
            )

        val client =
            openRouterClient
                ?: throw CustomException(ErrorCode.LLM_SERVICE_UNAVAILABLE)

        val response =
            client
                .post()
                .uri("/v1/chat/completions")
                .header("Authorization", "Bearer ${props.apiKey}")
                .header("HTTP-Referer", props.siteUrl)
                .header("X-Title", props.siteName)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToMono<OpenAiChatResponse>()
                .block()
                ?: throw CustomException(ErrorCode.LLM_INVALID_RESPONSE)

        val choice = response.choices?.firstOrNull()
        val content = choice?.message?.content
        // 파싱 실패 시 LlmResult 가 버려지므로, 진단 정보는 여기서 남긴다 (응답 잘림 원인 추적용)
        log.info {
            "OpenRouter id=${response.id}, finish=${choice?.finishReason}/${choice?.nativeFinishReason}, " +
                "completion=${response.usage?.completionTokens}, " +
                "reasoning=${response.usage?.completionTokensDetails?.reasoningTokens}, length=${content?.length}"
        }
        if (content == null) {
            throw CustomException(ErrorCode.LLM_INVALID_RESPONSE)
        }
        val usage =
            response.usage
                ?.let { TokenUsage(it.promptTokens, it.completionTokens, it.totalTokens) }
                ?: TokenUsage()
        return LlmResult(content, usage)
    }

    private fun parseClassify(raw: String): WeeklyReportClassifyResult =
        decodeJson(raw, "LLM classify") { objectMapper.readValue(it, WeeklyReportClassifyResult::class.java) }

    private fun parseMatch(raw: String): WeeklyReportMatchResult =
        decodeJson(raw, "LLM match") { objectMapper.readValue(it, WeeklyReportMatchResult::class.java) }

    private fun parseActions(raw: String): List<LlmAction> =
        decodeJson(raw, "LLM 액션") { json ->
            if (json.trimStart().startsWith("[")) {
                objectMapper.readValue(
                    json,
                    objectMapper.typeFactory.constructCollectionType(List::class.java, LlmAction::class.java),
                )
            } else {
                listOf(objectMapper.readValue(json, LlmAction::class.java))
            }
        }

    internal fun parseIntent(raw: String): IntentResult = decodeJson(raw, "Intent") { objectMapper.readValue(it, IntentResult::class.java) }

    /**
     * 공통 JSON 파싱 골격: 코드펜스 제거 → blank 검증 → decode. 실패 시 LLM_INVALID_RESPONSE.
     * 타입별로 변하는 decode 만 주입받는다.
     */
    private fun <T> decodeJson(
        raw: String,
        label: String,
        decode: (String) -> T,
    ): T {
        val json = stripCodeFence(raw).trim()
        if (json.isBlank()) {
            throw CustomException(ErrorCode.LLM_INVALID_RESPONSE)
        }
        return try {
            decode(json)
        } catch (e: Exception) {
            log.error(e) { "$label JSON 파싱 실패: $json" }
            throw CustomException(ErrorCode.LLM_INVALID_RESPONSE)
        }
    }

    companion object {
        /** classify 응답을 스키마 JSON 하나로 강제 — 팀별로 객체를 나눠 내보내는 출력이 불가능해진다. */
        private val CLASSIFY_RESPONSE_FORMAT =
            ResponseFormat(jsonSchema = JsonSchemaSpec(name = ClassifySchema.NAME, schema = ClassifySchema.SCHEMA))

        /** 분류·추출엔 추론이 불필요하다. 추론이 출력 예산을 먹어 본문이 잘리는 것도 막는다. */
        private val REASONING_OFF = ReasoningConfig()

        private const val MAX_RETRIES = 3
        private const val INITIAL_BACKOFF_MS = 1000L

        // 1s, 2s, 4s 지수 증가
        private fun retryBackoffMs(attempt: Int): Long = INITIAL_BACKOFF_MS shl attempt

        fun stripCodeFence(raw: String): String {
            val trimmed = raw.trim()
            if (!trimmed.startsWith("```")) return trimmed
            val lines = trimmed.lines()
            val start = 1
            val end = if (lines.last().trim() == "```") lines.size - 1 else lines.size
            return lines.subList(start, end).joinToString("\n")
        }
    }
}
