package com.pluxity.weekly.chat.dto

import com.pluxity.weekly.chat.exception.ChatClarifyException

enum class ChatActionType(
    val key: String,
    val requiredFields: List<String> = emptyList(),
    val validatesMissingFields: Boolean = true,
    val isMutating: Boolean = false,
) {
    READ("read", validatesMissingFields = false),
    CLARIFY("clarify"),
    CREATE("create", validatesMissingFields = false, isMutating = true),
    UPDATE("update", requiredFields = listOf("id"), isMutating = true),
    DELETE("delete", requiredFields = listOf("id"), isMutating = true),
    REVIEW_REQUEST("review_request", requiredFields = listOf("id"), isMutating = true),
    ASSIGN("assign", requiredFields = listOf("id", "user_ids"), isMutating = true),
    UNASSIGN("unassign", requiredFields = listOf("id", "remove_user_ids"), isMutating = true),
    ;

    companion object {
        fun from(key: String?): ChatActionType =
            entries.firstOrNull { it.key == key }
                ?: throw ChatClarifyException(message = "지원하지 않는 요청입니다.")
    }
}
