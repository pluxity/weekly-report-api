package com.pluxity.weekly.report.service

import com.pluxity.weekly.chat.llm.dto.MatchedPairRaw
import com.pluxity.weekly.chat.llm.dto.WeeklyReportMatchResult
import com.pluxity.weekly.report.dto.ReportItem
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class MatchedEnricherTest :
    BehaviorSpec({

        fun item(
            assignee: String?,
            text: String,
        ) = ReportItem(assignee = assignee, category = null, text = text, progress = null, dueDate = null)

        Given("enrichMatched") {
            // P1 김인엽 API.., P2 김형래 VLM.. / C1 김인엽 API.., C2 윤지선 차트..
            val prevNextWeek =
                listOf(
                    item("김인엽", "API 벤더 인증 추가"),
                    item("김형래", "VLM 데모 초기 버전 배포"),
                )
            val currThisWeek =
                listOf(
                    item("김인엽", "API 벤더 인증 추가"),
                    item("윤지선", "차트 컴포넌트 추가"),
                )

            When("id 쌍이 정상 매칭되면") {
                val raw = WeeklyReportMatchResult(matched = listOf(MatchedPairRaw(prev = "P1", curr = "C1")))

                val result = enrichMatched(raw, numberItems(prevNextWeek, "P"), numberItems(currThisWeek, "C"))

                Then("matched는 id로 원본 항목을 복원한다") {
                    result.matched.size shouldBe 1
                    result.matched[0].assignee shouldBe "김인엽"
                    result.matched[0].prev shouldBe "API 벤더 인증 추가"
                    result.matched[0].curr shouldBe "API 벤더 인증 추가"
                }
                Then("매칭 안 된 prev는 missing, curr는 new로 파생된다") {
                    result.missing.map { it.assignee to it.text } shouldBe listOf("김형래" to "VLM 데모 초기 버전 배포")
                    result.new.map { it.assignee to it.text } shouldBe listOf("윤지선" to "차트 컴포넌트 추가")
                }
            }

            When("유효하지 않은 id(LLM 환각)가 오면") {
                // prev에 이름 "이원희"가 들어옴 (실제 버그 케이스)
                val raw = WeeklyReportMatchResult(matched = listOf(MatchedPairRaw(prev = "이원희", curr = "C1")))

                val result = enrichMatched(raw, numberItems(prevNextWeek, "P"), numberItems(currThisWeek, "C"))

                Then("그 쌍은 무시되고 C1은 매칭 안 된 것으로 처리") {
                    result.matched shouldBe emptyList()
                    result.new.map { it.text } shouldBe listOf("API 벤더 인증 추가", "차트 컴포넌트 추가")
                }
            }

            When("같은 curr에 두 쌍이 오면 (1:1 위반)") {
                val raw =
                    WeeklyReportMatchResult(
                        matched =
                            listOf(
                                MatchedPairRaw(prev = "P1", curr = "C1"),
                                MatchedPairRaw(prev = "P2", curr = "C1"), // C1 중복 → 무시
                            ),
                    )

                val result = enrichMatched(raw, numberItems(prevNextWeek, "P"), numberItems(currThisWeek, "C"))

                Then("먼저 온 쌍만 매칭되고 나머지는 무시된다") {
                    result.matched.size shouldBe 1
                    result.matched[0].prev shouldBe "API 벤더 인증 추가"
                    result.missing.map { it.text } shouldBe listOf("VLM 데모 초기 버전 배포") // P2는 missing
                }
            }

            When("동명 작업을 여러 사람이 적었고 둘 다 매칭 안 되면") {
                // 핵심 버그 픽스: text가 같아도 id가 다르니 담당자가 섞이지 않는다
                val prevDup =
                    listOf(
                        item("한새싹", "운영 모니터링 및 이슈 대응"),
                        item("최승은", "운영 모니터링 및 이슈 대응"),
                    )
                val raw = WeeklyReportMatchResult(matched = emptyList())

                val result = enrichMatched(raw, numberItems(prevDup, "P"), numberItems(emptyList(), "C"))

                Then("각 항목이 원래 담당자 그대로 missing에 들어간다") {
                    result.missing.map { it.assignee to it.text } shouldBe
                        listOf(
                            "한새싹" to "운영 모니터링 및 이슈 대응",
                            "최승은" to "운영 모니터링 및 이슈 대응",
                        )
                }
            }
        }
    })
