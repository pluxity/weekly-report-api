package com.pluxity.weekly.report.service

import com.pluxity.weekly.chat.llm.dto.WeeklyReportClassifyResult
import com.pluxity.weekly.report.dto.WeeklyReportResponse
import com.pluxity.weekly.report.dto.toResponse
import com.pluxity.weekly.report.entity.WeeklyReport
import com.pluxity.weekly.report.repository.WeeklyReportRepository
import com.pluxity.weekly.team.entity.Team
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class WeeklyReportWriteService(
    private val weeklyReportRepository: WeeklyReportRepository,
) {
    /**
     * chat 라우터에서 LLM 분류 결과를 받아 즉시 저장 (UPSERT).
     * 같은 team_id + week_start가 있으면 update, 없으면 신규 save.
     * teamNameRaw는 LLM이 추출 못한 경우 context의 team.name으로 fallback.
     */
    fun upsertFromClassify(
        team: Team,
        rawContent: String,
        classify: WeeklyReportClassifyResult,
    ): WeeklyReportResponse {
        val teamNameRaw = classify.teamNameRaw ?: team.name
        val existing =
            weeklyReportRepository.findByTeamIdAndWeekStart(
                teamId = team.requiredId,
                weekStart = classify.weekStart,
            )
        val saved =
            if (existing != null) {
                existing.rawContent = rawContent
                existing.formatted = classify.formatted
                existing.matchedAgainstPrev = null // 내용 변경 → 매칭 캐시 무효화
                existing
            } else {
                weeklyReportRepository.save(
                    WeeklyReport(
                        team = team,
                        teamNameRaw = teamNameRaw,
                        weekStart = classify.weekStart,
                        weekLabel = classify.weekLabel,
                        rawContent = rawContent,
                        formatted = classify.formatted,
                    ),
                )
            }
        return saved.toResponse()
    }
}
