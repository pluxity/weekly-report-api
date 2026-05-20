package com.pluxity.weekly.report.dto

import com.fasterxml.jackson.annotation.JsonUnwrapped
import com.pluxity.weekly.core.response.BaseResponse
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDate

@Schema(
    description = """
    주간보고 요약 (팀 × 주차의 작성 상태). 풀 데이터 없이 메타만 내려주는 경량 응답.
    
    용도:
    - 주차 네비에서 미작성 주차 표시 (amber/badge)
    - 팀 리스트에서 미작성/지각 팀 표시
    """,
)
data class WeeklyReportSummaryResponse(
    @field:Schema(description = "팀 ID", example = "10")
    val teamId: Long,
    @field:Schema(description = "주차 시작일 (해당 주 월요일)", example = "2026-05-11")
    val weekStart: LocalDate,
    @field:Schema(description = "해당 팀×주차의 보고 작성 여부", example = "true")
    val exists: Boolean,
    @field:JsonUnwrapped
    val baseResponse: BaseResponse,
)
