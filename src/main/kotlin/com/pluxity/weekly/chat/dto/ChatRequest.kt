package com.pluxity.weekly.chat.dto

import jakarta.validation.constraints.NotBlank

data class ChatRequest(
    @field:NotBlank(message = "메시지는 필수입니다")
    val message: String,
)
