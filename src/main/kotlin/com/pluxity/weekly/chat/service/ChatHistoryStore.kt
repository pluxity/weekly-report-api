package com.pluxity.weekly.chat.service

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
