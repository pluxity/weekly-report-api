package com.pluxity.weekly.report.service

import com.pluxity.weekly.core.response.BaseResponse
import com.pluxity.weekly.report.dto.FormattedReport
import com.pluxity.weekly.report.dto.MatchedAgainstPrev
import com.pluxity.weekly.report.dto.MatchedPair
import com.pluxity.weekly.report.dto.ReportItem
import com.pluxity.weekly.report.dto.WeeklyReportResponse
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@Service
@Transactional(readOnly = true)
class WeeklyReportService {
    fun findAll(
        teamId: Long?,
        weekStart: LocalDate?,
        weekEnd: LocalDate?,
    ): List<WeeklyReportResponse> = listOf(sampleResponse())

    fun findById(id: Long): WeeklyReportResponse = sampleResponse().copy(id = id)

    // TODO: entity/repository 도입 후 실제 조회 로직 + 권한 가드 구현
    private fun sampleResponse(): WeeklyReportResponse =
        WeeklyReportResponse(
            id = 1L,
            teamId = 10L,
            teamName = "개발팀",
            weekStart = LocalDate.of(2026, 5, 11),
            rawContent =
                """
                주간업무보고
                보고 기간 금주: 2026.05.11 ~ 05.15

                홍길동
                금주 (05/11 ~ 05/15)
                프로젝트 | 업무 내용 | 진행률
                ProductA v1.0 | #95 메인 페이지 구현 | 100%
                """.trimIndent(),
            formatted =
                FormattedReport(
                    thisWeek =
                        listOf(
                            ReportItem(
                                assignee = "홍길동",
                                category = "ProductA v1.0",
                                text = "#95 메인 페이지 구현",
                                progress = "100%",
                                dueDate = null,
                            ),
                        ),
                    nextWeek =
                        listOf(
                            ReportItem(
                                assignee = "홍길동",
                                category = "ProductB",
                                text = "신규 모듈 검토",
                                progress = null,
                                dueDate = LocalDate.of(2026, 5, 22),
                            ),
                        ),
                ),
            matchedAgainstPrev =
                MatchedAgainstPrev(
                    matched =
                        listOf(
                            MatchedPair(
                                prev = "#95 메인 페이지 구현",
                                curr = "#95 메인 페이지 구현 완료",
                            ),
                        ),
                    missing = listOf("로그인 모듈 리팩토링"),
                    new = listOf("신규 모듈 검토"),
                ),
            baseResponse =
                BaseResponse(
                    createdAt = "2026-05-20T14:30:00",
                    createdBy = "sample.user",
                    updatedAt = "2026-05-20T14:30:00",
                    updatedBy = "sample.user",
                ),
        )
}
