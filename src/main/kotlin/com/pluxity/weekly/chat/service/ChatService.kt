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
            return processChat(message, userId.toString())
        } finally {
            redisTemplate.execute(RELEASE_LOCK_SCRIPT, listOf(lockKey), lockValue)
        }
    }

    private fun processChat(
        message: String,
        userId: String,
    ): List<ChatActionResponse> {
        // 히스토리 로드
        val history = chatHistoryStore.load(userId)
        if (history.isNotEmpty()) {
            log.info { "히스토리 (${history.size}건):\n${history.joinToString("\n") { it.content }}" }
        }

        // 1차: 의도 추출 (히스토리 포함)
        val intentMessages = promptBuilder.buildIntentMessages(message, history)
        val intent = llmService.extractIntent(intentMessages)
        log.info { "1차 의도 추출 - action: ${intent.actions}, target: ${intent.target}, id: ${intent.id}, response: $intent" }

        // target별+action별+권한별 context 조회
        val targetType = ChatTarget.fromOrNull(intent.target) ?: ChatTarget.TASK
        val actionTypes = intent.actions.mapNotNull { ChatActionType.fromOrNull(it) }
        val context = contextBuilder.build(targetType, actionTypes)
        log.info { "context:\n${objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(objectMapper.readTree(context))}" }

        // 2차: LlmAction 생성
        val actionMessages = promptBuilder.buildActionMessages(message, intent, context)
        val actions = llmService.generate(actionMessages).take(1)
        log.info { "LLM 응답 액션:\n${objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(actions)}" }

        // LlmAction → ChatActionResponse 변환
        val responses = actions.map { chatActionRouter.route(it) }

        chatHistoryStore.recordChatTurn(userId, message, intent.target, intent.actions, responses)
        return responses
    }
}
