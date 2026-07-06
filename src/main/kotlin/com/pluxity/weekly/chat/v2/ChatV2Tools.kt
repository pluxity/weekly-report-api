package com.pluxity.weekly.chat.v2

import com.pluxity.weekly.chat.v2.dto.FunctionDefinition
import com.pluxity.weekly.chat.v2.dto.ToolDefinition

/**
 * /chat/v2 PoC의 tool 스키마 선언.
 * 기존 system-prompt의 "target별 필드" 표가 코드로 내려온 것 — 매 LLM 요청에 함께 전송된다.
 */
object ChatV2Tools {
    const val SEARCH_TASKS = "search_tasks"
    const val UPDATE_TASK = "update_task"

    val ALL: List<ToolDefinition> =
        listOf(
            ToolDefinition(
                function =
                    FunctionDefinition(
                        name = SEARCH_TASKS,
                        description = "태스크를 검색한다. 이름은 부분 일치. 수정 전 대상 확인에 반드시 사용할 것.",
                        parameters =
                            mapOf(
                                "type" to "object",
                                "properties" to
                                    mapOf(
                                        "name" to
                                            mapOf(
                                                "type" to "string",
                                                "description" to "태스크 이름 검색어 (부분 일치)",
                                            ),
                                        "status" to
                                            mapOf(
                                                "type" to "string",
                                                "enum" to listOf("TODO", "IN_PROGRESS", "IN_REVIEW", "DONE"),
                                                "description" to "상태 필터",
                                            ),
                                        "assignee_me" to
                                            mapOf(
                                                "type" to "boolean",
                                                "description" to "true면 현재 사용자에게 할당된 태스크만",
                                            ),
                                    ),
                                "required" to emptyList<String>(),
                            ),
                    ),
            ),
            ToolDefinition(
                function =
                    FunctionDefinition(
                        name = UPDATE_TASK,
                        description =
                            "태스크를 수정한다. id는 반드시 search_tasks 결과의 값을 사용할 것 (추측 금지). " +
                                "상태는 TODO/IN_PROGRESS만 지정 가능 — 완료 처리는 이 도구로 불가.",
                        parameters =
                            mapOf(
                                "type" to "object",
                                "properties" to
                                    mapOf(
                                        "id" to
                                            mapOf(
                                                "type" to "integer",
                                                "description" to "태스크 ID (search_tasks로 확인한 값)",
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
