package com.pluxity.weekly.chat.v2

/**
 * chat/v2가 다루는 엔티티 종류. 하나로 세 곳에서 함께 쓴다:
 * - search_items의 검색 대상 종류 (task/epic/project/team)
 * - get_item_details의 상세 대상 종류 (task/epic/project/team)
 * - id 레지스트리([ChatV2IdRegistry])의 네임스페이스 (+ user)
 *
 * LLM은 문자열([key])로 주고받고 내부에서는 이 enum으로 다룬다. [from]은 대소문자 무시 매칭이며
 * 인식 못 하면 **null** — "type 생략(전체 검색)"과 "잘못된 type"은 호출부가 원본 문자열의 null 여부로 구분한다.
 *
 * USER는 search_users·담당자 필터의 id 네임스페이스로만 쓰이고 search_items/get_item_details 대상은 아니다.
 */
enum class ChatV2EntityType(
    val key: String,
) {
    TASK("task"),
    EPIC("epic"),
    PROJECT("project"),
    TEAM("team"),
    USER("user"),
    ;

    companion object {
        fun from(raw: String?): ChatV2EntityType? = raw?.let { s -> entries.find { it.key.equals(s, ignoreCase = true) } }
    }
}
