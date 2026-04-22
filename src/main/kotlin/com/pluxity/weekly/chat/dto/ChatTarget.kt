package com.pluxity.weekly.chat.dto

import com.pluxity.weekly.chat.exception.ChatClarifyException

enum class ChatTarget(val key: String) {
    TASK("task"),
    EPIC("epic"),
    PROJECT("project"),
    TEAM("team"),
    REVIEW("review"),
    ;

    companion object {
        fun fromOrNull(key: String?): ChatTarget? = entries.firstOrNull { it.key == key }

        fun from(key: String?): ChatTarget =
            fromOrNull(key) ?: throw ChatClarifyException(message = "지원하지 않는 대상입니다.")
    }
}
