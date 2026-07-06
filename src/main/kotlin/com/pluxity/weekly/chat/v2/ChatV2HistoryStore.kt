package com.pluxity.weekly.chat.v2

import com.pluxity.weekly.chat.v2.dto.ToolMessage
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper
import java.time.Duration

private val log = KotlinLogging.logger {}

/**
 * v2 대화 히스토리 — user/assistant 텍스트 메시지만 저장 (tool 호출 내역은 턴 안에서만 사용).
 * "AA 수정해줘" → "뭘 수정할까요?" → "진행률 80" 같은 멀티턴 흐름을 지원한다.
 */
@Component
class ChatV2HistoryStore(
    private val redisTemplate: StringRedisTemplate,
    private val objectMapper: ObjectMapper,
) {
    fun load(userId: Long): List<ToolMessage> {
        val key = keyOf(userId)
        return try {
            redisTemplate
                .opsForList()
                .range(key, 0, -1)
                ?.mapNotNull { parse(it) }
                ?: emptyList()
        } catch (e: Exception) {
            log.warn(e) { "chat/v2 히스토리 로드 실패: $userId" }
            emptyList()
        }
    }

    fun appendTurn(
        userId: Long,
        userMessage: String,
        assistantReply: String,
    ) {
        val key = keyOf(userId)
        redisTemplate.opsForList().rightPush(key, toJson("user", userMessage))
        redisTemplate.opsForList().rightPush(key, toJson("assistant", assistantReply))
        redisTemplate.opsForList().trim(key, -MAX_MESSAGES.toLong(), -1)
        redisTemplate.expire(key, Duration.ofHours(TTL_HOURS))
    }

    private fun toJson(
        role: String,
        content: String,
    ): String = objectMapper.writeValueAsString(mapOf("role" to role, "content" to content))

    private fun parse(json: String): ToolMessage? =
        try {
            val node = objectMapper.readTree(json)
            ToolMessage(role = node.path("role").asString(), content = node.path("content").asString())
        } catch (e: Exception) {
            log.warn(e) { "chat/v2 히스토리 파싱 실패: $json" }
            null
        }

    private fun keyOf(userId: Long): String = "chatv2:history:$userId"

    companion object {
        private const val MAX_MESSAGES = 12
        private const val TTL_HOURS = 24L
    }
}
