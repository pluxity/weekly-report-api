package com.pluxity.weekly.teams.event

import com.pluxity.weekly.teams.entity.TeamsNotificationType

/**
 * Teams Proactive 알림 이벤트.
 *
 * @property userId 알림 대상 사용자 ID (weekly-report userId)
 * @property type 알림 유형 (저장/조회 시 분류 용도)
 * @property message 알림 메시지 (card 가 null 일 때 text 로 전송)
 * @property card Adaptive Card 콘텐츠. null 이면 text 로, 있으면 card 로 전송.
 */
data class TeamsNotificationEvent(
    val userId: Long,
    val type: TeamsNotificationType,
    val message: String,
    val card: Map<String, Any>? = null,
)
