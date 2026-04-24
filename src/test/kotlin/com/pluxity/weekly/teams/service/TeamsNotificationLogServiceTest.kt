package com.pluxity.weekly.teams.service

import com.pluxity.weekly.auth.authorization.AuthorizationService
import com.pluxity.weekly.core.constant.ErrorCode
import com.pluxity.weekly.core.exception.CustomException
import com.pluxity.weekly.teams.entity.TeamsNotificationLog
import com.pluxity.weekly.teams.entity.TeamsNotificationStatus
import com.pluxity.weekly.teams.entity.TeamsNotificationType
import com.pluxity.weekly.teams.repository.TeamsNotificationLogRepository
import com.pluxity.weekly.test.entity.dummyUser
import com.pluxity.weekly.test.withAudit
import com.pluxity.weekly.test.withId
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.repository.findByIdOrNull
import java.time.LocalDateTime

class TeamsNotificationLogServiceTest :
    BehaviorSpec({

        val logRepository: TeamsNotificationLogRepository = mockk()
        val authorizationService: AuthorizationService = mockk()
        val service = TeamsNotificationLogService(logRepository, authorizationService)

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
                every { logRepository.findByUserIdOrderByIdDesc(10L, pageable) } returns page

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
    })
