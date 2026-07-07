package com.pluxity.weekly.chat.v2

import com.pluxity.weekly.chat.v2.dto.ChatV2Request
import com.pluxity.weekly.chat.v2.dto.ChatV2Response
import com.pluxity.weekly.core.response.DataResponseBody
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/chat/v2")
@Tag(name = "Chat V2 Controller", description = "tool calling 루프 기반 조회 전용 채팅 (검색/상세/집계/리뷰 목록·이력) — CUD는 보드/폼에서")
class ChatV2Controller(
    private val chatV2Service: ChatV2Service,
) {
    @Operation(
        summary = "채팅 v2 (조회 전용, tool calling)",
        description = "모델이 검색/상세/집계/리뷰 조회 도구를 스스로 호출하며 대화형으로 답합니다. 변경 작업은 지원하지 않습니다. 응답에 tool 실행 trace 포함.",
    )
    @PostMapping
    fun chat(
        @RequestBody @Valid request: ChatV2Request,
    ): ResponseEntity<DataResponseBody<ChatV2Response>> =
        ResponseEntity.ok(DataResponseBody(chatV2Service.chat(request.message)))
}
