package com.pluxity.weekly.dashboard.dto

import com.pluxity.weekly.epic.entity.EpicStatus
import com.pluxity.weekly.project.entity.ProjectStatus
import com.pluxity.weekly.task.entity.TaskStatus
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDate
import java.time.LocalDateTime

@Schema(description = "PM 대시보드 응답")
data class PmDashboardResponse(
    @field:Schema(description = "프로젝트 요약 정보")
    val project: PmProjectSummary,
    @field:Schema(description = "에픽 목록 (간트차트/테이블뷰 공용)")
    val epics: List<PmEpicItem>,
)

@Schema(description = "PM 프로젝트 요약")
data class PmProjectSummary(
    @field:Schema(description = "프로젝트 ID", example = "1")
    val projectId: Long,
    @field:Schema(description = "프로젝트명", example = "알파 프로젝트")
    val projectName: String,
    @field:Schema(description = "PM 이름", example = "홍길동")
    val pmName: String,
    @field:Schema(description = "프로젝트 상태", example = "IN_PROGRESS")
    val status: ProjectStatus,
    @field:Schema(description = "전체 진행률 (태스크 평균)", example = "45")
    val progress: Int,
    @field:Schema(description = "시작일", example = "2026-01-01")
    val startDate: LocalDate?,
    @field:Schema(description = "마감일", example = "2026-06-30")
    val dueDate: LocalDate?,
    @field:Schema(description = "에픽 수", example = "5")
    val epicCount: Int,
    @field:Schema(description = "태스크 수", example = "20")
    val taskCount: Int,
    @field:Schema(description = "참여 인원 수", example = "8")
    val memberCount: Int,
    @field:Schema(description = "최종 수정일")
    val updatedAt: LocalDateTime,
)

@Schema(description = "PM 대시보드 에픽 항목 (간트차트/테이블뷰 공용)")
data class PmEpicItem(
    @field:Schema(description = "에픽 ID", example = "1")
    val epicId: Long,
    @field:Schema(description = "에픽명", example = "백엔드 구축")
    val epicName: String,
    @field:Schema(description = "에픽 상태", example = "IN_PROGRESS")
    val status: EpicStatus,
    @field:Schema(description = "에픽 진행률 (태스크 평균)", example = "45")
    val progress: Int,
    @field:Schema(description = "시작일", example = "2026-01-01")
    val startDate: LocalDate?,
    @field:Schema(description = "마감일", example = "2026-03-31")
    val dueDate: LocalDate?,
    @field:Schema(description = "최종 수정일")
    val updatedAt: LocalDateTime,
    @field:Schema(description = "태스크 목록")
    val tasks: List<PmTaskItem>,
)

@Schema(description = "PM 대시보드 태스크 항목 (간트차트/테이블뷰 공용)")
data class PmTaskItem(
    @field:Schema(description = "태스크 ID", example = "1")
    val taskId: Long,
    @field:Schema(description = "태스크명", example = "DB 설계")
    val taskName: String,
    @field:Schema(description = "태스크 상태", example = "IN_PROGRESS")
    val status: TaskStatus,
    @field:Schema(description = "진행률 (0~100)", example = "50")
    val progress: Int,
    @field:Schema(description = "담당자명")
    val assigneeName: String?,
    @field:Schema(description = "시작일", example = "2026-01-15")
    val startDate: LocalDate?,
    @field:Schema(description = "마감일", example = "2026-02-15")
    val dueDate: LocalDate?,
    @field:Schema(description = "일수 차이 (음수=조기, 양수=지연)")
    val daysDelta: Int?,
    @field:Schema(description = "검토 요청일 (REVIEW_REQUEST 상태 기준)")
    val requestDate: LocalDateTime,
)
