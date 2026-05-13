package com.pluxity.weekly.teams.service

import com.pluxity.weekly.auth.authorization.AuthorizationService
import com.pluxity.weekly.core.constant.ErrorCode
import com.pluxity.weekly.core.exception.CustomException
import com.pluxity.weekly.teams.entity.TeamsNotificationLog
import com.pluxity.weekly.teams.entity.TeamsNotificationStatus
import com.pluxity.weekly.teams.entity.TeamsNotificationType
import com.pluxity.weekly.teams.event.TeamsNotificationEvent
import com.pluxity.weekly.teams.repository.TeamsNotificationLogRepository
import com.pluxity.weekly.test.entity.dummyUser
import com.pluxity.weekly.test.withAudit
import com.pluxity.weekly.test.withId
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.repository.findByIdOrNull
import java.time.LocalDateTime

class TeamsNotificationLogServiceTest :
    BehaviorSpec({

        val logRepository: TeamsNotificationLogRepository = mockk()
        val authorizationService: AuthorizationService = mockk()
        val eventPublisher: ApplicationEventPublisher = mockk()
        val service = TeamsNotificationLogService(logRepository, authorizationService, eventPublisher)

        Given("savePending") {
            When("로그를 PENDING 상태로 저장하면") {
                val captured = slot<TeamsNotificationLog>()
                every { logRepository.save(capture(captured)) } answers { captured.captured.withId(1L) }

                val result =
                    service.savePending(
                        userId = 10L,
                        type = TeamsNotificationType.TASK_REVIEW_REQUEST,
                        message = "리뷰 요청",
                    )

                Then("전달한 값과 PENDING 상태로 저장된다") {
                    captured.captured.userId shouldBe 10L
                    captured.captured.type shouldBe TeamsNotificationType.TASK_REVIEW_REQUEST
                    captured.captured.message shouldBe "리뷰 요청"
                    captured.captured.status shouldBe TeamsNotificationStatus.PENDING
                    captured.captured.failReason shouldBe null
                    result.requiredId shouldBe 1L
                }
            }
        }

        Given("markSent") {
            When("존재하는 로그를 SENT 로 표시하면") {
                val log =
                    TeamsNotificationLog(
                        userId = 1L,
                        type = TeamsNotificationType.TASK_APPROVE,
                        message = "승인",
                    ).withId(5L)
                every { logRepository.findByIdOrNull(5L) } returns log

                service.markSent(5L)

                Then("상태가 SENT 로 바뀌고 failReason 은 null 이 된다") {
                    log.status shouldBe TeamsNotificationStatus.SENT
                    log.failReason shouldBe null
                }
            }

            When("존재하지 않는 로그면") {
                every { logRepository.findByIdOrNull(999L) } returns null

                Then("예외 없이 조용히 종료된다") {
                    service.markSent(999L)
                }
            }
        }

        Given("markFailed") {
            When("존재하는 로그를 FAILED 로 표시하면") {
                val log =
                    TeamsNotificationLog(
                        userId = 1L,
                        type = TeamsNotificationType.EPIC_ASSIGN,
                        message = "배정",
                    ).withId(7L)
                every { logRepository.findByIdOrNull(7L) } returns log

                service.markFailed(7L, "HTTP 500: boom")

                Then("상태가 FAILED 로 바뀌고 실패 사유가 저장된다") {
                    log.status shouldBe TeamsNotificationStatus.FAILED
                    log.failReason shouldBe "HTTP 500: boom"
                }
            }

            When("존재하지 않는 로그면") {
                every { logRepository.findByIdOrNull(404L) } returns null

                Then("예외 없이 조용히 종료된다") {
                    service.markFailed(404L, "never applied")
                }
            }
        }

        Given("findMine") {
            When("현재 사용자의 알림 목록을 조회하면") {
                val user = dummyUser(id = 10L, name = "홍길동")
                val log1 =
                    TeamsNotificationLog(
                        userId = 10L,
                        type = TeamsNotificationType.TASK_APPROVE,
                        message = "승인",
                    ).withId(2L).withAudit(createdAt = LocalDateTime.of(2026, 4, 24, 10, 0))
                val log2 =
                    TeamsNotificationLog(
                        userId = 10L,
                        type = TeamsNotificationType.TASK_REVIEW_REQUEST,
                        message = "리뷰 요청",
                    ).withId(1L).withAudit(createdAt = LocalDateTime.of(2026, 4, 23, 10, 0))
                val pageable = PageRequest.of(0, 20)
                val page = PageImpl(listOf(log1, log2), pageable, 2)

                every { authorizationService.currentUser() } returns user
                every { logRepository.findByUserId(10L, pageable) } returns page

                val result = service.findMine(pageable)

                Then("현재 사용자 userId 로 조회된 결과가 Response 로 변환된다") {
                    result.content.size shouldBe 2
                    result.content[0].id shouldBe 2L
                    result.content[0].type shouldBe TeamsNotificationType.TASK_APPROVE
                    result.content[1].id shouldBe 1L
                    result.totalElements shouldBe 2
                    result.pageNumber shouldBe 1
                    result.pageSize shouldBe 20
                }
            }

            When("현재 사용자의 id 가 null 이면") {
                val orphanUser = dummyUser(id = null, name = "고아")
                every { authorizationService.currentUser() } returns orphanUser

                val exception =
                    shouldThrow<CustomException> {
                        service.findMine(PageRequest.of(0, 20))
                    }

                Then("PERMISSION_DENIED 예외가 발생한다") {
                    exception.code shouldBe ErrorCode.PERMISSION_DENIED
                }
            }
        }

        Given("findAllForAdmin") {
            When("ADMIN 이 status 필터 없이 전체 조회하면") {
                val admin = dummyUser(id = 1L, name = "admin")
                val log1 =
                    TeamsNotificationLog(
                        userId = 10L,
                        type = TeamsNotificationType.TASK_APPROVE,
                        message = "승인",
                    ).withId(2L)
                val log2 =
                    TeamsNotificationLog(
                        userId = 11L,
                        type = TeamsNotificationType.EPIC_ASSIGN,
                        message = "배정",
                    ).withId(1L)
                val pageable = PageRequest.of(0, 20)
                val page = PageImpl(listOf(log1, log2), pageable, 2)

                every { authorizationService.currentUser() } returns admin
                every { authorizationService.requireAdmin(admin) } just runs
                every { logRepository.findAll(pageable) } returns page

                val result = service.findAllForAdmin(null, pageable)

                Then("전체 알림이 Response 로 반환된다") {
                    result.content.size shouldBe 2
                    result.totalElements shouldBe 2
                }
            }

            When("ADMIN 이 status=FAILED 로 필터링하면") {
                val admin = dummyUser(id = 1L, name = "admin")
                val failedLog =
                    TeamsNotificationLog(
                        userId = 10L,
                        type = TeamsNotificationType.TASK_REVIEW_REQUEST,
                        message = "리뷰 요청",
                        status = TeamsNotificationStatus.FAILED,
                        failReason = "HTTP 500: boom",
                    ).withId(7L)
                val pageable = PageRequest.of(0, 20)
                val page = PageImpl(listOf(failedLog), pageable, 1)

                every { authorizationService.currentUser() } returns admin
                every { authorizationService.requireAdmin(admin) } just runs
                every { logRepository.findByStatus(TeamsNotificationStatus.FAILED, pageable) } returns page

                val result = service.findAllForAdmin(TeamsNotificationStatus.FAILED, pageable)

                Then("FAILED 알림만 반환된다") {
                    result.content.size shouldBe 1
                    result.content[0].id shouldBe 7L
                    result.content[0].status shouldBe TeamsNotificationStatus.FAILED
                    result.content[0].failReason shouldBe "HTTP 500: boom"
                }
            }

            When("ADMIN 권한이 없으면") {
                val nonAdmin = dummyUser(id = 5L, name = "일반")
                every { authorizationService.currentUser() } returns nonAdmin
                every {
                    authorizationService.requireAdmin(nonAdmin)
                } throws CustomException(ErrorCode.PERMISSION_DENIED)

                val exception =
                    shouldThrow<CustomException> {
                        service.findAllForAdmin(null, PageRequest.of(0, 20))
                    }

                Then("PERMISSION_DENIED 예외가 발생한다") {
                    exception.code shouldBe ErrorCode.PERMISSION_DENIED
                }
            }
        }

        Given("retry") {
            When("ADMIN 이 FAILED 알림을 재시도하면") {
                val log =
                    TeamsNotificationLog(
                        userId = 10L,
                        type = TeamsNotificationType.EPIC_ASSIGN,
                        message = "배정",
                        status = TeamsNotificationStatus.FAILED,
                        failReason = "HTTP 500: boom",
                    ).withId(3L)
                val admin = dummyUser(id = 1L, name = "admin")

                every { authorizationService.currentUser() } returns admin
                every { authorizationService.requireAdmin(admin) } just runs
                every { logRepository.findByIdOrNull(3L) } returns log
                val captured = slot<TeamsNotificationEvent>()
                every { eventPublisher.publishEvent(capture(captured)) } just runs

                val result = service.retry(3L)

                Then("상태가 PENDING 으로 바뀌고 메시지만 담은 이벤트가 재발행된다") {
                    log.status shouldBe TeamsNotificationStatus.PENDING
                    log.failReason shouldBe null
                    captured.captured.logId shouldBe 3L
                    captured.captured.userId shouldBe 10L
                    captured.captured.message shouldBe "배정"
                    captured.captured.card shouldBe null
                    result.status shouldBe TeamsNotificationStatus.PENDING
                }
            }

            When("존재하지 않는 logId 이면") {
                val admin = dummyUser(id = 1L, name = "admin")
                every { authorizationService.currentUser() } returns admin
                every { authorizationService.requireAdmin(admin) } just runs
                every { logRepository.findByIdOrNull(999L) } returns null

                val exception =
                    shouldThrow<CustomException> {
                        service.retry(999L)
                    }

                Then("NOT_FOUND_TEAMS_NOTIFICATION 예외가 발생한다") {
                    exception.code shouldBe ErrorCode.NOT_FOUND_TEAMS_NOTIFICATION
                }
            }

            When("FAILED 가 아닌 상태이면") {
                val log =
                    TeamsNotificationLog(
                        userId = 10L,
                        type = TeamsNotificationType.TASK_APPROVE,
                        message = "승인",
                        status = TeamsNotificationStatus.SENT,
                    ).withId(8L)
                val admin = dummyUser(id = 1L, name = "admin")
                every { authorizationService.currentUser() } returns admin
                every { authorizationService.requireAdmin(admin) } just runs
                every { logRepository.findByIdOrNull(8L) } returns log

                val exception =
                    shouldThrow<CustomException> {
                        service.retry(8L)
                    }

                Then("INVALID_STATUS_TRANSITION 예외가 발생한다") {
                    exception.code shouldBe ErrorCode.INVALID_STATUS_TRANSITION
                }
            }

            When("ADMIN 권한이 없으면") {
                val nonAdmin = dummyUser(id = 5L, name = "일반")
                every { authorizationService.currentUser() } returns nonAdmin
                every {
                    authorizationService.requireAdmin(nonAdmin)
                } throws CustomException(ErrorCode.PERMISSION_DENIED)

                val exception =
                    shouldThrow<CustomException> {
                        service.retry(1L)
                    }

                Then("PERMISSION_DENIED 예외가 발생한다") {
                    exception.code shouldBe ErrorCode.PERMISSION_DENIED
                }
            }
        }
    })
