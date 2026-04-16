package com.pluxity.weekly.teams.service

import com.pluxity.weekly.auth.user.repository.UserRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service

private val log = KotlinLogging.logger {}

@Service
class TeamsNotificationService(
    private val messageSender: TeamsMessageSender,
    private val userRepository: UserRepository,
) {
    fun sendDm(
        userId: Long,
        message: String,
    ) {
        val (serviceUrl, conversationId) = findTeamsInfo(userId) ?: return
        messageSender.notify(serviceUrl, conversationId, message)
    }

    fun sendCard(
        userId: Long,
        card: Map<String, Any>,
    ) {
        val (serviceUrl, conversationId) = findTeamsInfo(userId) ?: return
        messageSender.notifyCard(serviceUrl, conversationId, card)
    }

    private fun findTeamsInfo(userId: Long): Pair<String, String>? {
        val user = userRepository.findByIdOrNull(userId)
        val serviceUrl = user?.teamsServiceUrl
        val conversationId = user?.teamsConversationId
        if (serviceUrl == null || conversationId == null) {
            log.warn { "Teams 전송 실패 - userId=$userId 의 Teams 정보 없음" }
            return null
        }
        return serviceUrl to conversationId
    }
}
