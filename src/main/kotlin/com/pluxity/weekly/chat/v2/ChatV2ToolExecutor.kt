package com.pluxity.weekly.chat.v2

import com.pluxity.weekly.core.exception.CustomException
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import tools.jackson.databind.exc.UnrecognizedPropertyException

private val log = KotlinLogging.logger {}

/**
 * tool_calls 실행부 — **조회 전용**. 도구명 → 핸들러 dispatch만 담당한다.
 * 도구별 로직은 각 핸들러에, 공통 부품(인자 파싱·id 검증·이름 해소·응답 매핑)은 [ChatV2ToolSupport]에 있다.
 *
 * 실행 범위·권한은 각 핸들러가 부르는 기존 서비스가 담당한다 (Service.search의 AuthorizationService 스코프 등).
 * 실패는 예외로 터뜨리지 않고 {"error": "..."} 결과로 모델에게 되돌려 자가교정하게 한다 (agent 패턴).
 */
@Component
class ChatV2ToolExecutor(
    private val support: ChatV2ToolSupport,
    private val searchItemsHandler: SearchItemsHandler,
    private val searchUsersHandler: SearchUsersHandler,
    private val aggregateItemsHandler: AggregateItemsHandler,
    private val listPendingReviewsHandler: ListPendingReviewsHandler,
    private val getTaskHistoryHandler: GetTaskHistoryHandler,
    private val getWeeklyReportHandler: GetWeeklyReportHandler,
) {
    fun execute(
        toolName: String,
        argumentsJson: String,
        currentUserId: Long,
        idRegistry: ChatV2IdRegistry,
    ): String =
        try {
            when (toolName) {
                ChatV2Tools.SEARCH_ITEMS -> searchItemsHandler.handle(argumentsJson, currentUserId, idRegistry)
                ChatV2Tools.SEARCH_USERS -> searchUsersHandler.handle(argumentsJson, idRegistry)
                ChatV2Tools.AGGREGATE_ITEMS -> aggregateItemsHandler.handle(argumentsJson, currentUserId, idRegistry)
                ChatV2Tools.LIST_PENDING_REVIEWS -> listPendingReviewsHandler.handle(idRegistry)
                ChatV2Tools.GET_TASK_HISTORY -> getTaskHistoryHandler.handle(argumentsJson, idRegistry)
                ChatV2Tools.GET_WEEKLY_REPORT -> getWeeklyReportHandler.handle(argumentsJson, currentUserId)
                else -> support.errorResult("알 수 없는 도구: $toolName")
            }
        } catch (e: UnrecognizedPropertyException) {
            // 모델이 지어낸 인자를 조용히 버리면 "필터 없는 결과"가 환각으로 이어진다 — 거부하고 스키마를 알려 재시도 유도
            log.info { "chat/v2 tool 인자 거부: $toolName — ${e.propertyName} (args=$argumentsJson)" }
            support.errorResult("지원하지 않는 인자: ${e.propertyName}. 사용 가능한 인자: ${e.knownPropertyIds.joinToString(", ")}")
        } catch (e: CustomException) {
            log.info { "chat/v2 tool 실행 거부: $toolName — ${e.message}" }
            support.errorResult(e.message ?: "요청을 처리할 수 없습니다.")
        } catch (e: Exception) {
            log.warn(e) { "chat/v2 tool 실행 실패: $toolName args=$argumentsJson" }
            support.errorResult("도구 실행 중 오류가 발생했습니다: ${e.message}")
        }
}
