package com.pluxity.weekly.chat.v2

import com.pluxity.weekly.chat.v2.dto.ToolMessage
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper
import java.time.Duration

private val log = KotlinLogging.logger {}

/**
 * v2 대화 히스토리 — 한 턴의 실제 메시지 배열을 **role 태그째** 저장·replay (2026-07-20 오염 fix, 옵션 D).
 *
 * tool calling 한 턴은 `user → assistant[tool_calls] → tool[result] → assistant[최종답]` 구조다.
 * 이걸 압축/재구성해 저장하면(최종 텍스트만·마커·user만) 역할 경계가 무너져 — 이전 턴 데이터가 새 답에
 * 새거나(fabrication), 이전 요청을 재실행하거나(blending), 가짜 마커를 앵무새(mimicry)한다(A~C 실측).
 * → 실제 메시지를 그대로 쌓고 그대로 되돌린다. trim은 tool_call↔result 짝이 안 깨지게 **턴 단위**로 한다.
 */
@Component
class ChatV2HistoryStore(
    private val redisTemplate: StringRedisTemplate,
    private val objectMapper: ObjectMapper,
) {
    fun load(userId: Long): List<ToolMessage> {
        val key = keyOf(userId)
        return try {
            redisTemplate.opsForList().range(key, 0, -1)?.mapNotNull { parse(it) } ?: emptyList()
        } catch (e: Exception) {
            log.warn(e) { "chat/v2 히스토리 로드 실패: $userId" }
            emptyList()
        }
    }

    /** 이번 턴에 생긴 메시지들(현재 user + assistant/tool + 최종 답)을 role 태그째 적재하고 최근 N턴만 남긴다. */
    fun appendMessages(
        userId: Long,
        messages: List<ToolMessage>,
    ) {
        if (messages.isEmpty()) return
        val key = keyOf(userId)
        val ops = redisTemplate.opsForList()
        messages.forEach { ops.rightPush(key, toJson(it)) }
        trimToRecentTurns(key)
        redisTemplate.expire(key, Duration.ofHours(TTL_HOURS))
    }

    /**
     * user 메시지를 턴 경계로 보고 최근 MAX_TURNS 턴만 유지한다.
     * 원소 개수로 자르면 tool_call만 남고 그 result가 잘려 다음 replay 시 API가 거부하므로 턴 단위로 자른다.
     */
    private fun trimToRecentTurns(key: String) {
        val all = redisTemplate.opsForList().range(key, 0, -1) ?: return
        val turnStarts = all.indices.filter { isUserMessage(all[it]) }
        if (turnStarts.size <= MAX_TURNS) return
        val cutFrom = turnStarts[turnStarts.size - MAX_TURNS]
        redisTemplate.opsForList().trim(key, cutFrom.toLong(), -1)
    }

    private fun isUserMessage(json: String): Boolean =
        try {
            objectMapper.readTree(json).path("role").asString() == "user"
        } catch (e: Exception) {
            false
        }

    private fun toJson(message: ToolMessage): String = objectMapper.writeValueAsString(message)

    private fun parse(json: String): ToolMessage? =
        try {
            objectMapper.readValue(json, ToolMessage::class.java)
        } catch (e: Exception) {
            log.warn(e) { "chat/v2 히스토리 파싱 실패: $json" }
            null
        }

    private fun keyOf(userId: Long): String = "chatv2:history:$userId"

    companion object {
        // 턴 수 기준 윈도우 — tool_result JSON이 커서 원소 수 아닌 턴 수로 제한
        private const val MAX_TURNS = 5
        private const val TTL_HOURS = 24L
    }
}
