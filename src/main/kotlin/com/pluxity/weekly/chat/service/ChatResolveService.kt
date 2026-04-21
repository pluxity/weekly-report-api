package com.pluxity.weekly.chat.service

import com.pluxity.weekly.auth.authorization.AuthorizationService
import com.pluxity.weekly.chat.dto.ChatActionResponse
import com.pluxity.weekly.chat.dto.ChatResolveRequest
import com.pluxity.weekly.chat.dto.LlmAction
import org.springframework.stereotype.Service
import tools.jackson.databind.ObjectMapper
import tools.jackson.module.kotlin.convertValue

@Service
class ChatResolveService(
    private val clarifyStore: ClarifyStore,
    private val chatActionRouter: ChatActionRouter,
    private val chatHistoryStore: ChatHistoryStore,
    private val authorizationService: AuthorizationService,
    private val objectMapper: ObjectMapper,
) {
    companion object {
        private val LIST_FIELDS = setOf("user_ids", "remove_user_ids")
    }

    fun resolve(request: ChatResolveRequest): ChatActionResponse {
        val userId = authorizationService.currentUser().requiredId
        val stored = clarifyStore.consume(userId, request.clarifyId)
        val mergedAction = mergeField(stored, request)
        val response = chatActionRouter.route(mergedAction)
        chatHistoryStore.recordResolvedTurn(
            userId = userId.toString(),
            target = mergedAction.target,
            action = mergedAction.action,
            response = response,
        )
        return response
    }

    private fun mergeField(
        stored: LlmAction,
        request: ChatResolveRequest,
    ): LlmAction {
        val map: MutableMap<String, Any?> = objectMapper.convertValue(stored)
        map[request.field] = resolveFieldValue(request)
        map.remove("missing_fields")
        map.remove("candidates")
        map.remove("message")
        val mergedJson = objectMapper.writeValueAsString(map)
        return objectMapper.readValue(mergedJson, LlmAction::class.java)
    }

    private fun resolveFieldValue(request: ChatResolveRequest): Any? {
        val values = request.values ?: return null
        return if (request.field in LIST_FIELDS) values else values.singleOrNull()
    }
}
