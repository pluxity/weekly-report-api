package com.pluxity.weekly.chat.v2.dto

import com.fasterxml.jackson.annotation.JsonProperty

// tool_calls arguments 역직렬화용.
// 필수 필드(non-null)가 빠지면 역직렬화가 실패하고, executor가 {"error":...}로 모델에 되돌린다.
// 스키마에 없는 인자도 실패시킨다(executor의 FAIL_ON_UNKNOWN_PROPERTIES) —
// 모델이 지어낸 인자가 조용히 버려져 "필터 없는 결과"가 환각으로 이어지는 것을 차단
// (실사례: search_items에 assignee_id를 지어냄 → 무필터 전체 목록을 특정인 업무로 포장).

data class SearchItemsArgs(
    val query: String? = null,
    val type: String? = null,
    val status: String? = null,
    @param:JsonProperty("assignee_me")
    val assigneeMe: Boolean? = null,
    /** 이 사용자 담당만 — 사용자 '이름'을 그대로 넣으면 서버가 찾아준다 (id 아님) */
    val assignee: String? = null,
    /** 이 프로젝트 이름의 하위만 — 서버가 이름→id 해소 (모델은 id를 몰라도 됨) */
    val project: String? = null,
    /** 이 업무 그룹(에픽) 이름의 하위만 — 서버가 이름→id 해소 */
    val epic: String? = null,
    @param:JsonProperty("due_date_from")
    val dueDateFrom: String? = null,
    @param:JsonProperty("due_date_to")
    val dueDateTo: String? = null,
    @param:JsonProperty("exclude_done")
    val excludeDone: Boolean? = null,
    val sort: String? = null,
    val order: String? = null,
    val limit: Int? = null,
)

data class SearchUsersArgs(
    val query: String? = null,
    val role: String? = null,
)

data class GetItemDetailsArgs(
    val type: String,
    val id: Long,
)

data class AggregateItemsArgs(
    val type: String,
    @param:JsonProperty("group_by")
    val groupBy: String,
    val status: String? = null,
    @param:JsonProperty("assignee_me")
    val assigneeMe: Boolean? = null,
    /** 이 사용자 담당만 — 사용자 '이름'을 그대로 넣으면 서버가 찾아준다 (id 아님) */
    val assignee: String? = null,
    /** 이 프로젝트 이름의 하위만 — 서버가 이름→id 해소 */
    val project: String? = null,
    /** 이 업무 그룹(에픽) 이름의 하위만 — 서버가 이름→id 해소 */
    val epic: String? = null,
    @param:JsonProperty("due_date_from")
    val dueDateFrom: String? = null,
    @param:JsonProperty("due_date_to")
    val dueDateTo: String? = null,
    @param:JsonProperty("exclude_done")
    val excludeDone: Boolean? = null,
)

data class GetTaskHistoryArgs(
    @param:JsonProperty("task_id")
    val taskId: Long,
)

data class GetWeeklyReportArgs(
    /** "this"(기본)/"last"/YYYY-MM-DD — 해석은 서버(resolveWeekStart) */
    val week: String? = null,
)
