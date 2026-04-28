package com.pluxity.weekly.epic.dto

import com.fasterxml.jackson.annotation.JsonUnwrapped
import com.pluxity.weekly.core.response.BaseResponse
import com.pluxity.weekly.core.response.toBaseResponse
import com.pluxity.weekly.epic.entity.Epic
import com.pluxity.weekly.epic.entity.EpicStatus
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDate

@Schema(description = "업무 그룹 응답")
data class EpicResponse(
    @field:Schema(description = "ID", example = "1")
    val id: Long,
    @field:Schema(description = "프로젝트 ID", example = "1")
    val projectId: Long,
    @field:Schema(description = "프로젝트명", example = "Safers")
    val projectName: String,
    @field:Schema(description = "업무 그룹명", example = "사용자 인증 모듈")
    val name: String,
    @field:Schema(description = "설명", example = "업무 그룹 설명입니다")
    val description: String?,
    @field:Schema(description = "상태", example = "TODO")
    val status: EpicStatus,
    @field:Schema(description = "시작일", example = "2026-01-01")
    val startDate: LocalDate?,
    @field:Schema(description = "마감일", example = "2026-03-31")
    val dueDate: LocalDate?,
    @field:Schema(description = "배정된 사용자 목록")
    val members: List<EpicMemberResponse>,
    @field:JsonUnwrapped
    val baseResponse: BaseResponse,
)

@Schema(description = "업무 그룹 배정 사용자 정보")
data class EpicMemberResponse(
    @field:Schema(description = "사용자 ID", example = "1")
    val userId: Long,
    @field:Schema(description = "사용자명", example = "홍길동")
    val userName: String,
)

fun Epic.toResponse(): EpicResponse =
    EpicResponse(
        id = this.requiredId,
        projectId = this.project.requiredId,
        projectName = this.project.name,
        name = this.name,
        description = this.description,
        status = this.status,
        startDate = this.startDate,
        dueDate = this.dueDate,
        members =
            this.assignments.map {
                EpicMemberResponse(
                    userId = it.user.requiredId,
                    userName = it.user.name,
                )
            },
        baseResponse = this.toBaseResponse(),
    )
