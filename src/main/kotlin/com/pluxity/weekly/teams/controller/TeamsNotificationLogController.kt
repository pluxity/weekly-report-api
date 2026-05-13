package com.pluxity.weekly.teams.controller

import com.pluxity.weekly.core.response.DataResponseBody
import com.pluxity.weekly.core.response.ErrorResponseBody
import com.pluxity.weekly.core.response.PageResponse
import com.pluxity.weekly.teams.dto.TeamsNotificationLogResponse
import com.pluxity.weekly.teams.entity.TeamsNotificationStatus
import com.pluxity.weekly.teams.service.TeamsNotificationLogService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.web.PageableDefault
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/notifications")
@Tag(name = "Notification Controller", description = "Teams 알림 이력 조회 API")
class TeamsNotificationLogController(
    private val service: TeamsNotificationLogService,
) {
    @Operation(summary = "내 알림 목록 조회", description = "현재 사용자가 수신한 Teams 알림 이력을 페이지네이션으로 조회합니다")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "조회 성공"),
            ApiResponse(
                responseCode = "401",
                description = "인증 실패",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponseBody::class))],
            ),
        ],
    )
    @GetMapping
    fun findMine(
        @PageableDefault(size = 20, sort = ["id"], direction = Sort.Direction.DESC) pageable: Pageable,
    ): ResponseEntity<DataResponseBody<PageResponse<TeamsNotificationLogResponse>>> =
        ResponseEntity.ok(DataResponseBody(service.findMine(pageable)))

    @Operation(
        summary = "전체 알림 목록 조회 (ADMIN)",
        description = "Teams 알림 전체를 페이지네이션으로 조회한다. status 파라미터로 상태 필터링이 가능하다. ADMIN 전용.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "조회 성공"),
            ApiResponse(
                responseCode = "403",
                description = "권한 없음 (ADMIN 아님)",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponseBody::class))],
            ),
        ],
    )
    @GetMapping("/admin")
    fun findAllForAdmin(
        @RequestParam(required = false) status: TeamsNotificationStatus?,
        @PageableDefault(size = 20, sort = ["id"], direction = Sort.Direction.DESC) pageable: Pageable,
    ): ResponseEntity<DataResponseBody<PageResponse<TeamsNotificationLogResponse>>> =
        ResponseEntity.ok(DataResponseBody(service.findAllForAdmin(status, pageable)))

    @Operation(
        summary = "실패 알림 재발사",
        description = "FAILED 상태의 Teams 알림을 다시 발사한다. ADMIN 전용. 실제 전송은 비동기로 처리되며 응답 시점의 status는 PENDING이다.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "재발사 요청 접수"),
            ApiResponse(
                responseCode = "400",
                description = "FAILED 상태가 아닌 알림에 대한 재발사 요청",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponseBody::class))],
            ),
            ApiResponse(
                responseCode = "403",
                description = "권한 없음 (ADMIN 아님)",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponseBody::class))],
            ),
            ApiResponse(
                responseCode = "404",
                description = "알림을 찾을 수 없음",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponseBody::class))],
            ),
        ],
    )
    @PostMapping("/{id}/retry")
    fun retry(
        @PathVariable id: Long,
    ): ResponseEntity<DataResponseBody<TeamsNotificationLogResponse>> = ResponseEntity.ok(DataResponseBody(service.retry(id)))
}
