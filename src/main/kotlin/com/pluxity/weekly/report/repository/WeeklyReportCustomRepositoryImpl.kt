package com.pluxity.weekly.report.repository

import com.linecorp.kotlinjdsl.support.spring.data.jpa.repository.KotlinJdslJpqlExecutor
import com.pluxity.weekly.chat.dto.WeeklyReportSearchFilter
import com.pluxity.weekly.core.utils.findAllNotNull
import com.pluxity.weekly.report.entity.WeeklyReport
import com.pluxity.weekly.team.entity.Team

class WeeklyReportCustomRepositoryImpl(
    private val executor: KotlinJdslJpqlExecutor,
) : WeeklyReportCustomRepository {
    override fun findByFilter(filter: WeeklyReportSearchFilter): List<WeeklyReport> =
        executor.findAllNotNull {
            select(entity(WeeklyReport::class))
                .from(
                    entity(WeeklyReport::class),
                    fetchJoin(WeeklyReport::team),
                ).whereAnd(
                    filter.teamId?.let { path(WeeklyReport::team)(Team::id).eq(it) },
                    filter.teamIds?.let { path(WeeklyReport::team)(Team::id).`in`(it) },
                    filter.weekStart?.let { path(WeeklyReport::weekStart).greaterThanOrEqualTo(it) },
                    filter.weekEnd?.let { path(WeeklyReport::weekStart).lessThanOrEqualTo(it) },
                ).orderBy(path(WeeklyReport::weekStart).desc())
        }
}
