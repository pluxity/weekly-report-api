package com.pluxity.weekly.teams.service

import com.pluxity.weekly.auth.user.service.UserService
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
    private val teamsApiClient: TeamsApiClient,
    private val userService: UserService,
) {
    fun handleActivity(activity: Activity) {
        when (activity.type) {
            "message" -> {
                if (activity.value != null) {
                    asyncChatHandler.handleMessage(activity)
                } else {
                    messageSender.reply(
                        activity,
                        cardConverter.textMessage("채팅 기능은 현재 비활성화되어 있습니다."),
                    )
                }
            }
            "installationUpdate" -> handleInstallationUpdate(activity)
            else -> log.debug { "Unhandled activity type: ${activity.type}" }
        }
    }

    /**
     *  Teams App 설치시 받는 메시지.
     *  허용 도메인 사용자라면 자동 가입(soft-deleted면 복원) + Teams 정보 저장까지 처리한다.
     */
    private fun handleInstallationUpdate(activity: Activity) {
        val action = activity.action ?: "unknown"
        log.info { "Installation update - action: $action, user: ${activity.from?.name}" }

        val serviceUrl = activity.serviceUrl
        val conversationId = activity.conversation?.id
        val aadObjectId = activity.from?.aadObjectId

        if (serviceUrl.isNullOrBlank() || conversationId.isNullOrBlank() || aadObjectId.isNullOrBlank()) {
            log.warn { "필수 정보 누락 - serviceUrl/conversationId/aadObjectId" }
        } else {
            val graphUser = teamsApiClient.getGraphUser(aadObjectId)
            userService.provisionFromTeams(
                aadObjectId = aadObjectId,
                displayName = graphUser?.displayName ?: activity.from?.name,
                email = graphUser?.mail,
                teamsServiceUrl = serviceUrl,
                teamsConversationId = conversationId,
            )
        }

        if (action == "add") {
            messageSender.reply(
                activity,
                cardConverter.textMessage("안녕하세요! Weekly Report 봇입니다. 자연어로 태스크를 관리해보세요."),
            )
        }
    }
}
