package com.pluxity.weekly.report.repository

import com.pluxity.weekly.report.entity.WeeklyReport
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDate

interface WeeklyReportRepository :
    JpaRepository<WeeklyReport, Long>,
    WeeklyReportCustomRepository {
    @Query(
        """
        SELECT w.team.id AS teamId,
               w.weekStart AS weekStart,
               w.createdAt AS createdAt,
               w.createdBy AS createdBy
          FROM WeeklyReport w
         WHERE w.weekStart BETWEEN :weekStart AND :weekEnd
        """,
    )
    fun findSummaryRows(
        @Param("weekStart") weekStart: LocalDate,
        @Param("weekEnd") weekEnd: LocalDate,
    ): List<WeeklyReportSummaryRow>

}
