package com.pluxity.weekly.chat.v2

import com.pluxity.weekly.chat.v2.dto.ChatV2Request
import com.pluxity.weekly.chat.v2.dto.ChatV2Response
import com.pluxity.weekly.chat.v2.dto.ChatV2WeeklyReportRequest
import com.pluxity.weekly.chat.v2.dto.ChatV2WeeklyReportResponse
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
@Tag(
    name = "Chat V2 Controller",
    description =
        "조회는 tool calling 루프(검색/상세/집계/리뷰/주간보고 조회), 주간보고 작성은 structured output 전용 엔드포인트. " +
            "태스크 CUD는 보드/폼에서. FE가 '질문'과 '주간보고 붙여넣기'를 구분해 라우팅한다.",
)
class ChatV2Controller(
    private val chatV2Service: ChatV2Service,
    private val chatV2WeeklyReportService: ChatV2WeeklyReportService,
) {
    @Operation(
        summary = "채팅 v2 (조회 전용, tool calling)",
        description = "모델이 검색/상세/집계/리뷰/주간보고 조회 도구를 스스로 호출하며 대화형으로 답합니다. 변경 작업은 지원하지 않습니다. 응답에 tool 실행 trace 포함.",
    )
    @PostMapping
    fun chat(
        @RequestBody @Valid request: ChatV2Request,
    ): ResponseEntity<DataResponseBody<ChatV2Response>> =
        ResponseEntity.ok(DataResponseBody(chatV2Service.chat(request.message)))

    @Operation(
        summary = "주간보고 작성 (structured output)",
        description =
            "붙여넣은 주간보고 본문을 표준 포맷으로 정리해 저장합니다 (팀 리더 전용, UPSERT). " +
                "본문 없음·항목 인식 실패·리더 아님은 오류가 아니라 안내 reply로 응답합니다.",
    )
    @PostMapping("/weekly-report")
    fun createWeeklyReport(
        @RequestBody @Valid request: ChatV2WeeklyReportRequest,
    ): ResponseEntity<DataResponseBody<ChatV2WeeklyReportResponse>> =
        ResponseEntity.ok(DataResponseBody(chatV2WeeklyReportService.create(request.message)))
}
