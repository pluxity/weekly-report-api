package com.pluxity.weekly.chat.service

import com.pluxity.weekly.chat.dto.LlmAction
import com.pluxity.weekly.chat.exception.ChatSessionExpiredException
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper
import java.time.Duration
import java.util.UUID

@Component
class ClarifyStore(
    private val redisTemplate: StringRedisTemplate,
    private val objectMapper: ObjectMapper,
) {
    companion object {
        private val TTL = Duration.ofMinutes(10)
    }

    fun save(
        userId: Long,
        action: LlmAction,
    ): String {
        val clarifyId = UUID.randomUUID().toString()
        val key = keyOf(userId, clarifyId)
        val json = objectMapper.writeValueAsString(action)
        redisTemplate.opsForValue().set(key, json, TTL)
        return clarifyId
    }

    fun peek(
        userId: Long,
        clarifyId: String,
    ): LlmAction {
        val key = keyOf(userId, clarifyId)
        val raw = redisTemplate.opsForValue().get(key) ?: throw ChatSessionExpiredException()
        return objectMapper.readValue(raw, LlmAction::class.java)
    }

    fun delete(
        userId: Long,
        clarifyId: String,
    ) {
        redisTemplate.delete(keyOf(userId, clarifyId))
    }

    private fun keyOf(
        userId: Long,
        clarifyId: String,
    ): String = "chat:clarify:$userId:$clarifyId"
}
