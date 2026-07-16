package com.pluxity.weekly.chat.v2

/**
 * 검색으로 확인된 id의 레지스트리. 모델이 검색 없이 지어낸 id로 조회하는 것을 서버측에서 차단한다
 * (프롬프트 "id 추측 금지"에 의존하지 않고 실행 전에 거부 — 실제로 지어낸 id를 찍는 사례가 반복됨).
 *
 * [ChatV2IdRegistryStore]가 유저별로 Redis에 저장해 **히스토리와 수명을 맞춘다**(세션 24h). 따라서 이전 턴
 * 검색으로 확인된 id는 다음 턴에도 유효하다 — "보여줘 → 자세히" 같은 멀티턴이 재검색 없이 이어진다.
 * (레지스트리는 id의 '실재성'만 보장한다. 현재 질문과의 '관련성' 판단은 모델 몫이다.)
 */
class ChatV2IdRegistry(
    currentUserId: Long,
) {
    private val known = mutableMapOf<ChatV2EntityType, MutableSet<Long>>()

    init {
        // 본인 id는 검색 없이 허용 (본인을 담당자로 지정하는 흐름)
        register(ChatV2EntityType.USER, currentUserId)
    }

    fun register(
        type: ChatV2EntityType,
        id: Long?,
    ) {
        if (id != null) {
            known.getOrPut(type) { mutableSetOf() } += id
        }
    }

    fun isKnown(
        type: ChatV2EntityType,
        id: Long,
    ): Boolean = known[type]?.contains(id) == true

    /** 저장된 세션 id를 복원한다 ([ChatV2IdRegistryStore.load]). */
    fun restore(saved: Map<ChatV2EntityType, Set<Long>>) {
        saved.forEach { (type, ids) -> ids.forEach { register(type, it) } }
    }

    /** 저장용 스냅샷 ([ChatV2IdRegistryStore.save]). */
    fun snapshot(): Map<ChatV2EntityType, Set<Long>> = known.mapValues { it.value.toSet() }
}
