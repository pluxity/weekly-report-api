package com.pluxity.weekly.chat.service

import com.pluxity.weekly.auth.user.repository.UserRepository
import com.pluxity.weekly.chat.dto.LlmAction
import com.pluxity.weekly.chat.dto.SelectField
import com.pluxity.weekly.epic.dto.EpicResponse
import com.pluxity.weekly.epic.repository.EpicRepository
import com.pluxity.weekly.epic.service.EpicAssignmentService
import com.pluxity.weekly.epic.service.EpicService
import com.pluxity.weekly.project.dto.ProjectResponse
import com.pluxity.weekly.project.repository.ProjectRepository
import com.pluxity.weekly.project.service.ProjectService
import com.pluxity.weekly.task.repository.TaskRepository
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.mockk.every
import io.mockk.mockk
import org.springframework.data.domain.Sort

class SelectFieldResolverStatusTest :
    BehaviorSpec({

        val projectRepository: ProjectRepository = mockk()
        val epicRepository: EpicRepository = mockk()
        val taskRepository: TaskRepository = mockk()
        val userRepository: UserRepository = mockk()
        val projectService: ProjectService = mockk()
        val epicService: EpicService = mockk()
        val epicAssignmentService: EpicAssignmentService = mockk()

        val resolver =
            SelectFieldResolver(
                projectRepository,
                epicRepository,
                taskRepository,
                userRepository,
                projectService,
                epicService,
                epicAssignmentService,
            )

        // 기본 mock: status 후보 검증과 무관한 의존성은 비어있거나 더미를 반환
        every { userRepository.findAllBy(any<Sort>()) } returns emptyList()
        every { projectService.findAll() } returns
            listOf(
                mockk<ProjectResponse> {
                    every { id } returns 1L
                    every { name } returns "더미 프로젝트"
                },
            )
        every { epicService.findAll() } returns
            listOf(
                mockk<EpicResponse> {
                    every { id } returns 10L
                    every { name } returns "더미 에픽"
                    every { members } returns emptyList()
                },
            )

        fun statusField(action: LlmAction): SelectField? =
            resolver.resolve(action).firstOrNull { it.field == "status" }

        fun statusNames(action: LlmAction): List<String> =
            statusField(action)?.candidates?.map { it.id } ?: emptyList()

        Given("PROJECT 대상") {
            When("CREATE 액션") {
                val action = LlmAction(action = "create", target = "project")
                Then("status 후보는 TODO / IN_PROGRESS 만 — DONE 으로는 신규 생성 불가") {
                    statusField(action).shouldNotBeNull()
                    statusNames(action) shouldContainExactlyInAnyOrder listOf("TODO", "IN_PROGRESS")
                    statusNames(action) shouldNotContain "DONE"
                }
            }
            When("UPDATE 액션") {
                val action = LlmAction(action = "update", target = "project", id = 1L)
                Then("status 후보는 TODO / IN_PROGRESS / DONE 모두 포함") {
                    statusNames(action) shouldContainExactlyInAnyOrder listOf("TODO", "IN_PROGRESS", "DONE")
                }
            }
        }

        Given("EPIC 대상") {
            When("CREATE 액션") {
                val action = LlmAction(action = "create", target = "epic")
                Then("status 후보는 TODO / IN_PROGRESS 만") {
                    statusNames(action) shouldContainExactlyInAnyOrder listOf("TODO", "IN_PROGRESS")
                    statusNames(action) shouldNotContain "DONE"
                }
            }
            When("UPDATE 액션") {
                val action = LlmAction(action = "update", target = "epic", id = 1L)
                Then("status 후보는 TODO / IN_PROGRESS / DONE 모두 포함") {
                    statusNames(action) shouldContainExactlyInAnyOrder listOf("TODO", "IN_PROGRESS", "DONE")
                }
            }
        }

        Given("TASK 대상") {
            When("CREATE 액션") {
                val action = LlmAction(action = "create", target = "task")
                Then("status 후보는 TODO / IN_PROGRESS 만") {
                    statusNames(action) shouldContainExactlyInAnyOrder listOf("TODO", "IN_PROGRESS")
                }
            }
            When("UPDATE 액션") {
                val action = LlmAction(action = "update", target = "task", id = 1L)
                Then("status 후보는 TODO / IN_PROGRESS 만 (DONE / IN_REVIEW 는 리뷰 흐름 전용)") {
                    statusNames(action) shouldContainExactlyInAnyOrder listOf("TODO", "IN_PROGRESS")
                    statusNames(action) shouldNotContain "DONE"
                    statusNames(action) shouldNotContain "IN_REVIEW"
                }
            }
        }

        Given("CREATE / UPDATE 외 액션") {
            When("READ 액션") {
                val action = LlmAction(action = "read", target = "project")
                Then("status 후보는 생성되지 않는다") {
                    statusField(action).shouldBeNull()
                }
            }
            When("DELETE 액션") {
                val action = LlmAction(action = "delete", target = "project", id = 1L)
                Then("status 후보는 생성되지 않는다") {
                    statusField(action).shouldBeNull()
                }
            }
        }
    })

