package com.pluxity.weekly.teams.service

import com.pluxity.weekly.teams.dto.Activity
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

private val log = KotlinLogging.logger {}

/**
 * 봇 서버 → Teams 사용자에게 메시지를 전송하는 클라이언트.
 * 전송 실패 시 예외를 던지지 않고 로그만 남긴다 (Teams 사용자에게 에러를 전달할 수단이 없으므로).
 */
@Component
class TeamsMessageSender(
    webClientBuilder: WebClient.Builder,
    private val teamsApiClient: TeamsApiClient,
) {
    private val webClient = webClientBuilder.build()

    fun reply(
        activity: Activity,
        responseBody: Map<String, Any>,
    ) {
        val serviceUrl = activity.serviceUrl?.trimEnd('/')
        val conversationId = activity.conversation?.id

        if (serviceUrl == null || conversationId == null) {
            log.warn { "serviceUrl 또는 conversationId 누락 - 응답 전송 불가" }
            return
        }

        val replyActivity = buildReplyActivity(activity, conversationId, responseBody)
        postActivity(serviceUrl, conversationId, replyActivity)
    }

    fun sendTyping(activity: Activity) {
        val serviceUrl = activity.serviceUrl?.trimEnd('/') ?: return
        val conversationId = activity.conversation?.id ?: return

        val typingActivity =
            buildReplyActivity(
                activity,
                conversationId,
                mapOf("type" to "typing"),
            )
        postActivity(serviceUrl, conversationId, typingActivity)
    }

    fun notify(
        serviceUrl: String,
        conversationId: String,
        message: String,
    ) {
        val body =
            mapOf(
                "type" to "message",
                "text" to message,
                "from" to mapOf("id" to "bot", "name" to "Bot"),
                "conversation" to mapOf("id" to conversationId),
            )

        postActivity(serviceUrl, conversationId, body)
    }

    private fun postActivity(
        serviceUrl: String,
        conversationId: String,
        body: Map<String, Any>,
    ) {
        val encodedConvId = URLEncoder.encode(conversationId, StandardCharsets.UTF_8)
        val uri = URI.create("${serviceUrl.trimEnd('/')}/v3/conversations/$encodedConvId/activities")

        try {
            val token = teamsApiClient.getBotToken()
            val result =
                webClient
                    .post()
                    .uri(uri)
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer $token")
                    .bodyValue(body)
                    .retrieve()
                    .toBodilessEntity()
                    .block()
            log.info { "전송 성공: ${result?.statusCode}" }
        } catch (e: WebClientResponseException) {
            log.error { "전송 실패 (${e.statusCode}): ${e.responseBodyAsString}" }
        } catch (e: Exception) {
            log.error(e) { "전송 중 예외" }
        }
    }

    private fun buildReplyActivity(
        activity: Activity,
        conversationId: String,
        responseBody: Map<String, Any>,
    ): Map<String, Any> =
        responseBody.toMutableMap().apply {
            put("replyToId", activity.id ?: "")
            put(
                "from",
                mapOf(
                    "id" to (activity.recipient?.id ?: "bot"),
                    "name" to (activity.recipient?.name ?: "Bot"),
                ),
            )
            put(
                "recipient",
                mapOf(
                    "id" to (activity.from?.id ?: ""),
                    "name" to (activity.from?.name ?: ""),
                ),
            )
            put("conversation", mapOf("id" to conversationId))
        }
}
