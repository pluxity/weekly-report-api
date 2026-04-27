package com.pluxity.weekly.task.service

import com.pluxity.weekly.auth.authorization.AuthorizationService
import com.pluxity.weekly.auth.user.entity.RoleType
import com.pluxity.weekly.core.constant.ErrorCode
import com.pluxity.weekly.core.exception.CustomException
import com.pluxity.weekly.epic.entity.dummyEpic
import com.pluxity.weekly.project.entity.dummyProject
import com.pluxity.weekly.task.entity.TaskApprovalAction
import com.pluxity.weekly.task.entity.TaskApprovalLog
import com.pluxity.weekly.task.entity.TaskStatus
import com.pluxity.weekly.task.entity.dummyTask
import com.pluxity.weekly.task.event.TaskApprovedEvent
import com.pluxity.weekly.task.event.TaskRejectedEvent
import com.pluxity.weekly.task.event.TaskReviewRequestedEvent
import com.pluxity.weekly.task.repository.TaskApprovalLogRepository
import com.pluxity.weekly.task.repository.TaskRepository
import com.pluxity.weekly.task.repository.TaskReviewRequestedAt
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
import java.time.LocalDateTime

class TaskReviewServiceTest :
    BehaviorSpec({

        val taskRepository: TaskRepository = mockk()
        val taskApprovalLogRepository: TaskApprovalLogRepository = mockk(relaxed = true)
        val authorizationService: AuthorizationService = mockk()
        val eventPublisher: ApplicationEventPublisher = mockk(relaxed = true)
        val service =
            TaskReviewService(
                taskRepository,
                taskApprovalLogRepository,
                authorizationService,
                eventPublisher,
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
            every { authorizationService.pmScopedProjectIds(any()) } returns null
            every { taskApprovalLogRepository.save(any<TaskApprovalLog>()) } answers { firstArg() }
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
                    (eventSlot.captured as TaskReviewRequestedEvent).pmId shouldBe pmId
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
                    (eventSlot.captured as TaskApprovedEvent).userId shouldBe 50L
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
                val assignee = dummyUser(id = 70L, name = "담당자")
                val entity =
                    dummyTask(
                        id = 30L,
                        epic = epic,
                        name = "반려 대상",
                        status = TaskStatus.IN_REVIEW,
                    ).apply { this.assignee = assignee }

                every { taskRepository.findWithEpicAndProjectById(30L) } returns entity
                val eventSlot = slot<Any>()
                every { eventPublisher.publishEvent(capture(eventSlot)) } just runs
                val logSlot = slot<TaskApprovalLog>()
                every { taskApprovalLogRepository.save(capture(logSlot)) } answers { logSlot.captured }

                service.reject(30L, "요구사항 불충족")

                Then("태스크 상태가 IN_PROGRESS 로 복귀되고 반려 사유가 로그와 이벤트에 전파된다") {
                    entity.status shouldBe TaskStatus.IN_PROGRESS
                    logSlot.captured.action shouldBe TaskApprovalAction.REJECT
                    logSlot.captured.reason shouldBe "요구사항 불충족"

                    val event = eventSlot.captured as TaskRejectedEvent
                    event.userId shouldBe 70L
                    event.taskName shouldBe "반려 대상"
                    event.reason shouldBe "요구사항 불충족"
                }
            }

            When("사유 없이(null) 반려하면") {
                val project = dummyProject(id = 1L, pmId = 99L)
                val epic = dummyEpic(id = 1L, project = project)
                val assignee = dummyUser(id = 71L, name = "담당자")
                val entity =
                    dummyTask(
                        id = 31L,
                        epic = epic,
                        name = "사유 없는 반려",
                        status = TaskStatus.IN_REVIEW,
                    ).apply { this.assignee = assignee }

                every { taskRepository.findWithEpicAndProjectById(31L) } returns entity
                val eventSlot = slot<Any>()
                every { eventPublisher.publishEvent(capture(eventSlot)) } just runs
                val logSlot = slot<TaskApprovalLog>()
                every { taskApprovalLogRepository.save(capture(logSlot)) } answers { logSlot.captured }

                service.reject(31L, null)

                Then("로그와 이벤트 모두 reason 이 null 로 전파된다") {
                    entity.status shouldBe TaskStatus.IN_PROGRESS
                    logSlot.captured.action shouldBe TaskApprovalAction.REJECT
                    logSlot.captured.reason shouldBe null
                    (eventSlot.captured as TaskRejectedEvent).reason shouldBe null
                }
            }

            When("담당자가 없는 태스크를 반려하면") {
                val project = dummyProject(id = 1L, pmId = 99L)
                val epic = dummyEpic(id = 1L, project = project)
                val entity =
                    dummyTask(
                        id = 33L,
                        epic = epic,
                        name = "담당자 없음",
                        status = TaskStatus.IN_REVIEW,
                    )

                every { taskRepository.findWithEpicAndProjectById(33L) } returns entity
                val eventSlot = slot<Any>()
                every { eventPublisher.publishEvent(capture(eventSlot)) } just runs
                val logSlot = slot<TaskApprovalLog>()
                every { taskApprovalLogRepository.save(capture(logSlot)) } answers { logSlot.captured }

                service.reject(33L, "사유")

                Then("로그는 기록되지만 알림 이벤트는 발행되지 않는다") {
                    logSlot.captured.action shouldBe TaskApprovalAction.REJECT
                    eventSlot.isCaptured shouldBe false
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

        Given("태스크 승인 로그 조회") {
            When("정상적으로 조회하면") {
                val epic = dummyEpic(id = 1L)
                val taskEntity = dummyTask(id = 10L, epic = epic, name = "로그 대상")
                val requester = dummyUser(id = 50L, name = "요청자")
                val reviewer = dummyUser(id = 99L, name = "리뷰어")

                val log1 = mockk<TaskApprovalLog>()
                every { log1.requiredId } returns 1L
                every { log1.task } returns taskEntity
                every { log1.actor } returns requester
                every { log1.action } returns TaskApprovalAction.REVIEW_REQUEST
                every { log1.reason } returns null
                every { log1.createdAt } returns LocalDateTime.of(2026, 4, 1, 9, 0)

                val log2 = mockk<TaskApprovalLog>()
                every { log2.requiredId } returns 2L
                every { log2.task } returns taskEntity
                every { log2.actor } returns reviewer
                every { log2.action } returns TaskApprovalAction.APPROVE
                every { log2.reason } returns null
                every { log2.createdAt } returns LocalDateTime.of(2026, 4, 2, 10, 0)

                every { taskRepository.findWithEpicAndProjectById(10L) } returns taskEntity
                every { taskApprovalLogRepository.findByTaskIdOrderByIdAsc(10L) } returns listOf(log1, log2)

                val result = service.findApprovalLogs(10L)

                Then("저장 순서대로 DTO 로 매핑되어 반환된다") {
                    result.size shouldBe 2
                    result[0].id shouldBe 1L
                    result[0].taskId shouldBe 10L
                    result[0].actorId shouldBe 50L
                    result[0].actorName shouldBe "요청자"
                    result[0].action shouldBe TaskApprovalAction.REVIEW_REQUEST
                    result[0].reason shouldBe null
                    result[1].id shouldBe 2L
                    result[1].actorId shouldBe 99L
                    result[1].action shouldBe TaskApprovalAction.APPROVE
                }
            }

            When("반려 사유가 있는 로그를 조회하면") {
                val epic = dummyEpic(id = 2L)
                val taskEntity = dummyTask(id = 11L, epic = epic, name = "반려 로그")
                val reviewer = dummyUser(id = 99L, name = "리뷰어")

                val rejectLog = mockk<TaskApprovalLog>()
                every { rejectLog.requiredId } returns 3L
                every { rejectLog.task } returns taskEntity
                every { rejectLog.actor } returns reviewer
                every { rejectLog.action } returns TaskApprovalAction.REJECT
                every { rejectLog.reason } returns "요구사항 불충족"
                every { rejectLog.createdAt } returns LocalDateTime.of(2026, 4, 3, 14, 0)

                every { taskRepository.findWithEpicAndProjectById(11L) } returns taskEntity
                every { taskApprovalLogRepository.findByTaskIdOrderByIdAsc(11L) } returns listOf(rejectLog)

                val result = service.findApprovalLogs(11L)

                Then("reason 이 응답에 포함된다") {
                    result.size shouldBe 1
                    result[0].action shouldBe TaskApprovalAction.REJECT
                    result[0].reason shouldBe "요구사항 불충족"
                }
            }

            When("로그가 없는 태스크를 조회하면") {
                val epic = dummyEpic(id = 3L)
                val taskEntity = dummyTask(id = 12L, epic = epic, name = "로그 없음")

                every { taskRepository.findWithEpicAndProjectById(12L) } returns taskEntity
                every { taskApprovalLogRepository.findByTaskIdOrderByIdAsc(12L) } returns emptyList()

                val result = service.findApprovalLogs(12L)

                Then("빈 목록이 반환된다") {
                    result shouldBe emptyList()
                }
            }

            When("존재하지 않는 태스크의 로그를 조회하면") {
                every { taskRepository.findWithEpicAndProjectById(999L) } returns null

                val exception =
                    shouldThrow<CustomException> {
                        service.findApprovalLogs(999L)
                    }

                Then("NOT_FOUND_TASK 예외가 발생한다") {
                    exception.code shouldBe ErrorCode.NOT_FOUND_TASK
                }
            }
        }
    })
