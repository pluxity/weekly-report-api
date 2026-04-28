package com.pluxity.weekly.task.dto

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDate
import java.time.LocalDateTime

@Schema(description = "검수 대기 태스크 응답 (PM 리뷰 큐)")
data class PendingReviewResponse(
    @field:Schema(description = "태스크 ID", example = "42")
    val taskId: Long,
    @field:Schema(description = "태스크명", example = "로그인 API 개발")
    val taskName: String,
    @field:Schema(description = "설명")
    val description: String?,
    @field:Schema(description = "프로젝트 ID", example = "1")
    val projectId: Long,
    @field:Schema(description = "프로젝트명", example = "SAFERS 관제 시스템")
    val projectName: String,
    @field:Schema(description = "업무 그룹 ID", example = "10")
    val epicId: Long,
    @field:Schema(description = "업무 그룹명", example = "기획")
    val epicName: String,
    @field:Schema(description = "담당자 ID", example = "5")
    val assigneeId: Long?,
    @field:Schema(description = "담당자 이름", example = "김담당")
    val assigneeName: String?,
    @field:Schema(description = "마감일", example = "2026-03-31")
    val dueDate: LocalDate?,
    @field:Schema(description = "검수 요청 시각 (마지막 REVIEW_REQUEST 로그 기준)")
    val reviewRequestedAt: LocalDateTime,
    @field:Schema(description = "수행 가능한 액션(승인/반려) URL")
    val actions: PendingReviewActions,
)

@Schema(description = "검수 대기 태스크의 액션 URL")
data class PendingReviewActions(
    val approve: ActionLink,
    val reject: ActionLink,
)

@Schema(description = "액션 링크")
data class ActionLink(
    @field:Schema(description = "HTTP 메서드", example = "POST")
    val method: String,
    @field:Schema(description = "호출 URL", example = "/tasks/42/approve")
    val url: String,
)
