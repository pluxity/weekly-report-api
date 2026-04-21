package com.pluxity.weekly.chat.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank

@Schema(description = "clarify 세션 해결 요청")
data class ChatResolveRequest(
    @field:Schema(description = "clarify 응답에서 발급된 clarifyId", example = "abc-123")
    @field:NotBlank
    val clarifyId: String,
    @field:Schema(description = "채울 필드명", example = "id")
    @field:NotBlank
    val field: String,
    @field:Schema(description = "Long 리스트 값", example = "[1,2]")
    val values: List<Long>? = null,
)
