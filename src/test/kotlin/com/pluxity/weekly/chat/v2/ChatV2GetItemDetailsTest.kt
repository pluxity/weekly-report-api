package com.pluxity.weekly.chat.v2

import com.pluxity.weekly.auth.user.repository.UserRepository
import com.pluxity.weekly.core.response.BaseResponse
import com.pluxity.weekly.epic.dto.EpicResponse
import com.pluxity.weekly.epic.entity.EpicStatus
import com.pluxity.weekly.epic.service.EpicService
import com.pluxity.weekly.project.dto.ProjectResponse
import com.pluxity.weekly.project.entity.ProjectStatus
import com.pluxity.weekly.project.service.ProjectService
import com.pluxity.weekly.report.service.WeeklyReportService
import com.pluxity.weekly.task.dto.TaskResponse
import com.pluxity.weekly.task.entity.TaskStatus
import com.pluxity.weekly.task.service.TaskReviewService
import com.pluxity.weekly.task.service.TaskService
import com.pluxity.weekly.team.dto.TeamResponse
import com.pluxity.weekly.team.repository.TeamRepository
import com.pluxity.weekly.team.service.TeamService
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.every
import io.mockk.mockk
import tools.jackson.databind.json.JsonMapper

/**
 * get_item_details 특성화 테스트 — 핸들러 분리(리팩토링) 전 동작을 박아두는 안전망.
 * execute() 진입점 기준이라 내부를 [GetItemDetailsHandler]로 빼도 그대로 통과해야 한다.
 */
