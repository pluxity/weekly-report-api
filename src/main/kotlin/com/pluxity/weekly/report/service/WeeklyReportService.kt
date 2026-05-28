package com.pluxity.weekly.report.service

import com.pluxity.weekly.auth.authorization.AuthorizationService
import com.pluxity.weekly.chat.dto.WeeklyReportSearchFilter
import com.pluxity.weekly.chat.llm.dto.WeeklyReportClassifyResult
import com.pluxity.weekly.core.constant.ErrorCode
import com.pluxity.weekly.core.exception.CustomException
import com.pluxity.weekly.report.dto.MatchedAgainstPrev
import com.pluxity.weekly.report.dto.ReportItem
import com.pluxity.weekly.report.dto.WeeklyReportResponse
import com.pluxity.weekly.report.dto.WeeklyReportSummaryResponse
import com.pluxity.weekly.report.dto.toResponse
import com.pluxity.weekly.report.entity.WeeklyReport
import com.pluxity.weekly.report.repository.WeeklyReportRepository
import com.pluxity.weekly.team.entity.Team
import com.pluxity.weekly.team.repository.TeamRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

@Service
@Transactional(readOnly = true)
class WeeklyReportService(
    private val weeklyReportRepository: WeeklyReportRepository,
    private val teamRepository: TeamRepository,
    private val authorizationService: AuthorizationService,
) {
    fun findAll(
        teamId: Long?,
        weekStart: LocalDate?,
        weekEnd: LocalDate?,
    ): List<WeeklyReportResponse> {
        val user = authorizationService.currentUser()
        authorizationService.requireAdminOrLeader(user)

        val visibleTeams = authorizationService.visibleTeamIds(user)
        val filter =
            when {
                teamId != null -> {
                    authorizationService.requireTeamAccess(user, teamId)
                    WeeklyReportSearchFilter(teamId = teamId, weekStart = weekStart, weekEnd = weekEnd)
                }
                visibleTeams == null -> {
                    // ADMIN, teamId 미지정 → 전체
                    WeeklyReportSearchFilter(weekStart = weekStart, weekEnd = weekEnd)
                }
                else -> {
                    // Leader, teamId 미지정 → 본인 팀들로 제한
                    WeeklyReportSearchFilter(teamIds = visibleTeams, weekStart = weekStart, weekEnd = weekEnd)
                }
            }

        return weeklyReportRepository.findByFilter(filter).map { it.toResponse() }
    }

    fun findForChat(week: String?): WeeklyReportResponse? {
        val user = authorizationService.currentUser()
        val team = teamRepository.findByLeaderId(user.requiredId).firstOrNull() ?: return null
        val weekStart = resolveWeekStart(week)
        return weeklyReportRepository
            .findByTeamIdAndWeekStart(team.requiredId, weekStart)
            ?.toResponse()
    }

    /**
     * 매칭용: 1주 전 보고의 nextWeek 항목(=지난주가 적은 "이번주 예정")을 반환. 없으면 빈 리스트.
     * formatted는 @Convert 컬럼이라 lazy 이슈 없음. LLM 콜은 호출 측(핸들러)에서 tx 밖으로.
     */
    fun findPrevWeekNextItems(
        team: Team,
        currWeekStart: LocalDate,
    ): List<ReportItem> {
        val prevMonday =
            currWeekStart
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .minusWeeks(1)
        return weeklyReportRepository
            .findByTeamIdAndWeekStart(team.requiredId, prevMonday)
            ?.formatted
            ?.nextWeek
            .orEmpty()
    }

    // ── 쓰기 (클래스 기본은 readOnly라 메서드에서 read-write로 오버라이드) ──

    /**
     * chat 라우터에서 LLM 분류 결과를 받아 즉시 저장 (UPSERT).
     * 같은 team_id + week_start가 있으면 update, 없으면 신규 save.
     * teamNameRaw는 LLM이 추출 못한 경우 context의 team.name으로 fallback.
     * matched는 호출 측에서 tx 밖에서 계산한 매칭 결과 (best-effort, 실패 시 null).
     */
    @Transactional
    fun upsertFromClassify(
        team: Team,
        rawContent: String,
        classify: WeeklyReportClassifyResult,
        matched: MatchedAgainstPrev? = null,
    ): WeeklyReportResponse {
        val teamNameRaw = classify.teamNameRaw ?: team.name
        // upsert 키 신뢰성: LLM이 월요일이 아닌 날짜를 줘도 그 주 월요일로 정규화 (중복 보고 방지)
        val weekStart = classify.weekStart.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val existing =
            weeklyReportRepository.findByTeamIdAndWeekStart(
                teamId = team.requiredId,
                weekStart = weekStart,
            )
        val saved =
            if (existing != null) {
                existing.rawContent = rawContent
                existing.formatted = classify.formatted
                existing.matchedAgainstPrev = matched // 재작성 시 재계산된 매칭으로 갱신 (없으면 null)
                existing
            } else {
                weeklyReportRepository.save(
                    WeeklyReport(
                        team = team,
                        teamNameRaw = teamNameRaw,
                        weekStart = weekStart,
                        rawContent = rawContent,
                        formatted = classify.formatted,
                        matchedAgainstPrev = matched,
                    ),
                )
            }
        return saved.toResponse()
    }

    /**
     * chat 삭제 전용. (team, weekStart)로 보고를 찾아 hard delete.
     * 호출 측이 team을 leader 본인 팀으로 확정해 넘기므로 권한 재검증 불필요.
     * 해당 주차 보고가 없으면 null 반환 (호출 측에서 안내).
     */
    @Transactional
    fun delete(
        team: Team,
        weekStart: LocalDate,
    ): Long? {
        val report =
            weeklyReportRepository.findByTeamIdAndWeekStart(
                teamId = team.requiredId,
                weekStart = weekStart,
            ) ?: return null
        val id = report.requiredId
        weeklyReportRepository.delete(report)
        return id
    }

    fun findById(id: Long): WeeklyReportResponse {
        val report =
            weeklyReportRepository.findByIdOrNull(id)
                ?: throw CustomException(ErrorCode.NOT_FOUND_WEEKLY_REPORT, id)

        val user = authorizationService.currentUser()
        authorizationService.requireTeamAccess(user, report.team.requiredId)

        return report.toResponse()
    }

    /**
     * 팀 × 주차 슬롯을 앱에서 생성하고, 실제 작성된 보고는 projection으로 lookup.
     * ADMIN은 전체 팀, Leader는 본인 팀들만 그리드에 노출.
     */
    fun findSummary(
        weekStart: LocalDate,
        weekEnd: LocalDate,
    ): List<WeeklyReportSummaryResponse> {
        val user = authorizationService.currentUser()
        authorizationService.requireAdminOrLeader(user)

        val normalizedStart = weekStart.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val normalizedEnd = weekEnd.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        if (normalizedStart.isAfter(normalizedEnd)) return emptyList()

        val visibleTeams = authorizationService.visibleTeamIds(user)
        val teams =
            if (visibleTeams == null) {
                teamRepository.findAll()
            } else {
                teamRepository.findAllById(visibleTeams)
            }
        val weeks = generateMondays(normalizedStart, normalizedEnd)
        val rowsByKey =
            weeklyReportRepository
                .findSummaryRows(normalizedStart, normalizedEnd)
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
