package com.pluxity.weekly.report.dto

import com.fasterxml.jackson.annotation.JsonUnwrapped
import com.pluxity.weekly.core.response.BaseResponse
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDate

@Schema(description = "주간보고 응답")
data class WeeklyReportResponse(
    @field:Schema(description = "ID", example = "1")
    val id: Long,
    @field:Schema(description = "팀 ID (LLM이 team_name_raw → teams.name 매칭 실패 시 null)", example = "10")
    val teamId: Long?,
    @field:Schema(description = "teams 테이블에서 매칭된 정식 팀명. 매칭 실패 시 null", example = "개발팀")
    val teamName: String?,
    @field:Schema(description = "LLM이 원문에서 추출한 팀명 원본 (항상 보존, 매칭 실패 시 표시용)", example = "본부A 개발팀")
    val teamNameRaw: String,
    @field:Schema(description = "주차 시작일 (해당 주 월요일로 정규화)", example = "2026-05-18")
    val weekStart: LocalDate,
    @field:Schema(description = "사용자가 원문에 적은 주차 표기 (UI 표시용)", example = "5월 2주(05/11~05/15)")
    val weekLabel: String?,
    @field:Schema(
        description = "원문 (사용자가 paste한 SSOT — 변질되지 않음)",
        example = "[개발팀 5/20 주간보고]\n\n홍길동\n- 이번주: A 기능 완료\n- 다음주: D 시작",
    )
    val rawContent: String,
    @field:Schema(description = "LLM이 분류·재배치한 정제 결과")
    val formatted: FormattedReport,
    @field:Schema(description = "지난주 보고와의 매칭 결과 . 지난주 보고 없거나 아직 계산 전이면 null")
    val matchedAgainstPrev: MatchedAgainstPrev?,
    @field:JsonUnwrapped
    val baseResponse: BaseResponse,
)
