package com.pluxity.weekly.project.service

import com.pluxity.weekly.auth.authorization.AuthorizationService
import com.pluxity.weekly.auth.user.entity.RoleType
import com.pluxity.weekly.auth.user.repository.UserRepository
import com.pluxity.weekly.core.constant.ErrorCode
import com.pluxity.weekly.core.exception.CustomException
import com.pluxity.weekly.epic.repository.EpicRepository
import com.pluxity.weekly.project.dto.dummyProjectRequest
import com.pluxity.weekly.project.dto.dummyProjectUpdateRequest
import com.pluxity.weekly.project.entity.Project
import com.pluxity.weekly.project.entity.ProjectStatus
import com.pluxity.weekly.project.entity.dummyProject
import com.pluxity.weekly.project.repository.ProjectRepository
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

class ProjectServiceTest :
    BehaviorSpec({

        val projectRepository: ProjectRepository = mockk()
        val epicRepository: EpicRepository = mockk()
        val userRepository: UserRepository = mockk()
        val authorizationService: AuthorizationService = mockk()
        val eventPublisher: ApplicationEventPublisher = mockk(relaxed = true)
        val service = ProjectService(projectRepository, epicRepository, userRepository, authorizationService, eventPublisher)

        val adminUser =
            dummyUser(id = 1L, name = "관리자").apply {
                addRole(dummyRole(id = 1L, name = "ADMIN").apply { auth = RoleType.ADMIN.name })
            }

        beforeSpec {
            every { authorizationService.currentUser() } returns adminUser
            every { authorizationService.requireProjectManager(any(), any()) } just runs
            every { authorizationService.requireAdmin(any()) } just runs
            every { authorizationService.visibleProjectIds(any()) } returns null
        }

        Given("프로젝트 전체 조회") {
            When("프로젝트 목록을 조회하면") {
                val entities =
                    listOf(
                        dummyProject(id = 1L, name = "프로젝트A"),
                        dummyProject(id = 2L, name = "프로젝트B"),
                        dummyProject(id = 3L, name = "프로젝트C"),
                    )

                every { projectRepository.findByFilter(any()) } returns entities
                every { projectRepository.findMembersByProjectIds(any()) } returns emptyList()
                every { userRepository.findAllById(any<List<Long>>()) } returns emptyList()

                val result = service.findAll()

                Then("전체 목록이 반환된다") {
                    result.size shouldBe 3
                    result[0].name shouldBe "프로젝트A"
                }
            }
        }

        Given("프로젝트 단건 조회") {
            When("존재하는 프로젝트를 조회하면") {
                val entity =
                    dummyProject(
                        id = 1L,
                        name = "테스트 프로젝트",
                        description = "설명",
                        status = ProjectStatus.IN_PROGRESS,
                        startDate = LocalDate.of(2026, 1, 1),
                        dueDate = LocalDate.of(2026, 3, 31),
                        pmId = 10L,
                    )

                every { projectRepository.findByIdOrNull(1L) } returns entity
                every { projectRepository.findMembersByProjectIds(listOf(1L)) } returns emptyList()
                every { userRepository.findByIdOrNull(10L) } returns dummyUser(id = 10L, name = "PM유저")

                val result = service.findById(1L)

                Then("프로젝트 정보가 반환된다") {
                    result.id shouldBe 1L
                    result.name shouldBe "테스트 프로젝트"
                    result.description shouldBe "설명"
                    result.status shouldBe ProjectStatus.IN_PROGRESS
                    result.startDate shouldBe LocalDate.of(2026, 1, 1)
                    result.dueDate shouldBe LocalDate.of(2026, 3, 31)
                    result.pmId shouldBe 10L
                }
            }

            When("존재하지 않는 프로젝트를 조회하면") {
                every { projectRepository.findByIdOrNull(999L) } returns null

                val exception =
                    shouldThrow<CustomException> {
                        service.findById(999L)
                    }

                Then("NOT_FOUND 예외가 발생한다") {
                    exception.code shouldBe ErrorCode.NOT_FOUND_PROJECT
                }
            }
        }

        Given("프로젝트 생성") {
            When("유효한 요청으로 프로젝트를 생성하면") {
                val request =
                    dummyProjectRequest(
                        name = "신규 프로젝트",
                        status = ProjectStatus.TODO,
                        pmId = 5L,
                    )
                val saved = dummyProject(id = 1L, name = "신규 프로젝트", pmId = 5L)

                every { userRepository.existsById(5L) } returns true
                every { projectRepository.save(any<Project>()) } returns saved

                val result = service.create(request)

                Then("생성된 프로젝트의 ID가 반환된다") {
                    result shouldBe 1L
                }
            }

            When("존재하지 않는 pmId로 생성하면") {
                val request = dummyProjectRequest(name = "pm없음", pmId = 999L)

                every { userRepository.existsById(999L) } returns false

                val exception =
                    shouldThrow<CustomException> {
                        service.create(request)
                    }

                Then("NOT_FOUND_USER 예외가 발생한다") {
                    exception.code shouldBe ErrorCode.NOT_FOUND_USER
                }
            }

            When("startDate가 dueDate보다 늦게 생성하면") {
                val request =
                    dummyProjectRequest(
                        name = "날짜 역전",
                        startDate = LocalDate.of(2026, 6, 1),
                        dueDate = LocalDate.of(2026, 5, 1),
                    )

                val exception =
                    shouldThrow<CustomException> {
                        service.create(request)
                    }

                Then("INVALID_DATE_RANGE 예외가 발생한다") {
                    exception.code shouldBe ErrorCode.INVALID_DATE_RANGE
                }
            }
        }

        Given("프로젝트 수정") {
            When("존재하는 프로젝트를 수정하면") {
                val entity = dummyProject(id = 1L, name = "기존 프로젝트")
                val request =
                    dummyProjectUpdateRequest(
                        name = "수정된 프로젝트",
                        status = ProjectStatus.IN_PROGRESS,
                        pmId = 10L,
                    )

                every { projectRepository.findByIdOrNull(1L) } returns entity
                every { userRepository.existsById(10L) } returns true

                service.update(1L, request)

                Then("프로젝트 정보가 수정된다") {
                    entity.name shouldBe "수정된 프로젝트"
                    entity.status shouldBe ProjectStatus.IN_PROGRESS
                    entity.pmId shouldBe 10L
                }
            }

            When("존재하지 않는 프로젝트를 수정하면") {
                every { projectRepository.findByIdOrNull(999L) } returns null

                val exception =
                    shouldThrow<CustomException> {
                        service.update(999L, dummyProjectUpdateRequest())
                    }

                Then("NOT_FOUND 예외가 발생한다") {
                    exception.code shouldBe ErrorCode.NOT_FOUND_PROJECT
                }
            }

            When("존재하지 않는 pmId로 수정하면") {
                val entity = dummyProject(id = 1L, name = "기존")
                every { projectRepository.findByIdOrNull(1L) } returns entity
                every { userRepository.existsById(888L) } returns false

                val exception =
                    shouldThrow<CustomException> {
                        service.update(1L, dummyProjectUpdateRequest(pmId = 888L))
                    }

                Then("NOT_FOUND_USER 예외가 발생한다") {
                    exception.code shouldBe ErrorCode.NOT_FOUND_USER
                }
            }

            When("기존 dueDate보다 늦은 startDate로 수정하면") {
                val entity =
                    dummyProject(
                        id = 80L,
                        name = "기존",
                        startDate = LocalDate.of(2026, 1, 1),
                        dueDate = LocalDate.of(2026, 3, 1),
                    )

                every { projectRepository.findByIdOrNull(80L) } returns entity

                val exception =
                    shouldThrow<CustomException> {
                        service.update(
                            80L,
                            dummyProjectUpdateRequest(startDate = LocalDate.of(2026, 4, 1)),
                        )
                    }

                Then("INVALID_DATE_RANGE 예외가 발생한다") {
                    exception.code shouldBe ErrorCode.INVALID_DATE_RANGE
                }
            }
        }

        Given("프로젝트 삭제") {
            When("존재하는 프로젝트를 삭제하면") {
                val entity = dummyProject(id = 1L, name = "삭제대상 프로젝트")

                every { projectRepository.findByIdOrNull(1L) } returns entity
                every { projectRepository.delete(any<Project>()) } just runs

                service.delete(1L)

                Then("삭제가 수행된다") {
                    verify(exactly = 1) { projectRepository.delete(entity) }
                }
            }

            When("존재하지 않는 프로젝트를 삭제하면") {

                every { projectRepository.findByIdOrNull(999L) } returns null

                val exception =
                    shouldThrow<CustomException> {
                        service.delete(999L)
                    }

                Then("NOT_FOUND 예외가 발생한다") {
                    exception.code shouldBe ErrorCode.NOT_FOUND_PROJECT
                }
            }
        }

        Given("프로젝트 DONE 가드") {
            When("DONE 상태 프로젝트를 update 로 수정하려 하면") {
                val entity = dummyProject(id = 70L, status = ProjectStatus.DONE, name = "완료된 프로젝트")
                every { projectRepository.findByIdOrNull(70L) } returns entity

                val exception =
                    shouldThrow<CustomException> {
                        service.update(70L, dummyProjectUpdateRequest(name = "이름만 변경"))
                    }

                Then("INVALID_STATUS_TRANSITION 예외가 발생한다") {
                    exception.code shouldBe ErrorCode.INVALID_STATUS_TRANSITION
                    entity.name shouldBe "완료된 프로젝트"
                }
            }

            When("하위 에픽 중 DONE 이 아닌 게 있는데 status=DONE 으로 변경하려 하면") {
                val entity = dummyProject(id = 71L, status = ProjectStatus.IN_PROGRESS)
                val epic1 =
                    com.pluxity.weekly.epic.entity
                        .dummyEpic(id = 1L, status = com.pluxity.weekly.epic.entity.EpicStatus.DONE)
                val epic2 =
                    com.pluxity.weekly.epic.entity
                        .dummyEpic(id = 2L, status = com.pluxity.weekly.epic.entity.EpicStatus.IN_PROGRESS)

                every { projectRepository.findByIdOrNull(71L) } returns entity
                every { epicRepository.findByProjectIdIn(listOf(71L)) } returns listOf(epic1, epic2)

                val exception =
                    shouldThrow<CustomException> {
                        service.update(71L, dummyProjectUpdateRequest(status = ProjectStatus.DONE))
                    }

                Then("EPIC_NOT_ALL_DONE 예외가 발생한다") {
                    exception.code shouldBe ErrorCode.EPIC_NOT_ALL_DONE
                }
            }

            When("하위 에픽이 0개인 프로젝트를 status=DONE 으로 변경하려 하면") {
                val entity = dummyProject(id = 72L, status = ProjectStatus.IN_PROGRESS)
                every { projectRepository.findByIdOrNull(72L) } returns entity
                every { epicRepository.findByProjectIdIn(listOf(72L)) } returns emptyList()

                val exception =
                    shouldThrow<CustomException> {
                        service.update(72L, dummyProjectUpdateRequest(status = ProjectStatus.DONE))
                    }

                Then("EPIC_NOT_ALL_DONE 예외가 발생한다 (빈 컨테이너 차단)") {
                    exception.code shouldBe ErrorCode.EPIC_NOT_ALL_DONE
                }
            }
        }
    })
