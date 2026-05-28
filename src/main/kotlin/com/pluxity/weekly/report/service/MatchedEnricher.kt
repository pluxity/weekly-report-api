package com.pluxity.weekly.report.service

import com.pluxity.weekly.chat.llm.dto.WeeklyReportMatchResult
import com.pluxity.weekly.report.dto.MatchedAgainstPrev
import com.pluxity.weekly.report.dto.MatchedItem
import com.pluxity.weekly.report.dto.MatchedPair
import com.pluxity.weekly.report.dto.ReportItem

/**
 * 항목에 휘발성 id(P1/C1...)를 부여한다. **번호 규칙은 여기 한 곳에서만 정의**하고,
 * 만들어진 맵을 buildMatchMessages(LLM 입력)와 enrichMatched(LLM 출력 복원)가 공유한다.
 * (입력 순서를 보존하는 LinkedHashMap)
 */
fun numberItems(
    items: List<ReportItem>,
    prefix: String,
): Map<String, ReportItem> = items.withIndex().associate { (i, item) -> "$prefix${i + 1}" to item }

/**
 * LLM이 반환한 id 쌍(P_/C_)을 실제 항목으로 복원하고, missing/new를 소스에서 계산한다.
 *
 * - prevById/currById는 numberItems로 한 번 부여된 id→항목 맵 (buildMatchMessages와 동일한 맵).
 * - 유효하지 않은 id(LLM 환각, 예 "이원희")는 무시.
 * - 한 P/C id는 각각 한 번만 (1:1) — 중복 매칭 방지.
 * - missing = 매칭 안 된 지난주 예정, new = 매칭 안 된 이번주 진행 (소스 항목에서 직접 → assignee 정확).
 *   동명 작업도 id가 다르므로 담당자가 섞이지 않는다.
 */
fun enrichMatched(
    raw: WeeklyReportMatchResult,
    prevById: Map<String, ReportItem>,
    currById: Map<String, ReportItem>,
): MatchedAgainstPrev {
    val usedPrev = mutableSetOf<String>()
    val usedCurr = mutableSetOf<String>()
    val matched = mutableListOf<MatchedPair>()
    for (pair in raw.matched) {
        val prevItem = prevById[pair.prev] ?: continue // 유효하지 않은 id → drop
        val currItem = currById[pair.curr] ?: continue
        if (pair.prev in usedPrev || pair.curr in usedCurr) continue // 1:1 보장
        usedPrev += pair.prev
        usedCurr += pair.curr
        matched += MatchedPair(assignee = prevItem.assignee, prev = prevItem.text, curr = currItem.text)
    }

    // 매칭 안 된 나머지를 소스에서 직접 파생 (LinkedHashMap이라 입력 순서 유지)
    val missing = prevById.filterKeys { it !in usedPrev }.values.map { MatchedItem(it.assignee, it.text) }
    val new = currById.filterKeys { it !in usedCurr }.values.map { MatchedItem(it.assignee, it.text) }

    return MatchedAgainstPrev(matched = matched, missing = missing, new = new)
}
