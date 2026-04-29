package com.pluxity.weekly.teams.event

/**
 * Teams Proactive 알림 이벤트.
 *
 * @property logId 저장된 로그 ID (teams 발송 후 상태 변경 용)
 * @property userId 알림 대상 사용자 ID (weekly-report userId)
 * @property message 알림 메시지 (card 가 null 일 때 text 로 전송)
 * @property card Adaptive Card 콘텐츠. null 이면 text 로, 있으면 card 로 전송.
 */
data class TeamsNotificationEvent(
    val logId: Long,
    val userId: Long,
    val message: String,
    val card: Map<String, Any>? = null,
)
