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
        if (tokens.isEmpty()) return false
        return tokens.all { target.contains(it) }
    }

    private fun normalize(s: String): String = s.lowercase().replace(" ", "")

    private val WHITESPACE = Regex("\\s+")
}
