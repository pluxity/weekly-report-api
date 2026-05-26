package com.pluxity.weekly.chat.dto

import com.pluxity.weekly.epic.dto.EpicResponse
import com.pluxity.weekly.project.dto.ProjectResponse
import com.pluxity.weekly.report.dto.WeeklyReportResponse
import com.pluxity.weekly.task.dto.PendingReviewResponse
import com.pluxity.weekly.task.dto.TaskResponse
import com.pluxity.weekly.team.dto.TeamResponse
import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "채팅 응답")
data class ChatActionResponse(
    @field:Schema(description = "액션", example = "create", allowableValues = ["create", "update", "delete", "read", "clarify"])
    val action: String,
    @field:Schema(description = "대상", example = "task", allowableValues = ["project", "epic", "task", "team"])
    val target: String,
    @field:Schema(description = "서버 실행 완료 시 대상 ID", example = "1")
    val id: Long? = null,
    @field:Schema(description = "target별 폼 데이터 (create/update)")
    val dto: ChatDto? = null,
    @field:Schema(description = "미확정 선택 필드 목록 (null이면 서버에서 실행 완료)")
    val selectFields: List<SelectField>? = null,
    @field:Schema(description = "조회 결과 (action=read 시)")
    val readResult: ChatReadResponse? = null,
)

@Schema(description = "조회 결과 (target별 하나만 채워짐)")
data class ChatReadResponse(
    @field:Schema(description = "태스크 목록")
    val tasks: List<TaskResponse>? = null,
    @field:Schema(description = "프로젝트 목록")
    val projects: List<ProjectResponse>? = null,
    @field:Schema(description = "업무 그룹 목록")
    val epics: List<EpicResponse>? = null,
    @field:Schema(description = "팀 목록")
    val teams: List<TeamResponse>? = null,
    @field:Schema(description = "PM 리뷰 대기 목록 (target=review)")
    val pendingReviews: List<PendingReviewResponse>? = null,
    @field:Schema(description = "주간보고 작성 결과 (target=weekly_report)")
    val weeklyReport: WeeklyReportResponse? = null,
)

@Schema(description = "미확정 선택 필드")
data class SelectField(
    @field:Schema(description = "필드명", example = "projectId")
    val field: String,
    @field:Schema(description = "후보 목록 (그룹이 있으면 groups 사용)")
    val candidates: List<Candidate> = emptyList(),
    @field:Schema(description = "그룹화된 후보 목록 (assigneeId 등 에픽별 묶음)")
    val groups: List<CandidateGroup>? = null,
)

@Schema(description = "후보 그룹")
data class CandidateGroup(
    @field:Schema(description = "그룹 라벨", example = "백엔드 구축")
    val label: String,
    @field:Schema(description = "그룹 ID (선택)", example = "100")
    val id: Long? = null,
    @field:Schema(description = "그룹 내 후보")
    val candidates: List<Candidate>,
)
