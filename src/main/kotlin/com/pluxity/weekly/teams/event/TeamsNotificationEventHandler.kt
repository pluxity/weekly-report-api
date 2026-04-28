package com.pluxity.weekly.teams.event

import com.pluxity.weekly.epic.event.EpicAssignedEvent
import com.pluxity.weekly.epic.event.EpicUnassignedEvent
import com.pluxity.weekly.task.event.TaskApprovedEvent
import com.pluxity.weekly.task.event.TaskRejectedEvent
import com.pluxity.weekly.task.event.TaskReviewRequestedEvent
import com.pluxity.weekly.teams.converter.TaskReviewCardBuilder
import com.pluxity.weekly.teams.entity.TeamsNotificationType
import com.pluxity.weekly.teams.service.TeamsNotificationLogService
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

/**
 * 도메인 이벤트를 수신해 같은 트랜잭션에서 알림 로그를 영속화하고,
 * 외부 발사용 [TeamsNotificationEvent] 를 재발행한다.
 *
 * 트랜잭션 경계:
 * - 이 핸들러는 도메인 트랜잭션과 동일한 TX 에서 동기 실행 → 도메인 변경 ↔ 알림 로그 저장의 원자성 보장
 * - 실제 외부 API 호출은 [TeamsNotificationListener] 가 AFTER_COMMIT + @Async 로 처리
 *
 * 주의: `@TransactionalEventListener` 로 바꾸면 원자성이 깨진다.
 */
@Component
class TeamsNotificationEventHandler(
    private val logService: TeamsNotificationLogService,
    private val eventPublisher: ApplicationEventPublisher,
    private val taskReviewCardBuilder: TaskReviewCardBuilder,
) {
    @EventListener
    fun on(event: EpicAssignedEvent) {
        publishNotification(
            userId = event.userId,
            type = TeamsNotificationType.EPIC_ASSIGN,
            message = "${event.epicName} 업무 그룹에 배정되었습니다.",
        )
    }

    @EventListener
    fun on(event: EpicUnassignedEvent) {
        publishNotification(
            userId = event.userId,
            type = TeamsNotificationType.EPIC_UNASSIGN,
            message = "${event.epicName} 업무 그룹 배정이 해제되었습니다.",
        )
    }

    @EventListener
    fun on(event: TaskReviewRequestedEvent) {
        val card =
            taskReviewCardBuilder.build(
                taskId = event.taskId,
                taskName = event.taskName,
                projectName = event.projectName,
                epicName = event.epicName,
                requesterName = event.requesterName,
            )

        publishNotification(
            userId = event.pmId,
            type = TeamsNotificationType.TASK_REVIEW_REQUEST,
            message = "[리뷰 요청] '${event.taskName}' 태스크가 리뷰 요청되었습니다. 요청자: ${event.requesterName}",
            card = card,
        )
    }

    @EventListener
    fun on(event: TaskApprovedEvent) {
        publishNotification(
            userId = event.userId,
            type = TeamsNotificationType.TASK_APPROVE,
            message = "[승인] '${event.taskName}' 태스크가 승인되었습니다.",
        )
    }

    @EventListener
    fun on(event: TaskRejectedEvent) {
        val reasonSuffix = event.reason?.let { " 사유: $it" } ?: ""
        publishNotification(
            userId = event.userId,
            type = TeamsNotificationType.TASK_REJECT,
            message = "[반려] '${event.taskName}' 태스크가 반려되었습니다.$reasonSuffix",
        )
    }

    private fun publishNotification(
        userId: Long,
        type: TeamsNotificationType,
        message: String,
        card: Map<String, Any>? = null,
    ) {
        val log =
            logService.savePending(
                userId = userId,
                type = type,
                message = message,
            )

        eventPublisher.publishEvent(
            TeamsNotificationEvent(
                logId = log.requiredId,
                userId = userId,
                message = message,
                card = card,
            ),
        )
    }
}
