package com.pluxity.weekly.chat.service

import com.pluxity.weekly.auth.authorization.AuthorizationService
import com.pluxity.weekly.chat.context.ContextBuilder
import com.pluxity.weekly.chat.dto.ChatActionResponse
import com.pluxity.weekly.chat.dto.ChatActionType
import com.pluxity.weekly.chat.dto.ChatTarget
import com.pluxity.weekly.chat.llm.LlmService
import com.pluxity.weekly.chat.llm.dto.IntentResult
import com.pluxity.weekly.core.constant.ErrorCode
import com.pluxity.weekly.core.exception.CustomException
import com.pluxity.weekly.report.service.WeeklyReportChatHandler
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
    private val weeklyReportChatHandler: WeeklyReportChatHandler,
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
    ): List<ChatActionResponse> =
        withChatLog(userId, message) { logData ->
            val userKey = userId.toString()

            // 1차: 의도 추출
            val intent = resolveIntent(message, userKey, logData)

            // target별+action별+권한별 context 조회
            val targetType = ChatTarget.fromOrNull(intent.target) ?: ChatTarget.TASK
            val actionType = ChatActionType.fromOrNull(intent.action)
            val context = contextBuilder.build(targetType, actionType)
            log.info { "context:\n${objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(objectMapper.readTree(context))}" }

            // 2차: weekly_report는 별도 핸들러로 우회, 그 외는 일반 LlmAction 흐름 (프롬프트가 다름)
            val responses =
                if (targetType == ChatTarget.WEEKLY_REPORT) {
                    runWeeklyReport(intent, message, context, logData)
                } else {
                    generateActions(intent, message, context, logData)
                }

            // weekly_report 턴은 history에 기록하지 않음
            if (targetType != ChatTarget.WEEKLY_REPORT) {
                chatHistoryStore.recordChatTurn(userKey, message, targetType.key, actionType?.key, responses)
            }
            responses
        }

    /**
     * chat 1턴 처리를 [ChatLogData] 수집으로 감싸는 골격.
     * 성공/실패와 무관하게 finally 에서 1회 기록하고, 저장 실패는 [ChatLogService.record] 가 삼킨다.
     */
    private inline fun withChatLog(
        userId: Long,
        message: String,
        block: (ChatLogData) -> List<ChatActionResponse>,
    ): List<ChatActionResponse> {
        val logData = ChatLogData(userId = userId, requestMessage = message)
        return try {
            block(logData).also { logData.success = true }
        } catch (e: Exception) {
            logData.success = false
            logData.errorMessage = e.message
            throw e
        } finally {
            chatLogService.record(logData)
        }
    }

    private fun loadHistory(userKey: String) =
        chatHistoryStore.load(userKey).also { history ->
            if (history.isNotEmpty()) {
                log.info { "히스토리 (${history.size}건):\n${history.joinToString("\n") { it.content }}" }
            }
        }

    private fun resolveIntent(
        message: String,
        userKey: String,
        logData: ChatLogData,
    ): IntentResult {
        val history = loadHistory(userKey)
        val intentMessages = promptBuilder.buildIntentMessages(message, history)
        val result = llmService.extractIntent(intentMessages)
        val intent = result.value
        logData.recordIntent(objectMapper.writeValueAsString(intent), result.usage)
        log.info { "1차 의도 추출 - action: ${intent.action}, target: ${intent.target}, id: ${intent.id}, response: $intent" }
        return intent
    }

    private fun generateActions(
        intent: IntentResult,
        message: String,
        context: String,
        logData: ChatLogData,
    ): List<ChatActionResponse> {
        val actionMessages = promptBuilder.buildActionMessages(message, intent, context)
        val result = llmService.generate(actionMessages)
        val actions = result.value.take(1)
        logData.recordAction(result.usage, objectMapper.writeValueAsString(actions))
        log.info { "LLM 응답 액션:\n${objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(actions)}" }
        return actions.map { chatActionRouter.route(it) }
    }

    private fun runWeeklyReport(
        intent: IntentResult,
        message: String,
        context: String,
        logData: ChatLogData,
    ): List<ChatActionResponse> {
        val result = weeklyReportChatHandler.handle(intent, message, context)
        logData.recordAction(result.usage)
        return result.value
    }
}
