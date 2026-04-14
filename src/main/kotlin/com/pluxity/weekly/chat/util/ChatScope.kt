package com.pluxity.weekly.chat.util

import java.time.LocalDate

object ChatScope {
    private const val WEEKS = 2L

    fun scopeStartDate(): LocalDate = LocalDate.now().minusWeeks(WEEKS)

    fun isWithinScope(startDate: LocalDate?): Boolean = startDate != null && startDate >= scopeStartDate()
}
