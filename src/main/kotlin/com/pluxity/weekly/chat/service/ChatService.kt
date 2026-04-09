package com.pluxity.weekly.chat.service

import com.pluxity.weekly.authorization.AuthorizationService
import com.pluxity.weekly.chat.context.ContextBuilder
import com.pluxity.weekly.chat.dto.ChatActionResponse
import com.pluxity.weekly.chat.dto.LlmAction
import com.pluxity.weekly.chat.exception.ChatClarifyException
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
    private val chatDtoMapper: ChatDtoMapper,
    private val selectFieldResolver: SelectFieldResolver,
    private val chatReadHandler: ChatReadHandler,
    private val chatExecutor: ChatExecutor,
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
        val context = contextBuilder.build(intent.target, intent.actions)
        log.info { "context:\n${objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(objectMapper.readTree(context))}" }

        // 2차: LlmAction 생성
        val actionMessages = promptBuilder.buildActionMessages(message, intent, context, history)
        val actions = llmService.generate(actionMessages).take(1)
        log.info { "LLM 응답 액션:\n${objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(actions)}" }

        // LlmAction → ChatActionResponse 변환
        try {
            val responses =
                actions.map { action ->
                    val target = action.target ?: "task"
                    when {
                        action.action == "read" ->
                            ChatActionResponse(
                                action = action.action,
                                target = target,
                                readResult = chatReadHandler.handle(action),
                            )
                        action.action == "clarify" -> throw ChatClarifyException(
                            message = action.message ?: "좀 더 구체적으로 말씀해주세요.",
                        )
                        action.action == "create" && target in listOf("project", "epic") -> {
                            val selectFields = selectFieldResolver.resolve(action)
                            ChatActionResponse(
                                action = action.action,
                                target = target,
                                dto = chatDtoMapper.toDto(action),
                                selectFields = selectFields.ifEmpty { null },
                            )
                        }
                        !action.missingFields.isNullOrEmpty() ||
                            (action.action in listOf("update", "delete", "review_request") && action.id == null) -> {
                            throw buildClarifyException(action)
                        }
                        else -> {
                            val resultId = chatExecutor.execute(action)
                            ChatActionResponse(
                                action = action.action,
                                target = target,
                                id = resultId,
                            )
                        }
                    }
                }

            saveHistory(userId, message, intent.target, intent.actions, buildActionSummary(responses))
            return responses
        } catch (e: CustomException) {
            if (e.code == ErrorCode.LLM_AMBIGUOUS_REQUEST) {
                saveHistory(userId, message, intent.target, intent.actions, "clarify: ${e.message}")
            }
            throw e
        }
    }

    private fun buildClarifyException(action: LlmAction): ChatClarifyException {
        val base = action.message ?: "대상을 특정할 수 없습니다."
        val candidates = action.candidates
        val missingFields = action.missingFields

        if (candidates.isNullOrEmpty() || missingFields.isNullOrEmpty()) {
            return ChatClarifyException(message = base)
        }

        val names =
            missingFields
                .firstNotNullOfOrNull { field ->
                    selectFieldResolver
                        .resolveCandidateNames(field, action.target, candidates)
                        .takeIf { it.isNotEmpty() }
                }.orEmpty()

        return ChatClarifyException(
            message = base,
            candidates = names.ifEmpty { null },
        )
    }

    private fun saveHistory(
        userId: String,
        message: String,
        target: String,
        actions: List<String>,
        summary: String,
    ) {
        val turnNumber = chatHistoryStore.incrementTurn(userId)
        chatHistoryStore.save(
            userId,
            "system",
            "--- 히스토리 #$turnNumber | 질문: $message | target: $target | actions: $actions | 결과: $summary ---",
        )
    }

    private fun buildActionSummary(responses: List<ChatActionResponse>): String =
        responses.joinToString(", ") { r ->
            when (r.action) {
                "read" -> {
                    val count =
                        r.readResult?.let {
                            it.tasks?.size ?: it.projects?.size ?: it.epics?.size ?: it.teams?.size ?: 0
                        } ?: 0
                    "read ${r.target} ${count}건"
                }
                "create", "update", "delete", "review_request" -> "${r.action} ${r.target} id=${r.id ?: "pending"}"
                else -> "${r.action} ${r.target}"
            }
        }
}
