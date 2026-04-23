package com.pluxity.weekly.epic.service

import com.pluxity.weekly.auth.authorization.AuthorizationService
import com.pluxity.weekly.auth.user.entity.RoleType
import com.pluxity.weekly.auth.user.repository.UserRepository
import com.pluxity.weekly.core.constant.ErrorCode
import com.pluxity.weekly.core.exception.CustomException
import com.pluxity.weekly.epic.entity.EpicStatus
import com.pluxity.weekly.epic.entity.dummyEpic
import com.pluxity.weekly.epic.entity.dummyEpicAssignment
import com.pluxity.weekly.epic.repository.EpicRepository
import com.pluxity.weekly.project.entity.dummyProject
import com.pluxity.weekly.task.repository.TaskRepository
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
import io.mockk.verify
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.repository.findByIdOrNull

class EpicAssignmentServiceTest :
    BehaviorSpec({

        val epicRepository: EpicRepository = mockk()
        val userRepository: UserRepository = mockk()
        val taskRepository: TaskRepository = mockk(relaxed = true)
        val authorizationService: AuthorizationService = mockk()
        val eventPublisher: ApplicationEventPublisher = mockk(relaxed = true)
        val service =
            EpicAssignmentService(
                epicRepository,
                userRepository,
                taskRepository,
                authorizationService,
                eventPublisher,
            )

        val adminUser =
            dummyUser(id = 1L, name = "관리자").apply {
                addRole(dummyRole(id = 1L, name = "ADMIN").apply { auth = RoleType.ADMIN.name })
            }

        beforeSpec {
            every { authorizationService.currentUser() } returns adminUser
            every { authorizationService.requireEpicAssign(any(), any()) } just runs
            every { authorizationService.requireAdminOrPm(any()) } just runs
        }

        Given("에픽 배정 목록 조회") {
            When("에픽에 배정된 사용자 목록을 조회하면") {
                val epic = dummyEpic(id = 1L)
                val u1 = dummyUser(id = 10L, name = "홍길동")
                val u2 = dummyUser(id = 20L, name = "김영희")
                epic.assignments.addAll(
                    listOf(
                        dummyEpicAssignment(id = 1L, epic = epic, user = u1),
                        dummyEpicAssignment(id = 2L, epic = epic, user = u2),
                    ),
                )

                every { epicRepository.findByIdOrNull(1L) } returns epic

                val result = service.findByEpic(1L)

                Then("배정 목록이 반환된다") {
                    result.size shouldBe 2
                }
            }
        }

        Given("에픽에 사용자 배정") {
            When("정상적으로 배정하면") {
                val epic = dummyEpic(id = 2L)
                val user = dummyUser(id = 30L, name = "신규")

                every { epicRepository.findByIdOrNull(2L) } returns epic
                every { userRepository.findByIdOrNull(30L) } returns user

                service.assign(2L, 30L)

                Then("에픽 assignments 에 추가되고 알림이 발행된다") {
                    epic.assignments.any { it.user == user } shouldBe true
                    verify { eventPublisher.publishEvent(match<TeamsNotificationEvent> { it.userId == 30L && it.message.contains("배정") }) }
                }
            }

            When("이미 배정된 사용자를 배정하면") {
                val epic = dummyEpic(id = 3L)
                val user = dummyUser(id = 40L, name = "중복")
                epic.assign(user)

                every { epicRepository.findByIdOrNull(3L) } returns epic
                every { userRepository.findByIdOrNull(40L) } returns user

                val exception =
                    shouldThrow<CustomException> {
                        service.assign(3L, 40L)
                    }

                Then("DUPLICATE_EPIC_ASSIGNMENT 예외가 발생한다") {
                    exception.code shouldBe ErrorCode.DUPLICATE_EPIC_ASSIGNMENT
                }
            }

            When("DONE 상태 에픽에 배정하려 하면") {
                val epic = dummyEpic(id = 4L, status = EpicStatus.DONE)

                every { epicRepository.findByIdOrNull(4L) } returns epic

                val exception =
                    shouldThrow<CustomException> {
                        service.assign(4L, 50L)
                    }

                Then("INVALID_STATUS_TRANSITION 예외가 발생한다") {
                    exception.code shouldBe ErrorCode.INVALID_STATUS_TRANSITION
                }
            }
        }

        Given("에픽에서 사용자 배정 해제") {
            When("배정된 사용자를 해제하면") {
                val epic = dummyEpic(id = 5L, name = "해제 에픽")
                val user = dummyUser(id = 60L, name = "해제 대상")
                epic.assign(user)

                every { epicRepository.findByIdOrNull(5L) } returns epic
                every { userRepository.findByIdOrNull(60L) } returns user

                service.unassign(5L, 60L)

                Then("assignments 에서 제거되고 해당 사용자의 태스크가 삭제되며 알림이 발행된다") {
                    epic.assignments.any { it.user == user } shouldBe false
                    verify { taskRepository.deleteByEpicIdAndAssigneeId(5L, 60L) }
                    verify {
                        eventPublisher.publishEvent(
                            match<TeamsNotificationEvent> { it.userId == 60L && it.message.contains("해제") },
                        )
                    }
                }
            }

            When("배정되지 않은 사용자를 해제하면") {
                val epic = dummyEpic(id = 6L)
                val user = dummyUser(id = 999L)

                every { epicRepository.findByIdOrNull(6L) } returns epic
                every { userRepository.findByIdOrNull(999L) } returns user

                val exception =
                    shouldThrow<CustomException> {
                        service.unassign(6L, 999L)
                    }

                Then("NOT_FOUND_EPIC_ASSIGNMENT 예외가 발생한다") {
                    exception.code shouldBe ErrorCode.NOT_FOUND_EPIC_ASSIGNMENT
                }
            }

            When("DONE 상태 에픽에서 해제하려 하면") {
                val epic = dummyEpic(id = 7L, status = EpicStatus.DONE)

                every { epicRepository.findByIdOrNull(7L) } returns epic

                val exception =
                    shouldThrow<CustomException> {
                        service.unassign(7L, 80L)
                    }

                Then("INVALID_STATUS_TRANSITION 예외가 발생한다") {
                    exception.code shouldBe ErrorCode.INVALID_STATUS_TRANSITION
                }
            }
        }

        Given("userIds 동기화 (sync)") {
            When("[2,3] 을 요청하면 기존 [1,2] 에서 1 은 해제되고 3 은 추가된다") {
                val project = dummyProject(id = 1L)
                val epic = dummyEpic(id = 10L, project = project, name = "동기화 에픽")
                val user1 = dummyUser(id = 1L, name = "유저1")
                val user2 = dummyUser(id = 2L, name = "유저2")
                val user3 = dummyUser(id = 3L, name = "유저3")
                epic.assign(user1)
                epic.assign(user2)

                every { userRepository.findAllById(listOf(2L, 3L)) } returns listOf(user2, user3)

                service.sync(epic, listOf(2L, 3L))

                Then("user1 은 해제되고 user3 은 추가된다") {
                    epic.assignments.map { it.user } shouldBe listOf(user2, user3)
                    verify { taskRepository.deleteByEpicIdAndAssigneeId(10L, 1L) }
                    verify { eventPublisher.publishEvent(match<TeamsNotificationEvent> { it.userId == 1L && it.message.contains("해제") }) }
                    verify { eventPublisher.publishEvent(match<TeamsNotificationEvent> { it.userId == 3L && it.message.contains("배정") }) }
                }
            }

            When("빈 배열을 요청하면 전체 해제된다") {
                val project = dummyProject(id = 1L)
                val epic = dummyEpic(id = 11L, project = project, name = "전체해제 에픽")
                val user1 = dummyUser(id = 10L, name = "유저A")
                val user2 = dummyUser(id = 20L, name = "유저B")
                epic.assign(user1)
                epic.assign(user2)

                every { userRepository.findAllById(emptyList()) } returns emptyList()

                service.sync(epic, emptyList())

                Then("배정이 모두 제거된다") {
                    epic.assignments.size shouldBe 0
                    verify { taskRepository.deleteByEpicIdAndAssigneeId(11L, 10L) }
                    verify { taskRepository.deleteByEpicIdAndAssigneeId(11L, 20L) }
                }
            }

            When("기존 배정이 없고 신규 추가만 있으면") {
                val project = dummyProject(id = 1L)
                val epic = dummyEpic(id = 12L, project = project, name = "신규추가 에픽")
                val u = dummyUser(id = 55L, name = "유저C")

                every { userRepository.findAllById(listOf(55L)) } returns listOf(u)

                service.sync(epic, listOf(55L))

                Then("추가만 수행되고 해제 관련 호출이 발생하지 않는다") {
                    epic.assignments.map { it.user } shouldBe listOf(u)
                    verify(exactly = 0) { taskRepository.deleteByEpicIdAndAssigneeId(12L, any()) }
                }
            }
        }

        Given("담당자 에픽 자동 편입 (ensureAssigned)") {
            When("assigneeId 가 null 이면 아무 동작도 하지 않는다") {
                val epic = dummyEpic(id = 20L)

                service.ensureAssigned(adminUser, null, epic)

                Then("권한 체크도 호출되지 않는다") {
                    verify(exactly = 0) { authorizationService.requireAdminOrPm(adminUser) }
                }
            }

            When("이미 에픽에 배정된 사용자이면 추가 작업 없이 통과한다") {
                val epic = dummyEpic(id = 21L)

                every { epicRepository.existsByAssignmentsUserIdAndId(70L, 21L) } returns true

                service.ensureAssigned(adminUser, 70L, epic)

                Then("이벤트가 발행되지 않는다") {
                    verify(exactly = 0) { userRepository.findByIdOrNull(70L) }
                    verify(exactly = 0) {
                        eventPublisher.publishEvent(match<TeamsNotificationEvent> { it.userId == 70L })
                    }
                }
            }

            When("에픽에 없는 사용자이면 자동 배정되고 알림이 발행된다") {
                val epic = dummyEpic(id = 22L, name = "자동편입")
                val assignee = dummyUser(id = 80L, name = "편입 대상")

                every { epicRepository.existsByAssignmentsUserIdAndId(80L, 22L) } returns false
                every { userRepository.findByIdOrNull(80L) } returns assignee

                service.ensureAssigned(adminUser, 80L, epic)

                Then("assignments 에 추가되고 배정 알림이 발행된다") {
                    epic.assignments.any { it.user == assignee } shouldBe true
                    verify { eventPublisher.publishEvent(match<TeamsNotificationEvent> { it.userId == 80L && it.message.contains("배정") }) }
                }
            }

            When("권한 없는 사용자가 호출하면") {
                val epic = dummyEpic(id = 23L)
                val generalUser = dummyUser(id = 99L, name = "일반")

                every { authorizationService.requireAdminOrPm(generalUser) } throws CustomException(ErrorCode.PERMISSION_DENIED)

                val exception =
                    shouldThrow<CustomException> {
                        service.ensureAssigned(generalUser, 100L, epic)
                    }

                Then("PERMISSION_DENIED 예외가 발생한다") {
                    exception.code shouldBe ErrorCode.PERMISSION_DENIED
                }
            }
        }
    })
