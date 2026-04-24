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
    /** @return null 이면 성공, 값이 있으면 실패 사유 */
    fun sendDm(
        userId: Long,
        message: String,
    ): String? {
        val (serviceUrl, conversationId) = findTeamsInfo(userId) ?: return MISSING_TEAMS_INFO
        return messageSender.notify(serviceUrl, conversationId, message)
    }

    /** @return null 이면 성공, 값이 있으면 실패 사유 */
    fun sendCard(
        userId: Long,
        card: Map<String, Any>,
    ): String? {
        val (serviceUrl, conversationId) = findTeamsInfo(userId) ?: return MISSING_TEAMS_INFO
        return messageSender.notifyCard(serviceUrl, conversationId, card)
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

    companion object {
        private const val MISSING_TEAMS_INFO = "수신자 Teams 연동 정보 없음"
    }
}
