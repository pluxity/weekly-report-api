package com.pluxity.weekly.chat.v2

/**
 * 주간보고 생성(structured output)용 JSON 스키마 — `response_format: json_schema`로 전송된다.
 *
 * 스키마 강도 원칙 (결정 문서 §7 "스키마 경직 vs 지저분한 입력"):
 * - 뼈대는 강하게 — **최상위 단일 팀 오브젝트**(배열 아님 → "3팀 동시 생성" 환각 구조적 차단),
 *   섹션 4개·항목 필드 required + additionalProperties=false (발명 필드 차단).
 * - 값은 유연하게 — category는 자유 문자열(프로젝트·사업명이라 고정 집합이 없음),
 *   progress는 원문 표기 보존("완료"/"80%"/"지연 대기 중" — FormattedReport 원문 보존 계약).
 *   지저분한 입력 대응 규칙은 스키마가 아니라 프롬프트(chat-v2-weekly-report-prompt.txt)에 둔다.
 * - 얕게 유지 — Gemini는 과대·과중첩 스키마를 거부할 수 있다.
 *
 * 출력은 기존 [com.pluxity.weekly.chat.llm.dto.WeeklyReportClassifyResult] /
 * [com.pluxity.weekly.chat.llm.dto.WeeklyReportMatchResult]로 역직렬화된다 (계약 재사용).
 */
object ChatV2WeeklyReportSchemas {
    const val CLASSIFY_NAME = "weekly_report_classify"
    const val MATCH_NAME = "weekly_report_match"

    private fun nullableString(description: String): Map<String, Any> =
        mapOf("type" to listOf("string", "null"), "description" to description)

    private fun obj(
        properties: Map<String, Any>,
        required: List<String> = properties.keys.toList(),
    ): Map<String, Any> =
        mapOf(
            "type" to "object",
            "properties" to properties,
            "required" to required,
            "additionalProperties" to false,
        )

    /** 보고 항목 — 기존 FormattedReport.ReportItem 계약과 동일한 필드 */
    private val reportItems: Map<String, Any> =
        mapOf(
            "type" to "array",
            "items" to
                obj(
                    mapOf(
                        "assignee" to nullableString("담당자명 (사람 헤더 없으면 null)"),
                        "category" to nullableString("사업/프로젝트명/태그 (없으면 null)"),
                        "text" to nullableString("항목 본문 — 원문 그대로"),
                        "progress" to nullableString("진행률/상태 원문 표기 (없으면 null)"),
                        "due_date" to nullableString("완전한 YYYY-MM-DD만, 아니면 null"),
                    ),
                ),
        )

    val CLASSIFY: Map<String, Any> =
        obj(
            mapOf(
                "team" to nullableString("요청자 팀의 팀 단위 이름"),
                "team_name_raw" to nullableString("원문 팀명 표기 (부서·본부 포함 그대로)"),
                "week_start" to mapOf("type" to "string", "description" to "보고 주차 시작일 YYYY-MM-DD"),
                "formatted" to
                    obj(
                        mapOf(
                            "thisWeek" to reportItems,
                            "nextWeek" to reportItems,
                            "issues" to reportItems,
                            "others" to reportItems,
                        ),
                    ),
            ),
        )

    val MATCH: Map<String, Any> =
        obj(
            mapOf(
                "matched" to
                    mapOf(
                        "type" to "array",
                        "items" to
                            obj(
                                mapOf(
                                    "prev" to mapOf("type" to "string", "description" to "지난주 예정 번호 (예: P2)"),
                                    "curr" to mapOf("type" to "string", "description" to "이번주 진행 번호 (예: C1)"),
                                ),
                            ),
                    ),
            ),
        )
}
