package com.pluxity.weekly.epic.service

import com.pluxity.weekly.auth.user.entity.RoleType
import com.pluxity.weekly.auth.user.repository.UserRepository
import com.pluxity.weekly.authorization.AuthorizationService
import com.pluxity.weekly.core.constant.ErrorCode
import com.pluxity.weekly.core.exception.CustomException
import com.pluxity.weekly.epic.dto.dummyEpicRequest
import com.pluxity.weekly.epic.dto.dummyEpicUpdateRequest
import com.pluxity.weekly.epic.entity.Epic
import com.pluxity.weekly.epic.entity.EpicStatus
import com.pluxity.weekly.epic.entity.dummyEpic
import com.pluxity.weekly.epic.entity.dummyEpicAssignment
import com.pluxity.weekly.epic.repository.EpicRepository
import com.pluxity.weekly.project.entity.dummyProject
import com.pluxity.weekly.project.repository.ProjectRepository
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
import java.time.LocalDate

class EpicServiceTest :
    BehaviorSpec({

        val epicRepository: EpicRepository = mockk()
        val projectRepository: ProjectRepository = mockk()
        val taskRepository: TaskRepository = mockk()
        val userRepository: UserRepository = mockk()
        val authorizationService: AuthorizationService = mockk()
        val eventPublisher: ApplicationEventPublisher = mockk()
        val service = EpicService(epicRepository, projectRepository, taskRepository, userRepository, authorizationService, eventPublisher)

        val adminUser =
            dummyUser(id = 1L, name = "관리자").apply {
                addRole(dummyRole(id = 1L, name = "ADMIN").apply { auth = RoleType.ADMIN.name })
            }

        beforeSpec {
            every { authorizationService.currentUser() } returns adminUser
            every { authorizationService.requireEpicManage(any(), any()) } just runs
            every { authorizationService.requireEpicAccess(any(), any()) } just runs
            every { authorizationService.requireEpicAssign(any(), any()) } just runs
            every { authorizationService.visibleEpicIds(any()) } returns null
        }

        Given("에픽 전체 조회") {
            When("에픽 목록을 조회하면") {
                val project = dummyProject(id = 1L)
                val entities =
                    listOf(
                        dummyEpic(id = 1L, project = project, name = "에픽A"),
                        dummyEpic(id = 2L, project = project, name = "에픽B"),
                        dummyEpic(id = 3L, project = project, name = "에픽C"),
                    )

                every { epicRepository.findByFilter(any()) } returns entities

                val result = service.findAll()

                Then("전체 목록이 반환된다") {
                    result.size shouldBe 3
                    result[0].name shouldBe "에픽A"
                }
            }
        }

        Given("에픽 단건 조회") {
            When("존재하는 에픽을 조회하면") {
                val project = dummyProject(id = 1L)
                val entity =
                    dummyEpic(
                        id = 1L,
                        project = project,
                        name = "테스트 에픽",
                        description = "설명",
                        status = EpicStatus.IN_PROGRESS,
                        startDate = LocalDate.of(2026, 1, 1),
                        dueDate = LocalDate.of(2026, 3, 31),
                    )

                every { epicRepository.findByIdOrNull(1L) } returns entity

                val result = service.findById(1L)

                Then("에픽 정보가 반환된다") {
                    result.id shouldBe 1L
                    result.projectId shouldBe 1L
                    result.name shouldBe "테스트 에픽"
                    result.description shouldBe "설명"
                    result.status shouldBe EpicStatus.IN_PROGRESS
                    result.startDate shouldBe LocalDate.of(2026, 1, 1)
                    result.dueDate shouldBe LocalDate.of(2026, 3, 31)
                }
            }

            When("존재하지 않는 에픽을 조회하면") {
                every { epicRepository.findByIdOrNull(999L) } returns null

                val exception =
                    shouldThrow<CustomException> {
                        service.findById(999L)
                    }

                Then("NOT_FOUND 예외가 발생한다") {
                    exception.code shouldBe ErrorCode.NOT_FOUND_EPIC
                }
            }
        }

        Given("에픽 생성") {
            When("유효한 요청으로 에픽을 생성하면") {
                val project = dummyProject(id = 1L)
                val request =
                    dummyEpicRequest(
                        projectId = 1L,
                        name = "신규 에픽",
                        status = EpicStatus.TODO,
                    )
                val saved = dummyEpic(id = 1L, project = project, name = "신규 에픽")

                every { projectRepository.findByIdOrNull(1L) } returns project
                every { epicRepository.save(any<Epic>()) } returns saved

                val result = service.create(request)

                Then("생성된 에픽의 ID가 반환된다") {
                    result shouldBe 1L
                }
            }
        }

        Given("에픽 수정") {
            When("존재하는 에픽을 수정하면") {
                val project = dummyProject(id = 1L)
                val entity = dummyEpic(id = 1L, project = project, name = "기존 에픽")
                val request =
                    dummyEpicUpdateRequest(
                        name = "수정된 에픽",
                        status = EpicStatus.IN_PROGRESS,
                    )

                every { epicRepository.findByIdOrNull(1L) } returns entity

                service.update(1L, request)

                Then("에픽 정보가 수정된다") {
                    entity.name shouldBe "수정된 에픽"
                    entity.status shouldBe EpicStatus.IN_PROGRESS
                }
            }

            When("존재하지 않는 에픽을 수정하면") {
                every { epicRepository.findByIdOrNull(999L) } returns null

                val exception =
                    shouldThrow<CustomException> {
                        service.update(999L, dummyEpicUpdateRequest())
                    }

                Then("NOT_FOUND 예외가 발생한다") {
                    exception.code shouldBe ErrorCode.NOT_FOUND_EPIC
                }
            }
        }

        Given("에픽 삭제") {
            When("존재하는 에픽을 삭제하면") {
                val entity = dummyEpic(id = 1L, name = "삭제대상 에픽")

                every { epicRepository.findByIdOrNull(1L) } returns entity
                every { taskRepository.existsByEpicId(1L) } returns false
                every { epicRepository.delete(any<Epic>()) } just runs

                service.delete(1L)

                Then("삭제가 수행된다") {
                    verify(exactly = 1) { epicRepository.delete(entity) }
                }
            }

            When("존재하지 않는 에픽을 삭제하면") {
                every { epicRepository.findByIdOrNull(999L) } returns null

                val exception =
                    shouldThrow<CustomException> {
                        service.delete(999L)
                    }

                Then("NOT_FOUND 예외가 발생한다") {
                    exception.code shouldBe ErrorCode.NOT_FOUND_EPIC
                }
            }
        }

        Given("에픽 수정 시 userIds 교체") {
            When("userIds로 [2,3]을 보내면 기존 [1,2]에서 1은 해제되고 3은 추가된다") {
                val project = dummyProject(id = 1L)
                val epic = dummyEpic(id = 10L, project = project, name = "배정교체 에픽")
                val user1 = dummyUser(id = 1L, name = "유저1")
                val user2 = dummyUser(id = 2L, name = "유저2")
                val user3 = dummyUser(id = 3L, name = "유저3")
                epic.assign(user1)
                epic.assign(user2)

                every { epicRepository.findByIdOrNull(10L) } returns epic
                every { userRepository.findByIdOrNull(2L) } returns user2
                every { userRepository.findByIdOrNull(3L) } returns user3
                every { taskRepository.deleteByEpicIdAndAssigneeId(10L, 1L) } just runs
                every { eventPublisher.publishEvent(any<TeamsNotificationEvent>()) } just runs

                service.update(10L, dummyEpicUpdateRequest(userIds = listOf(2L, 3L)))

                Then("user1은 해제되고 user3은 추가된다") {
                    epic.assignments.map { it.user } shouldBe listOf(user2, user3)
                    verify { taskRepository.deleteByEpicIdAndAssigneeId(10L, 1L) }
                    verify { eventPublisher.publishEvent(match<TeamsNotificationEvent> { it.userId == 1L && it.message.contains("해제") }) }
                    verify { eventPublisher.publishEvent(match<TeamsNotificationEvent> { it.userId == 3L && it.message.contains("배정") }) }
                }
            }

            When("빈 배열을 보내면 전체 해제된다") {
                val project = dummyProject(id = 1L)
                val epic = dummyEpic(id = 11L, project = project, name = "전체해제 에픽")
                val user1 = dummyUser(id = 10L, name = "유저A")
                val user2 = dummyUser(id = 20L, name = "유저B")
                epic.assign(user1)
                epic.assign(user2)

                every { epicRepository.findByIdOrNull(11L) } returns epic
                every { taskRepository.deleteByEpicIdAndAssigneeId(11L, any()) } just runs
                every { eventPublisher.publishEvent(any<TeamsNotificationEvent>()) } just runs

                service.update(11L, dummyEpicUpdateRequest(userIds = emptyList()))

                Then("배정이 모두 제거된다") {
                    epic.assignments.size shouldBe 0
                    verify { taskRepository.deleteByEpicIdAndAssigneeId(11L, 10L) }
                    verify { taskRepository.deleteByEpicIdAndAssigneeId(11L, 20L) }
                }
            }

            When("userIds가 null이면 배정이 변경되지 않는다") {
                val project = dummyProject(id = 1L)
                val epic = dummyEpic(id = 12L, project = project, name = "변경없음 에픽")
                val user1 = dummyUser(id = 10L, name = "유저X")
                epic.assign(user1)

                every { epicRepository.findByIdOrNull(12L) } returns epic

                service.update(12L, dummyEpicUpdateRequest(userIds = null))

                Then("기존 배정이 유지된다") {
                    epic.assignments.size shouldBe 1
                    epic.assignments[0].user shouldBe user1
                }
            }
        }

        // ── EpicAssignment ──

        Given("에픽 배정 목록 조회") {
            When("에픽에 배정된 사용자를 조회하면") {
                val epic = dummyEpic(id = 1L)
                val user1 = dummyUser(id = 10L, name = "홍길동")
                val user2 = dummyUser(id = 20L, name = "김영희")
                epic.assignments.addAll(
                    listOf(
                        dummyEpicAssignment(id = 1L, epic = epic, user = user1),
                        dummyEpicAssignment(id = 2L, epic = epic, user = user2),
                    ),
                )

                every { epicRepository.findByIdOrNull(1L) } returns epic

                val result = service.findAssignments(1L)

                Then("배정 목록이 반환된다") {
                    result.size shouldBe 2
                }
            }
        }

        Given("에픽 배정 해제") {
            When("배정된 사용자를 해제하면") {
                val epic = dummyEpic(id = 1L)
                val user = dummyUser(id = 10L)
                val event = TeamsNotificationEvent(user.requiredId, "테스트 에픽 에픽에서 해제되었습니다")
                epic.assign(user)

                every { epicRepository.findByIdOrNull(1L) } returns epic
                every { userRepository.findByIdOrNull(10L) } returns user
                every { taskRepository.deleteByEpicIdAndAssigneeId(1L, 10L) } just runs
                every { eventPublisher.publishEvent(event) } just runs

                service.unassign(1L, 10L)

                Then("배정이 제거된다") {
                    epic.assignments.size shouldBe 0
                }
            }

            When("배정되지 않은 사용자를 해제하면") {
                val epic = dummyEpic(id = 1L)
                val user = dummyUser(id = 999L)

                every { epicRepository.findByIdOrNull(1L) } returns epic
                every { userRepository.findByIdOrNull(999L) } returns user

                val exception =
                    shouldThrow<CustomException> {
                        service.unassign(1L, 999L)
                    }

                Then("NOT_FOUND 예외가 발생한다") {
                    exception.code shouldBe ErrorCode.NOT_FOUND_EPIC_ASSIGNMENT
                }
            }
        }

        Given("DONE 프로젝트에 에픽 생성 차단") {
            When("DONE 상태 프로젝트에 에픽을 생성하려 하면") {
                val doneProject =
                    dummyProject(id = 50L, status = com.pluxity.weekly.project.entity.ProjectStatus.DONE)
                val request = dummyEpicRequest(projectId = 50L, name = "신규 에픽")

                every { projectRepository.findByIdOrNull(50L) } returns doneProject

                val exception =
                    shouldThrow<CustomException> {
                        service.create(request)
                    }

                Then("INVALID_STATUS_TRANSITION 예외가 발생한다") {
                    exception.code shouldBe ErrorCode.INVALID_STATUS_TRANSITION
                }
            }
        }

        Given("에픽 DONE 가드") {
            When("DONE 상태 에픽을 update 로 수정하려 하면") {
                val entity = dummyEpic(id = 60L, status = EpicStatus.DONE, name = "완료된 에픽")
                every { epicRepository.findByIdOrNull(60L) } returns entity

                val exception =
                    shouldThrow<CustomException> {
                        service.update(60L, dummyEpicUpdateRequest(name = "이름만 변경"))
                    }

                Then("INVALID_STATUS_TRANSITION 예외가 발생한다") {
                    exception.code shouldBe ErrorCode.INVALID_STATUS_TRANSITION
                    entity.name shouldBe "완료된 에픽"
                }
            }

            When("하위 태스크 중 DONE 이 아닌 게 있는데 status=DONE 으로 변경하려 하면") {
                val entity = dummyEpic(id = 61L, status = EpicStatus.IN_PROGRESS)
                val task1 =
                    com.pluxity.weekly.task.entity
                        .dummyTask(id = 1L, status = com.pluxity.weekly.task.entity.TaskStatus.DONE)
                val task2 =
                    com.pluxity.weekly.task.entity
                        .dummyTask(id = 2L, status = com.pluxity.weekly.task.entity.TaskStatus.IN_PROGRESS)

                every { epicRepository.findByIdOrNull(61L) } returns entity
                every { taskRepository.findByEpicId(61L) } returns listOf(task1, task2)

                val exception =
                    shouldThrow<CustomException> {
                        service.update(61L, dummyEpicUpdateRequest(status = EpicStatus.DONE))
                    }

                Then("TASK_NOT_ALL_DONE 예외가 발생한다") {
                    exception.code shouldBe ErrorCode.TASK_NOT_ALL_DONE
                }
            }

            When("하위 태스크가 0개인 에픽을 status=DONE 으로 변경하려 하면") {
                val entity = dummyEpic(id = 62L, status = EpicStatus.IN_PROGRESS)
                every { epicRepository.findByIdOrNull(62L) } returns entity
                every { taskRepository.findByEpicId(62L) } returns emptyList()

                val exception =
                    shouldThrow<CustomException> {
                        service.update(62L, dummyEpicUpdateRequest(status = EpicStatus.DONE))
                    }

                Then("TASK_NOT_ALL_DONE 예외가 발생한다 (빈 컨테이너 차단)") {
                    exception.code shouldBe ErrorCode.TASK_NOT_ALL_DONE
                }
            }
        }
    })
