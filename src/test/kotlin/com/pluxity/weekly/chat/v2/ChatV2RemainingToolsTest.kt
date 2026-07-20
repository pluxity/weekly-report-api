package com.pluxity.weekly.chat.v2

import com.pluxity.weekly.auth.user.repository.UserRepository
import com.pluxity.weekly.report.service.WeeklyReportService
import com.pluxity.weekly.task.service.TaskReviewService
import com.pluxity.weekly.team.repository.TeamRepository
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.every
import io.mockk.mockk
import tools.jackson.databind.json.JsonMapper

/**
 * search_users / list_pending_reviews / get_task_history / get_weekly_report 특성화 테스트.
 * execute() 진입점 기준으로 각 핸들러의 dispatch·핵심 게이트를 박아둔다 (핸들러 분리 안전망).
 */
class ChatV2RemainingToolsTest :
    BehaviorSpec({

        val mapper = JsonMapper()
        val userRepository: UserRepository = mockk()
        val taskReviewService: TaskReviewService = mockk()
        val teamRepository: TeamRepository = mockk()
        val weeklyReportService: WeeklyReportService = mockk()
        val support = ChatV2ToolSupport(mapper)

        val executor =
            ChatV2ToolExecutor(
                support = support,
                searchItemsHandler = mockk<SearchItemsHandler>(),
                searchUsersHandler = SearchUsersHandler(userRepository, support, mapper),
                aggregateItemsHandler = mockk<AggregateItemsHandler>(),
                listPendingReviewsHandler = ListPendingReviewsHandler(taskReviewService, mapper),
                getTaskHistoryHandler = GetTaskHistoryHandler(taskReviewService, support, mapper),
                getWeeklyReportHandler = GetWeeklyReportHandler(teamRepository, weeklyReportService, support, mapper),
            )

        fun exec(
            tool: String,
            argsJson: String,
            registry: ChatV2IdRegistry = ChatV2IdRegistry(1L),
        ) = executor.execute(tool, argsJson, 1L, registry)

        Given("search_users") {
            When("사용자가 없으면") {
                every { userRepository.findAllBy(any()) } returns emptyList()

                val result = mapper.readValue(exec(ChatV2Tools.SEARCH_USERS, """{}"""), Map::class.java)

                Then("users 키로 빈 목록을 반환한다 (dispatch 정상)") {
                    (result["users"] as List<*>).size shouldBe 0
                }
            }
        }

        Given("list_pending_reviews") {
            When("대기 리뷰가 없으면") {
                every { taskReviewService.findPendingReviews() } returns emptyList()

                val result = mapper.readValue(exec(ChatV2Tools.LIST_PENDING_REVIEWS, """{}"""), Map::class.java)

                Then("pending_reviews 키로 빈 목록을 반환한다") {
                    (result["pending_reviews"] as List<*>).size shouldBe 0
                }
            }
        }

        Given("get_task_history") {
            When("검색으로 확인되지 않은 task_id면") {
                val result = exec(ChatV2Tools.GET_TASK_HISTORY, """{"task_id":99}""")

                Then("id 추측을 차단하고 error를 반환한다") {
                    result shouldContain "error"
                    result shouldContain "확인되지 않은 id"
                }
            }
        }

        Given("get_weekly_report") {
            When("리더가 아닌 사용자가 조회하면") {
                every { teamRepository.findByLeaderId(any()) } returns emptyList()

                val result = exec(ChatV2Tools.GET_WEEKLY_REPORT, """{}""")

                Then("팀 리더 전용 error를 반환한다") {
                    result shouldContain "error"
                    result shouldContain "팀 리더만"
                }
            }
        }
    })
