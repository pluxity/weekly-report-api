package com.pluxity.weekly.chat.v2

import com.pluxity.weekly.chat.v2.dto.FunctionDefinition
import com.pluxity.weekly.chat.v2.dto.ToolDefinition

/**
 * /chat/v2 PoC의 tool 스키마 선언.
 * 기존 system-prompt의 "target별 필드" 표가 코드로 내려온 것 — 매 LLM 요청에 함께 전송된다.
 */
object ChatV2Tools {
    const val SEARCH_ITEMS = "search_items"
    const val UPDATE_TASK = "update_task"

    val ALL: List<ToolDefinition> =
        listOf(
            ToolDefinition(
                function =
                    FunctionDefinition(
                        name = SEARCH_ITEMS,
                        description =
                            "태스크·업무 그룹(에픽)·프로젝트를 이름으로 통합 검색한다. " +
                                "사용자가 종류를 말하지 않아도 3계층을 한 번에 찾아준다. " +
                                "이름은 단어 단위 부분 일치(대소문자·공백 무시). 수정 전 대상 확인에 반드시 사용할 것.",
                        parameters =
                            mapOf(
                                "type" to "object",
                                "properties" to
                                    mapOf(
                                        "query" to
                                            mapOf(
                                                "type" to "string",
                                                "description" to "찾을 이름 (예: 'cctv 목록', 'safers')",
                                            ),
                                        "type" to
                                            mapOf(
                                                "type" to "string",
                                                "enum" to listOf("task", "epic", "project"),
                                                "description" to "사용자가 종류를 명시한 경우에만 지정 (생략 시 전체 검색)",
                                            ),
                                        "status" to
                                            mapOf(
                                                "type" to "string",
                                                "enum" to listOf("TODO", "IN_PROGRESS", "IN_REVIEW", "DONE"),
                                                "description" to "태스크 상태 필터 (태스크 검색에만 적용)",
                                            ),
                                        "assignee_me" to
                                            mapOf(
                                                "type" to "boolean",
                                                "description" to "true면 현재 사용자에게 할당된 태스크만 (태스크 검색에만 적용)",
                                            ),
                                    ),
                                "required" to listOf("query"),
                            ),
                    ),
            ),
            ToolDefinition(
                function =
                    FunctionDefinition(
                        name = UPDATE_TASK,
                        description =
                            "태스크를 수정한다. id는 반드시 search_items 결과 tasks의 값을 사용할 것 (추측 금지). " +
                                "상태는 TODO/IN_PROGRESS만 지정 가능 — 완료 처리는 이 도구로 불가. " +
                                "업무 그룹/프로젝트 수정은 지원하지 않는다.",
                        parameters =
                            mapOf(
                                "type" to "object",
                                "properties" to
                                    mapOf(
                                        "id" to
                                            mapOf(
                                                "type" to "integer",
                                                "description" to "태스크 ID (search_items 결과 tasks에서 확인한 값)",
                                            ),
                                        "name" to mapOf("type" to "string", "description" to "새 이름"),
                                        "description" to mapOf("type" to "string", "description" to "새 설명"),
                                        "status" to
                                            mapOf(
                                                "type" to "string",
                                                "enum" to listOf("TODO", "IN_PROGRESS"),
                                                "description" to "새 상태 (IN_REVIEW/DONE 지정 불가)",
                                            ),
                                        "progress" to
                                            mapOf(
                                                "type" to "integer",
                                                "minimum" to 0,
                                                "maximum" to 100,
                                                "description" to "진행률 0~100",
                                            ),
                                        "start_date" to
                                            mapOf(
                                                "type" to "string",
                                                "description" to "시작일 YYYY-MM-DD",
                                            ),
                                        "due_date" to
                                            mapOf(
                                                "type" to "string",
                                                "description" to "마감일 YYYY-MM-DD",
                                            ),
                                    ),
                                "required" to listOf("id"),
                            ),
                    ),
            ),
        )
}
