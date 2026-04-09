package com.pluxity.weekly.teams.event

import com.pluxity.weekly.teams.service.TeamsNotificationService
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class TeamsNotificationListener(
    private val notificationService: TeamsNotificationService,
) {
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handleNotification(event: TeamsNotificationEvent) {
        if (event.card != null) {
            notificationService.sendCard(event.userId, event.card)
        } else {
            notificationService.sendDm(event.userId, event.message)
        }
    }
}
