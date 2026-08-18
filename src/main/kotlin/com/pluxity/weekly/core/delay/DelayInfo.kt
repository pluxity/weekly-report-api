package com.pluxity.weekly.core.delay

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * 지연 계산 결과 — Task/Epic/Project 응답 및 대시보드 지표의 단일 기준.
 *
 * `completedAt != null` 이면 "완료됨"으로 본다.
 * - 완료건: `completedAt - dueDate` (음수=조기완료, 0=정시, 양수=지연완료)
 * - 미완료 + 마감초과: `today - dueDate` (양수=지연중)
 * - 미완료 & 마감 이내, 또는 dueDate 없음: `delayDays = null`
 */
data class DelayInfo(
    val completedAt: LocalDate?,
    val delayed: Boolean,
    val delayDays: Int?,
) {
    companion object {
        fun of(
            dueDate: LocalDate?,
            completedAt: LocalDate?,
            today: LocalDate,
        ): DelayInfo {
            if (dueDate == null) return DelayInfo(completedAt, delayed = false, delayDays = null)
            val days =
                when {
                    completedAt != null -> ChronoUnit.DAYS.between(dueDate, completedAt).toInt()
                    today.isAfter(dueDate) -> ChronoUnit.DAYS.between(dueDate, today).toInt()
                    else -> return DelayInfo(completedAt = null, delayed = false, delayDays = null)
                }
            return DelayInfo(completedAt = completedAt, delayed = days > 0, delayDays = days)
        }
    }
}
