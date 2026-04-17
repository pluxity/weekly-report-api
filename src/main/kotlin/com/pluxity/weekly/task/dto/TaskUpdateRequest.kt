package com.pluxity.weekly.task.dto

import com.pluxity.weekly.task.entity.TaskStatus
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.Size
import java.time.LocalDate

@Schema(description = "태스크 수정 요청 (null인 필드는 변경하지 않음)")
data class TaskUpdateRequest(
    @field:Schema(description = "태스크명", example = "로그인 API 개발")
    @field:Size(max = 255, message = "태스크명은 최대 255자까지 입력 가능합니다")
    val name: String? = null,
    @field:Schema(description = "설명")
    @field:Size(max = 1000, message = "설명은 최대 1000자까지 입력 가능합니다")
    val description: String? = null,
    @field:Schema(description = "상태", example = "TODO")
    val status: TaskStatus? = null,
    @field:Schema(description = "진행률 (0~100)", example = "0")
    @field:Min(value = 0, message = "진행률은 0 이상이어야 합니다")
    @field:Max(value = 100, message = "진행률은 100 이하이어야 합니다")
    val progress: Int? = null,
    @field:Schema(description = "시작일", example = "2026-01-01")
    val startDate: LocalDate? = null,
    @field:Schema(description = "마감일", example = "2026-03-31")
    val dueDate: LocalDate? = null,
    @field:Schema(description = "담당자 ID", example = "1")
    val assigneeId: Long? = null,
)
