package com.pluxity.weekly.report.entity

import com.pluxity.weekly.report.dto.FormattedReport
import com.pluxity.weekly.report.dto.MatchedAgainstPrev
import com.pluxity.weekly.team.entity.Team
import com.pluxity.weekly.team.entity.dummyTeam
import com.pluxity.weekly.test.withAudit
import com.pluxity.weekly.test.withId
import java.time.LocalDate

fun dummyWeeklyReport(
    id: Long? = null,
    team: Team = dummyTeam(id = 1L),
    teamNameRaw: String = team.name,
    weekStart: LocalDate = LocalDate.of(2026, 5, 11),
    rawContent: String = "원문 내용",
    formatted: FormattedReport = FormattedReport(),
    matchedAgainstPrev: MatchedAgainstPrev? = null,
) = WeeklyReport(
    team = team,
    teamNameRaw = teamNameRaw,
    weekStart = weekStart,
    rawContent = rawContent,
    formatted = formatted,
    matchedAgainstPrev = matchedAgainstPrev,
).withId(id).withAudit()
