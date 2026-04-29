package com.pluxity.weekly.task.controller

import com.pluxity.weekly.core.annotation.ResponseCreated
import com.pluxity.weekly.core.response.DataResponseBody
import com.pluxity.weekly.core.response.ErrorResponseBody
import com.pluxity.weekly.task.dto.PendingReviewResponse
import com.pluxity.weekly.task.dto.TaskApprovalLogResponse
import com.pluxity.weekly.task.dto.TaskRejectRequest
import com.pluxity.weekly.task.dto.TaskRequest
import com.pluxity.weekly.task.dto.TaskResponse
import com.pluxity.weekly.task.dto.TaskUpdateRequest
import com.pluxity.weekly.task.service.TaskReviewService
import com.pluxity.weekly.task.service.TaskService
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
@RequestMapping("/tasks")
@Tag(name = "Task Controller", description = "태스크 관리 API")
class TaskController(
    private val service: TaskService,
    private val reviewService: TaskReviewService,
) {
    @Operation(summary = "태스크 전체 조회", description = "태스크 전체 목록을 조회합니다")
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
    fun findAll(): ResponseEntity<DataResponseBody<List<TaskResponse>>> = ResponseEntity.ok(DataResponseBody(service.findAll()))

    @Operation(summary = "태스크 단건 조회", description = "ID로 태스크를 조회합니다")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "조회 성공"),
            ApiResponse(
                responseCode = "404",
                description = "태스크를 찾을 수 없음",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponseBody::class))],
            ),
        ],
    )
    @GetMapping("/{id}")
    fun findById(
        @PathVariable id: Long,
    ): ResponseEntity<DataResponseBody<TaskResponse>> = ResponseEntity.ok(DataResponseBody(service.findById(id)))

    @Operation(summary = "태스크 등록", description = "새로운 태스크를 등록합니다")
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
    @ResponseCreated(path = "/tasks/{id}")
    fun create(
        @RequestBody @Valid request: TaskRequest,
    ): ResponseEntity<Long> = ResponseEntity.ok(service.create(request))

    @Operation(summary = "태스크 부분 수정", description = "전달된 필드만 수정합니다")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "204", description = "수정 성공"),
            ApiResponse(
                responseCode = "404",
                description = "태스크를 찾을 수 없음",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponseBody::class))],
            ),
        ],
    )
    @PatchMapping("/{id}")
    fun update(
        @PathVariable id: Long,
        @RequestBody @Valid request: TaskUpdateRequest,
    ): ResponseEntity<Void> {
        service.update(id, request)
        return ResponseEntity.noContent().build()
    }

    @Operation(summary = "태스크 리뷰 요청", description = "담당자가 본인 태스크를 IN_REVIEW 로 전이시키고 PM 에게 알림을 발송합니다")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "204", description = "리뷰 요청 성공"),
            ApiResponse(
                responseCode = "400",
                description = "잘못된 상태 전이",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponseBody::class))],
            ),
        ],
    )
    @PostMapping("/{id}/review-request")
    fun requestReview(
        @PathVariable id: Long,
    ): ResponseEntity<Void> {
        reviewService.requestReview(id)
        return ResponseEntity.noContent().build()
    }

    @Operation(summary = "태스크 승인", description = "PM 이 리뷰 중인 태스크를 승인하여 DONE 으로 전이시킵니다")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "204", description = "승인 성공"),
            ApiResponse(
                responseCode = "400",
                description = "잘못된 상태 전이",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponseBody::class))],
            ),
        ],
    )
    @PostMapping("/{id}/approve")
    fun approve(
        @PathVariable id: Long,
    ): ResponseEntity<Void> {
        reviewService.approve(id)
        return ResponseEntity.noContent().build()
    }

    @Operation(summary = "태스크 반려", description = "PM 이 리뷰 중인 태스크를 반려하여 IN_PROGRESS 로 복귀시킵니다")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "204", description = "반려 성공"),
            ApiResponse(
                responseCode = "400",
                description = "잘못된 요청",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponseBody::class))],
            ),
        ],
    )
    @PostMapping("/{id}/reject")
    fun reject(
        @PathVariable id: Long,
        @RequestBody @Valid request: TaskRejectRequest,
    ): ResponseEntity<Void> {
        reviewService.reject(id, request.reason)
        return ResponseEntity.noContent().build()
    }

    @Operation(
        summary = "리뷰 대기 조회",
        description = "현재 로그인한 PM/ADMIN 에게 들어온 IN_REVIEW 태스크 목록을 오래 기다린 순으로 조회합니다",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "조회 성공"),
            ApiResponse(
                responseCode = "403",
                description = "권한 없음 (PM/ADMIN 아님)",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponseBody::class))],
            ),
        ],
    )
    @GetMapping("/pending-reviews")
    fun findPendingReviews(): ResponseEntity<DataResponseBody<List<PendingReviewResponse>>> =
        ResponseEntity.ok(DataResponseBody(reviewService.findPendingReviews()))

    @Operation(summary = "태스크 승인 로그 조회", description = "태스크의 전체 리뷰/승인/반려 이력을 시간순으로 조회합니다")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "조회 성공"),
        ],
    )
    @GetMapping("/{id}/approval-logs")
    fun findApprovalLogs(
        @PathVariable id: Long,
    ): ResponseEntity<DataResponseBody<List<TaskApprovalLogResponse>>> =
        ResponseEntity.ok(DataResponseBody(reviewService.findApprovalLogs(id)))

    @Operation(summary = "태스크 삭제", description = "태스크를 삭제합니다")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "204", description = "삭제 성공"),
            ApiResponse(
                responseCode = "404",
                description = "태스크를 찾을 수 없음",
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

    @Operation(summary = "태스크 복구", description = "소프트 삭제된 태스크를 복구합니다")
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
                description = "태스크를 찾을 수 없음",
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
}
