package com.pluxity.weekly.teams.service

import com.pluxity.weekly.authorization.AuthorizationService
import com.pluxity.weekly.teams.converter.AdaptiveCardConverter
import com.pluxity.weekly.teams.dto.Activity
import com.pluxity.weekly.teams.entity.TeamsAccount
import com.pluxity.weekly.teams.repository.TeamsAccountRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service

private val log = KotlinLogging.logger {}

@Service
class TeamsMessageHandler(
    private val asyncChatHandler: AsyncChatHandler,
    private val cardConverter: AdaptiveCardConverter,
    private val messageSender: TeamsMessageSender,
    private val authorizationService: AuthorizationService,
    private val teamsAccountRepository: TeamsAccountRepository,
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
        } else {
            val currentUser = authorizationService.currentUser()
            val existing = teamsAccountRepository.findByUserId(currentUser.requiredId)
            if (existing != null) {
                existing.update(serviceUrl, conversationId)
            } else if (!aadObjectId.isNullOrBlank()) {
                teamsAccountRepository.save(
                    TeamsAccount(
                        aadObjectId = aadObjectId,
                        userId = currentUser.requiredId,
                        serviceUrl = serviceUrl,
                        conversationId = conversationId,
                    ),
                )
            } else {
                log.warn { "aadObjectId 누락 - TeamsAccount 저장 불가" }
            }
        }

        if (action == "add") {
            messageSender.reply(
                activity,
                cardConverter.textMessage("안녕하세요! Weekly Report 봇입니다. 자연어로 태스크를 관리해보세요."),
            )
        }
    }
}
