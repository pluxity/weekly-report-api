package com.pluxity.weekly.task.dto

import com.pluxity.weekly.task.entity.TaskApprovalAction
import com.pluxity.weekly.task.entity.TaskApprovalLog
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime

@Schema(description = "태스크 승인 로그 응답")
data class TaskApprovalLogResponse(
    @field:Schema(description = "로그 ID", example = "1")
    val id: Long,
    @field:Schema(description = "태스크 ID", example = "1")
    val taskId: Long,
    @field:Schema(description = "액션 수행자 ID", example = "1")
    val actorId: Long,
    @field:Schema(description = "액션 수행자 이름", example = "홍길동")
    val actorName: String,
    @field:Schema(description = "액션", example = "APPROVE")
    val action: TaskApprovalAction,
    @field:Schema(description = "반려 사유", example = "요구사항 불충족")
    val reason: String?,
    @field:Schema(description = "생성 일시")
    val createdAt: LocalDateTime,
)

fun TaskApprovalLog.toResponse(): TaskApprovalLogResponse =
    TaskApprovalLogResponse(
        id = this.requiredId,
        taskId = this.task.requiredId,
        actorId = this.actor.requiredId,
        actorName = this.actor.name,
        action = this.action,
        reason = this.reason,
        createdAt = this.createdAt,
    )
