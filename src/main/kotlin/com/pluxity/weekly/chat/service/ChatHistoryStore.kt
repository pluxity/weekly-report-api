package com.pluxity.weekly.chat.service

import com.pluxity.weekly.chat.dto.ChatActionResponse
import com.pluxity.weekly.chat.llm.dto.Message
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper
import java.time.Duration

private val log = KotlinLogging.logger {}

@Component
class ChatHistoryStore(
    private val redisTemplate: StringRedisTemplate,
    private val objectMapper: ObjectMapper,
) {
    companion object {
        private const val MAX_HISTORY = 10
        private const val TTL_HOURS = 24L
    }

    fun save(
        userId: String,
        role: String,
        content: String,
    ) {
        val key = "chat:history:$userId"
        val entry = objectMapper.writeValueAsString(Message(role = role, content = content))
        redisTemplate.opsForList().rightPush(key, entry)
        redisTemplate.opsForList().trim(key, -MAX_HISTORY.toLong(), -1)
        redisTemplate.expire(key, Duration.ofHours(TTL_HOURS))
    }

    fun incrementTurn(userId: String): Long {
        val key = "chat:turn:$userId"
        val turn = redisTemplate.opsForValue().increment(key) ?: 1L
        redisTemplate.expire(key, Duration.ofHours(TTL_HOURS))
        return turn
    }

    fun load(userId: String): List<Message> {
        val key = "chat:history:$userId"
        return try {
            redisTemplate
                .opsForList()
                .range(key, 0, -1)
                ?.mapNotNull { parseMessage(it) }
                ?: emptyList()
        } catch (e: Exception) {
            log.warn(e) { "대화 히스토리 로드 실패: $userId" }
            emptyList()
        }
    }

    fun recordChatTurn(
        userId: String,
        message: String,
        target: String,
        actions: List<String>,
        responses: List<ChatActionResponse>,
    ) {
        val summary = buildActionSummary(responses)
        val turnNumber = incrementTurn(userId)
        save(
            userId,
            "system",
            "--- 히스토리 #$turnNumber | 질문: $message | target: $target | actions: $actions | 결과: $summary ---",
        )
    }

    fun recordResolvedTurn(
        userId: String,
        target: String?,
        action: String,
        response: ChatActionResponse,
    ) {
        val summary = buildResolveSummary(response)
        val turnNumber = incrementTurn(userId)
        save(
            userId,
            "system",
            "--- 히스토리 #$turnNumber | resolved | target: ${target ?: "-"} | actions: [$action] | 결과: $summary ---",
        )
    }

    private fun buildResolveSummary(response: ChatActionResponse): String =
        "${response.action} ${response.target} id=${response.id ?: "pending"}"

    private fun buildActionSummary(responses: List<ChatActionResponse>): String =
        responses.joinToString(", ") { r ->
            when (r.action) {
                "read" -> {
                    val count =
                        r.readResult?.let {
                            it.tasks?.size
                                ?: it.projects?.size
                                ?: it.epics?.size
                                ?: it.teams?.size
                                ?: it.pendingReviews?.size
                                ?: 0
                        } ?: 0
                    "read ${r.target} ${count}건"
                }
                "create", "update", "delete", "review_request", "assign", "unassign" -> "${r.action} ${r.target} id=${r.id ?: "pending"}"
                else -> "${r.action} ${r.target}"
            }
        }

    private fun parseMessage(json: String): Message? =
        try {
            val node = objectMapper.readTree(json)
            Message(
                role = node.path("role").asString(),
                content = node.path("content").asString(),
            )
        } catch (e: Exception) {
            log.warn(e) { "메시지 파싱 실패: $json" }
            null
        }
}
