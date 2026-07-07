package com.pluxity.weekly.chat.v2

/**
 * 한 턴(tool 루프) 동안 검색/실행 결과로 확인된 id의 레지스트리.
 * 모델이 검색 없이 지어낸 id로 mutating tool을 호출하는 것을 구조적으로 차단한다
 * (프롬프트 "id 추측 금지"의 서버측 강제 — 실제로 epic_id를 찍어서 엉뚱한 곳에 생성한 사례가 있었음).
 *
 * 히스토리에는 id가 남지 않으므로 멀티턴에서는 턴마다 재검색이 강제된다 —
 * "의미 매칭 비용을 필요할 때만 지불"하는 설계와 같은 방향의 의도된 비용.
 */
class ChatV2IdRegistry(
    currentUserId: Long,
) {
    private val known = mutableMapOf<String, MutableSet<Long>>()

    init {
        // 본인 id는 검색 없이 허용 (본인을 담당자로 지정하는 흐름)
        register(USER, currentUserId)
    }

    fun register(
        type: String,
        id: Long?,
    ) {
        if (id != null) {
            known.getOrPut(type) { mutableSetOf() } += id
        }
    }

    fun isKnown(
        type: String,
        id: Long,
    ): Boolean = known[type]?.contains(id) == true

    companion object {
        const val TASK = "task"
        const val EPIC = "epic"
        const val PROJECT = "project"
        const val USER = "user"
    }
}
