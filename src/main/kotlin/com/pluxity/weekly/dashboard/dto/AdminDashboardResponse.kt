package com.pluxity.weekly.dashboard.dto

import com.pluxity.weekly.project.entity.ProjectStatus
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDate

@Schema(description = "어드민 대시보드 응답")
data class AdminDashboardResponse(
    @field:Schema(description = "프로젝트 카드 목록")
    val projects: List<AdminProjectCard>,
    @field:Schema(description = "팀 요약 목록")
    val teamSummaries: List<TeamSummaryItem>,
)

@Schema(description = "어드민 프로젝트 카드")
data class AdminProjectCard(
    @field:Schema(description = "프로젝트 ID", example = "1")
    val projectId: Long,
    @field:Schema(description = "프로젝트명", example = "알파 프로젝트")
    val projectName: String,
    @field:Schema(description = "PM 이름")
    val pmName: String?,
    @field:Schema(description = "프로젝트 상태", example = "IN_PROGRESS")
    val status: ProjectStatus,
    @field:Schema(description = "전체 진행률 (태스크 평균)", example = "45")
    val progress: Int,
    @field:Schema(description = "에픽 수", example = "5")
    val epicCount: Int,
    @field:Schema(description = "참여 인원 수", example = "8")
    val memberCount: Int,
    @field:Schema(description = "지연 태스크 수", example = "3")
    val delayedTaskCount: Int,
    @field:Schema(description = "시작일", example = "2026-01-01")
    val startDate: LocalDate?,
    @field:Schema(description = "마감일", example = "2026-06-30")
    val dueDate: LocalDate?,
)

@Schema(description = "팀 요약 항목")
data class TeamSummaryItem(
    @field:Schema(description = "팀 ID", example = "1")
    val teamId: Long,
    @field:Schema(description = "팀명", example = "백엔드팀")
    val teamName: String,
    @field:Schema(description = "팀장 이름")
    val leaderName: String?,
    @field:Schema(description = "팀원 수", example = "5")
    val memberCount: Int,
    @field:Schema(description = "진행중 태스크 수 (status != DONE)", example = "12")
    val activeTaskCount: Int,
    @field:Schema(description = "완료율 (%)", example = "65")
    val completionRate: Int,
)
