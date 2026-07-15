package com.pluxity.weekly.chat.v2

/**
 * search_items의 검색 대상 종류. LLM은 문자열(task/epic/project/team)로 주고, 여기서 enum으로 파싱한다.
 * [from]은 대소문자 무시 매칭이며, **인식 못 하면 null** — "type 생략(전체 검색)"과 "잘못된 type(빈 결과)"은
 * 호출부에서 원본 문자열의 null 여부로 구분한다(핸들러의 typeOmitted).
 */
enum class SearchType {
    TASK,
    EPIC,
    PROJECT,
    TEAM,
    ;

    companion object {
        fun from(raw: String?): SearchType? = raw?.let { s -> entries.find { it.name.equals(s, ignoreCase = true) } }
    }
}
