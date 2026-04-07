package com.pluxity.weekly.teams.event

/**
 * Teams Proactive 알림 이벤트.
 *
 * @property userId 알림 대상 사용자 ID (weekly-report userId)
 * @property message 알림 메시지
 */
data class TeamsNotificationEvent(
    val userId: Long,
    val message: String,
)