class ChatV2GetItemDetailsTest :
    BehaviorSpec({

        val mapper = JsonMapper()
        val taskService: TaskService = mockk()
        val epicService: EpicService = mockk()
        val projectService: ProjectService = mockk()
        val teamService: TeamService = mockk()

        val support = ChatV2ToolSupport(mapper)
        val getItemDetailsHandler =
            GetItemDetailsHandler(
                taskService = taskService,
                epicService = epicService,
                projectService = projectService,
                teamService = teamService,
                support = support,
                objectMapper = mapper,
            )
        val searchItemsHandler =
            SearchItemsHandler(
                taskService = taskService,
                epicService = epicService,
                projectService = projectService,
                userRepository = mockk<UserRepository>(),
                teamService = mockk<TeamService>(),
                support = support,
                objectMapper = mapper,
            )

        val executor =
            ChatV2ToolExecutor(
                taskService = taskService,
                taskReviewService = mockk<TaskReviewService>(),
                epicService = epicService,
                projectService = projectService,
                teamRepository = mockk<TeamRepository>(),
                weeklyReportService = mockk<WeeklyReportService>(),
                userRepository = mockk<UserRepository>(),
                objectMapper = mapper,
                support = support,
                searchItemsHandler = searchItemsHandler,
                getItemDetailsHandler = getItemDetailsHandler,
            )

        val base = BaseResponse("2026-01-01T00:00", "tester", "2026-01-01T00:00", "tester")

        fun task(
            id: Long,
            name: String,
        ) = TaskResponse(
            id = id, projectId = 1, projectName = "알파", epicId = 1, epicName = "기획",
            name = name, description = "설명", status = TaskStatus.TODO, progress = 0,
            startDate = null, dueDate = null, assigneeId = null, assigneeName = null, baseResponse = base,
        )

        fun epic(
            id: Long,
            name: String,
        ) = EpicResponse(
            id = id, projectId = 1, projectName = "알파", name = name, description = "설명",
            status = EpicStatus.TODO, startDate = null, dueDate = null, members = emptyList(), baseResponse = base,
        )

        fun project(
            id: Long,
            name: String,
        ) = ProjectResponse(
            id = id, name = name, description = "설명", status = ProjectStatus.TODO, startDate = null,
            dueDate = null, pmId = null, pmName = null, members = emptyList(), progress = 42, baseResponse = base,
        )

        fun team(
            id: Long,
            name: String,
        ) = TeamResponse(
            id = id, name = name, leaderId = 1, leaderName = "나동규", members = emptyList(), baseResponse = base,
        )

        // 검색으로 id를 확인한 상태를 흉내내기 위해 레지스트리에 직접 등록해 넘긴다.
        fun registryWith(
            type: ChatV2EntityType,
            id: Long,
        ) = ChatV2IdRegistry(1L).apply { register(type, id) }

        fun details(
            argsJson: String,
            registry: ChatV2IdRegistry = ChatV2IdRegistry(1L),
        ) = executor.execute(ChatV2Tools.GET_ITEM_DETAILS, argsJson, 1L, registry)

        Given("상세 조회할 수 없는 type") {
            When("user처럼 상세 대상이 아닌 종류를 주면") {
                val result = details("""{"type":"user","id":1}""")

                Then("error를 반환한다 (task/epic/project/team만 가능)") {
                    result shouldContain "error"
                    result shouldContain "상세 조회할 수 없는 종류"
                }
            }
        }

        Given("검색으로 확인되지 않은 id") {
            When("이번 턴에 등록된 적 없는 id로 조회하면") {
                val result = details("""{"type":"task","id":10}""")

                Then("id 추측을 차단하고 error를 반환한다") {
                    result shouldContain "error"
                    result shouldContain "확인되지 않은 id"
                }
            }
        }

        Given("등록된 task id 상세 조회") {
            When("검색으로 등록된 id면") {
                every { taskService.findById(10) } returns task(10, "로그인 API")

                val result =
                    mapper.readValue(
                        details("""{"type":"task","id":10}""", registryWith(ChatV2EntityType.TASK, 10L)),
                        Map::class.java,
                    )

                Then("task 키 아래 상세를 반환한다 (검색엔 없던 description·start_date 포함)") {
                    val detail = result["task"] as Map<*, *>
                    detail["name"] shouldBe "로그인 API"
                    detail.containsKey("description") shouldBe true
                    detail.containsKey("start_date") shouldBe true
                }
            }
        }

        Given("등록된 epic id 상세 조회") {
            When("검색으로 등록된 id면") {
                every { epicService.findById(20) } returns epic(20, "로그인 개편")

                val result =
                    mapper.readValue(
                        details("""{"type":"epic","id":20}""", registryWith(ChatV2EntityType.EPIC, 20L)),
                        Map::class.java,
                    )

                Then("epic 키 아래 상세를 반환한다") {
                    val detail = result["epic"] as Map<*, *>
                    detail["name"] shouldBe "로그인 개편"
                    detail.containsKey("description") shouldBe true
                }
            }
        }

        Given("등록된 project id 상세 조회") {
            When("검색으로 등록된 id면") {
                every { projectService.findById(30) } returns project(30, "알파")

                val result =
                    mapper.readValue(
                        details("""{"type":"project","id":30}""", registryWith(ChatV2EntityType.PROJECT, 30L)),
                        Map::class.java,
                    )

                Then("project 키 아래 상세를 반환한다 (progress·members 포함)") {
                    val detail = result["project"] as Map<*, *>
                    detail["name"] shouldBe "알파"
                    detail.containsKey("progress") shouldBe true
                    detail.containsKey("members") shouldBe true
                }
            }
        }

        Given("등록된 team id 상세 조회") {
            When("검색으로 등록된 id면") {
                every { teamService.findById(40) } returns team(40, "개발팀")

                val result =
                    mapper.readValue(
                        details("""{"type":"team","id":40}""", registryWith(ChatV2EntityType.TEAM, 40L)),
                        Map::class.java,
                    )

                Then("team 키 아래 상세를 반환한다 (leader·members 포함)") {
                    val detail = result["team"] as Map<*, *>
                    detail["name"] shouldBe "개발팀"
                    detail["leader"] shouldBe "나동규"
                    detail.containsKey("members") shouldBe true
                }
            }
        }
    })
