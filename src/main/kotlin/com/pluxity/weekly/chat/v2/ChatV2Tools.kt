package com.pluxity.weekly.chat.v2

import com.pluxity.weekly.chat.v2.dto.FunctionDefinition
import com.pluxity.weekly.chat.v2.dto.ToolDefinition

/**
 * /chat/v2의 tool 스키마 선언 — **조회 전용** (2026-07-07 전환 결정).
 * CUD는 보드/폼에서 처리하고, 채팅은 검색·상세·집계·이력 조회만 담당한다.
 * 매 LLM 요청(루프 스텝마다)에 함께 전송된다.
 *
 * ⚠️ 스키마는 스텝 수만큼 곱해지는 input 비용이다. 행동 규칙(검색 먼저, id는 검색 결과만 등)은
 * chat-v2-prompt.txt에 한 번만 쓰고, 여기 description은 "무엇을 하는 도구인지"만 최소로 적는다.
 */
object ChatV2Tools {
    const val SEARCH_ITEMS = "search_items"
    const val SEARCH_USERS = "search_users"
    const val GET_ITEM_DETAILS = "get_item_details"
    const val AGGREGATE_ITEMS = "aggregate_items"
    const val LIST_PENDING_REVIEWS = "list_pending_reviews"
    const val GET_TASK_HISTORY = "get_task_history"

    private fun tool(
        name: String,
        description: String,
        properties: Map<String, Any>,
        required: List<String> = emptyList(),
    ): ToolDefinition =
        ToolDefinition(
            function =
                FunctionDefinition(
                    name = name,
                    description = description,
                    parameters =
                        buildMap {
                            put("type", "object")
                            put("properties", properties)
                            if (required.isNotEmpty()) put("required", required)
                        },
                ),
        )

    private fun str(
        description: String,
        enum: List<String>? = null,
    ): Map<String, Any> =
        buildMap {
            put("type", "string")
            put("description", description)
            if (enum != null) put("enum", enum)
        }

    private fun int(
        description: String,
        minimum: Int? = null,
        maximum: Int? = null,
    ): Map<String, Any> =
        buildMap {
            put("type", "integer")
            put("description", description)
            if (minimum != null) put("minimum", minimum)
            if (maximum != null) put("maximum", maximum)
        }

    private fun bool(description: String): Map<String, Any> = mapOf("type" to "boolean", "description" to description)

    private fun date(description: String): Map<String, Any> = str("$description YYYY-MM-DD")

    /** search_items / aggregate_items 공통 필터 */
    private fun commonFilters(): Map<String, Any> =
        mapOf(
            "status" to str("상태 필터 (태스크: TODO/IN_PROGRESS/IN_REVIEW/DONE, 그 외: TODO/IN_PROGRESS/DONE)"),
            "assignee_me" to bool("true면 내 담당만"),
            "assignee_id" to int("이 사용자 담당만"),
            "project_id" to int("이 프로젝트 소속만"),
            "epic_id" to int("이 업무 그룹의 태스크만"),
            "due_date_from" to date("마감일 범위 시작"),
            "due_date_to" to date("마감일 범위 끝"),
            "exclude_done" to bool("true면 완료(DONE) 제외 — 지연/남은 일 조회"),
        )

    val ALL: List<ToolDefinition> =
        listOf(
            tool(
                name = SEARCH_ITEMS,
                description =
                    "태스크·업무 그룹(에픽)·프로젝트·팀 통합 검색. 이름은 단어 단위 부분 일치. " +
                        "type 생략 시 태스크·업무 그룹·프로젝트를 한 번에 검색 (팀은 type='team' 명시 시에만). " +
                        "결과는 타입당 limit건 + totals(전체 개수).",
                properties =
                    mapOf(
                        "query" to str("찾을 이름 (목록 조회면 생략)"),
                        "type" to str("종류를 명시한 경우에만", listOf("task", "epic", "project", "team")),
                    ) + commonFilters() +
                        mapOf(
                            "sort" to str("정렬 기준", listOf("due_date", "progress", "name")),
                            "order" to str("생략 시 asc", listOf("asc", "desc")),
                            "limit" to int("타입당 최대 결과 수 (생략 시 10)", 1, 30),
                        ),
            ),
            tool(
                name = GET_ITEM_DETAILS,
                description = "태스크·업무 그룹·프로젝트 1건의 상세 조회 (설명·시작일·구성원 등 검색 결과에 없는 필드 포함).",
                properties =
                    mapOf(
                        "type" to str("종류", listOf("task", "epic", "project")),
                        "id" to int("항목 ID"),
                    ),
                required = listOf("type", "id"),
            ),
            tool(
                name = AGGREGATE_ITEMS,
                description =
                    "태스크·업무 그룹·프로젝트 집계 — 그룹별 개수·평균 진행률. " +
                        "'몇 개', '진행률 얼마', '누가 제일 많아' 같은 수치 질문은 검색 대신 이걸 쓸 것.",
                properties =
                    mapOf(
                        "type" to str("집계 대상", listOf("task", "epic", "project")),
                        "group_by" to
                            str(
                                "그룹 기준 (epic·assignee는 task에서만, project는 task·epic에서만, status는 전부)",
                                listOf("status", "project", "epic", "assignee"),
                            ),
                    ) + commonFilters(),
                required = listOf("type", "group_by"),
            ),
            tool(
                name = SEARCH_USERS,
                description = "사용자 검색 — id와 역할 확인.",
                properties =
                    mapOf(
                        "query" to str("사용자 이름 (생략 시 전체)"),
                        "role" to str("이 역할만", listOf("ADMIN", "PM", "PO", "LEADER")),
                    ),
            ),
            tool(
                name = LIST_PENDING_REVIEWS,
                description = "리뷰 대기(IN_REVIEW) 태스크 목록.",
                properties = emptyMap(),
            ),
            tool(
                name = GET_TASK_HISTORY,
                description = "태스크의 리뷰 이력 조회 — 리뷰 요청/승인/반려 기록과 반려 사유.",
                properties = mapOf("task_id" to int("태스크 ID")),
                required = listOf("task_id"),
            ),
        )
}
