package com.pluxity.weekly.chat.v2

import com.pluxity.weekly.auth.authorization.AuthorizationService
import com.pluxity.weekly.chat.v2.dto.GetWeeklyReportArgs
import com.pluxity.weekly.core.exception.CustomException
import com.pluxity.weekly.report.dto.ReportItem
import com.pluxity.weekly.report.dto.WeeklyReportResponse
import com.pluxity.weekly.report.service.WeeklyReportService
import com.pluxity.weekly.team.entity.Team
import com.pluxity.weekly.team.repository.TeamRepository
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

/**
 * get_weekly_report 실행부 — 주간보고 조회 (팀 리더 또는 admin).
 *
 * 스코프는 [WeeklyReportService.findAll]에 위임한다(admin=전체, 리더=본인 팀들, member=거부).
 * - team 지정: 그 팀 주간보고 내용(없으면 미작성 안내).
 * - team 미지정 + 리더(단일 팀): 내 팀 내용(기존 UX).
 * - team 미지정 + admin(다팀): 팀별 제출현황 + 미제출 팀 목록("이번주 안 낸 팀 누구").
 * rawContent(원문 전체)는 제외하고 정리된 항목·지난주 매칭만 준다. week 해석은 weekStartOf(resolveWeekStart).
 */
@Component
class GetWeeklyReportHandler(
    private val teamRepository: TeamRepository,
    private val weeklyReportService: WeeklyReportService,
    private val authorizationService: AuthorizationService,
    private val support: ChatV2ToolSupport,
    private val objectMapper: ObjectMapper,
) {
    fun handle(
        argumentsJson: String,
        currentUserId: Long,
    ): String {
        val args = support.readArgs<GetWeeklyReportArgs>(argumentsJson)
        val weekStart = weeklyReportService.weekStartOf(args.week)

        val user = authorizationService.currentUser()
        // 스코프 내 팀 목록(이름 필요): null=ADMIN(전체 팀), 아니면 리더인 팀들.
        val visibleTeamIds = authorizationService.visibleTeamIds(user)
        val visibleTeams: List<Team> =
            if (visibleTeamIds == null) teamRepository.findAll() else teamRepository.findAllById(visibleTeamIds)

        val requestedTeam = args.team?.trim()?.takeIf { it.isNotBlank() }
        if (requestedTeam != null) {
            return handleSpecificTeam(requestedTeam, visibleTeams, weekStart)
        }

        // team 미지정 — 스코프 자동(admin=전체, 리더=내 팀). member는 findAll이 CustomException을 던진다.
        val reports =
            runCatching { weeklyReportService.findAll(null, weekStart, weekStart) }
                .getOrElse { e ->
                    if (e is CustomException) return leaderOrAdminOnly()
                    throw e
                }

        return if (visibleTeams.size <= 1) {
            // 리더 단일 팀 — 기존 UX 유지.
            reports.firstOrNull()?.let { reportContent(it) }
                ?: objectMapper.writeValueAsString(
                    mapOf("submitted" to false, "message" to "이번 주 주간보고가 없습니다."),
                )
        } else {
            // admin 다팀 — 제출현황 + 미제출 팀 목록.
            submissionStatus(visibleTeams, reports, weekStart)
        }
    }

    private fun handleSpecificTeam(
        requestedTeam: String,
        visibleTeams: List<Team>,
        weekStart: java.time.LocalDate,
    ): String {
        // member는 visibleTeams가 비어 0건 매칭 → 아래에서 안내. 리더/ admin은 스코프 내 팀만 해소.
        val matched = visibleTeams.filter { ItemNameMatcher.matches(requestedTeam, it.name) }
        val team =
            when (matched.size) {
                0 ->
                    return support.errorResult(
                        "'$requestedTeam' 팀을 볼 수 없거나 존재하지 않습니다. " +
                            "팀 리더는 본인 팀만, admin은 전 팀을 조회할 수 있습니다.",
                    )
                1 -> matched.first()
                else ->
                    return support.errorResult(
                        "'$requestedTeam'에 해당하는 팀이 여러 개입니다: " +
                            matched.take(5).joinToString(", ") { it.name } + ". 팀 이름을 더 구체적으로 알려주세요.",
                    )
            }

        val reports =
            runCatching { weeklyReportService.findAll(team.requiredId, weekStart, weekStart) }
                .getOrElse { e ->
                    if (e is CustomException) return leaderOrAdminOnly()
                    throw e
                }

        return reports.firstOrNull()?.let { reportContent(it) }
            ?: objectMapper.writeValueAsString(
                mapOf(
                    "team" to team.name,
                    "week_start" to weekStart.toString(),
                    "submitted" to false,
                    "message" to "이번 주 주간보고가 없습니다 (미작성).",
                ),
            )
    }

    /** admin 다팀 뷰 — 각 팀 제출 여부 + 미제출 팀 이름 목록. */
    private fun submissionStatus(
        visibleTeams: List<Team>,
        reports: List<WeeklyReportResponse>,
        weekStart: java.time.LocalDate,
    ): String {
        val submitted = reports.map { it.teamName }.toSet()
        return objectMapper.writeValueAsString(
            mapOf(
                "week_start" to weekStart.toString(),
                "teams" to visibleTeams.map { mapOf("team" to it.name, "submitted" to (it.name in submitted)) },
                "not_submitted" to visibleTeams.map { it.name }.filter { it !in submitted },
            ),
        )
    }

    /** 단일 팀 주간보고 내용 (정리된 항목 + 지난주 대비 매칭). */
    private fun reportContent(report: WeeklyReportResponse): String {
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

    private fun leaderOrAdminOnly(): String =
        support.errorResult("주간보고는 팀 리더 또는 admin만 조회할 수 있습니다.")

    private fun reportItemMap(item: ReportItem): Map<String, Any?> =
        mapOf(
            "assignee" to item.assignee,
            "category" to item.category,
            "text" to item.text,
            "progress" to item.progress,
            "due_date" to item.dueDate?.toString(),
        )
}
