package com.pluxity.weekly.teams.service

import com.pluxity.weekly.auth.authorization.AuthorizationService
import com.pluxity.weekly.auth.user.repository.UserRepository
import com.pluxity.weekly.teams.converter.AdaptiveCardConverter
import com.pluxity.weekly.teams.dto.Activity
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service

private val log = KotlinLogging.logger {}

@Service
class TeamsMessageHandler(
    private val asyncChatHandler: AsyncChatHandler,
    private val cardConverter: AdaptiveCardConverter,
    private val messageSender: TeamsMessageSender,
    private val authorizationService: AuthorizationService,
    private val userRepository: UserRepository,
) {
    fun handleActivity(activity: Activity) {
        when (activity.type) {
            "message" -> {
                messageSender.sendTyping(activity)
                asyncChatHandler.handleMessage(activity)
            }
            "installationUpdate" -> handleInstallationUpdate(activity)
            else -> log.debug { "Unhandled activity type: ${activity.type}" }
        }
    }

    /**
     *  Teams App 설치시 받는 메시지
     */
    private fun handleInstallationUpdate(activity: Activity) {
        val action = activity.action ?: "unknown"
        log.info { "Installation update - action: $action, user: ${activity.from?.name}" }

        val serviceUrl = activity.serviceUrl
        val conversationId = activity.conversation?.id
        val aadObjectId = activity.from?.aadObjectId
        if (serviceUrl.isNullOrBlank() || conversationId.isNullOrBlank()) {
            log.warn { "serviceUrl 또는 conversationId 누락 - conversationReference 저장 불가" }
        } else if (!aadObjectId.isNullOrBlank()) {
            val currentUser = authorizationService.currentUser()
            currentUser.updateTeamsInfo(aadObjectId, serviceUrl, conversationId)
            userRepository.save(currentUser)
        } else {
            log.warn { "aadObjectId 누락 - Teams 정보 저장 불가" }
        }

        if (action == "add") {
            messageSender.reply(
                activity,
                cardConverter.textMessage("안녕하세요! Weekly Report 봇입니다. 자연어로 태스크를 관리해보세요."),
            )
        }
    }
}
