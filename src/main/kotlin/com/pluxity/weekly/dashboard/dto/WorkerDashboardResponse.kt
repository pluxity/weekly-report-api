package com.pluxity.weekly.dashboard.dto

import com.pluxity.weekly.epic.entity.EpicStatus
import com.pluxity.weekly.task.entity.TaskStatus
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDate
import java.time.LocalDateTime

@Schema(description = "작업자 대시보드 응답")
data class WorkerDashboardResponse(
    @field:Schema(description = "요약 정보")
    val summary: WorkerSummary,
    @field:Schema(description = "배정된 에픽 목록")
    val epics: List<WorkerEpicItem>,
)

@Schema(description = "작업자 요약 정보")
data class WorkerSummary(
    @field:Schema(description = "마감 7일 이내 태스크 수", example = "3")
    val approachingDeadline: Int,
    @field:Schema(description = "진행중 태스크 수", example = "5")
    val inProgress: Int,
    @field:Schema(description = "완료 태스크 수", example = "10")
    val completed: Int,
    @field:Schema(description = "전체 태스크 수", example = "20")
    val total: Int,
)

@Schema(description = "작업자 에픽 항목")
data class WorkerEpicItem(
    @field:Schema(description = "에픽 ID", example = "1")
    val epicId: Long,
    @field:Schema(description = "에픽명", example = "백엔드 구축")
    val epicName: String,
    @field:Schema(description = "프로젝트 ID", example = "1")
    val projectId: Long,
    @field:Schema(description = "프로젝트명", example = "알파 프로젝트")
    val projectName: String,
    @field:Schema(description = "에픽 상태", example = "IN_PROGRESS")
    val status: EpicStatus,
    @field:Schema(description = "에픽 진행률 (본인 태스크 평균)", example = "45")
    val progress: Int,
    @field:Schema(description = "시작일", example = "2026-01-01")
    val startDate: LocalDate?,
    @field:Schema(description = "마감일", example = "2026-03-31")
    val dueDate: LocalDate?,
    @field:Schema(description = "최종 수정일")
    val updatedAt: LocalDateTime,
    @field:Schema(description = "본인 태스크 목록")
    val tasks: List<WorkerTaskItem>,
)

@Schema(description = "작업자 태스크 항목")
data class WorkerTaskItem(
    @field:Schema(description = "태스크 ID", example = "1")
    val taskId: Long,
    @field:Schema(description = "태스크명", example = "DB 설계")
    val taskName: String,
    @field:Schema(description = "태스크 상태", example = "IN_PROGRESS")
    val status: TaskStatus,
    @field:Schema(description = "진행률 (0~100)", example = "50")
    val progress: Int,
    @field:Schema(description = "마감일", example = "2026-03-31")
    val dueDate: LocalDate?,
    @field:Schema(description = "마감까지 남은 일수 (음수 = 초과)", example = "5")
    val daysUntilDue: Int?,
    @field:Schema(description = "검토 요청일 (REVIEW_REQUEST 상태 기준)")
    val requestDate: LocalDateTime,
)
