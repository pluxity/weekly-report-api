package com.pluxity.weekly.teams.dto

import com.pluxity.weekly.teams.entity.TeamsNotificationLog
import com.pluxity.weekly.teams.entity.TeamsNotificationStatus
import com.pluxity.weekly.teams.entity.TeamsNotificationType
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime

@Schema(description = "Teams 알림 로그 응답")
data class TeamsNotificationLogResponse(
    @field:Schema(description = "로그 ID", example = "1")
    val id: Long,
    @field:Schema(description = "수신자 사용자 ID", example = "10")
    val userId: Long,
    @field:Schema(description = "알림 유형", example = "TASK_REVIEW_REQUEST")
    val type: TeamsNotificationType,
    @field:Schema(description = "알림 메시지")
    val message: String,
    @field:Schema(description = "발송 상태", example = "SENT")
    val status: TeamsNotificationStatus,
    @field:Schema(description = "실패 사유 (상태가 FAILED 일 때)")
    val failReason: String?,
    @field:Schema(description = "생성 일시")
    val createdAt: LocalDateTime,
)

fun TeamsNotificationLog.toResponse(): TeamsNotificationLogResponse =
    TeamsNotificationLogResponse(
        id = this.requiredId,
        userId = this.userId,
        type = this.type,
        message = this.message,
        status = this.status,
        failReason = this.failReason,
        createdAt = this.createdAt,
    )
