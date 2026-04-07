package com.pluxity.weekly.project.dto

import com.fasterxml.jackson.annotation.JsonUnwrapped
import com.pluxity.weekly.core.response.BaseResponse
import com.pluxity.weekly.core.response.toBaseResponse
import com.pluxity.weekly.project.entity.Project
import com.pluxity.weekly.project.entity.ProjectStatus
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDate

@Schema(description = "프로젝트 응답")
data class ProjectResponse(
    @field:Schema(description = "ID", example = "1")
    val id: Long,
    @field:Schema(description = "프로젝트명", example = "2026 1분기 프로젝트")
    val name: String,
    @field:Schema(description = "설명", example = "프로젝트 설명입니다")
    val description: String?,
    @field:Schema(description = "상태", example = "TODO")
    val status: ProjectStatus,
    @field:Schema(description = "시작일", example = "2026-01-01")
    val startDate: LocalDate?,
    @field:Schema(description = "마감일", example = "2026-03-31")
    val dueDate: LocalDate?,
    @field:Schema(description = "담당 PM 사용자 ID", example = "1")
    val pmId: Long?,
    @field:Schema(description = "담당 PM 이름", example = "홍길동")
    val pmName: String?,
    @field:Schema(description = "참여자 목록")
    val members: List<ProjectMemberResponse>,
    @field:JsonUnwrapped
    val baseResponse: BaseResponse,
)

@Schema(description = "프로젝트 참여자 정보")
data class ProjectMemberResponse(
    @field:Schema(description = "프로젝트 ID", example = "1")
    val projectId: Long,
    @field:Schema(description = "사용자 ID", example = "1")
    val userId: Long,
    @field:Schema(description = "사용자명", example = "홍길동")
    val userName: String,
    @field:Schema(description = "소속 팀 ID", example = "1")
    val teamId: Long?,
    @field:Schema(description = "소속 팀명", example = "개발팀")
    val teamName: String?,
)

fun Project.toResponse(
    memberInfos: List<ProjectMemberResponse> = emptyList(),
    pmName: String? = null,
): ProjectResponse =
    ProjectResponse(
        id = this.requiredId,
        name = this.name,
        description = this.description,
        status = this.status,
        startDate = this.startDate,
        dueDate = this.dueDate,
        pmId = this.pmId,
        pmName = pmName,
        members = memberInfos,
        baseResponse = this.toBaseResponse(),
    )
