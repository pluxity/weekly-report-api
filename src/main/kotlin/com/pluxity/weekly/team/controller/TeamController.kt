package com.pluxity.weekly.team.controller

import com.pluxity.weekly.auth.user.dto.UserResponse
import com.pluxity.weekly.core.annotation.ResponseCreated
import com.pluxity.weekly.core.response.DataResponseBody
import com.pluxity.weekly.core.response.ErrorResponseBody
import com.pluxity.weekly.team.dto.TeamMemberRequest
import com.pluxity.weekly.team.dto.TeamRequest
import com.pluxity.weekly.team.dto.TeamResponse
import com.pluxity.weekly.team.dto.TeamUpdateRequest
import com.pluxity.weekly.team.service.TeamService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/teams")
@Tag(name = "Team Controller", description = "팀 관리 API")
class TeamController(
    private val service: TeamService,
) {
    @Operation(summary = "팀 전체 조회", description = "팀 전체 목록을 조회합니다")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "조회 성공"),
            ApiResponse(
                responseCode = "500",
                description = "서버 오류",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponseBody::class))],
            ),
        ],
    )
    @GetMapping
    fun findAll(): ResponseEntity<DataResponseBody<List<TeamResponse>>> = ResponseEntity.ok(DataResponseBody(service.findAll()))

    @Operation(summary = "팀 단건 조회", description = "ID로 팀을 조회합니다")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "조회 성공"),
            ApiResponse(
                responseCode = "404",
                description = "팀을 찾을 수 없음",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponseBody::class))],
            ),
        ],
    )
    @GetMapping("/{id}")
    fun findById(
        @PathVariable id: Long,
    ): ResponseEntity<DataResponseBody<TeamResponse>> = ResponseEntity.ok(DataResponseBody(service.findById(id)))

    @Operation(summary = "팀 등록", description = "새로운 팀을 등록합니다")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "201", description = "등록 성공"),
            ApiResponse(
                responseCode = "400",
                description = "잘못된 요청",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponseBody::class))],
            ),
        ],
    )
    @PostMapping
    @ResponseCreated(path = "/teams/{id}")
    fun create(
        @RequestBody @Valid request: TeamRequest,
    ): ResponseEntity<Long> = ResponseEntity.ok(service.create(request))

    @Operation(summary = "팀 부분 수정", description = "전달된 필드만 수정합니다")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "204", description = "수정 성공"),
            ApiResponse(
                responseCode = "404",
                description = "팀을 찾을 수 없음",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponseBody::class))],
            ),
        ],
    )
    @PatchMapping("/{id}")
    fun update(
        @PathVariable id: Long,
        @RequestBody @Valid request: TeamUpdateRequest,
    ): ResponseEntity<Void> {
        service.update(id, request)
        return ResponseEntity.noContent().build()
    }

    @Operation(summary = "팀 삭제", description = "팀을 삭제합니다")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "204", description = "삭제 성공"),
            ApiResponse(
                responseCode = "404",
                description = "팀을 찾을 수 없음",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponseBody::class))],
            ),
        ],
    )
    @DeleteMapping("/{id}")
    fun delete(
        @PathVariable id: Long,
    ): ResponseEntity<Void> {
        service.delete(id)
        return ResponseEntity.noContent().build()
    }

    // ── TeamMember ──

    @Operation(summary = "팀원 목록 조회", description = "팀에 소속된 팀원 목록을 조회합니다")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "조회 성공"),
            ApiResponse(
                responseCode = "404",
                description = "팀을 찾을 수 없음",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponseBody::class))],
            ),
        ],
    )
    @GetMapping("/{teamId}/members")
    fun findMembers(
        @PathVariable teamId: Long,
    ): ResponseEntity<DataResponseBody<List<UserResponse>>> = ResponseEntity.ok(DataResponseBody(service.findMembers(teamId)))

    @Operation(summary = "팀원 추가", description = "팀에 사용자를 추가합니다")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "201", description = "추가 성공"),
            ApiResponse(
                responseCode = "400",
                description = "이미 소속된 사용자",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponseBody::class))],
            ),
            ApiResponse(
                responseCode = "404",
                description = "팀을 찾을 수 없음",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponseBody::class))],
            ),
        ],
    )
    @PostMapping("/{teamId}/members")
    @ResponseCreated(path = "/teams/{teamId}/members/{id}")
    fun addMember(
        @PathVariable teamId: Long,
        @RequestBody @Valid request: TeamMemberRequest,
    ): ResponseEntity<Long> = ResponseEntity.ok(service.addMember(teamId, request.userId))

    @Operation(summary = "팀원 제거", description = "팀에서 사용자를 제거합니다")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "204", description = "제거 성공"),
            ApiResponse(
                responseCode = "404",
                description = "팀 또는 팀원을 찾을 수 없음",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponseBody::class))],
            ),
        ],
    )
    @DeleteMapping("/{teamId}/members/{userId}")
    fun removeMember(
        @PathVariable teamId: Long,
        @PathVariable userId: Long,
    ): ResponseEntity<Void> {
        service.removeMember(teamId, userId)
        return ResponseEntity.noContent().build()
    }
}
