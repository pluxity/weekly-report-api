package com.pluxity.weekly.teams.event

import com.pluxity.weekly.teams.service.TeamsNotificationLogService
import com.pluxity.weekly.teams.service.TeamsNotificationService
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class TeamsNotificationListener(
    private val notificationService: TeamsNotificationService,
    private val logService: TeamsNotificationLogService,
) {
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handleNotification(event: TeamsNotificationEvent) {
        val logId = event.logId

        val failReason =
            runCatching {
                if (event.card != null) {
                    notificationService.sendCard(event.userId, event.card)
                } else {
                    notificationService.sendDm(event.userId, event.message)
                }
            }.getOrElse { e -> e.message ?: e.javaClass.simpleName }

        if (failReason == null) {
            logService.markSent(logId)
        } else {
            logService.markFailed(logId, failReason)
        }
    }
}
