package com.pluxity.weekly.report.dto

import io.swagger.v3.oas.annotations.media.Schema

@Schema(
    description = """
지난주 보고와의 매칭 결과. 항목 text만 단순 표시.
- matched: 지난주 nextWeek → 이번주 thisWeek 매칭 쌍
- missing: 지난주 예정이었으나 이번주에 매칭되지 않은 항목 (누락 의심)
- new: 지난주 예정에 없던 이번주 신규 항목
""",
)
data class MatchedAgainstPrev(
    @field:Schema(description = "매칭된 쌍")
    val matched: List<MatchedPair> = emptyList(),
    @field:Schema(description = "누락 의심 항목 텍스트 리스트")
    val missing: List<String> = emptyList(),
    @field:Schema(description = "신규 항목 텍스트 리스트")
    val new: List<String> = emptyList(),
)

@Schema(description = "지난주-이번주 매칭 쌍 (텍스트만)")
data class MatchedPair(
    @field:Schema(description = "지난주 nextWeek 항목 텍스트", example = "#95 메인 페이지 구현")
    val prev: String,
    @field:Schema(description = "이번주 thisWeek 항목 텍스트", example = "#95 메인 페이지 구현 완료")
    val curr: String,
)
