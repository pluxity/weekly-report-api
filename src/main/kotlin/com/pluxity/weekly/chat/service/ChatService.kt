package com.pluxity.weekly.chat.service

import com.pluxity.weekly.auth.authorization.AuthorizationService
import com.pluxity.weekly.chat.context.ContextBuilder
import com.pluxity.weekly.chat.dto.ChatActionResponse
import com.pluxity.weekly.chat.dto.ChatActionType
import com.pluxity.weekly.chat.dto.ChatTarget
import com.pluxity.weekly.chat.llm.LlmService
import com.pluxity.weekly.core.constant.ErrorCode
import com.pluxity.weekly.core.exception.CustomException
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.core.io.ClassPathResource
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.script.RedisScript
import org.springframework.stereotype.Service
import tools.jackson.databind.ObjectMapper
import java.time.Duration
import java.util.UUID

private val log = KotlinLogging.logger {}

@Service
class ChatService(
    private val llmService: LlmService,
    private val promptBuilder: ChatPromptBuilder,
    private val contextBuilder: ContextBuilder,
    private val chatActionRouter: ChatActionRouter,
    private val chatHistoryStore: ChatHistoryStore,
    private val objectMapper: ObjectMapper,
    private val redisTemplate: RedisTemplate<String, Any>,
    private val authorizationService: AuthorizationService,
    private val weeklyReportChatHandler: com.pluxity.weekly.report.service.WeeklyReportChatHandler,
    private val chatLogService: ChatLogService,
) {
    companion object {
        private val RELEASE_LOCK_SCRIPT =
            RedisScript.of(
                ClassPathResource("scripts/release-lock.lua"),
                Long::class.java,
            )
    }

    fun chat(message: String): List<ChatActionResponse> {
        val userId = authorizationService.currentUser().requiredId
        val lockKey = "chat:lock:$userId"
        val lockValue = UUID.randomUUID().toString()

        val acquired = redisTemplate.opsForValue().setIfAbsent(lockKey, lockValue, Duration.ofSeconds(30))
        if (acquired != true) {
            throw CustomException(ErrorCode.CHAT_ALREADY_PROCESSING)
        }

        try {
            return processChat(message, userId)
        } finally {
            redisTemplate.execute(RELEASE_LOCK_SCRIPT, listOf(lockKey), lockValue)
        }
    }

    private fun processChat(
        message: String,
        userId: Long,
    ): List<ChatActionResponse> {
        val userKey = userId.toString()
        // 단계별로 채워 finally 에서 1회 저장 (디버깅용 로그)
        val logData = ChatLogData(userId = userId, requestMessage = message)

        try {
            // 히스토리 로드
            val history = chatHistoryStore.load(userKey)
            if (history.isNotEmpty()) {
                log.info { "히스토리 (${history.size}건):\n${history.joinToString("\n") { it.content }}" }
            }

            // 1차: 의도 추출 (히스토리 포함)
            val intentMessages = promptBuilder.buildIntentMessages(message, history)
            val intentResult = llmService.extractIntent(intentMessages)
            val intent = intentResult.value
            logData.intentResult = objectMapper.writeValueAsString(intent)
            logData.intentInputTokens = intentResult.usage.promptTokens
            logData.intentOutputTokens = intentResult.usage.completionTokens
            log.info { "1차 의도 추출 - action: ${intent.action}, target: ${intent.target}, id: ${intent.id}, response: $intent" }

            // target별+action별+권한별 context 조회
            val targetType = ChatTarget.fromOrNull(intent.target) ?: ChatTarget.TASK
            val actionType = ChatActionType.fromOrNull(intent.action)
            val context = contextBuilder.build(targetType, actionType)
            log.info { "context:\n${objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(objectMapper.readTree(context))}" }

            // weekly_report는 일반 LlmAction 흐름을 우회하고 별도 핸들러로
            val responses =
                if (targetType == ChatTarget.WEEKLY_REPORT) {
                    val weeklyResult = weeklyReportChatHandler.handle(intent, message, context)
                    logData.actionInputTokens = weeklyResult.usage.promptTokens
                    logData.actionOutputTokens = weeklyResult.usage.completionTokens
                    weeklyResult.value
                } else {
                    // 2차: LlmAction 생성
                    val actionMessages = promptBuilder.buildActionMessages(message, intent, context)
                    val actionResult = llmService.generate(actionMessages)
                    logData.actionInputTokens = actionResult.usage.promptTokens
                    logData.actionOutputTokens = actionResult.usage.completionTokens
                    val actions = actionResult.value.take(1)
                    logData.actionResult = objectMapper.writeValueAsString(actions)
                    log.info { "LLM 응답 액션:\n${objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(actions)}" }

                    // LlmAction → ChatActionResponse 변환
                    actions.map { chatActionRouter.route(it) }
                }

            if (targetType != ChatTarget.WEEKLY_REPORT) {
                chatHistoryStore.recordChatTurn(userKey, message, targetType.key, actionType?.key, responses)
            }

            logData.success = true
            return responses
        } catch (e: Exception) {
            logData.success = false
            logData.errorMessage = e.message
            throw e
        } finally {
            chatLogService.record(logData)
        }
    }
}
