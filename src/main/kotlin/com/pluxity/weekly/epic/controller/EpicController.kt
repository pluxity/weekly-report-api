package com.pluxity.weekly.epic.controller

import com.pluxity.weekly.core.annotation.ResponseCreated
import com.pluxity.weekly.core.response.DataResponseBody
import com.pluxity.weekly.core.response.ErrorResponseBody
import com.pluxity.weekly.epic.dto.EpicAssignmentResponse
import com.pluxity.weekly.epic.dto.EpicRequest
import com.pluxity.weekly.epic.dto.EpicResponse
import com.pluxity.weekly.epic.dto.EpicUpdateRequest
import com.pluxity.weekly.epic.service.EpicAssignmentService
import com.pluxity.weekly.epic.service.EpicService
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
@RequestMapping("/epics")
@Tag(name = "Epic Controller", description = "업무 그룹 관리 API")
class EpicController(
    private val service: EpicService,
    private val assignmentService: EpicAssignmentService,
) {
    @Operation(summary = "업무 그룹 전체 조회", description = "업무 그룹 전체 목록을 조회합니다")
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
    fun findAll(): ResponseEntity<DataResponseBody<List<EpicResponse>>> = ResponseEntity.ok(DataResponseBody(service.findAll()))

    @Operation(summary = "업무 그룹 단건 조회", description = "ID로 업무 그룹을 조회합니다")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "조회 성공"),
            ApiResponse(
                responseCode = "404",
                description = "업무 그룹을 찾을 수 없음",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponseBody::class))],
            ),
        ],
    )
    @GetMapping("/{id}")
    fun findById(
        @PathVariable id: Long,
    ): ResponseEntity<DataResponseBody<EpicResponse>> = ResponseEntity.ok(DataResponseBody(service.findById(id)))

    @Operation(summary = "업무 그룹 등록", description = "새로운 업무 그룹을 등록합니다")
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
    @ResponseCreated(path = "/epics/{id}")
    fun create(
        @RequestBody @Valid request: EpicRequest,
    ): ResponseEntity<Long> = ResponseEntity.ok(service.create(request))

    @Operation(summary = "업무 그룹 부분 수정", description = "전달된 필드만 수정합니다")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "204", description = "수정 성공"),
            ApiResponse(
                responseCode = "404",
                description = "업무 그룹을 찾을 수 없음",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponseBody::class))],
            ),
        ],
    )
    @PatchMapping("/{id}")
    fun update(
        @PathVariable id: Long,
        @RequestBody @Valid request: EpicUpdateRequest,
    ): ResponseEntity<Void> {
        service.update(id, request)
        return ResponseEntity.noContent().build()
    }

    @Operation(summary = "업무 그룹 삭제", description = "업무 그룹을 삭제합니다")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "204", description = "삭제 성공"),
            ApiResponse(
                responseCode = "404",
                description = "업무 그룹을 찾을 수 없음",
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

    @Operation(summary = "업무 그룹 복구", description = "소프트 삭제된 업무 그룹과 하위 태스크를 복구합니다")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "204", description = "복구 성공"),
            ApiResponse(
                responseCode = "403",
                description = "권한 없음",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponseBody::class))],
            ),
            ApiResponse(
                responseCode = "404",
                description = "업무 그룹을 찾을 수 없음",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponseBody::class))],
            ),
        ],
    )
    @PostMapping("/{id}/restore")
    fun restore(
        @PathVariable id: Long,
    ): ResponseEntity<Void> {
        service.restore(id)
        return ResponseEntity.noContent().build()
    }

    @Operation(summary = "업무 그룹 배정 목록 조회", description = "업무 그룹에 배정된 사용자 목록을 조회합니다")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "조회 성공"),
            ApiResponse(
                responseCode = "404",
                description = "업무 그룹을 찾을 수 없음",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponseBody::class))],
            ),
        ],
    )
    @GetMapping("/{epicId}/assignments")
    fun findAssignments(
        @PathVariable epicId: Long,
    ): ResponseEntity<DataResponseBody<List<EpicAssignmentResponse>>> =
        ResponseEntity.ok(DataResponseBody(assignmentService.findByEpic(epicId)))

    @Operation(summary = "업무 그룹 사용자 배정", description = "업무 그룹에 사용자를 배정합니다")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "204", description = "배정 성공"),
            ApiResponse(
                responseCode = "400",
                description = "이미 배정된 사용자",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponseBody::class))],
            ),
            ApiResponse(
                responseCode = "404",
                description = "업무 그룹 또는 사용자를 찾을 수 없음",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponseBody::class))],
            ),
        ],
    )
    @PostMapping("/{epicId}/assignments/{userId}")
    fun assign(
        @PathVariable epicId: Long,
        @PathVariable userId: Long,
    ): ResponseEntity<Void> {
        assignmentService.assign(epicId, userId)
        return ResponseEntity.noContent().build()
    }

    @Operation(summary = "업무 그룹 사용자 배정 해제", description = "업무 그룹에서 사용자 배정을 해제합니다")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "204", description = "해제 성공"),
            ApiResponse(
                responseCode = "404",
                description = "업무 그룹 또는 배정을 찾을 수 없음",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponseBody::class))],
            ),
        ],
    )
    @DeleteMapping("/{epicId}/assignments/{userId}")
    fun unassign(
        @PathVariable epicId: Long,
        @PathVariable userId: Long,
    ): ResponseEntity<Void> {
        assignmentService.unassign(epicId, userId)
        return ResponseEntity.noContent().build()
    }
}
