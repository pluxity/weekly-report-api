package com.pluxity.weekly.report.repository

import com.pluxity.weekly.chat.dto.WeeklyReportSearchFilter
import com.pluxity.weekly.report.entity.WeeklyReport

interface WeeklyReportCustomRepository {
    fun findByFilter(filter: WeeklyReportSearchFilter): List<WeeklyReport>
}
