package com.pluxity.weekly.report.repository

import java.time.LocalDate
import java.time.LocalDateTime

interface WeeklyReportSummaryRow {
    val teamId: Long
    val weekStart: LocalDate
    val createdAt: LocalDateTime
    val createdBy: String?
}
