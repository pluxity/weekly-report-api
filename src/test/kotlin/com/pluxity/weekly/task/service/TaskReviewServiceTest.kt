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
import java.time.LocalDateTime

class TaskReviewServiceTest :
    BehaviorSpec({

        val taskRepository: TaskRepository = mockk()
        val taskApprovalLogRepository: TaskApprovalLogRepository = mockk(relaxed = true)
        val authorizationService: AuthorizationService = mockk()
        val eventPublisher: ApplicationEventPublisher = mockk(relaxed = true)
        val taskReviewCardBuilder: TaskReviewCardBuilder = mockk(relaxed = true)
        val service =
            TaskReviewService(
                taskRepository,
                taskApprovalLogRepository,
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
    })
