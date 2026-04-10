package com.pluxity.weekly.chat.util

import java.time.LocalDate

object ChatScope {
    private const val WEEKS = 2L

    fun isWithinScope(startDate: LocalDate?): Boolean =
        startDate != null && startDate >= LocalDate.now().minusWeeks(WEEKS)
}
