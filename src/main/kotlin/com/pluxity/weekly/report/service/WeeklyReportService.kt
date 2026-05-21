package com.pluxity.weekly.report.service

import com.pluxity.weekly.chat.dto.WeeklyReportSearchFilter
import com.pluxity.weekly.core.constant.ErrorCode
import com.pluxity.weekly.core.exception.CustomException
import com.pluxity.weekly.report.dto.WeeklyReportResponse
import com.pluxity.weekly.report.dto.WeeklyReportSummaryResponse
import com.pluxity.weekly.report.dto.toResponse
import com.pluxity.weekly.report.repository.WeeklyReportRepository
import com.pluxity.weekly.team.repository.TeamRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@Service
@Transactional(readOnly = true)
class WeeklyReportService(
    private val weeklyReportRepository: WeeklyReportRepository,
    private val teamRepository: TeamRepository,
) {
    fun findAll(
        teamId: Long?,
        weekStart: LocalDate?,
        weekEnd: LocalDate?,
    ): List<WeeklyReportResponse> {
        // TODO: 권한 가드 — ADMIN 전체 / Leader 본인 팀만
        val filter = WeeklyReportSearchFilter(teamId = teamId, weekStart = weekStart, weekEnd = weekEnd)
        return weeklyReportRepository.findByFilter(filter).map { it.toResponse() }
    }

    fun findById(id: Long): WeeklyReportResponse {
        val report =
            weeklyReportRepository.findByIdOrNull(id)
                ?: throw CustomException(ErrorCode.NOT_FOUND_WEEKLY_REPORT, id)
        return report.toResponse()
    }

    /**
     * 팀 × 주차 슬롯을 앱에서 생성하고, 실제 작성된 보고는 projection으로 lookup.
     */
    fun findSummary(
        weekStart: LocalDate,
        weekEnd: LocalDate,
    ): List<WeeklyReportSummaryResponse> {
        val teams = teamRepository.findAll()
        val weeks = generateMondays(weekStart, weekEnd)
        val rowsByKey =
            weeklyReportRepository
                .findSummaryRows(weekStart, weekEnd)
                .associateBy { it.teamId to it.weekStart }

        return teams.flatMap { team ->
            val teamId = team.requiredId
            weeks.map { week ->
                val row = rowsByKey[teamId to week]
                WeeklyReportSummaryResponse(
                    teamId = teamId,
                    weekStart = week,
                    exists = row != null,
                    createdAt = row?.createdAt,
                    createdBy = row?.createdBy,
                )
            }
        }
    }

    private fun generateMondays(
        start: LocalDate,
        end: LocalDate,
    ): List<LocalDate> {
        val list = mutableListOf<LocalDate>()
        var current = start
        while (!current.isAfter(end)) {
            list.add(current)
            current = current.plusWeeks(1)
        }
        return list
    }
}
