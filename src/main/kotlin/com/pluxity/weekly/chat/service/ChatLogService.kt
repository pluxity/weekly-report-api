package com.pluxity.weekly.chat.service

import com.pluxity.weekly.chat.config.LlmProperties
import com.pluxity.weekly.chat.entity.ChatLog
import com.pluxity.weekly.chat.llm.dto.TokenUsage
import com.pluxity.weekly.chat.repository.ChatLogRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode

private val log = KotlinLogging.logger {}

/**
 * chat 1턴의 디버그 로그 저장 전담.
 *
 * 저장 실패가 chat 응답을 깨면 안 되므로 [record] 는 예외를 삼키고 로그만 남긴다.
 * (알림 로그와 달리 도메인/응답과의 원자성이 목적이 아니라, 흐름 격리가 목적)
 */
@Service
class ChatLogService(
    private val chatLogRepository: ChatLogRepository,
    private val llmProperties: LlmProperties,
) {
    fun record(data: ChatLogData) {
        // 단가·모델은 현재 사용 중인 openrouter 설정 기준 (저장 시점 스냅샷)
        val openrouter = llmProperties.openrouter
        val cost =
            ChatLogData.calculateCost(
                inputTokens = data.totalInputTokens,
                outputTokens = data.totalOutputTokens,
                inputPricePerMillion = openrouter.inputPricePerMillion,
                outputPricePerMillion = openrouter.outputPricePerMillion,
            )
        runCatching { chatLogRepository.save(data.toEntity(cost = cost, model = openrouter.model)) }
            .onFailure { e -> log.error(e) { "ChatLog 저장 실패 (무시): userId=${data.userId}" } }
    }
}

/**
 * processChat 진행 중 누적되는 로그 데이터. 단계별로 채워지다가 마지막에 [toEntity] 로 영속화된다.
 * OpenRouter 외 provider(Gemini/Ollama)는 아직 사용량 미추출이라 토큰·cost 가 0으로 남는다.
 */
data class ChatLogData(
    val userId: Long,
    val requestMessage: String,
    var success: Boolean = false,
    var intentResult: String? = null,
    var actionResult: String? = null,
    var errorMessage: String? = null,
    var intentInputTokens: Int = 0,
    var intentOutputTokens: Int = 0,
    var actionInputTokens: Int = 0,
    var actionOutputTokens: Int = 0,
) {
    val totalInputTokens: Int get() = intentInputTokens + actionInputTokens
    val totalOutputTokens: Int get() = intentOutputTokens + actionOutputTokens

    /** 1차(의도 추출) 결과·토큰 기록 */
    fun recordIntent(
        intentJson: String,
        usage: TokenUsage,
    ) {
        intentResult = intentJson
        intentInputTokens = usage.promptTokens
        intentOutputTokens = usage.completionTokens
    }

    /** 2차(액션 생성/weekly) 결과·토큰 기록. weekly 는 본문 미저장이라 actionJson 생략 */
    fun recordAction(
        usage: TokenUsage,
        actionJson: String? = null,
    ) {
        actionInputTokens = usage.promptTokens
        actionOutputTokens = usage.completionTokens
        actionResult = actionJson
    }

    fun toEntity(
        cost: BigDecimal,
        model: String,
    ): ChatLog =
        ChatLog(
            userId = userId,
            requestMessage = requestMessage,
            success = success,
            intentResult = intentResult,
            actionResult = actionResult,
            errorMessage = errorMessage,
            intentInputTokens = intentInputTokens,
            intentOutputTokens = intentOutputTokens,
            actionInputTokens = actionInputTokens,
            actionOutputTokens = actionOutputTokens,
            cost = cost,
            model = model,
        )

    companion object {
        private val ONE_MILLION = BigDecimal(1_000_000)

        fun calculateCost(
            inputTokens: Int,
            outputTokens: Int,
            inputPricePerMillion: BigDecimal,
            outputPricePerMillion: BigDecimal,
        ): BigDecimal =
            BigDecimal(inputTokens)
                .multiply(inputPricePerMillion)
                .add(BigDecimal(outputTokens).multiply(outputPricePerMillion))
                .divide(ONE_MILLION, 8, RoundingMode.HALF_UP)
    }
}
