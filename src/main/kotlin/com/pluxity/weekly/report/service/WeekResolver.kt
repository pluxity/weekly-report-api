package com.pluxity.weekly.report.service

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.IsoFields
import java.time.temporal.TemporalAdjusters

/**
 * chat intent의 week 표현을 해당 주 월요일로 해석한다.
 * - "this"/null/빈값 → 이번주 월요일
 * - "last" → 지난주 월요일
 * - "M월 N주차" (예: "5월 4주차") → 그 달 1일이 속한 주가 1주차(월요일 시작), N번째 주의 월요일 (5월 4주차 → 2026-05-18)
 * - "N주차" (예: "28주차") → 오늘의 ISO 주 기준 연도에서 N번째 ISO 주의 월요일 (28주차 → 2026-07-06)
 * - ISO 날짜("2026-05-12") → 그 주 월요일 (LLM이 오늘 날짜 기준 완전한 ISO로 보정해 넘김)
 * - 그 외(LLM이 준 쓰레기 값) → 이번주 월요일 (lenient, 500 방지)
 *
 * 주차 계산은 LLM이 아니라 서버가 결정적으로 수행한다 (LLM은 원문 "N주차"/"M월 N주차"만 넘긴다).
 * "M월 N주차"는 "N주차"(연중 ISO)보다 먼저 검사한다 — "5월 4주차"의 "4주차"가 ISO 4주차로 오독되는 것 방지.
 */
fun resolveWeekStart(
    week: String?,
    today: LocalDate = LocalDate.now(java.time.ZoneId.of("Asia/Seoul")),
): LocalDate {
    val thisMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    return when (week?.trim()?.lowercase()) {
        null, "", "this" -> thisMonday
        "last" -> thisMonday.minusWeeks(1)
        else -> {
            val raw = week.trim()
            // "월...주차" 형태면 month-week로 확정(무효여도 ISO로 새지 않음), 그다음 "N주차", 마지막 ISO 날짜.
            when {
                MONTH_WEEK_REGEX.containsMatchIn(raw) -> parseMonthWeekMonday(raw, today) ?: thisMonday
                WEEK_NUMBER_REGEX.containsMatchIn(raw) -> parseWeekNumber(raw)?.let { isoWeekMonday(it, today) } ?: thisMonday
                else -> parseIsoDateMonday(raw) ?: thisMonday
            }
        }
    }
}

/** "5월 4주차", "5월4주차" 등에서 (월, 월내주차) 추출. */
private val MONTH_WEEK_REGEX = Regex("""(\d{1,2})\s*월\s*(\d{1,2})\s*주차""")

/** "28주차", "제28주차", "28 주차" 등에서 주차 번호만 추출. 매칭 실패 시 null. */
private val WEEK_NUMBER_REGEX = Regex("""(\d{1,2})\s*주차""")

private fun parseWeekNumber(text: String): Int? = WEEK_NUMBER_REGEX.find(text)?.groupValues?.get(1)?.toIntOrNull()

/**
 * "M월 N주차"를 그 주의 월요일로 계산한다 (A안: 그 달 1일이 속한 주 = 1주차, 월요일 시작).
 * - 연도는 오늘의 달력 연도를 사용.
 * - month가 1..12 밖이거나, weekOfMonth가 그 달의 유효 범위(대개 1..4~6)를 벗어나면 null.
 */
private fun parseMonthWeekMonday(
    text: String,
    today: LocalDate,
): LocalDate? {
    val m = MONTH_WEEK_REGEX.find(text) ?: return null
    val month = m.groupValues[1].toIntOrNull() ?: return null
    val weekOfMonth = m.groupValues[2].toIntOrNull() ?: return null
    if (month !in 1..12) return null

    val firstOfMonth = LocalDate.of(today.year, month, 1)
    val firstWeekMonday = firstOfMonth.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val lastWeekMonday =
        firstOfMonth
            .with(TemporalAdjusters.lastDayOfMonth())
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val maxWeek = ChronoUnit.WEEKS.between(firstWeekMonday, lastWeekMonday).toInt() + 1
    if (weekOfMonth !in 1..maxWeek) return null

    return firstWeekMonday.plusWeeks((weekOfMonth - 1).toLong())
}

/** 완전한 ISO 날짜면 그 주 월요일, 아니면 null. */
private fun parseIsoDateMonday(text: String): LocalDate? =
    runCatching { LocalDate.parse(text) }
        .getOrNull()
        ?.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

/**
 * ISO 주 기준으로 N주차의 월요일을 계산한다.
 * - 연도는 오늘의 주 기준 연도(WEEK_BASED_YEAR)를 사용 (연말/연초 경계에서 달력 연도와 어긋나는 것 방지).
 * - weekNo가 그 해의 유효 범위(1..52 또는 1..53)를 벗어나면 null.
 */
private fun isoWeekMonday(
    weekNo: Int,
    today: LocalDate,
): LocalDate? {
    val weekBasedYear = today.get(IsoFields.WEEK_BASED_YEAR)
    // 12월 28일은 항상 그 해의 마지막 ISO 주에 속한다 → 유효 최대 주차
    val maxWeek = LocalDate.of(weekBasedYear, 12, 28).get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)
    if (weekNo !in 1..maxWeek) return null
    // 1월 4일은 항상 그 해의 1주차에 속한다(ISO 정의) → 1주차 월요일 기준으로 (N-1)주 이동
    return LocalDate.of(weekBasedYear, 1, 4)
        .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        .plusWeeks((weekNo - 1).toLong())
}
