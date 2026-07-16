package com.pluxity.weekly.chat.v2

import com.pluxity.weekly.chat.v2.dto.GetWeeklyReportArgs
import com.pluxity.weekly.report.dto.ReportItem
import com.pluxity.weekly.report.service.WeeklyReportService
import com.pluxity.weekly.team.repository.TeamRepository
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

/**
 * get_weekly_report 실행부 — 내 팀 주간보고 조회 (팀 리더 전용).
 * rawContent(원문 전체)는 제외하고 정리된 항목·지난주 매칭만 준다. week 해석은 findForChat의 resolveWeekStart.
 */
@Component
class GetWeeklyReportHandler(
    private val teamRepository: TeamRepository,
    private val weeklyReportService: WeeklyReportService,
    private val support: ChatV2ToolSupport,
    private val objectMapper: ObjectMapper,
) {
    fun handle(
        argumentsJson: String,
        currentUserId: Long,
    ): String {
        val args = support.readArgs<GetWeeklyReportArgs>(argumentsJson)
        teamRepository.findByLeaderId(currentUserId).firstOrNull()
            ?: return support.errorResult("주간보고는 팀 리더만 조회할 수 있습니다.")
        val report =
            weeklyReportService.findForChat(args.week)
                ?: return objectMapper.writeValueAsString(
                    mapOf("weekly_report" to null, "message" to "해당 주차에 작성된 주간보고가 없습니다."),
                )
        val matched =
            report.matchedAgainstPrev?.let { m ->
                mapOf(
                    "matched" to m.matched.map { mapOf("assignee" to it.assignee, "prev" to it.prev, "curr" to it.curr) },
                    "missing_from_prev_plan" to m.missing.map { mapOf("assignee" to it.assignee, "text" to it.text) },
                    "new_this_week" to m.new.map { mapOf("assignee" to it.assignee, "text" to it.text) },
                )
            }
        return objectMapper.writeValueAsString(
            mapOf(
                "weekly_report" to
                    mapOf(
                        "team" to report.teamName,
                        "week_start" to report.weekStart.toString(),
                        "this_week" to report.formatted.thisWeek.map(::reportItemMap),
                        "next_week" to report.formatted.nextWeek.map(::reportItemMap),
                        "issues" to report.formatted.issues.map(::reportItemMap),
                        "others" to report.formatted.others.map(::reportItemMap),
                        "matched_against_prev" to matched,
                    ),
            ),
        )
    }

    private fun reportItemMap(item: ReportItem): Map<String, Any?> =
        mapOf(
            "assignee" to item.assignee,
            "category" to item.category,
            "text" to item.text,
            "progress" to item.progress,
            "due_date" to item.dueDate?.toString(),
        )
}
