package com.pluxity.weekly.task.service

import com.pluxity.weekly.auth.authorization.AuthorizationService
import com.pluxity.weekly.auth.user.entity.RoleType
import com.pluxity.weekly.auth.user.repository.UserRepository
import com.pluxity.weekly.core.constant.ErrorCode
import com.pluxity.weekly.core.exception.CustomException
import com.pluxity.weekly.epic.entity.dummyEpic
import com.pluxity.weekly.epic.repository.EpicRepository
import com.pluxity.weekly.project.entity.dummyProject
import com.pluxity.weekly.task.dto.dummyTaskRequest
import com.pluxity.weekly.task.dto.dummyTaskUpdateRequest
import com.pluxity.weekly.task.entity.Task
import com.pluxity.weekly.task.entity.TaskApprovalAction
import com.pluxity.weekly.task.entity.TaskApprovalLog
import com.pluxity.weekly.task.entity.TaskStatus
import com.pluxity.weekly.task.entity.dummyTask
import com.pluxity.weekly.task.repository.TaskApprovalLogRepository
import com.pluxity.weekly.task.repository.TaskRepository
import com.pluxity.weekly.task.repository.TaskReviewRequestedAt
import com.pluxity.weekly.teams.converter.TaskReviewCardBuilder
import com.pluxity.weekly.teams.event.TeamsNotificationEvent
import com.pluxity.weekly.test.entity.dummyRole
import com.pluxity.weekly.test.entity.dummyUser
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.repository.findByIdOrNull
import java.time.LocalDate
import java.time.LocalDateTime

