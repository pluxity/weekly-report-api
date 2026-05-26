package com.pluxity.weekly.chat.llm.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.pluxity.weekly.report.dto.FormattedReport
import java.time.LocalDate

/**
 * weekly-report-classify-prompt.txt 응답 매핑 DTO.
 * LLM이 추가 필드(raw_line 등)를 출력해도 무시 (ignoreUnknown).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class WeeklyReportClassifyResult(
    val team: String? = null,
    @param:JsonProperty("team_name_raw")
    val teamNameRaw: String? = null,
    @param:JsonProperty("week_start")
    val weekStart: LocalDate,
    val formatted: FormattedReport,
)
