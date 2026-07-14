package com.pluxity.weekly.chat.v2

import com.pluxity.weekly.chat.llm.dto.WeeklyReportClassifyResult
import com.pluxity.weekly.chat.llm.dto.WeeklyReportMatchResult
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import tools.jackson.databind.json.JsonMapper
import java.time.LocalDate

/**
 * structured output 스키마 ↔ 역직렬화 DTO 계약 검증.
 * 스키마를 만족하는 모델 출력(전 필드 required, 없는 값은 null)이 기존
 * WeeklyReportClassifyResult/WeeklyReportMatchResult로 그대로 파싱되어야 한다 —
 * 여기 깨지면 생성 경로가 LLM_INVALID_RESPONSE로 실패한다.
 */
class ChatV2WeeklyReportSchemasTest :
    BehaviorSpec({

        val objectMapper = JsonMapper()

        Given("classify 스키마") {
            When("스키마 정의를 보면") {
                Then("최상위는 단일 팀 오브젝트(배열 아님)이고 4개 섹션이 required다") {
                    ChatV2WeeklyReportSchemas.CLASSIFY["type"] shouldBe "object"

                    @Suppress("UNCHECKED_CAST")
                    val formatted =
                        (ChatV2WeeklyReportSchemas.CLASSIFY["properties"] as Map<String, Any>)["formatted"] as Map<String, Any>
                    formatted["required"] shouldBe listOf("thisWeek", "nextWeek", "issues", "others")
                }
            }

            When("스키마를 만족하는 모델 출력을 파싱하면") {
                val content =
                    """
                    {
                      "team": "경영기획팀",
                      "team_name_raw": "경영전략본부 경영기획팀",
                      "week_start": "2026-07-06",
                      "formatted": {
                        "thisWeek": [
                          {"assignee": "윤승현", "category": "기타", "text": "IR 자료 업데이트", "progress": "80%", "due_date": null}
                        ],
                        "nextWeek": [
                          {"assignee": null, "category": "한싱가포르", "text": "용역변경비 변경", "progress": null, "due_date": "2026-07-17"}
                        ],
                        "issues": [],
                        "others": []
                      }
                    }
                    """.trimIndent()

                val result = objectMapper.readValue(content, WeeklyReportClassifyResult::class.java)

                Then("WeeklyReportClassifyResult로 매핑된다") {
                    result.team shouldBe "경영기획팀"
                    result.teamNameRaw shouldBe "경영전략본부 경영기획팀"
                    result.weekStart shouldBe LocalDate.of(2026, 7, 6)
                    result.formatted.thisWeek.size shouldBe 1
                    result.formatted.thisWeek
                        .first()
                        .progress shouldBe "80%"
                    result.formatted.nextWeek
                        .first()
                        .dueDate shouldBe LocalDate.of(2026, 7, 17)
                    result.formatted.issues shouldBe emptyList()
                }
            }

            When("본문 없는 입력에 대한 빈 섹션 출력을 파싱하면") {
                val content =
                    """
                    {"team": null, "team_name_raw": null, "week_start": "2026-07-13",
                     "formatted": {"thisWeek": [], "nextWeek": [], "issues": [], "others": []}}
                    """.trimIndent()

                val result = objectMapper.readValue(content, WeeklyReportClassifyResult::class.java)

                Then("team null·빈 섹션으로 매핑된다 (requireClassifiedItems가 안내로 돌려보낼 모양)") {
                    result.team.shouldBeNull()
                    result.formatted.thisWeek shouldBe emptyList()
                }
            }
        }

        Given("match 스키마") {
            When("스키마를 만족하는 모델 출력을 파싱하면") {
                val content = """{"matched": [{"prev": "P2", "curr": "C1"}]}"""

                val result = objectMapper.readValue(content, WeeklyReportMatchResult::class.java)

                Then("WeeklyReportMatchResult로 매핑된다") {
                    result.matched.size shouldBe 1
                    result.matched.first().prev shouldBe "P2"
                    result.matched.first().curr shouldBe "C1"
                }
            }
        }
    })
