package com.pluxity.weekly.report.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import io.swagger.v3.oas.annotations.media.Schema

@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(
    description = """
지난주 보고와의 매칭 결과. 각 항목에 assignee를 붙여 화면에서 사용자별 그룹핑 가능.
- matched: 지난주 nextWeek → 이번주 thisWeek 매칭 쌍
- missing: 지난주 예정이었으나 이번주에 매칭되지 않은 항목 (누락 의심)
- new: 지난주 예정에 없던 이번주 신규 항목
""",
)
data class MatchedAgainstPrev(
    @field:Schema(description = "매칭된 쌍")
    val matched: List<MatchedPair> = emptyList(),
    @field:Schema(description = "누락 의심 항목 (지난주 예정)")
    val missing: List<MatchedItem> = emptyList(),
    @field:Schema(description = "이번주 신규 항목")
    val new: List<MatchedItem> = emptyList(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = "지난주-이번주 매칭 쌍")
data class MatchedPair(
    @field:Schema(description = "담당자명 (그룹핑용, 복구 실패 시 null)", example = "윤지선")
    val assignee: String?,
    @field:Schema(description = "지난주 nextWeek 항목 텍스트", example = "#95 메인 페이지 구현")
    val prev: String,
    @field:Schema(description = "이번주 thisWeek 항목 텍스트", example = "#95 메인 페이지 구현 완료")
    val curr: String,
)

@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = "매칭 단건 항목 (missing/new)")
data class MatchedItem(
    @field:Schema(description = "담당자명 (그룹핑용, 복구 실패 시 null)", example = "김형래")
    val assignee: String?,
    @field:Schema(description = "항목 텍스트", example = "VLM 데모 초기 버전 배포")
    val text: String,
)
