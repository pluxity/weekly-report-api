package com.pluxity.weekly.chat.service

import com.pluxity.weekly.auth.authorization.AuthorizationService
import com.pluxity.weekly.chat.dto.ChatActionResponse
import com.pluxity.weekly.chat.dto.ChatResolveRequest
import com.pluxity.weekly.chat.dto.LlmAction
import com.pluxity.weekly.chat.exception.ChatResolveInvalidException
import com.pluxity.weekly.chat.exception.ChatSessionExpiredException
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
        val stored = clarifyStore.peek(userId, request.clarifyId)
        validate(stored, request)
        val mergedAction = mergeField(stored, request)
        val response = chatActionRouter.route(mergedAction)
        clarifyStore.delete(userId, request.clarifyId)
        chatHistoryStore.recordResolvedTurn(
            userId = userId.toString(),
            target = mergedAction.target,
            action = mergedAction.action,
            response = response,
        )
        return response
    }

    private fun validate(
        stored: LlmAction,
        request: ChatResolveRequest,
    ) {
        val missingField =
            stored.missingFields?.singleOrNull()
                ?: throw ChatSessionExpiredException()

        if (request.field != missingField) {
            throw ChatResolveInvalidException(
                "요청한 필드 '${request.field}'는 이번 clarify의 대상이 아닙니다 (기대: $missingField)",
            )
        }

        val values = request.values
        if (values.isNullOrEmpty()) {
            throw ChatResolveInvalidException("values는 비어있을 수 없습니다")
        }

        if (request.field !in LIST_FIELDS && values.size != 1) {
            throw ChatResolveInvalidException("'${request.field}' 필드는 단일 값만 허용합니다")
        }

        val candidates = stored.candidates
        if (!candidates.isNullOrEmpty()) {
            val invalid = values - candidates.toSet()
            if (invalid.isNotEmpty()) {
                throw ChatResolveInvalidException("후보에 없는 값: $invalid (허용: $candidates)")
            }
        }
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
        return objectMapper.convertValue(map)
    }

    private fun resolveFieldValue(request: ChatResolveRequest): Any? {
        val values = request.values ?: return null
        return if (request.field in LIST_FIELDS) values else values.singleOrNull()
    }
}
