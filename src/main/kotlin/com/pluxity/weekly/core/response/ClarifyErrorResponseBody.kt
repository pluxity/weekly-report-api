package com.pluxity.weekly.core.response

import com.pluxity.weekly.chat.dto.Candidate
import io.swagger.v3.oas.annotations.media.Schema
import org.springframework.http.HttpStatus

@Schema(description = "후보 선택 필요 오류 응답 (CHAT_SELECT_REQUIRED) — candidates 중 선택하여 /chat/resolve 로 전달")
class ClarifyErrorResponseBody(
    status: HttpStatus,
    message: String?,
    code: String,
    error: String,
    @field:Schema(description = "clarify 세션 ID (/chat/resolve 요청에 사용)", example = "b7e8a1f0-3c2d-4e5f-9a6b-1c2d3e4f5a6b")
    val clarifyId: String,
    @field:Schema(description = "선택이 필요한 필드명", example = "projectId")
    val field: String,
    @field:Schema(description = "선택 후보 목록")
    val candidates: List<Candidate>,
) : ErrorResponseBody(status, message, code, error)
