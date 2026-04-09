package com.pluxity.weekly.teams.service

import com.pluxity.weekly.teams.repository.TeamsAccountRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service

private val log = KotlinLogging.logger {}

@Service
class TeamsNotificationService(
    private val messageSender: TeamsMessageSender,
    private val referenceRepository: TeamsAccountRepository,
) {
    fun sendDm(
        userId: Long,
        message: String,
    ) {
        val reference = referenceRepository.findByUserId(userId)
        if (reference == null) {
            log.warn { "Teams 알림 전송 실패 - userId=$userId 의 conversationReference 없음" }
            return
        }

        messageSender.notify(reference.serviceUrl, reference.conversationId, message)
    }

    fun sendCard(
        userId: Long,
        card: Map<String, Any>,
    ) {
        val reference = referenceRepository.findByUserId(userId)
        if (reference == null) {
            log.warn { "Teams 카드 전송 실패 - userId=$userId 의 conversationReference 없음" }
            return
        }

        messageSender.notifyCard(reference.serviceUrl, reference.conversationId, card)
    }
}
