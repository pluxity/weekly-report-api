package com.pluxity.weekly.chat.v2

import com.pluxity.weekly.core.constant.ErrorCode
import com.pluxity.weekly.core.exception.CustomException
import org.springframework.core.io.ClassPathResource
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.script.RedisScript
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.UUID

/**
 * 유저당 chat 요청 직렬화 락. v1(/chat)과 같은 키(`chat:lock:{userId}`)를 쓰므로
 * v1/v2 조회/생성이 한 유저 안에서 서로 직렬화된다 (동시 upsert·중복 LLM 호출 방지).
 * 값 비교 해제(release-lock.lua)로 만료 후 남의 락을 지우는 것을 방지한다.
 */
@Component
class ChatV2UserLock(
    private val redisTemplate: RedisTemplate<String, Any>,
) {
    fun <T> withLock(
        userId: Long,
        block: () -> T,
    ): T {
        val lockKey = "chat:lock:$userId"
        val lockValue = UUID.randomUUID().toString()
        val acquired = redisTemplate.opsForValue().setIfAbsent(lockKey, lockValue, LOCK_TTL)
        if (acquired != true) {
            throw CustomException(ErrorCode.CHAT_ALREADY_PROCESSING)
        }
        try {
            return block()
        } finally {
            redisTemplate.execute(RELEASE_LOCK_SCRIPT, listOf(lockKey), lockValue)
        }
    }

    companion object {
        private val LOCK_TTL = Duration.ofSeconds(30)
        private val RELEASE_LOCK_SCRIPT =
            RedisScript.of(
                ClassPathResource("scripts/release-lock.lua"),
                Long::class.java,
            )
    }
}
