package com.pluxity.weekly.chat.v2

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper
import java.time.Duration

private val log = KotlinLogging.logger {}

/**
 * [ChatV2IdRegistry]의 유저별 세션 저장소. 히스토리([ChatV2HistoryStore])와 같은 수명(24h)으로 Redis에 둬,
 * 이전 턴 검색으로 확인된 id가 다음 턴에도 유효하도록 한다 ("보여줘 → 자세히" 멀티턴).
 *
 * 저장 형태는 {종류키: [id...]} — 예: {"task":[10,11],"project":[3]}.
 * 로드/저장 실패는 삼켜서 빈 레지스트리로 진행한다 (조회는 막히지 않고, 지어낸 id 차단만 잠깐 느슨).
 */
@Component
class ChatV2IdRegistryStore(
    private val redisTemplate: StringRedisTemplate,
    private val objectMapper: ObjectMapper,
) {
    /** 저장된 세션 id를 얹은 레지스트리를 만든다 (없으면 본인 id만 있는 새 레지스트리). */
    fun load(userId: Long): ChatV2IdRegistry {
        val registry = ChatV2IdRegistry(userId)
        val json =
            try {
                redisTemplate.opsForValue().get(keyOf(userId))
            } catch (e: Exception) {
                log.warn(e) { "chat/v2 id 레지스트리 로드 실패: $userId" }
                null
            } ?: return registry
        registry.restore(parse(json))
        return registry
    }

    /** 이번 턴까지 누적된 id를 저장한다 (스냅샷 전체 덮어쓰기 — 로드분을 이미 포함). */
    fun save(
        userId: Long,
        registry: ChatV2IdRegistry,
    ) {
        val payload = registry.snapshot().entries.associate { it.key.key to it.value.toList() }
        try {
            redisTemplate.opsForValue().set(keyOf(userId), objectMapper.writeValueAsString(payload), Duration.ofHours(TTL_HOURS))
        } catch (e: Exception) {
            log.warn(e) { "chat/v2 id 레지스트리 저장 실패: $userId" }
        }
    }

    private fun parse(json: String): Map<ChatV2EntityType, Set<Long>> =
        try {
            @Suppress("UNCHECKED_CAST")
            val raw = objectMapper.readValue(json, Map::class.java) as Map<String, List<Number>>
            raw.mapNotNull { (typeKey, ids) ->
                ChatV2EntityType.from(typeKey)?.let { type -> type to ids.map { it.toLong() }.toSet() }
            }.toMap()
        } catch (e: Exception) {
            log.warn(e) { "chat/v2 id 레지스트리 파싱 실패: $json" }
            emptyMap()
        }

    private fun keyOf(userId: Long): String = "chatv2:idregistry:$userId"

    companion object {
        private const val TTL_HOURS = 24L
    }
}
