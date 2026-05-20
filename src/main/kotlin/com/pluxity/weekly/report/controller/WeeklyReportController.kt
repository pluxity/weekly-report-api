package com.pluxity.weekly.report.controller

import com.pluxity.weekly.core.response.DataResponseBody
import com.pluxity.weekly.core.response.ErrorResponseBody
import com.pluxity.weekly.report.dto.WeeklyReportResponse
import com.pluxity.weekly.report.dto.WeeklyReportSummaryResponse
import com.pluxity.weekly.report.service.WeeklyReportService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

@RestController
@RequestMapping("/weekly-reports")
@Tag(name = "Weekly Report Controller", description = "주간보고 조회 API (작성/수정/삭제는 chat 라우터에서 처리)")
class WeeklyReportController(
    private val service: WeeklyReportService,
) {
    @Operation(
        summary = "주간보고 목록 조회",
        description = """
        주간보고를 조회합니다. 조회 권한은 다음과 같습니다.
        - ADMIN: 전체 팀 조회 가능
        - Leader: 본인이 team.leaderId인 팀만 조회 가능
        - 그 외: 403
        - teamId 미지정 시 권한 범위 내 모든 팀
        - weekStart / weekEnd: 검색 범위의 시작/종료 주차(해당 주 월요일). inclusive. 둘 다 생략 가능, weekStart만 → 이후 전체, weekEnd만 → 이전 전체.
        """,
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "조회 성공"),
            ApiResponse(
                responseCode = "403",
                description = "권한 없음 (Leader가 본인 팀이 아닌 팀을 조회 등)",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponseBody::class))],
            ),
        ],
    )
    @GetMapping
    fun findAll(
        @Parameter(description = "팀 ID (미지정 시 권한 범위 내 전체)", example = "10")
        @RequestParam(required = false) teamId: Long?,
        @Parameter(description = "검색 범위 시작 주차 (해당 주 월요일, ISO yyyy-MM-dd, 포함)", example = "2026-05-18")
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) weekStart: LocalDate?,
        @Parameter(description = "검색 범위 종료 주차 (해당 주 월요일, ISO yyyy-MM-dd, 포함)", example = "2026-06-15")
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) weekEnd: LocalDate?,
    ): ResponseEntity<DataResponseBody<List<WeeklyReportResponse>>> =
        ResponseEntity.ok(DataResponseBody(service.findAll(teamId, weekStart, weekEnd)))

    @Operation(
        summary = "주간보고 단건 조회",
        description = "ID로 단건 조회. 권한 검사는 목록 조회와 동일.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "조회 성공"),
            ApiResponse(
                responseCode = "403",
                description = "권한 없음",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponseBody::class))],
            ),
            ApiResponse(
                responseCode = "404",
                description = "주간보고를 찾을 수 없음",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponseBody::class))],
            ),
        ],
    )
    @GetMapping("/{id}")
    fun findById(
        @PathVariable id: Long,
    ): ResponseEntity<DataResponseBody<WeeklyReportResponse>> = ResponseEntity.ok(DataResponseBody(service.findById(id)))

    @Operation(
        summary = "주간보고 요약 조회 (팀 × 주차의 작성 상태)",
        description = """
        풀 데이터 없이 메타만 내려주는 경량 응답. 대시보드 초기 로딩에 사용.

        용도:
        - 팀 리스트에서 미작성/지각 팀 뱃지

        권한은 목록 조회와 동일 (ADMIN 전체, Leader 본인 팀만).
        """,
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "조회 성공"),
            ApiResponse(
                responseCode = "403",
                description = "권한 없음",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponseBody::class))],
            ),
        ],
    )
    @GetMapping("/summary")
    fun findSummary(
        @Parameter(description = "검색 범위 시작 주차 (해당 주 월요일, ISO yyyy-MM-dd, inclusive)", example = "2026-05-18")
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) weekStart: LocalDate?,
        @Parameter(description = "검색 범위 종료 주차 (해당 주 월요일, ISO yyyy-MM-dd, inclusive)", example = "2026-06-15")
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) weekEnd: LocalDate?,
    ): ResponseEntity<DataResponseBody<List<WeeklyReportSummaryResponse>>> =
        ResponseEntity.ok(DataResponseBody(service.findSummary(weekStart, weekEnd)))
}
