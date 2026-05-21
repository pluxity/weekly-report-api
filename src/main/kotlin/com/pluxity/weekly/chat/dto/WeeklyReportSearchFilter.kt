package com.pluxity.weekly.chat.dto

import java.time.LocalDate

data class WeeklyReportSearchFilter(
    val teamId: Long? = null,
    val teamIds: List<Long>? = null,
    val weekStart: LocalDate? = null,
    val weekEnd: LocalDate? = null,
)
