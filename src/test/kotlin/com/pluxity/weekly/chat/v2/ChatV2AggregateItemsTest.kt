package com.pluxity.weekly.chat.v2

import com.pluxity.weekly.auth.user.repository.UserRepository
import com.pluxity.weekly.core.response.BaseResponse
import com.pluxity.weekly.epic.service.EpicService
import com.pluxity.weekly.project.service.ProjectService
import com.pluxity.weekly.task.dto.TaskResponse
import com.pluxity.weekly.task.entity.TaskStatus
import com.pluxity.weekly.task.service.TaskService
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.every
import io.mockk.mockk
import tools.jackson.databind.json.JsonMapper

/**
 * aggregate_items 특성화 테스트 — 핸들러 분리 전 동작을 박아두는 안전망.
 * execute() 진입점 기준이라 내부를 [AggregateItemsHandler]로 빼도 그대로 통과해야 한다.
 */
class ChatV2AggregateItemsTest :
    BehaviorSpec({

        val mapper = JsonMapper()
        val taskService: TaskService = mockk()
        val epicService: EpicService = mockk()
        val projectService: ProjectService = mockk()
        val userRepository: UserRepository = mockk()
        val support = ChatV2ToolSupport(mapper)

        val aggregateItemsHandler =
            AggregateItemsHandler(
                taskService = taskService,
                epicService = epicService,
                projectService = projectService,
                userRepository = userRepository,
                support = support,
                objectMapper = mapper,
            )
        val executor =
            ChatV2ToolExecutor(
                support = support,
                searchItemsHandler = mockk<SearchItemsHandler>(),
                searchUsersHandler = mockk<SearchUsersHandler>(),
                getItemDetailsHandler = mockk<GetItemDetailsHandler>(),
                aggregateItemsHandler = aggregateItemsHandler,
                listPendingReviewsHandler = mockk<ListPendingReviewsHandler>(),
                getTaskHistoryHandler = mockk<GetTaskHistoryHandler>(),
                getWeeklyReportHandler = mockk<GetWeeklyReportHandler>(),
            )

        val base = BaseResponse("2026-01-01T00:00", "tester", "2026-01-01T00:00", "tester")

        fun task(
            id: Long,
            status: TaskStatus,
            progress: Int = 0,
        ) = TaskResponse(
            id = id, projectId = 1, projectName = "알파", epicId = 1, epicName = "기획",
            name = "작업$id", description = null, status = status, progress = progress,
            startDate = null, dueDate = null, assigneeId = null, assigneeName = null, baseResponse = base,
        )

        fun agg(
            argsJson: String,
            registry: ChatV2IdRegistry = ChatV2IdRegistry(1L),
        ) = executor.execute(ChatV2Tools.AGGREGATE_ITEMS, argsJson, 1L, registry)

        Given("task 상태별 집계") {
            When("group_by=status") {
                every { taskService.search(any()) } returns
                    listOf(task(1, TaskStatus.TODO, 0), task(2, TaskStatus.TODO, 20), task(3, TaskStatus.DONE, 100))

                val result = mapper.readValue(agg("""{"type":"task","group_by":"status"}"""), Map::class.java)

                Then("상태별 count·avg_progress와 total을 반환한다") {
                    result["type"] shouldBe "task"
                    (result["total"] as Number).toInt() shouldBe 3
                    @Suppress("UNCHECKED_CAST")
                    val groups = result["groups"] as List<Map<String, Any?>>
                    val todo = groups.first { it["group"] == "TODO" }
                    (todo["count"] as Number).toInt() shouldBe 2
                    (todo["avg_progress"] as Number).toInt() shouldBe 10
                    (groups.first { it["group"] == "DONE" }["count"] as Number).toInt() shouldBe 1
                }
            }
        }

        Given("잘못된 group_by") {
            When("project 집계에 group_by=assignee를 주면") {
                val result = agg("""{"type":"project","group_by":"assignee"}""")

                Then("project는 status만 가능하다는 error") {
                    result shouldContain "error"
                    result shouldContain "status만 가능"
                }
            }
        }

        Given("집계할 수 없는 type") {
            When("type=team이면") {
                val result = agg("""{"type":"team","group_by":"status"}""")

                Then("error를 반환한다") {
                    result shouldContain "error"
                    result shouldContain "집계할 수 없는 종류"
                }
            }
        }
    })
