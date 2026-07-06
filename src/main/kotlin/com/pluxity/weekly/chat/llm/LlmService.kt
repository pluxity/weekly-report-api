package com.pluxity.weekly.chat.llm

import com.pluxity.weekly.chat.config.LlmProperties
import com.pluxity.weekly.chat.dto.LlmAction
import com.pluxity.weekly.chat.llm.dto.GeminiContent
import com.pluxity.weekly.chat.llm.dto.GeminiGenerationConfig
import com.pluxity.weekly.chat.llm.dto.GeminiPart
import com.pluxity.weekly.chat.llm.dto.GeminiRequest
import com.pluxity.weekly.chat.llm.dto.GeminiResponse
import com.pluxity.weekly.chat.llm.dto.IntentResult
import com.pluxity.weekly.chat.llm.dto.LlmResult
import com.pluxity.weekly.chat.llm.dto.Message
import com.pluxity.weekly.chat.llm.dto.OllamaChatRequest
import com.pluxity.weekly.chat.llm.dto.OllamaChatResponse
import com.pluxity.weekly.chat.llm.dto.OllamaOptions
import com.pluxity.weekly.chat.llm.dto.OpenAiChatRequest
import com.pluxity.weekly.chat.llm.dto.OpenAiChatResponse
import com.pluxity.weekly.chat.llm.dto.TokenUsage
import com.pluxity.weekly.chat.llm.dto.WeeklyReportClassifyResult
import com.pluxity.weekly.chat.llm.dto.WeeklyReportMatchResult
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
    private val ollamaClient: WebClient? =
        properties.ollama.takeIf { it.isEnabled }?.let {
            webClientFactory.createClient(
                baseUrl = it.baseUrl,
                responseTimeoutMs = properties.timeoutMs,
                readTimeoutMs = properties.timeoutMs,
            )
        }

    private val geminiClient: WebClient? =
        properties.gemini.takeIf { it.isEnabled }?.let {
            webClientFactory.createClient(
                baseUrl = "https://generativelanguage.googleapis.com",
                responseTimeoutMs = properties.timeoutMs,
                readTimeoutMs = properties.timeoutMs,
            )
        }

    private val openRouterClient: WebClient? =
        properties.openrouter.takeIf { it.isEnabled }?.let {
            webClientFactory.createClient(
                baseUrl = it.baseUrl,
                responseTimeoutMs = properties.timeoutMs,
                readTimeoutMs = properties.timeoutMs,
            )
        }

    init {
        log.info {
            "LLM Ollama: ${properties.ollama.isEnabled}, Gemini: ${properties.gemini.isEnabled}, " +
                "OpenRouter: ${properties.openrouter.isEnabled}"
        }
    }

    fun extractIntent(messages: List<Message>): LlmResult<IntentResult> = callWithRetry(messages, "Intent 추출", ::parseIntent)

    /** answer 액션용 자연어 응답. JSON 파싱 없이 평문 그대로 반환한다. */
    fun answerChat(messages: List<Message>): LlmResult<String> = callWithRetry(messages, "답변 생성") { it.trim() }

    fun generate(messages: List<Message>): LlmResult<List<LlmAction>> = callWithRetry(messages, "LLM 액션 생성", ::parseActions)

    fun classifyWeeklyReport(messages: List<Message>): LlmResult<WeeklyReportClassifyResult> =
        callWithRetry(messages, "LLM classify", ::parseClassify)

    fun matchWeeklyReport(messages: List<Message>): LlmResult<WeeklyReportMatchResult> = callWithRetry(messages, "LLM match", ::parseMatch)

    /**
     * LLM 호출 + 재시도 공통 골격. 타입별로 변하는 parse 만 주입받는다.
     * - CustomException 은 즉시 전파(재시도 안 함)
     * - 그 외 예외는 MAX_RETRIES 까지 지수 backoff 후 재시도, 모두 실패 시 LLM_SERVICE_UNAVAILABLE
     */
    private fun <T> callWithRetry(
        messages: List<Message>,
        label: String,
        parse: (String) -> T,
    ): LlmResult<T> {
        var lastException: Exception? = null
        repeat(MAX_RETRIES) { attempt ->
            try {
                val result = callLlm(messages)
                log.info { "$label 응답: ${result.value}" }
                return LlmResult(parse(result.value), result.usage)
            } catch (e: CustomException) {
                throw e
            } catch (e: Exception) {
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

    private fun callLlm(messages: List<Message>): LlmResult<String> =
        when {
            properties.openrouter.isEnabled -> callOpenRouter(messages)
            properties.gemini.isEnabled -> callGemini(messages)
            properties.ollama.isEnabled -> callOllama(messages)
            else -> throw CustomException(ErrorCode.LLM_SERVICE_UNAVAILABLE)
        }

    private fun callOpenRouter(messages: List<Message>): LlmResult<String> {
        val props = properties.openrouter
        val request =
            OpenAiChatRequest(
                model = props.model,
                messages = messages,
                temperature = properties.temperature,
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

        val content =
            response.choices
                ?.firstOrNull()
                ?.message
                ?.content
                ?: throw CustomException(ErrorCode.LLM_INVALID_RESPONSE)
        val usage =
            response.usage
                ?.let { TokenUsage(it.promptTokens, it.completionTokens, it.totalTokens) }
                ?: TokenUsage()
        return LlmResult(content, usage)
    }

    private fun callOllama(messages: List<Message>): LlmResult<String> {
        val props = properties.ollama
        val request =
            OllamaChatRequest(
                model = props.model,
                messages = messages,
                stream = false,
                options = OllamaOptions(temperature = properties.temperature),
            )

        val client =
            ollamaClient
                ?: throw CustomException(ErrorCode.LLM_SERVICE_UNAVAILABLE)

        val response =
            client
                .post()
                .uri("/api/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToMono<OllamaChatResponse>()
                .block()
                ?: throw CustomException(ErrorCode.LLM_INVALID_RESPONSE)

        val content =
            response.message?.content
                ?: throw CustomException(ErrorCode.LLM_INVALID_RESPONSE)
        // TODO: Ollama 토큰 사용량(prompt_eval_count/eval_count) 추출 — 현재는 OpenRouter만 실값
        return LlmResult(content)
    }

    private fun callGemini(messages: List<Message>): LlmResult<String> {
        val props = properties.gemini
        val systemMessage = messages.firstOrNull { it.role == "system" }
        val userMessages = messages.filter { it.role != "system" }

        val request =
            GeminiRequest(
                systemInstruction =
                    systemMessage?.let {
                        GeminiContent(parts = listOf(GeminiPart(text = it.content)))
                    },
                contents =
                    userMessages.map {
                        GeminiContent(
                            role = "user",
                            parts = listOf(GeminiPart(text = it.content)),
                        )
                    },
                generationConfig = GeminiGenerationConfig(temperature = properties.temperature),
            )

        val client =
            geminiClient
                ?: throw CustomException(ErrorCode.LLM_SERVICE_UNAVAILABLE)

        val response =
            client
                .post()
                .uri("/v1beta/models/${props.model}:generateContent")
                .header("x-goog-api-key", props.apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToMono<GeminiResponse>()
                .block()
                ?: throw CustomException(ErrorCode.LLM_INVALID_RESPONSE)

        val content =
            response
                .candidates
                ?.firstOrNull()
                ?.content
                ?.parts
                ?.firstOrNull()
                ?.text
                ?: throw CustomException(ErrorCode.LLM_INVALID_RESPONSE)
        // TODO: Gemini 토큰 사용량(usageMetadata) 추출 — 현재는 OpenRouter만 실값
        return LlmResult(content)
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
