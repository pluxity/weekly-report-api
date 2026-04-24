package com.pluxity.weekly.teams.controller

import com.pluxity.weekly.core.response.DataResponseBody
import com.pluxity.weekly.core.response.ErrorResponseBody
import com.pluxity.weekly.core.response.PageResponse
import com.pluxity.weekly.teams.dto.TeamsNotificationLogResponse
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
import org.springframework.web.bind.annotation.RequestMapping
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
}
