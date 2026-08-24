package com.pluxity.weekly.chat.llm.schema

/**
 * classify 응답 강제용 JSON Schema. `WeeklyReportClassifyResult` / `FormattedReport` 계약과 1:1.
 *
 * 최상위를 단일 팀 오브젝트로 고정 → 입력에 팀이 여러 개여도 "팀별 JSON 나열"이 구조적으로 불가능하다.
 * 값(category/progress)은 자유 문자열 — 고정 집합이 없고 원문 보존 계약이 있다.
 * due_date 는 format 을 걸지 않는다 (LenientLocalDateDeserializer 의 관용 파싱과 충돌).
 */
object ClassifySchema {
    const val NAME = "weekly_report_classify"

    private fun nullableString(description: String): Map<String, Any> =
        mapOf("type" to listOf("string", "null"), "description" to description)

    private fun obj(properties: Map<String, Any>): Map<String, Any> =
        mapOf(
            "type" to "object",
            "properties" to properties,
            "required" to properties.keys.toList(),
            "additionalProperties" to false,
        )

    private val items: Map<String, Any> =
        mapOf(
            "type" to "array",
            "items" to
                obj(
                    mapOf(
                        "assignee" to nullableString("담당자명. 사람 헤더가 없으면 null"),
                        "category" to nullableString("사업·프로젝트명. 없으면 null"),
                        "text" to nullableString("항목 본문 — 원문 그대로"),
                        "progress" to nullableString("진행률·상태 원문 표기. 없으면 null"),
                        "due_date" to nullableString("완전한 YYYY-MM-DD 만. 아니면 null"),
                    ),
                ),
        )

    val SCHEMA: Map<String, Any> =
        obj(
            mapOf(
                "team" to nullableString("팀 단위 이름 (부서·본부 제외)"),
                "team_name_raw" to nullableString("원문 팀명 표기 (부서·본부 포함)"),
                "week_start" to mapOf("type" to "string", "description" to "보고 주차 시작일 YYYY-MM-DD"),
                "formatted" to
                    obj(
                        mapOf(
                            "thisWeek" to items,
                            "nextWeek" to items,
                            "issues" to items,
                            "others" to items,
                        ),
                    ),
            ),
        )
}
