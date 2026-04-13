package com.pluxity.weekly.dashboard.service

import com.pluxity.weekly.auth.user.repository.UserRepository
import com.pluxity.weekly.authorization.AuthorizationService
import com.pluxity.weekly.epic.entity.EpicStatus
import com.pluxity.weekly.epic.entity.dummyEpic
import com.pluxity.weekly.epic.repository.EpicRepository
import com.pluxity.weekly.project.entity.dummyProject
import com.pluxity.weekly.project.repository.ProjectRepository
import com.pluxity.weekly.task.entity.TaskApprovalAction
import com.pluxity.weekly.task.entity.TaskStatus
import com.pluxity.weekly.task.entity.dummyTask
import com.pluxity.weekly.task.repository.TaskApprovalLogRepository
import com.pluxity.weekly.task.repository.TaskRepository
import com.pluxity.weekly.task.repository.TaskReviewRequestedAt
import com.pluxity.weekly.team.repository.TeamMemberRepository
import com.pluxity.weekly.team.repository.TeamRepository
import com.pluxity.weekly.test.entity.dummyUser
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import java.time.LocalDate
import java.time.LocalDateTime

class DashboardServiceTest :
    BehaviorSpec({

        val authorizationService: AuthorizationService = mockk()
        val projectRepository: ProjectRepository = mockk()
        val epicRepository: EpicRepository = mockk()
        val taskRepository: TaskRepository = mockk()
        val userRepository: UserRepository = mockk()
        val teamRepository: TeamRepository = mockk()
        val teamMemberRepository: TeamMemberRepository = mockk()
        val taskApprovalLogRepository: TaskApprovalLogRepository = mockk(relaxed = true)
        val service =
            DashboardService(
                authorizationService,
                projectRepository,
                epicRepository,
                taskRepository,
                userRepository,
                teamRepository,
                teamMemberRepository,
                taskApprovalLogRepository,
            )

        val currentUser = dummyUser(id = 1L, name = "작업자")
        val userId = currentUser.requiredId

        beforeSpec {
            every { authorizationService.currentUser() } returns currentUser
        }

        afterEach {
            clearAllMocks()
        }

        // ── getWorkerDashboard ──

        Given("에픽과 태스크가 모두 있는 경우") {
            val project = dummyProject(id = 1L, name = "알파 프로젝트")
            val epic =
                dummyEpic(
                    id = 10L,
                    project = project,
                    name = "백엔드 구축",
                    status = EpicStatus.IN_PROGRESS,
                )
            val task1 = dummyTask(id = 100L, epic = epic, name = "DB 설계", progress = 30)
            val task2 = dummyTask(id = 101L, epic = epic, name = "API 개발", progress = 70)

            When("대시보드를 조회하면") {
                every { authorizationService.currentUser() } returns currentUser
                every { epicRepository.findByAssignmentsUserIdWithProject(userId) } returns listOf(epic)
                every { taskRepository.findByAssigneeId(userId) } returns listOf(task1, task2)

                val result = service.getWorkerDashboard()

                Then("에픽 목록이 반환된다") {
                    result.epics.size shouldBe 1
                    result.epics[0].epicId shouldBe 10L
                    result.epics[0].epicName shouldBe "백엔드 구축"
                    result.epics[0].projectId shouldBe 1L
                    result.epics[0].projectName shouldBe "알파 프로젝트"
                    result.epics[0].status shouldBe EpicStatus.IN_PROGRESS
                }

                Then("에픽에 속한 태스크가 포함된다") {
                    result.epics[0].tasks.size shouldBe 2
                }

                Then("progress는 태스크 진행률의 평균이다") {
                    result.epics[0].progress shouldBe 50
                }
            }
        }

        Given("에픽은 있지만 태스크가 없는 경우") {
            val project = dummyProject(id = 1L)
            val epic = dummyEpic(id = 10L, project = project)

            When("대시보드를 조회하면") {
                every { authorizationService.currentUser() } returns currentUser
                every { epicRepository.findByAssignmentsUserIdWithProject(userId) } returns listOf(epic)
                every { taskRepository.findByAssigneeId(userId) } returns emptyList()

                val result = service.getWorkerDashboard()

                Then("에픽의 progress는 0이다") {
                    result.epics[0].progress shouldBe 0
                }

                Then("에픽의 태스크 목록은 비어있다") {
                    result.epics[0].tasks.size shouldBe 0
                }
            }
        }

        Given("에픽이 아예 없는 경우") {
            When("대시보드를 조회하면") {
                every { authorizationService.currentUser() } returns currentUser
                every { epicRepository.findByAssignmentsUserIdWithProject(userId) } returns emptyList()
                every { taskRepository.findByAssigneeId(userId) } returns emptyList()

                val result = service.getWorkerDashboard()

                Then("에픽 목록은 비어있다") {
                    result.epics.size shouldBe 0
                }
            }
        }

        Given("여러 에픽에 태스크가 분산된 경우") {
            val project = dummyProject(id = 1L)
            val epic1 = dummyEpic(id = 10L, project = project, name = "에픽A")
            val epic2 = dummyEpic(id = 20L, project = project, name = "에픽B")

            val taskForEpic1 = dummyTask(id = 100L, epic = epic1, name = "태스크1", progress = 40)
            val taskForEpic2a = dummyTask(id = 200L, epic = epic2, name = "태스크2", progress = 20)
            val taskForEpic2b = dummyTask(id = 201L, epic = epic2, name = "태스크3", progress = 60)

            When("대시보드를 조회하면") {
                every { authorizationService.currentUser() } returns currentUser
                every { epicRepository.findByAssignmentsUserIdWithProject(userId) } returns listOf(epic1, epic2)
                every { taskRepository.findByAssigneeId(userId) } returns
                    listOf(
                        taskForEpic1,
                        taskForEpic2a,
                        taskForEpic2b,
                    )

                val result = service.getWorkerDashboard()

                Then("에픽별로 태스크가 올바르게 그룹핑된다") {
                    val resultEpic1 = result.epics.find { it.epicId == 10L }
                    val resultEpic2 = result.epics.find { it.epicId == 20L }

                    resultEpic1 shouldNotBe null
                    resultEpic2 shouldNotBe null
                    resultEpic1!!.tasks.size shouldBe 1
                    resultEpic2!!.tasks.size shouldBe 2
                }

                Then("에픽A의 progress는 태스크1의 진행률 그대로이다") {
                    val resultEpic1 = result.epics.find { it.epicId == 10L }!!
                    resultEpic1.progress shouldBe 40
                }

                Then("에픽B의 progress는 태스크2, 태스크3 진행률의 평균이다") {
                    val resultEpic2 = result.epics.find { it.epicId == 20L }!!
                    resultEpic2.progress shouldBe 40
                }
            }
        }

        // ── buildSummary — approachingDeadline ──

        Given("buildSummary — approachingDeadline 계산") {
            val project = dummyProject(id = 1L)
            val epic = dummyEpic(id = 10L, project = project)
            val today = LocalDate.now()

            When("dueDate가 7일 이내이고 미완료 태스크이면") {
                val task =
                    dummyTask(
                        id = 100L,
                        epic = epic,
                        status = TaskStatus.IN_PROGRESS,
                        dueDate = today.plusDays(5),
                    )
                every { authorizationService.currentUser() } returns currentUser
                every { epicRepository.findByAssignmentsUserIdWithProject(userId) } returns listOf(epic)
                every { taskRepository.findByAssigneeId(userId) } returns listOf(task)

                val result = service.getWorkerDashboard()

                Then("approachingDeadline 카운트에 포함된다") {
                    result.summary.approachingDeadline shouldBe 1
                }
            }

            When("dueDate가 7일 이내이지만 DONE 상태이면") {
                val task =
                    dummyTask(
                        id = 100L,
                        epic = epic,
                        status = TaskStatus.DONE,
                        dueDate = today.plusDays(5),
                    )
                every { authorizationService.currentUser() } returns currentUser
                every { epicRepository.findByAssignmentsUserIdWithProject(userId) } returns listOf(epic)
                every { taskRepository.findByAssigneeId(userId) } returns listOf(task)

                val result = service.getWorkerDashboard()

                Then("approachingDeadline 카운트에 포함되지 않는다") {
                    result.summary.approachingDeadline shouldBe 0
                }
            }

            When("dueDate가 7일을 초과하는 경우") {
                val task =
                    dummyTask(
                        id = 100L,
                        epic = epic,
                        status = TaskStatus.IN_PROGRESS,
                        dueDate = today.plusDays(8),
                    )
                every { authorizationService.currentUser() } returns currentUser
                every { epicRepository.findByAssignmentsUserIdWithProject(userId) } returns listOf(epic)
                every { taskRepository.findByAssigneeId(userId) } returns listOf(task)

                val result = service.getWorkerDashboard()

                Then("approachingDeadline 카운트에 포함되지 않는다") {
                    result.summary.approachingDeadline shouldBe 0
                }
            }

            When("dueDate가 이미 지난 경우 (음수 차이)") {
                val task =
                    dummyTask(
                        id = 100L,
                        epic = epic,
                        status = TaskStatus.IN_PROGRESS,
                        dueDate = today.minusDays(1),
                    )
                every { authorizationService.currentUser() } returns currentUser
                every { epicRepository.findByAssignmentsUserIdWithProject(userId) } returns listOf(epic)
                every { taskRepository.findByAssigneeId(userId) } returns listOf(task)

                val result = service.getWorkerDashboard()

                Then("approachingDeadline 카운트에 포함되지 않는다") {
                    result.summary.approachingDeadline shouldBe 0
                }
            }

            When("dueDate가 null인 경우") {
                val task =
                    dummyTask(
                        id = 100L,
                        epic = epic,
                        status = TaskStatus.IN_PROGRESS,
                        dueDate = null,
                    )
                every { authorizationService.currentUser() } returns currentUser
                every { epicRepository.findByAssignmentsUserIdWithProject(userId) } returns listOf(epic)
                every { taskRepository.findByAssigneeId(userId) } returns listOf(task)

                val result = service.getWorkerDashboard()

                Then("approachingDeadline 카운트에 포함되지 않는다") {
                    result.summary.approachingDeadline shouldBe 0
                }
            }

            When("dueDate가 오늘인 경우 (0일 차이)") {
                val task =
                    dummyTask(
                        id = 100L,
                        epic = epic,
                        status = TaskStatus.TODO,
                        dueDate = today,
                    )
                every { authorizationService.currentUser() } returns currentUser
                every { epicRepository.findByAssignmentsUserIdWithProject(userId) } returns listOf(epic)
                every { taskRepository.findByAssigneeId(userId) } returns listOf(task)

                val result = service.getWorkerDashboard()

                Then("approachingDeadline 카운트에 포함된다") {
                    result.summary.approachingDeadline shouldBe 1
                }
            }
        }

        // ── buildSummary — 기타 카운트 ──

        Given("buildSummary — inProgress / completed / total 카운트") {
            val project = dummyProject(id = 1L)
            val epic = dummyEpic(id = 10L, project = project)

            When("다양한 상태의 태스크가 혼재할 때 대시보드를 조회하면") {
                val tasks =
                    listOf(
                        dummyTask(id = 1L, epic = epic, status = TaskStatus.TODO),
                        dummyTask(id = 2L, epic = epic, status = TaskStatus.IN_PROGRESS),
                        dummyTask(id = 3L, epic = epic, status = TaskStatus.IN_PROGRESS),
                        dummyTask(id = 4L, epic = epic, status = TaskStatus.DONE),
                        dummyTask(id = 5L, epic = epic, status = TaskStatus.DONE),
                        dummyTask(id = 6L, epic = epic, status = TaskStatus.DONE),
                    )
                every { authorizationService.currentUser() } returns currentUser
                every { epicRepository.findByAssignmentsUserIdWithProject(userId) } returns listOf(epic)
                every { taskRepository.findByAssigneeId(userId) } returns tasks

                val result = service.getWorkerDashboard()

                Then("inProgress는 IN_PROGRESS 태스크 수이다") {
                    result.summary.inProgress shouldBe 2
                }

                Then("completed는 DONE 태스크 수이다") {
                    result.summary.completed shouldBe 3
                }

                Then("total은 전체 태스크 수이다") {
                    result.summary.total shouldBe 6
                }
            }
        }

        // ── progress 계산 ──

        Given("progress 계산") {
            val project = dummyProject(id = 1L)
            val epic = dummyEpic(id = 10L, project = project)

            When("태스크가 여러 개인 경우") {
                every { authorizationService.currentUser() } returns currentUser
                every { epicRepository.findByAssignmentsUserIdWithProject(userId) } returns listOf(epic)
                every { taskRepository.findByAssigneeId(userId) } returns
                    listOf(
                        dummyTask(id = 100L, epic = epic, progress = 30),
                        dummyTask(id = 101L, epic = epic, progress = 70),
                    )

                val result = service.getWorkerDashboard()

                Then("progress는 태스크 진행률의 평균이다") {
                    result.epics[0].progress shouldBe 50
                }
            }

            When("태스크가 1개인 경우") {
                every { authorizationService.currentUser() } returns currentUser
                every { epicRepository.findByAssignmentsUserIdWithProject(userId) } returns listOf(epic)
                every { taskRepository.findByAssigneeId(userId) } returns listOf(dummyTask(id = 100L, epic = epic, progress = 65))

                val result = service.getWorkerDashboard()

                Then("progress는 해당 태스크의 진행률 그대로이다") {
                    result.epics[0].progress shouldBe 65
                }
            }

            When("태스크가 없는 경우") {
                every { authorizationService.currentUser() } returns currentUser
                every { epicRepository.findByAssignmentsUserIdWithProject(userId) } returns listOf(epic)
                every { taskRepository.findByAssigneeId(userId) } returns emptyList()

                val result = service.getWorkerDashboard()

                Then("progress는 0이다") {
                    result.epics[0].progress shouldBe 0
                }
            }
        }

        // ── toWorkerTaskItem — daysUntilDue ──

        Given("toWorkerTaskItem — daysUntilDue 계산") {
            val project = dummyProject(id = 1L)
            val epicDueDate = LocalDate.now().plusDays(7)
            val epic = dummyEpic(id = 10L, project = project, dueDate = epicDueDate)

            When("dueDate가 있는 경우") {
                val futureDate = LocalDate.now().plusDays(3)
                val task = dummyTask(id = 100L, epic = epic, dueDate = futureDate)
                every { authorizationService.currentUser() } returns currentUser
                every { epicRepository.findByAssignmentsUserIdWithProject(userId) } returns listOf(epic)
                every { taskRepository.findByAssigneeId(userId) } returns listOf(task)

                val result = service.getWorkerDashboard()

                Then("daysUntilDue가 계산되어 반환된다") {
                    val taskItem = result.epics[0].tasks[0]
                    taskItem.daysUntilDue shouldBe 4
                }
            }

            When("dueDate가 null인 경우") {
                val task = dummyTask(id = 100L, epic = epic, dueDate = null)
                every { authorizationService.currentUser() } returns currentUser
                every { epicRepository.findByAssignmentsUserIdWithProject(userId) } returns listOf(epic)
                every { taskRepository.findByAssigneeId(userId) } returns listOf(task)

                val result = service.getWorkerDashboard()

                Then("daysUntilDue는 null이다") {
                    val taskItem = result.epics[0].tasks[0]
                    taskItem.daysUntilDue shouldBe null
                }
            }
        }

        // ── requestDate ──

        Given("REVIEW_REQUEST 승인 로그가 존재하는 경우") {
            val project = dummyProject(id = 1L)
            val epic = dummyEpic(id = 10L, project = project)
            val task = dummyTask(id = 100L, epic = epic)
            val reviewRequestedAt = LocalDateTime.of(2026, 4, 10, 14, 30, 0)

            When("대시보드를 조회하면") {
                every { authorizationService.currentUser() } returns currentUser
                every { epicRepository.findByAssignmentsUserIdWithProject(userId) } returns listOf(epic)
                every { taskRepository.findByAssigneeId(userId) } returns listOf(task)
                every {
                    taskApprovalLogRepository.findLatestCreatedAtByTaskIdsAndAction(
                        listOf(100L),
                        TaskApprovalAction.REVIEW_REQUEST,
                    )
                } returns listOf(TaskReviewRequestedAt(100L, reviewRequestedAt))

                val result = service.getWorkerDashboard()

                Then("requestDate는 REVIEW_REQUEST의 createdAt이다") {
                    result.epics[0].tasks[0].requestDate shouldBe reviewRequestedAt
                }
            }
        }

        Given("REVIEW_REQUEST 승인 로그가 없는 경우") {
            val project = dummyProject(id = 1L)
            val epic = dummyEpic(id = 10L, project = project)
            val task = dummyTask(id = 100L, epic = epic)

            When("대시보드를 조회하면") {
                every { authorizationService.currentUser() } returns currentUser
                every { epicRepository.findByAssignmentsUserIdWithProject(userId) } returns listOf(epic)
                every { taskRepository.findByAssigneeId(userId) } returns listOf(task)
                every {
                    taskApprovalLogRepository.findLatestCreatedAtByTaskIdsAndAction(
                        listOf(100L),
                        TaskApprovalAction.REVIEW_REQUEST,
                    )
                } returns emptyList()

                val result = service.getWorkerDashboard()

                Then("requestDate는 오늘 날짜이다") {
                    result.epics[0].tasks[0].requestDate.toLocalDate() shouldBe LocalDate.now()
                }
            }
        }

        // ── updatedAt ──

        Given("에픽의 updatedAt 전달 확인") {
            val epicUpdatedAt = LocalDateTime.of(2026, 4, 12, 10, 0, 0)
            val project = dummyProject(id = 1L)
            val epic =
                dummyEpic(
                    id = 10L,
                    project = project,
                    status = EpicStatus.IN_PROGRESS,
                )
            org.springframework.test.util.ReflectionTestUtils.setField(epic, "updatedAt", epicUpdatedAt)

            When("대시보드를 조회하면") {
                every { authorizationService.currentUser() } returns currentUser
                every { epicRepository.findByAssignmentsUserIdWithProject(userId) } returns listOf(epic)
                every { taskRepository.findByAssigneeId(userId) } returns emptyList()

                val result = service.getWorkerDashboard()

                Then("에픽의 updatedAt이 반환된다") {
                    result.epics[0].updatedAt shouldBe epicUpdatedAt
                }
            }
        }
    })
