package com.pluxity.weekly.chat.v2

/**
 * 통합 검색용 이름 매칭.
 * 검색어를 공백 기준 토큰으로 쪼개고, 대소문자·공백을 무시한 이름에 모든 토큰이 포함되면 매칭.
 * 예: "cctv API" ↔ "CCTV 목록 API" 매칭 ("cctv"와 "api"가 모두 포함).
 */
object ItemNameMatcher {
    fun matches(
        query: String,
        name: String,
    ): Boolean {
        val target = normalize(name)
        val tokens =
            query
                .trim()
                .split(WHITESPACE)
                .map { normalize(it) }
                .filter { it.isNotBlank() }
        // 모델이 붙이는 종류 단어("철도 인적오류 프로젝트", "오송… 업무그룹")는 실제 이름엔 없어 AND 매칭을 깬다 → 제거.
        // 단, 모두 종류 단어면(예: query="프로젝트") 원본 유지.
        val meaningful = tokens.filterNot { it in TYPE_WORDS }.ifEmpty { tokens }
        if (meaningful.isEmpty()) return false
        return meaningful.all { target.contains(it) }
    }

    private fun normalize(s: String): String = s.lowercase().replace(" ", "")

    private val WHITESPACE = Regex("\\s+")

    /** 검색어에 흔히 붙지만 실제 이름엔 없는 종류 단어 (normalize된 형태). */
    private val TYPE_WORDS = setOf("프로젝트", "업무그룹", "에픽", "태스크", "팀", "project", "epic", "task", "team")
}
