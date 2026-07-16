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
import com.pluxity.weekly.team.repository.TeamRepository
import com.pluxity.weekly.team.service.TeamService
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.every
import io.mockk.mockk
import tools.jackson.databind.json.JsonMapper

/**
 * searchItems 특성화 테스트 — 핸들러 분리(리팩토링) 전에 현재 동작을 박아두는 안전망.
 * execute() 진입점 기준으로 검증하므로 내부를 핸들러로 빼도 이 테스트는 그대로 통과해야 한다.
 */
class ChatV2SearchItemsTest :
    BehaviorSpec({

        val mapper = JsonMapper()
        val taskService: TaskService = mockk()
        val epicService: EpicService = mockk()
        val projectService: ProjectService = mockk()

        val support = ChatV2ToolSupport(mapper)
        val searchItemsHandler =
            SearchItemsHandler(
                taskService = taskService,
                epicService = epicService,
                projectService = projectService,
                teamService = mockk<TeamService>(),
                userRepository = mockk<UserRepository>(),
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
                getItemDetailsHandler = mockk<GetItemDetailsHandler>(),
            )

        val base = BaseResponse("2026-01-01T00:00", "tester", "2026-01-01T00:00", "tester")

        fun task(
            id: Long,
            name: String,
            status: TaskStatus = TaskStatus.TODO,
        ) = TaskResponse(
            id = id, projectId = 1, projectName = "알파", epicId = 1, epicName = "기획",
            name = name, description = null, status = status, progress = 0,
            startDate = null, dueDate = null, assigneeId = null, assigneeName = null, baseResponse = base,
        )

        fun epic(
            id: Long,
            name: String,
        ) = EpicResponse(
            id = id, projectId = 1, projectName = "알파", name = name, description = null,
            status = EpicStatus.TODO, startDate = null, dueDate = null, members = emptyList(), baseResponse = base,
        )

        fun project(
            id: Long,
            name: String,
        ) = ProjectResponse(
            id = id, name = name, description = null, status = ProjectStatus.TODO, startDate = null,
            dueDate = null, pmId = null, pmName = null, members = emptyList(), progress = 0, baseResponse = base,
        )

        // 기본: 세 계층 모두 빈 결과. 각 테스트에서 필요한 것만 override.
        every { taskService.search(any()) } returns emptyList()
        every { epicService.search(any()) } returns emptyList()
        every { projectService.search(any()) } returns emptyList()

        fun search(
            argsJson: String,
            registry: ChatV2IdRegistry = ChatV2IdRegistry(1L),
        ) = executor.execute(ChatV2Tools.SEARCH_ITEMS, argsJson, 1L, registry)

        Given("type 없이 이름만 검색") {
            When("query가 이름에 매칭되면") {
                every { taskService.search(any()) } returns listOf(task(10, "CCTV 목록 API"), task(11, "결제 모듈"))
                every { epicService.search(any()) } returns listOf(epic(20, "CCTV 개편"))
                every { projectService.search(any()) } returns listOf(project(30, "알파"))

                val result = mapper.readValue("""{"query":"cctv"}""".let { search(it) }, Map::class.java)

                Then("이름 매칭된 것만 계층별로 반환된다 (결제 모듈·알파 프로젝트 제외)") {
                    (result["tasks"] as List<*>).size shouldBe 1
                    (result["epics"] as List<*>).size shouldBe 1
                    (result["projects"] as List<*>).size shouldBe 0
                    @Suppress("UNCHECKED_CAST")
                    ((result["tasks"] as List<Map<String, Any?>>).first()["name"]) shouldBe "CCTV 목록 API"
                }
            }
        }

        Given("type 처리") {
            When("type=task로 명시하면") {
                every { taskService.search(any()) } returns listOf(task(10, "로그인 API"))
                every { epicService.search(any()) } returns listOf(epic(20, "로그인 개편"))
                every { projectService.search(any()) } returns listOf(project(30, "로그인 프로젝트"))

                val result = mapper.readValue(search("""{"type":"task","query":"로그인"}"""), Map::class.java)

                Then("task만 검색되고 epic·project는 조회 자체를 건너뛴다") {
                    (result["tasks"] as List<*>).size shouldBe 1
                    (result["epics"] as List<*>).size shouldBe 0
                    (result["projects"] as List<*>).size shouldBe 0
                }
            }

            When("type=TASK로 대문자로 줘도") {
                every { taskService.search(any()) } returns listOf(task(10, "로그인 API"))

                val result = mapper.readValue(search("""{"type":"TASK","query":"로그인"}"""), Map::class.java)

                Then("대소문자 무시하고 task로 인식된다") {
                    (result["tasks"] as List<*>).size shouldBe 1
                }
            }

            When("존재하지 않는 type(foo)을 주면") {
                val result = search("""{"type":"foo","query":"로그인"}""")

                Then("잘못된 종류이므로 error를 반환한다 (get_item_details와 일관, 조용한 빈 결과 방지)") {
                    result shouldContain "error"
                    result shouldContain "알 수 없는 종류"
                }
            }
        }

        Given("status=IN_REVIEW 검색") {
            When("리뷰 대기 상태로 검색하면") {
                every { taskService.search(any()) } returns listOf(task(10, "로그인 API", TaskStatus.IN_REVIEW))

                val result = mapper.readValue(search("""{"status":"IN_REVIEW"}"""), Map::class.java)

                Then("task만 반환되고 epic·project는 빈 결과다 (IN_REVIEW는 태스크 전용 상태)") {
                    (result["tasks"] as List<*>).size shouldBe 1
                    (result["epics"] as List<*>).size shouldBe 0
                    (result["projects"] as List<*>).size shouldBe 0
                }
            }
        }

        Given("이름 필터 — 부모를 이름으로 지정") {
            When("존재하지 않는 프로젝트 이름으로 필터하면") {
                every { projectService.search(any()) } returns emptyList()

                val result = search("""{"query":"cctv","project":"없는프로젝트"}""")

                Then("서버가 해소 실패를 안내한다 (id를 지어낼 여지 없음)") {
                    result shouldContain "error"
                    result shouldContain "찾을 수 없습니다"
                }
            }

            When("유일하게 매칭되는 프로젝트 이름으로 필터하면") {
                every { projectService.search(any()) } returns listOf(project(30, "알파"))
                every { taskService.search(any()) } returns listOf(task(10, "CCTV 목록 API"))

                val result = mapper.readValue(search("""{"type":"task","project":"알파"}"""), Map::class.java)

                Then("서버가 id로 해소해 그 프로젝트 하위를 조회한다") {
                    (result["tasks"] as List<*>).size shouldBe 1
                }
            }
        }

        Given("아무 조건 없는 검색") {
            When("query·type·필터가 전부 없으면") {
                val result = search("""{}""")

                Then("전체 조회 방지를 위해 error가 반환된다") {
                    result shouldContain "error"
                }
            }
        }

        Given("limit을 넘는 결과") {
            When("매칭 결과가 limit보다 많으면") {
                every { taskService.search(any()) } returns (1..15).map { task(it.toLong(), "작업$it") }

                val result = mapper.readValue(search("""{"query":"작업","limit":10}"""), Map::class.java)

                Then("tasks는 limit개로 잘리고 totals는 전체 개수·truncated=true") {
                    (result["tasks"] as List<*>).size shouldBe 10
                    ((result["totals"] as Map<*, *>)["tasks"] as Number).toInt() shouldBe 15
                    result["truncated"] shouldBe true
                }
            }
        }
    })
