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
 *
 * 각 ToolDefinition이 직렬화되면 아래 OpenAI 스타일 JSON이 된다 (예: get_task_history):
 * ```json
 * {
 *   "type": "function",
 *   "function": {
 *     "name": "get_task_history",
 *     "description": "태스크의 리뷰 이력 조회 (...).",
 *     "parameters": {
 *       "type": "object",
 *       "properties": {
 *         "task_id": { "type": "integer", "description": "태스크 ID" }
 *       },
 *       "required": ["task_id"]
 *     }
 *   }
 * }
 * ```
 */
object ChatV2Tools {
    const val SEARCH_ITEMS = "search_items"
    const val SEARCH_USERS = "search_users"
    const val AGGREGATE_ITEMS = "aggregate_items"
    const val LIST_PENDING_REVIEWS = "list_pending_reviews"
    const val GET_TASK_HISTORY = "get_task_history"
    const val GET_WEEKLY_REPORT = "get_weekly_report"

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
            "assignee" to str("이 사용자 담당만 — 사용자 '이름'을 그대로 넣으면 서버가 찾아준다 (id 아님)"),
            "project" to str("이 프로젝트 소속만 — 프로젝트 '이름'을 그대로 넣으면 서버가 찾아준다 (id 아님)"),
            "epic" to str("이 업무 그룹의 태스크만 — 업무 그룹 '이름'을 그대로 넣으면 서버가 찾아준다 (id 아님)"),
            "due_date_from" to date("마감일 범위 시작"),
            "due_date_to" to date("마감일 범위 끝"),
            "exclude_done" to bool("true면 완료(DONE) 제외 — 지연/남은 일 조회"),
        )

    val ALL: List<ToolDefinition> =
        listOf(
            tool(
                name = SEARCH_ITEMS,
                description =
                    "태스크·업무 그룹(에픽)·프로젝트·팀 **개별 항목 검색·나열**. 이름은 단어 단위 부분 일치. " +
                        "type 생략 시 태스크·업무 그룹·프로젝트를 한 번에 검색 (팀은 type='team' 명시 시에만). " +
                        "결과는 타입당 limit건 + totals(전체 개수). " +
                        "개수·평균·분포·순위(최다/최소)는 이 도구로 세지 말고 aggregate_items를 쓸 것 — 목록이 limit에 잘려 집계가 틀린다.",
                properties =
                    mapOf(
                        "query" to str("찾을 이름 (목록 조회면 생략)"),
                        "type" to str("종류를 명시한 경우에만", listOf("task", "epic", "project", "team")),
                    ) + commonFilters() +
                        mapOf(
                            "pm_me" to bool("true면 내가 PM인 프로젝트만"),
                            "pm" to str("이 사용자가 PM인 프로젝트만 — 사용자 '이름'을 그대로 넣으면 서버가 찾아준다 (id 아님)"),
                            "team_me" to bool("true면 내가 리더인 팀의 태스크만 (팀 멤버 담당)"),
                            "team" to str("이 팀의 태스크만 — 팀 '이름'을 그대로 넣으면 서버가 멤버→담당 태스크로 찾아준다 (id 아님)"),
                            "completed_from" to date("완료일 범위 시작 — '이번주/저번주 한 일' 회고 (태스크 전용)"),
                            "completed_to" to date("완료일 범위 끝"),
                            "detail" to
                                str(
                                    "응답 상세도. concise(기본)=핵심 필드. detailed=설명·시작일·구성원 등 상세 필드까지 (대상을 좁혔을 때만).",
                                    listOf("concise", "detailed"),
                                ),
                            "sort" to str("정렬 기준", listOf("due_date", "progress", "name")),
                            "order" to str("생략 시 asc", listOf("asc", "desc")),
                            "limit" to int("타입당 최대 결과 수 (생략 시 10)", 1, 30),
                        ),
            ),
            tool(
                name = AGGREGATE_ITEMS,
                description =
                    "태스크·업무 그룹·프로젝트 집계 — 개별 항목은 나열하지 않고 그룹별 개수·평균 진행률만 반환. " +
                        "'몇 개', '진행률 얼마', '누가 제일 많아', '프로젝트별 분포/개수' 같은 수치·순위·분포 질문은 " +
                        "반드시 이 도구(search_items로 세지 말 것 — 목록이 잘려 틀린다).",
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
            tool(
                name = GET_WEEKLY_REPORT,
                description = "내 팀 주간보고 조회 (팀 리더 전용) — 정리된 항목과 지난주 대비 매칭(누락/신규) 포함.",
                properties = mapOf("week" to str("조회 주차: this(생략 시)/last/YYYY-MM-DD")),
            ),
        )
}
