package com.pluxity.weekly.report.service

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * chat intent의 week 표현을 해당 주 월요일로 해석한다.
 * - "this"/null/빈값 → 이번주 월요일
 * - "last" → 지난주 월요일
 * - ISO 날짜("2026-05-12") → 그 주 월요일 (LLM이 오늘 날짜 기준 완전한 ISO로 보정해 넘김)
 * - 그 외(LLM이 준 쓰레기 값) → 이번주 월요일 (lenient, 500 방지)
 */
fun resolveWeekStart(
    week: String?,
    today: LocalDate = LocalDate.now(java.time.ZoneId.of("Asia/Seoul")),
): LocalDate {
    val thisMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    return when (week?.trim()?.lowercase()) {
        null, "", "this" -> thisMonday
        "last" -> thisMonday.minusWeeks(1)
        else ->
            runCatching { LocalDate.parse(week.trim()) }
                .getOrNull()
                ?.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                ?: thisMonday
    }
}
