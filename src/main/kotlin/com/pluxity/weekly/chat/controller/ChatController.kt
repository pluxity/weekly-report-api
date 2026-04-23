package com.pluxity.weekly.chat.controller

import com.pluxity.weekly.chat.dto.ChatActionResponse
import com.pluxity.weekly.chat.dto.ChatRequest
import com.pluxity.weekly.chat.dto.ChatResolveRequest
import com.pluxity.weekly.chat.service.ChatResolveService
import com.pluxity.weekly.chat.service.ChatService
import com.pluxity.weekly.core.response.ClarifyErrorResponseBody
import com.pluxity.weekly.core.response.DataResponseBody
import com.pluxity.weekly.core.response.ErrorResponseBody
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/chat")
@Tag(name = "Chat Controller", description = "자연어 채팅 CRUD API")
class ChatController(
    private val chatService: ChatService,
    private val chatResolveService: ChatResolveService,
) {
    @Operation(summary = "채팅 메시지 전송", description = "자연어 메시지를 분석하여 폼 조립용 JSON을 반환합니다")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "처리 성공"),
            ApiResponse(
                responseCode = "503",
                description = "LLM 서비스 불가",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponseBody::class))],
            ),
        ],
    )
    @PostMapping
    fun chat(
        @RequestBody @Valid request: ChatRequest,
    ): ResponseEntity<DataResponseBody<List<ChatActionResponse>>> {
        val response = chatService.chat(request.message)
        return ResponseEntity.ok(DataResponseBody(response))
    }

    @Operation(
        summary = "clarify 세션 해결",
        description = "CHAT_SELECT_REQUIRED 응답의 clarifyId에 대해 누락 필드 값을 제공하여 액션을 완성합니다.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "처리 성공"),
            ApiResponse(
                responseCode = "400",
                description = "추가 clarify 필요시",
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema( ClarifyErrorResponseBody::class),
                    ),
                ],
            ),
        ],
    )
    @PostMapping("/resolve")
    fun resolve(
        @RequestBody @Valid request: ChatResolveRequest,
    ): ResponseEntity<DataResponseBody<ChatActionResponse>> {
        val response = chatResolveService.resolve(request)
        return ResponseEntity.ok(DataResponseBody(response))
    }
}
