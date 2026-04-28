package com.pluxity.weekly.epic.dto

import com.pluxity.weekly.epic.entity.EpicAssignment
import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "업무 그룹 배정 응답")
data class EpicAssignmentResponse(
    @field:Schema(description = "배정 ID", example = "1")
    val id: Long,
    @field:Schema(description = "업무 그룹 ID", example = "1")
    val epicId: Long,
    @field:Schema(description = "배정된 사용자 ID", example = "1")
    val userId: Long,
)

fun EpicAssignment.toResponse(): EpicAssignmentResponse =
    EpicAssignmentResponse(
        id = this.requiredId,
        epicId = this.epic.requiredId,
        userId = this.user.requiredId,
    )
