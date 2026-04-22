package com.pluxity.weekly.core.validation

import com.pluxity.weekly.core.constant.ErrorCode
import com.pluxity.weekly.core.exception.CustomException
import java.time.LocalDate

fun validateDateRange(
    startDate: LocalDate?,
    dueDate: LocalDate?,
) {
    if (startDate != null && dueDate != null && startDate.isAfter(dueDate)) {
        throw CustomException(ErrorCode.INVALID_DATE_RANGE, startDate, dueDate)
    }
}
