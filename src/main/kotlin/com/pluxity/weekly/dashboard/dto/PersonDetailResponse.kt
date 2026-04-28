package com.pluxity.weekly.dashboard.dto

import com.pluxity.weekly.task.entity.TaskStatus
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime

@Schema(description = "개인 상세 대시보드 응답")
data class PersonDetailResponse(
    @field:Schema(description = "사용자 ID", example = "1")
    val userId: Long,
    @field:Schema(description = "사용자명", example = "홍길동")
    val userName: String,
    @field:Schema(description = "소속 팀명")
    val department: String?,
    @field:Schema(description = "KPI 지표")
    val kpi: PersonKpi,
    @field:Schema(description = "최근 수정 태스크 10건")
    val recentTasks: List<RecentTaskItem>,
    @field:Schema(description = "프로젝트 참여 현황")
    val projectParticipations: List<ProjectParticipation>,
)

@Schema(description = "개인 KPI")
data class PersonKpi(
    @field:Schema(description = "완료율 (%)", example = "65")
    val completionRate: Int,
    @field:Schema(description = "기한 내 완료율 (%)", example = "80")
    val onTimeRate: Int,
    @field:Schema(description = "평균 지연일 (DONE+지연 기준)", example = "2.5")
    val averageDelayDays: Double,
    @field:Schema(description = "진행중 태스크 수", example = "5")
    val activeTaskCount: Int,
)

@Schema(description = "최근 태스크 항목")
data class RecentTaskItem(
    @field:Schema(description = "태스크 ID", example = "1")
    val taskId: Long,
    @field:Schema(description = "태스크명", example = "DB 설계")
    val taskName: String,
    @field:Schema(description = "업무 그룹명", example = "백엔드 구축")
    val epicName: String,
    @field:Schema(description = "프로젝트명", example = "알파 프로젝트")
    val projectName: String,
    @field:Schema(description = "태스크 상태", example = "IN_PROGRESS")
    val status: TaskStatus,
    @field:Schema(description = "진행률 (0~100)", example = "50")
    val progress: Int,
    @field:Schema(description = "최종 수정일")
    val updatedAt: LocalDateTime,
    @field:Schema(description = "검토 요청일 (REVIEW_REQUEST 상태 기준)")
    val requestDate: LocalDateTime,
)

@Schema(description = "프로젝트 참여 현황")
data class ProjectParticipation(
    @field:Schema(description = "프로젝트 ID", example = "1")
    val projectId: Long,
    @field:Schema(description = "프로젝트명", example = "알파 프로젝트")
    val projectName: String,
    @field:Schema(description = "업무 그룹명", example = "백엔드 구축")
    val epicName: String,
    @field:Schema(description = "해당 업무 그룹 내 본인 태스크 수", example = "5")
    val taskCount: Int,
    @field:Schema(description = "완료 태스크 수", example = "3")
    val completedCount: Int,
)
