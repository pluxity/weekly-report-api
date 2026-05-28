package com.pluxity.weekly.chat.llm.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

/**
 * weekly-report-match-prompt.txt의 LLM 응답 매핑.
 * LLM은 id 쌍(P_/C_)만 반환하고, 실제 항목 복원·assignee·missing/new 계산은 코드에서 (MatchedEnricher).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class WeeklyReportMatchResult(
    val matched: List<MatchedPairRaw> = emptyList(),
)

/** prev/curr는 항목 텍스트가 아니라 id (예: "P2", "C1"). */
@JsonIgnoreProperties(ignoreUnknown = true)
data class MatchedPairRaw(
    val prev: String = "",
    val curr: String = "",
)
