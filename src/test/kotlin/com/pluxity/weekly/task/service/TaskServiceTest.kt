package com.pluxity.weekly.task.service

import com.pluxity.weekly.auth.authorization.AuthorizationService
import com.pluxity.weekly.auth.user.entity.RoleType
import com.pluxity.weekly.auth.user.repository.UserRepository
import com.pluxity.weekly.core.constant.ErrorCode
import com.pluxity.weekly.core.exception.CustomException
import com.pluxity.weekly.epic.entity.Epic
import com.pluxity.weekly.epic.entity.dummyEpic
import com.pluxity.weekly.epic.repository.EpicRepository
import com.pluxity.weekly.epic.service.EpicAssignmentService
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
import io.mockk.slot
import io.mockk.verify
import org.springframework.data.repository.findByIdOrNull
import java.time.LocalDate

class TaskServiceTest :
    BehaviorSpec({

        val taskRepository: TaskRepository = mockk()
        val epicRepository: EpicRepository = mockk()
        val userRepository: UserRepository = mockk()
        val authorizationService: AuthorizationService = mockk()
        val assignmentService: EpicAssignmentService = mockk(relaxed = true)
        val service =
            TaskService(
                taskRepository,
                epicRepository,
                userRepository,
                authorizationService,
                assignmentService,
            )

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

        Given("태스크 생성 시 assignmentService 위임") {
            When("다른 사용자를 assignee로 지정하면 ensureAssigned 에 위임된다") {
                val epic = dummyEpic(id = 5L)
                val newAssignee = dummyUser(id = 30L, name = "신규 담당자")
                val request = dummyTaskRequest(epicId = 5L, name = "위임 create", assigneeId = 30L)
                val saved = dummyTask(id = 100L, epic = epic, name = "위임 create").apply { assignee = newAssignee }

                every { epicRepository.findByIdOrNull(5L) } returns epic
                every { taskRepository.existsByEpicIdAndName(5L, request.name) } returns false
                every { userRepository.findByIdOrNull(30L) } returns newAssignee
                every { taskRepository.save(any<Task>()) } returns saved

                service.create(request)

                Then("assignmentService.ensureAssigned 가 올바른 인자로 호출된다") {
                    verify { assignmentService.ensureAssigned(adminUser, 30L, epic) }
                }
            }

            When("ensureAssigned 가 권한 예외를 던지면 전파된다") {
                val epic = dummyEpic(id = 9L)
                val request = dummyTaskRequest(epicId = 9L, name = "권한없음 create", assigneeId = 50L)

                every { epicRepository.findByIdOrNull(9L) } returns epic
                every { taskRepository.existsByEpicIdAndName(9L, request.name) } returns false
                every {
                    assignmentService.ensureAssigned(any(), any(), any<Epic>())
                } throws CustomException(ErrorCode.PERMISSION_DENIED)

                val exception =
                    shouldThrow<CustomException> {
                        service.create(request)
                    }

                Then("PERMISSION_DENIED 예외가 발생한다") {
                    exception.code shouldBe ErrorCode.PERMISSION_DENIED
                }
            }

            When("assigneeId가 현재 로그인 사용자와 같으면 null 로 위임된다") {
                val epic = dummyEpic(id = 6L)
                val request = dummyTaskRequest(epicId = 6L, name = "본인 지정", assigneeId = 1L)
                val savedSlot = slot<Task>()
                val saved = dummyTask(id = 101L, epic = epic, name = "본인 지정")

                every { epicRepository.findByIdOrNull(6L) } returns epic
                every { taskRepository.existsByEpicIdAndName(6L, request.name) } returns false
                every { taskRepository.save(capture(savedSlot)) } returns saved

                service.create(request)

                Then("ensureAssigned 가 null 로 호출되고 currentUser 가 assignee 로 설정된다") {
                    verify { assignmentService.ensureAssigned(adminUser, null, epic) }
                    verify(exactly = 0) { userRepository.findByIdOrNull(1L) }
                    savedSlot.captured.assignee shouldBe adminUser
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
                every { taskRepository.existsByEpicIdAndName(1L, "수정된 태스크") } returns false

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

            When("같은 epic의 다른 태스크 이름으로 수정하면") {
                val epic = dummyEpic(id = 1L)
                val entity = dummyTask(id = 50L, epic = epic, name = "원본")

                every { taskRepository.findWithEpicAndProjectById(50L) } returns entity
                every { taskRepository.existsByEpicIdAndName(1L, "중복") } returns true

                val exception =
                    shouldThrow<CustomException> {
                        service.update(50L, dummyTaskUpdateRequest(name = "중복"))
                    }

                Then("DUPLICATE_TASK 예외가 발생한다") {
                    exception.code shouldBe ErrorCode.DUPLICATE_TASK
                }
            }

            When("본인 이름과 동일한 이름으로 수정하면") {
                val epic = dummyEpic(id = 1L)
                val entity = dummyTask(id = 51L, epic = epic, name = "변경없음")

                every { taskRepository.findWithEpicAndProjectById(51L) } returns entity

                service.update(51L, dummyTaskUpdateRequest(name = "변경없음"))

                Then("중복 체크 쿼리가 호출되지 않는다") {
                    verify(exactly = 0) {
                        taskRepository.existsByEpicIdAndName(any(), any())
                    }
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

        Given("태스크 수정 시 assignmentService 위임") {
            When("담당자가 변경되면 ensureAssigned 가 호출된다") {
                val epic = dummyEpic(id = 1L)
                val oldAssignee = dummyUser(id = 10L, name = "기존 담당자")
                val newAssignee = dummyUser(id = 20L, name = "새 담당자")
                val entity =
                    dummyTask(id = 70L, epic = epic, name = "담당자 변경 태스크").apply {
                        this.assignee = oldAssignee
                    }

                every { taskRepository.findWithEpicAndProjectById(70L) } returns entity
                every { userRepository.findByIdOrNull(20L) } returns newAssignee

                service.update(70L, dummyTaskUpdateRequest(assigneeId = 20L))

                Then("ensureAssigned 가 새 assignee id 로 호출되고 엔티티에 반영된다") {
                    verify { assignmentService.ensureAssigned(adminUser, 20L, epic) }
                    entity.assignee shouldBe newAssignee
                }
            }

            When("ensureAssigned 가 권한 예외를 던지면 전파된다") {
                val epic = dummyEpic(id = 1L)
                val entity =
                    dummyTask(id = 71L, epic = epic).apply {
                        this.assignee = adminUser
                    }

                every { taskRepository.findWithEpicAndProjectById(71L) } returns entity
                every {
                    assignmentService.ensureAssigned(any(), any(), any<Epic>())
                } throws CustomException(ErrorCode.PERMISSION_DENIED)

                val exception =
                    shouldThrow<CustomException> {
                        service.update(71L, dummyTaskUpdateRequest(assigneeId = 999L))
                    }

                Then("PERMISSION_DENIED 예외가 발생한다") {
                    exception.code shouldBe ErrorCode.PERMISSION_DENIED
                }
            }

            When("동일한 담당자 ID를 보내면 ensureAssigned 에 null 이 전달된다") {
                val epic = dummyEpic(id = 1L)
                val currentAssignee = dummyUser(id = 10L, name = "현재 담당자")
                val entity =
                    dummyTask(id = 73L, epic = epic, name = "동일 담당자").apply {
                        this.assignee = currentAssignee
                    }

                every { taskRepository.findWithEpicAndProjectById(73L) } returns entity

                service.update(73L, dummyTaskUpdateRequest(assigneeId = 10L))

                Then("null 로 위임되고 assignee 가 유지된다") {
                    verify { assignmentService.ensureAssigned(adminUser, null, epic) }
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
                every { taskRepository.existsByEpicIdAndName(any(), any()) } returns false

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

        Given("태스크 복구") {
            When("존재하는 태스크를 복구하면") {
                val entity = dummyTask(id = 800L, name = "복구 대상")
                every { taskRepository.findRawById(800L) } returns entity
                every { taskRepository.restoreById(800L) } returns 1

                service.restore(800L)

                Then("태스크가 복구된다") {
                    verify(exactly = 1) { taskRepository.restoreById(800L) }
                }
            }

            When("존재하지 않는 태스크를 복구하면") {
                every { taskRepository.findRawById(999L) } returns null

                val exception =
                    shouldThrow<CustomException> {
                        service.restore(999L)
                    }

                Then("NOT_FOUND_TASK 예외가 발생하고 복구 쿼리는 실행되지 않는다") {
                    exception.code shouldBe ErrorCode.NOT_FOUND_TASK
                    verify(exactly = 0) { taskRepository.restoreById(any()) }
                }
            }
        }
    })
