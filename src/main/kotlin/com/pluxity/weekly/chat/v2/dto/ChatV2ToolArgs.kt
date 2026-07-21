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
    /** true면 내가 PM인 프로젝트만 (assignee_me의 PM 판) */
    @param:JsonProperty("pm_me")
    val pmMe: Boolean? = null,
    /** 이 사용자가 PM인 프로젝트만 — 사용자 '이름'을 그대로 넣으면 서버가 id로 해소 (PROJECT 전용) */
    val pm: String? = null,
    /** true면 내가 리더인 팀의 태스크만 (팀 멤버 담당 태스크) */
    @param:JsonProperty("team_me")
    val teamMe: Boolean? = null,
    /** 이 팀의 태스크만 — 팀 '이름'을 그대로 넣으면 서버가 멤버→담당 태스크로 해소 (TASK 전용) */
    val team: String? = null,
    /** 이 프로젝트 이름의 하위만 — 서버가 이름→id 해소 (모델은 id를 몰라도 됨) */
    val project: String? = null,
    /** 이 업무 그룹(에픽) 이름의 하위만 — 서버가 이름→id 해소 */
    val epic: String? = null,
    @param:JsonProperty("due_date_from")
    val dueDateFrom: String? = null,
    @param:JsonProperty("due_date_to")
    val dueDateTo: String? = null,
    /** 완료일 범위 — "이번주/저번주 한 일" 회고 (태스크 전용) */
    @param:JsonProperty("completed_from")
    val completedFrom: String? = null,
    @param:JsonProperty("completed_to")
    val completedTo: String? = null,
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

data class GetDetailArgs(
    /** task/epic/project/team — 상세 대상 종류 */
    val type: String,
    /** 항목 이름 — 그대로 넣으면 서버가 찾아 단건으로 좁혀 상세를 준다 (id 아님) */
    val name: String,
)

data class GetWeeklyReportArgs(
    /** "this"(기본)/"last"/YYYY-MM-DD — 해석은 서버(resolveWeekStart) */
    val week: String? = null,
    /** 특정 팀 이름 (선택) — 그 팀 주간보고 내용. 생략 시 admin은 전 팀 제출현황, 리더는 내 팀. */
    val team: String? = null,
)
