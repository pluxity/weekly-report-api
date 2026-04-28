package com.pluxity.weekly.task.dto

import com.fasterxml.jackson.annotation.JsonUnwrapped
import com.pluxity.weekly.core.response.BaseResponse
import com.pluxity.weekly.core.response.toBaseResponse
import com.pluxity.weekly.task.entity.Task
import com.pluxity.weekly.task.entity.TaskStatus
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDate

@Schema(description = "태스크 응답")
data class TaskResponse(
    @field:Schema(description = "ID", example = "1")
    val id: Long,
    @field:Schema(description = "프로젝트 ID", example = "1")
    val projectId: Long,
    @field:Schema(description = "프로젝트명", example = "SAFERS 관제 시스템")
    val projectName: String,
    @field:Schema(description = "업무 그룹 ID", example = "1")
    val epicId: Long,
    @field:Schema(description = "업무 그룹명", example = "기획")
    val epicName: String,
    @field:Schema(description = "태스크명", example = "로그인 API 개발")
    val name: String,
    @field:Schema(description = "설명", example = "태스크 설명입니다")
    val description: String?,
    @field:Schema(description = "상태", example = "TODO")
    val status: TaskStatus,
    @field:Schema(description = "진행률 (0~100)", example = "0")
    val progress: Int,
    @field:Schema(description = "시작일", example = "2026-01-01")
    val startDate: LocalDate?,
    @field:Schema(description = "마감일", example = "2026-03-31")
    val dueDate: LocalDate?,
    @field:Schema(description = "담당자 ID", example = "1")
    val assigneeId: Long?,
    @field:Schema(description = "담당자 이름", example = "홍길동")
    val assigneeName: String?,
    @field:JsonUnwrapped
    val baseResponse: BaseResponse,
)

fun Task.toResponse(): TaskResponse =
    TaskResponse(
        id = this.requiredId,
        epicId = this.epic.requiredId,
        epicName = this.epic.name,
        projectId = this.epic.project.requiredId,
        projectName = this.epic.project.name,
        name = this.name,
        description = this.description,
        status = this.status,
        progress = this.progress,
        startDate = this.startDate,
        dueDate = this.dueDate,
        assigneeId = this.assignee?.id,
        assigneeName = this.assignee?.name,
        baseResponse = this.toBaseResponse(),
    )
