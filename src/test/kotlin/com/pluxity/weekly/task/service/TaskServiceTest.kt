package com.pluxity.weekly.task.service

import com.pluxity.weekly.auth.user.entity.RoleType
import com.pluxity.weekly.auth.user.repository.UserRepository
import com.pluxity.weekly.authorization.AuthorizationService
import com.pluxity.weekly.core.constant.ErrorCode
import com.pluxity.weekly.core.exception.CustomException
import com.pluxity.weekly.epic.entity.dummyEpic
import com.pluxity.weekly.epic.repository.EpicRepository
import com.pluxity.weekly.task.dto.dummyTaskRequest
import com.pluxity.weekly.task.dto.dummyTaskUpdateRequest
import com.pluxity.weekly.task.entity.Task
import com.pluxity.weekly.task.entity.TaskStatus
import com.pluxity.weekly.task.entity.dummyTask
import com.pluxity.weekly.task.repository.TaskRepository
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
import org.springframework.data.repository.findByIdOrNull
import java.time.LocalDate

class TaskServiceTest :
    BehaviorSpec({

        val taskRepository: TaskRepository = mockk()
        val epicRepository: EpicRepository = mockk()
        val userRepository: UserRepository = mockk()
        val authorizationService: AuthorizationService = mockk()
        val service = TaskService(taskRepository, epicRepository, userRepository, authorizationService)

        val adminUser =
            dummyUser(id = 1L, name = "관리자").apply {
                addRole(dummyRole(id = 1L, name = "ADMIN").apply { auth = RoleType.ADMIN.name })
            }

        beforeSpec {
            every { authorizationService.currentUser() } returns adminUser
            every { authorizationService.requireEpicAccess(any(), any()) } just runs
            every { authorizationService.requireTaskOwner(any(), any()) } just runs
            every { authorizationService.visibleEpicIds(any()) } returns null
            every { authorizationService.restrictedAssigneeId(any()) } returns null
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

                every { taskRepository.findByIdOrNull(1L) } returns entity

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
                every { taskRepository.findByIdOrNull(999L) } returns null

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

                every { taskRepository.findByIdOrNull(1L) } returns entity

                service.update(1L, request)

                Then("태스크 정보가 수정된다") {
                    entity.name shouldBe "수정된 태스크"
                    entity.status shouldBe TaskStatus.IN_PROGRESS
                    entity.progress shouldBe 30
                }
            }

            When("존재하지 않는 태스크를 수정하면") {
                every { taskRepository.findByIdOrNull(999L) } returns null

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

                every { taskRepository.findByIdOrNull(1L) } returns entity
                every { taskRepository.delete(any<Task>()) } just runs

                service.delete(1L)

                Then("삭제가 수행된다") {
                    verify(exactly = 1) { taskRepository.delete(entity) }
                }
            }

            When("존재하지 않는 태스크를 삭제하면") {
                every { taskRepository.findByIdOrNull(999L) } returns null

                val exception =
                    shouldThrow<CustomException> {
                        service.delete(999L)
                    }

                Then("NOT_FOUND 예외가 발생한다") {
                    exception.code shouldBe ErrorCode.NOT_FOUND_TASK
                }
            }
        }
    })