class TaskServiceTest :
    BehaviorSpec({

        val taskRepository: TaskRepository = mockk()
        val taskApprovalLogRepository: TaskApprovalLogRepository = mockk(relaxed = true)
        val epicRepository: EpicRepository = mockk()
        val userRepository: UserRepository = mockk()
        val authorizationService: AuthorizationService = mockk()
        val eventPublisher: ApplicationEventPublisher = mockk(relaxed = true)
        val taskReviewCardBuilder: TaskReviewCardBuilder = mockk(relaxed = true)
        val service =
            TaskService(
                taskRepository,
                taskApprovalLogRepository,
                epicRepository,
                userRepository,
                authorizationService,
                eventPublisher,
                taskReviewCardBuilder,
            )

        val adminUser =
            dummyUser(id = 1L, name = "관리자").apply {
                addRole(dummyRole(id = 1L, name = "ADMIN").apply { auth = RoleType.ADMIN.name })
            }

        beforeSpec {
            every { authorizationService.currentUser() } returns adminUser
            every { authorizationService.requireEpicAccess(any(), any()) } just runs
            every { authorizationService.requireTaskOwner(any(), any()) } just runs
            every { authorizationService.requireTaskReviewer(any(), any()) } just runs
            every { authorizationService.visibleEpicIds(any()) } returns null
            every { authorizationService.restrictedAssigneeId(any()) } returns null
            every { authorizationService.pmScopedProjectIds(any()) } returns null
            every { taskApprovalLogRepository.save(any<TaskApprovalLog>()) } answers { firstArg() }
        }

        Given("태스크 전체 조회") {
            When("태스크 목록을 조회하면") {
                val epic = dummyEpic(id = 1L)
                val entities =
                    listOf(
                        dummyTask(id = 1L, epic = epic, name = "태스크A"),
                        dummyTask(id = 2L, epic = epic, name = "태스크B"),
                        dummyTask(id = 3L, epic = epic, name = "태스크C"),
                    )

                every { taskRepository.findByFilter(any()) } returns entities

                val result = service.findAll()

                Then("전체 목록이 반환된다") {
                    result.size shouldBe 3
                    result[0].name shouldBe "태스크A"
                }
            }
        }

        Given("태스크 단건 조회") {
            When("존재하는 태스크를 조회하면") {
                val epic = dummyEpic(id = 1L)
                val entity =
                    dummyTask(
                        id = 1L,
                        epic = epic,
                        name = "테스트 태스크",
                        description = "설명",
                        status = TaskStatus.IN_PROGRESS,
                        progress = 50,
                        startDate = LocalDate.of(2026, 1, 1),
                        dueDate = LocalDate.of(2026, 3, 31),
                    )

                every { taskRepository.findWithEpicAndProjectById(1L) } returns entity

                val result = service.findById(1L)

                Then("태스크 정보가 반환된다") {
                    result.id shouldBe 1L
                    result.epicId shouldBe 1L
                    result.name shouldBe "테스트 태스크"
                    result.description shouldBe "설명"
                    result.status shouldBe TaskStatus.IN_PROGRESS
                    result.progress shouldBe 50
                    result.startDate shouldBe LocalDate.of(2026, 1, 1)
                    result.dueDate shouldBe LocalDate.of(2026, 3, 31)
                }
            }

            When("존재하지 않는 태스크를 조회하면") {
                every { taskRepository.findWithEpicAndProjectById(999L) } returns null

                val exception =
                    shouldThrow<CustomException> {
                        service.findById(999L)
                    }

                Then("NOT_FOUND 예외가 발생한다") {
                    exception.code shouldBe ErrorCode.NOT_FOUND_TASK
                }
            }
        }

        Given("태스크 생성") {
            When("유효한 요청으로 태스크를 생성하면") {
                val epic = dummyEpic(id = 1L)
                val request =
                    dummyTaskRequest(
                        epicId = 1L,
                        name = "신규 태스크",
                        status = TaskStatus.TODO,
                        progress = 0,
                    )
                val saved = dummyTask(id = 1L, epic = epic, name = "신규 태스크")

                every { epicRepository.findByIdOrNull(1L) } returns epic
                every { taskRepository.existsByEpicIdAndName(1L, request.name) } returns false
                every { taskRepository.save(any<Task>()) } returns saved

                val result = service.create(request)

                Then("생성된 태스크의 ID가 반환된다") {
                    result shouldBe 1L
                }
            }
        }

        Given("태스크 생성 시 assignee epic 배정 검증") {
            When("ADMIN/PM이 다른 사용자를 assignee로 지정하면 epic에 자동 배정된다") {
                val epic = dummyEpic(id = 5L)
                val newAssignee = dummyUser(id = 30L, name = "신규 담당자")
                val request = dummyTaskRequest(epicId = 5L, name = "자동배정 create", assigneeId = 30L)
                val saved =
                    dummyTask(id = 100L, epic = epic, name = "자동배정 create").apply {
                        this.assignee = newAssignee
                    }

                every { epicRepository.findByIdOrNull(5L) } returns epic
                every { taskRepository.existsByEpicIdAndName(5L, request.name) } returns false
                every { authorizationService.requireAdminOrPm(any()) } just runs
                every { epicRepository.existsByAssignmentsUserIdAndId(30L, 5L) } returns false
                every { userRepository.findByIdOrNull(30L) } returns newAssignee
                every { taskRepository.save(any<Task>()) } returns saved

                service.create(request)

                Then("epic에 자동 배정되고 알림이 발행된다") {
                    epic.assignments.any { it.user == newAssignee } shouldBe true
                    verify { eventPublisher.publishEvent(match<TeamsNotificationEvent> { it.userId == 30L }) }
                }
            }

            When("일반 사용자가 다른 사용자를 assignee로 지정하면 권한 거부") {
                val epic = dummyEpic(id = 9L)
                val request = dummyTaskRequest(epicId = 9L, name = "권한없음 create", assigneeId = 50L)

                every { epicRepository.findByIdOrNull(9L) } returns epic
                every { taskRepository.existsByEpicIdAndName(9L, request.name) } returns false
                every { authorizationService.requireAdminOrPm(any()) } throws CustomException(ErrorCode.PERMISSION_DENIED)

                val exception =
                    shouldThrow<CustomException> {
                        service.create(request)
                    }

                Then("PERMISSION_DENIED 예외가 발생한다") {
                    exception.code shouldBe ErrorCode.PERMISSION_DENIED
                }
            }

            When("assigneeId가 현재 로그인 사용자와 같으면 권한 체크/자동배정을 건너뛴다") {
                val epic = dummyEpic(id = 6L)
                val request = dummyTaskRequest(epicId = 6L, name = "본인 지정", assigneeId = 1L)
                val saved =
                    dummyTask(id = 101L, epic = epic, name = "본인 지정").apply {
                        this.assignee = adminUser
                    }

                every { epicRepository.findByIdOrNull(6L) } returns epic
                every { taskRepository.existsByEpicIdAndName(6L, request.name) } returns false
                every { userRepository.findByIdOrNull(1L) } returns adminUser
                every { taskRepository.save(any<Task>()) } returns saved

                service.create(request)

                Then("requireAdminOrPm 호출되지 않고 epic에 자동 배정도 되지 않는다") {
                    verify(exactly = 0) { authorizationService.requireAdminOrPm(any()) }
                    verify(exactly = 0) { epicRepository.existsByAssignmentsUserIdAndId(1L, 6L) }
                }
            }
        }

        Given("태스크 생성 시 날짜 검증") {
            When("startDate가 dueDate보다 늦게 생성하면") {
                val epic = dummyEpic(id = 7L)
                val request =
                    dummyTaskRequest(
                        epicId = 7L,
                        name = "날짜 역전",
                        startDate = LocalDate.of(2026, 6, 1),
                        dueDate = LocalDate.of(2026, 5, 1),
                    )

                every { epicRepository.findByIdOrNull(7L) } returns epic
                every { taskRepository.existsByEpicIdAndName(7L, request.name) } returns false

                val exception =
                    shouldThrow<CustomException> {
                        service.create(request)
                    }

                Then("INVALID_DATE_RANGE 예외가 발생한다") {
                    exception.code shouldBe ErrorCode.INVALID_DATE_RANGE
                }
            }
        }

        Given("태스크 수정 시 날짜 검증") {
            When("기존 dueDate보다 늦은 startDate로 수정하면") {
                val epic = dummyEpic(id = 8L)
                val entity =
                    dummyTask(
                        id = 80L,
                        epic = epic,
                        startDate = LocalDate.of(2026, 1, 1),
                        dueDate = LocalDate.of(2026, 3, 1),
                    )

                every { taskRepository.findWithEpicAndProjectById(80L) } returns entity

                val exception =
                    shouldThrow<CustomException> {
                        service.update(
                            80L,
                            dummyTaskUpdateRequest(startDate = LocalDate.of(2026, 4, 1)),
                        )
                    }

                Then("INVALID_DATE_RANGE 예외가 발생한다") {
                    exception.code shouldBe ErrorCode.INVALID_DATE_RANGE
                }
            }
        }

        Given("DONE 에픽에 태스크 생성 차단") {
            When("DONE 상태 에픽에 태스크를 생성하려 하면") {
                val doneEpic = dummyEpic(id = 99L, status = com.pluxity.weekly.epic.entity.EpicStatus.DONE)
                val request = dummyTaskRequest(epicId = 99L, name = "신규")

                every { epicRepository.findByIdOrNull(99L) } returns doneEpic

                val exception =
                    shouldThrow<CustomException> {
                        service.create(request)
                    }

                Then("INVALID_STATUS_TRANSITION 예외가 발생한다") {
                    exception.code shouldBe ErrorCode.INVALID_STATUS_TRANSITION
                }
            }
        }

        Given("태스크 수정") {
            When("존재하는 태스크를 수정하면") {
                val epic = dummyEpic(id = 1L)
                val entity = dummyTask(id = 1L, epic = epic, name = "기존 태스크")
                val request =
                    dummyTaskUpdateRequest(
                        name = "수정된 태스크",
                        status = TaskStatus.IN_PROGRESS,
                        progress = 30,
                    )

                every { taskRepository.findWithEpicAndProjectById(1L) } returns entity

                service.update(1L, request)

                Then("태스크 정보가 수정된다") {
                    entity.name shouldBe "수정된 태스크"
                    entity.status shouldBe TaskStatus.IN_PROGRESS
                    entity.progress shouldBe 30
                }
            }

            When("존재하지 않는 태스크를 수정하면") {
                every { taskRepository.findWithEpicAndProjectById(999L) } returns null

                val exception =
                    shouldThrow<CustomException> {
                        service.update(999L, dummyTaskUpdateRequest())
                    }

                Then("NOT_FOUND 예외가 발생한다") {
                    exception.code shouldBe ErrorCode.NOT_FOUND_TASK
                }
            }
        }

        Given("태스크 삭제") {
            When("존재하는 태스크를 삭제하면") {
                val entity = dummyTask(id = 1L, name = "삭제대상 태스크")

                every { taskRepository.findWithEpicAndProjectById(1L) } returns entity
                every { taskRepository.delete(any<Task>()) } just runs

                service.delete(1L)

                Then("삭제가 수행된다") {
                    verify(exactly = 1) { taskRepository.delete(entity) }
                }
            }

            When("존재하지 않는 태스크를 삭제하면") {
                every { taskRepository.findWithEpicAndProjectById(999L) } returns null

                val exception =
                    shouldThrow<CustomException> {
                        service.delete(999L)
                    }

                Then("NOT_FOUND 예외가 발생한다") {
                    exception.code shouldBe ErrorCode.NOT_FOUND_TASK
                }
            }
        }

        Given("태스크 검수 요청") {
            When("IN_PROGRESS 태스크를 검수 요청하면") {
                val pmId = 99L
                val project = dummyProject(id = 1L, pmId = pmId)
                val epic = dummyEpic(id = 1L, project = project)
                val entity =
                    dummyTask(
                        id = 10L,
                        epic = epic,
                        name = "리뷰 태스크",
                        status = TaskStatus.IN_PROGRESS,
                    )

                every { taskRepository.findWithEpicAndProjectById(10L) } returns entity
                val eventSlot = slot<Any>()
                every { eventPublisher.publishEvent(capture(eventSlot)) } just runs

                service.requestReview(10L)

                Then("태스크 상태가 IN_REVIEW 로 변경되고 PM 에게 알림이 발행된다") {
                    entity.status shouldBe TaskStatus.IN_REVIEW
                    verify(exactly = 1) { taskApprovalLogRepository.save(any<TaskApprovalLog>()) }
                    (eventSlot.captured as TeamsNotificationEvent).userId shouldBe pmId
                }
            }

            When("이미 IN_REVIEW 인 태스크를 다시 검수 요청하면") {
                val project = dummyProject(id = 1L, pmId = 99L)
                val epic = dummyEpic(id = 1L, project = project)
                val entity =
                    dummyTask(
                        id = 11L,
                        epic = epic,
                        name = "이미 리뷰중",
                        status = TaskStatus.IN_REVIEW,
                    )

                every { taskRepository.findWithEpicAndProjectById(11L) } returns entity

                val exception =
                    shouldThrow<CustomException> {
                        service.requestReview(11L)
                    }

                Then("INVALID_TASK_STATUS_TRANSITION 예외가 발생한다") {
                    exception.code shouldBe ErrorCode.INVALID_STATUS_TRANSITION
                }
            }
        }

        Given("태스크 승인") {
            When("IN_REVIEW 태스크를 승인하면") {
                val project = dummyProject(id = 1L, pmId = 99L)
                val epic = dummyEpic(id = 1L, project = project)
                val assignee = dummyUser(id = 50L, name = "담당자")
                val entity =
                    dummyTask(
                        id = 20L,
                        epic = epic,
                        name = "승인 대상",
                        status = TaskStatus.IN_REVIEW,
                    ).apply { this.assignee = assignee }

                every { taskRepository.findWithEpicAndProjectById(20L) } returns entity
                val eventSlot = slot<Any>()
                every { eventPublisher.publishEvent(capture(eventSlot)) } just runs

                service.approve(20L)

                Then("태스크 상태가 DONE 으로 변경되고 담당자에게 알림이 발행된다") {
                    entity.status shouldBe TaskStatus.DONE
                    verify(exactly = 1) { taskApprovalLogRepository.save(any<TaskApprovalLog>()) }
                    (eventSlot.captured as TeamsNotificationEvent).userId shouldBe 50L
                }
            }

            When("IN_PROGRESS 태스크를 승인하면") {
                val entity = dummyTask(id = 21L, status = TaskStatus.IN_PROGRESS)
                every { taskRepository.findWithEpicAndProjectById(21L) } returns entity

                val exception =
                    shouldThrow<CustomException> {
                        service.approve(21L)
                    }

                Then("INVALID_TASK_STATUS_TRANSITION 예외가 발생한다") {
                    exception.code shouldBe ErrorCode.INVALID_STATUS_TRANSITION
                }
            }
        }

        Given("태스크 반려") {
            When("IN_REVIEW 태스크를 사유와 함께 반려하면") {
                val project = dummyProject(id = 1L, pmId = 99L)
                val epic = dummyEpic(id = 1L, project = project)
                val entity =
                    dummyTask(
                        id = 30L,
                        epic = epic,
                        name = "반려 대상",
                        status = TaskStatus.IN_REVIEW,
                    )

                every { taskRepository.findWithEpicAndProjectById(30L) } returns entity
                every { eventPublisher.publishEvent(any<TeamsNotificationEvent>()) } just runs
                val logSlot = slot<TaskApprovalLog>()
                every { taskApprovalLogRepository.save(capture(logSlot)) } answers { logSlot.captured }

                service.reject(30L, "요구사항 불충족")

                Then("태스크 상태가 IN_PROGRESS 로 복귀되고 반려 사유가 로그에 기록된다") {
                    entity.status shouldBe TaskStatus.IN_PROGRESS
                    logSlot.captured.action shouldBe TaskApprovalAction.REJECT
                    logSlot.captured.reason shouldBe "요구사항 불충족"
                }
            }

            When("사유 없이(null) 반려하면") {
                val project = dummyProject(id = 1L, pmId = 99L)
                val epic = dummyEpic(id = 1L, project = project)
                val entity =
                    dummyTask(
                        id = 31L,
                        epic = epic,
                        name = "사유 없는 반려",
                        status = TaskStatus.IN_REVIEW,
                    )

                every { taskRepository.findWithEpicAndProjectById(31L) } returns entity
                every { eventPublisher.publishEvent(any<TeamsNotificationEvent>()) } just runs
                val logSlot = slot<TaskApprovalLog>()
                every { taskApprovalLogRepository.save(capture(logSlot)) } answers { logSlot.captured }

                service.reject(31L, null)

                Then("태스크 상태가 IN_PROGRESS 로 복귀되고 reason 은 null 로 기록된다") {
                    entity.status shouldBe TaskStatus.IN_PROGRESS
                    logSlot.captured.action shouldBe TaskApprovalAction.REJECT
                    logSlot.captured.reason shouldBe null
                }
            }
        }

        Given("검수 대기 큐 조회") {
            When("ADMIN 이 조회하면") {
                val project = dummyProject(id = 1L, pmId = 99L)
                val epic = dummyEpic(id = 1L, project = project)
                val t1 = dummyTask(id = 100L, epic = epic, name = "먼저 요청", status = TaskStatus.IN_REVIEW)
                val t2 = dummyTask(id = 101L, epic = epic, name = "나중 요청", status = TaskStatus.IN_REVIEW)

                every { authorizationService.pmScopedProjectIds(any()) } returns null
                every { taskRepository.findByStatus(TaskStatus.IN_REVIEW) } returns listOf(t2, t1)
                every {
                    taskApprovalLogRepository.findLatestCreatedAtByTaskIdsAndAction(
                        any(),
                        TaskApprovalAction.REVIEW_REQUEST,
                    )
                } returns
                    listOf(
                        TaskReviewRequestedAt(100L, LocalDateTime.of(2026, 4, 1, 9, 0)),
                        TaskReviewRequestedAt(101L, LocalDateTime.of(2026, 4, 8, 9, 0)),
                    )

                val result = service.findPendingReviews()

                Then("오래 기다린 순으로 정렬되고 actions URL 이 포함된다") {
                    result.size shouldBe 2
                    result[0].taskId shouldBe 100L
                    result[1].taskId shouldBe 101L
                    result[0].actions.approve.url shouldBe "/tasks/100/approve"
                    result[0].actions.reject.url shouldBe "/tasks/100/reject"
                    result[0].actions.approve.method shouldBe "POST"
                    result[0].reviewRequestedAt shouldBe LocalDateTime.of(2026, 4, 1, 9, 0)
                }
            }

            When("PM 이 본인 프로젝트 범위만 조회하면") {
                val project = dummyProject(id = 5L, pmId = 99L)
                val epic = dummyEpic(id = 50L, project = project)
                val t = dummyTask(id = 200L, epic = epic, status = TaskStatus.IN_REVIEW)

                every { authorizationService.pmScopedProjectIds(any()) } returns listOf(5L)
                every {
                    taskRepository.findByStatusAndEpicProjectIdIn(TaskStatus.IN_REVIEW, listOf(5L))
                } returns listOf(t)
                every {
                    taskApprovalLogRepository.findLatestCreatedAtByTaskIdsAndAction(
                        listOf(200L),
                        TaskApprovalAction.REVIEW_REQUEST,
                    )
                } returns listOf(TaskReviewRequestedAt(200L, LocalDateTime.of(2026, 4, 5, 12, 0)))

                val result = service.findPendingReviews()

                Then("PM 범위 프로젝트의 태스크만 반환된다") {
                    result.size shouldBe 1
                    result[0].taskId shouldBe 200L
                    result[0].projectId shouldBe 5L
                }
            }

            When("PM 이 담당 프로젝트가 없으면") {
                every { authorizationService.pmScopedProjectIds(any()) } returns emptyList()

                val result = service.findPendingReviews()

                Then("빈 목록이 반환된다") {
                    result shouldBe emptyList()
                }
            }
        }

        Given("태스크 담당자 변경 권한") {
            When("ADMIN이 담당자를 변경하면") {
                val epic = dummyEpic(id = 1L)
                val oldAssignee = dummyUser(id = 10L, name = "기존 담당자")
                val newAssignee = dummyUser(id = 20L, name = "새 담당자")
                val entity =
                    dummyTask(id = 70L, epic = epic, name = "담당자 변경 태스크").apply {
                        this.assignee = oldAssignee
                    }

                every { taskRepository.findWithEpicAndProjectById(70L) } returns entity
                every { authorizationService.requireAdminOrPm(any()) } just runs
                every { epicRepository.existsByAssignmentsUserIdAndId(20L, 1L) } returns true
                every { userRepository.findByIdOrNull(20L) } returns newAssignee

                service.update(70L, dummyTaskUpdateRequest(assigneeId = 20L))

                Then("담당자가 변경된다") {
                    entity.assignee shouldBe newAssignee
                }
            }

            When("일반 사용자가 담당자를 변경하려 하면") {
                val epic = dummyEpic(id = 1L)
                val entity =
                    dummyTask(id = 71L, epic = epic).apply {
                        this.assignee = adminUser
                    }

                every { taskRepository.findWithEpicAndProjectById(71L) } returns entity
                every { authorizationService.requireAdminOrPm(any()) } throws CustomException(ErrorCode.PERMISSION_DENIED)

                val exception =
                    shouldThrow<CustomException> {
                        service.update(71L, dummyTaskUpdateRequest(assigneeId = 999L))
                    }

                Then("PERMISSION_DENIED 예외가 발생한다") {
                    exception.code shouldBe ErrorCode.PERMISSION_DENIED
                }
            }

            When("새 담당자가 에픽에 미배정이면 자동 배정된다") {
                val epic = dummyEpic(id = 1L)
                val oldAssignee = dummyUser(id = 10L, name = "기존 담당자")
                val newAssignee = dummyUser(id = 30L, name = "미배정 담당자")
                val entity =
                    dummyTask(id = 72L, epic = epic, name = "자동배정 태스크").apply {
                        this.assignee = oldAssignee
                    }

                every { taskRepository.findWithEpicAndProjectById(72L) } returns entity
                every { authorizationService.requireAdminOrPm(any()) } just runs
                every { epicRepository.existsByAssignmentsUserIdAndId(30L, 1L) } returns false
                every { userRepository.findByIdOrNull(30L) } returns newAssignee

                service.update(72L, dummyTaskUpdateRequest(assigneeId = 30L))

                Then("에픽에 자동 배정되고 담당자가 변경된다") {
                    epic.assignments.any { it.user == newAssignee } shouldBe true
                    entity.assignee shouldBe newAssignee
                    verify { eventPublisher.publishEvent(match<TeamsNotificationEvent> { it.userId == 30L }) }
                }
            }

            When("동일한 담당자 ID를 보내면 권한 체크를 건너뛴다") {
                val epic = dummyEpic(id = 1L)
                val currentAssignee = dummyUser(id = 10L, name = "현재 담당자")
                val entity =
                    dummyTask(id = 73L, epic = epic, name = "동일 담당자").apply {
                        this.assignee = currentAssignee
                    }

                every { taskRepository.findWithEpicAndProjectById(73L) } returns entity

                service.update(73L, dummyTaskUpdateRequest(assigneeId = 10L))

                Then("requireAdminOrPm이 호출되지 않는다") {
                    verify(exactly = 0) { authorizationService.requireAdminOrPm(any()) }
                    entity.assignee shouldBe currentAssignee
                }
            }
        }

        Given("일반 수정으로 IN_REVIEW / DONE 상태 전이 차단") {
            When("update 로 status 를 IN_REVIEW 로 변경하려 하면") {
                val entity = dummyTask(id = 40L, status = TaskStatus.IN_PROGRESS)
                every { taskRepository.findWithEpicAndProjectById(40L) } returns entity

                val exception =
                    shouldThrow<CustomException> {
                        service.update(40L, dummyTaskUpdateRequest(status = TaskStatus.IN_REVIEW))
                    }

                Then("INVALID_TASK_STATUS_TRANSITION 예외가 발생한다") {
                    exception.code shouldBe ErrorCode.INVALID_STATUS_TRANSITION
                }
            }

            When("update 로 status 를 DONE 으로 변경하려 하면") {
                val entity = dummyTask(id = 41L, status = TaskStatus.IN_PROGRESS)
                every { taskRepository.findWithEpicAndProjectById(41L) } returns entity

                val exception =
                    shouldThrow<CustomException> {
                        service.update(41L, dummyTaskUpdateRequest(status = TaskStatus.DONE))
                    }

                Then("INVALID_TASK_STATUS_TRANSITION 예외가 발생한다") {
                    exception.code shouldBe ErrorCode.INVALID_STATUS_TRANSITION
                }
            }

            When("현재 상태가 DONE 인 태스크를 update 로 수정하려 하면") {
                val entity = dummyTask(id = 42L, status = TaskStatus.DONE, name = "완료된 태스크")
                every { taskRepository.findWithEpicAndProjectById(42L) } returns entity

                val exception =
                    shouldThrow<CustomException> {
                        service.update(42L, dummyTaskUpdateRequest(name = "이름만 변경"))
                    }

                Then("status 필드가 없어도 INVALID_TASK_STATUS_TRANSITION 예외가 발생한다") {
                    exception.code shouldBe ErrorCode.INVALID_STATUS_TRANSITION
                    entity.name shouldBe "완료된 태스크"
                }
            }
        }
    })
