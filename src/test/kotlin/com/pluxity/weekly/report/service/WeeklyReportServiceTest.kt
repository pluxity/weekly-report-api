package com.pluxity.weekly.report.service

import com.pluxity.weekly.auth.authorization.AuthorizationService
import com.pluxity.weekly.core.constant.ErrorCode
import com.pluxity.weekly.core.exception.CustomException
import com.pluxity.weekly.report.entity.dummyWeeklyReport
import com.pluxity.weekly.report.repository.WeeklyReportRepository
import com.pluxity.weekly.report.repository.WeeklyReportSummaryRow
import com.pluxity.weekly.team.entity.dummyTeam
import com.pluxity.weekly.team.repository.TeamRepository
import com.pluxity.weekly.test.entity.dummyUser
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import org.springframework.data.repository.findByIdOrNull
import java.time.LocalDate
import java.time.LocalDateTime

class WeeklyReportServiceTest :
    BehaviorSpec({

        val weeklyReportRepository: WeeklyReportRepository = mockk()
        val teamRepository: TeamRepository = mockk()
        val authorizationService: AuthorizationService = mockk()
        val service = WeeklyReportService(weeklyReportRepository, teamRepository, authorizationService)

        val adminUser = dummyUser(id = 1L, name = "관리자")
        val leaderUser = dummyUser(id = 2L, name = "리더")
        val workerUser = dummyUser(id = 3L, name = "워커")
        val team10 = dummyTeam(id = 10L, name = "개발팀", leaderId = 2L)
        val team20 = dummyTeam(id = 20L, name = "디자인팀", leaderId = 99L)

        Given("findAll - ADMIN") {
            every { authorizationService.currentUser() } returns adminUser
            every { authorizationService.requireAdminOrLeader(adminUser) } just runs
            every { authorizationService.visibleTeamIds(adminUser) } returns null

            When("teamId 없이 조회하면") {
                every {
                    weeklyReportRepository.findByFilter(
                        match { it.teamId == null && it.teamIds == null },
                    )
                } returns listOf(dummyWeeklyReport(id = 1L, team = team10))

                val result = service.findAll(null, null, null)

                Then("teamId/teamIds 제약 없이 조회된다") {
                    result.size shouldBe 1
                    result[0].id shouldBe 1L
                }
            }

            When("teamId를 지정하면") {
                every { authorizationService.requireTeamAccess(adminUser, 10L) } just runs
                every {
                    weeklyReportRepository.findByFilter(
                        match { it.teamId == 10L && it.teamIds == null },
                    )
                } returns listOf(dummyWeeklyReport(id = 1L, team = team10))

                val result = service.findAll(10L, null, null)

                Then("해당 팀 보고만 조회된다") {
                    result.size shouldBe 1
                    result[0].teamId shouldBe 10L
                }
            }
        }

        Given("findAll - Leader") {
            every { authorizationService.currentUser() } returns leaderUser
            every { authorizationService.requireAdminOrLeader(leaderUser) } just runs
            every { authorizationService.visibleTeamIds(leaderUser) } returns listOf(10L)

            When("teamId 없이 조회하면") {
                every {
                    weeklyReportRepository.findByFilter(
                        match { it.teamId == null && it.teamIds == listOf(10L) },
                    )
                } returns listOf(dummyWeeklyReport(id = 1L, team = team10))

                val result = service.findAll(null, null, null)

                Then("본인이 leader인 팀들로 제한된다") {
                    result.size shouldBe 1
                    result[0].teamId shouldBe 10L
                }
            }

            When("본인이 leader인 팀의 teamId를 지정하면") {
                every { authorizationService.requireTeamAccess(leaderUser, 10L) } just runs
                every {
                    weeklyReportRepository.findByFilter(
                        match { it.teamId == 10L },
                    )
                } returns listOf(dummyWeeklyReport(id = 1L, team = team10))

                val result = service.findAll(10L, null, null)

                Then("해당 팀 보고가 조회된다") {
                    result.size shouldBe 1
                }
            }

            When("본인이 leader가 아닌 teamId를 지정하면") {
                every { authorizationService.requireTeamAccess(leaderUser, 20L) } throws
                    CustomException(ErrorCode.PERMISSION_DENIED)

                val exception =
                    shouldThrow<CustomException> {
                        service.findAll(20L, null, null)
                    }

                Then("PERMISSION_DENIED 예외가 발생한다") {
                    exception.code shouldBe ErrorCode.PERMISSION_DENIED
                }
            }
        }

        Given("findAll - 권한 없는 사용자") {
            When("ADMIN/Leader가 아닌 사용자가 호출하면") {
                every { authorizationService.currentUser() } returns workerUser
                every { authorizationService.requireAdminOrLeader(workerUser) } throws
                    CustomException(ErrorCode.PERMISSION_DENIED)

                val exception =
                    shouldThrow<CustomException> {
                        service.findAll(null, null, null)
                    }

                Then("PERMISSION_DENIED 예외가 발생한다") {
                    exception.code shouldBe ErrorCode.PERMISSION_DENIED
                }
            }
        }

        Given("findById") {
            every { authorizationService.currentUser() } returns leaderUser

            When("존재하는 보고를 본인 팀으로 조회하면") {
                val report = dummyWeeklyReport(id = 1L, team = team10)
                every { weeklyReportRepository.findByIdOrNull(1L) } returns report
                every { authorizationService.requireTeamAccess(leaderUser, 10L) } just runs

                val result = service.findById(1L)

                Then("보고가 반환된다") {
                    result.id shouldBe 1L
                    result.teamId shouldBe 10L
                }
            }

            When("존재하지 않는 보고를 조회하면") {
                every { weeklyReportRepository.findByIdOrNull(999L) } returns null

                val exception =
                    shouldThrow<CustomException> {
                        service.findById(999L)
                    }

                Then("NOT_FOUND_WEEKLY_REPORT 예외가 발생한다") {
                    exception.code shouldBe ErrorCode.NOT_FOUND_WEEKLY_REPORT
                }
            }

            When("본인이 leader가 아닌 팀의 보고를 조회하면") {
                val report = dummyWeeklyReport(id = 2L, team = team20)
                every { weeklyReportRepository.findByIdOrNull(2L) } returns report
                every { authorizationService.requireTeamAccess(leaderUser, 20L) } throws
                    CustomException(ErrorCode.PERMISSION_DENIED)

                val exception =
                    shouldThrow<CustomException> {
                        service.findById(2L)
                    }

                Then("PERMISSION_DENIED 예외가 발생한다") {
                    exception.code shouldBe ErrorCode.PERMISSION_DENIED
                }
            }
        }

        Given("findSummary") {
            val weekStart = LocalDate.of(2026, 5, 11)
            val weekEnd = LocalDate.of(2026, 5, 18) // 5/11 + 5/18 = 2주

            When("ADMIN이 조회하면") {
                every { authorizationService.currentUser() } returns adminUser
                every { authorizationService.requireAdminOrLeader(adminUser) } just runs
                every { authorizationService.visibleTeamIds(adminUser) } returns null
                every { teamRepository.findAll() } returns listOf(team10, team20)
                every { weeklyReportRepository.findSummaryRows(weekStart, weekEnd) } returns emptyList()

                val result = service.findSummary(weekStart, weekEnd)

                Then("모든 팀 × 모든 주차 슬롯이 반환된다 (2팀 × 2주 = 4)") {
                    result.size shouldBe 4
                    result.all { !it.exists } shouldBe true
                }
            }

            When("Leader가 조회하면") {
                every { authorizationService.currentUser() } returns leaderUser
                every { authorizationService.requireAdminOrLeader(leaderUser) } just runs
                every { authorizationService.visibleTeamIds(leaderUser) } returns listOf(10L)
                every { teamRepository.findAllById(listOf(10L)) } returns listOf(team10)
                every { weeklyReportRepository.findSummaryRows(weekStart, weekEnd) } returns emptyList()

                val result = service.findSummary(weekStart, weekEnd)

                Then("본인 팀 × 주차 슬롯만 반환된다 (1팀 × 2주 = 2)") {
                    result.size shouldBe 2
                    result.all { it.teamId == 10L } shouldBe true
                }
            }

            When("실제 row가 일부 존재하면") {
                every { authorizationService.currentUser() } returns leaderUser
                every { authorizationService.requireAdminOrLeader(leaderUser) } just runs
                every { authorizationService.visibleTeamIds(leaderUser) } returns listOf(10L)
                every { teamRepository.findAllById(listOf(10L)) } returns listOf(team10)
                val row =
                    mockk<WeeklyReportSummaryRow>().apply {
                        every { teamId } returns 10L
                        every { this@apply.weekStart } returns LocalDate.of(2026, 5, 11)
                        every { createdAt } returns LocalDateTime.of(2026, 5, 11, 10, 0)
                        every { createdBy } returns "leader1"
                    }
                every { weeklyReportRepository.findSummaryRows(weekStart, weekEnd) } returns listOf(row)

                val result = service.findSummary(weekStart, weekEnd)

                Then("해당 슬롯만 exists=true") {
                    result.size shouldBe 2
                    result.first { it.weekStart == LocalDate.of(2026, 5, 11) }.exists shouldBe true
                    result.first { it.weekStart == LocalDate.of(2026, 5, 18) }.exists shouldBe false
                }

                Then("exists=true 슬롯은 audit 정보를 가진다") {
                    val existing = result.first { it.exists }
                    existing.createdBy shouldBe "leader1"
                    existing.createdAt shouldBe LocalDateTime.of(2026, 5, 11, 10, 0)
                }
            }

            When("권한 없는 사용자가 호출하면") {
                every { authorizationService.currentUser() } returns workerUser
                every { authorizationService.requireAdminOrLeader(workerUser) } throws
                    CustomException(ErrorCode.PERMISSION_DENIED)

                val exception =
                    shouldThrow<CustomException> {
                        service.findSummary(weekStart, weekEnd)
                    }

                Then("PERMISSION_DENIED 예외가 발생한다") {
                    exception.code shouldBe ErrorCode.PERMISSION_DENIED
                }
            }
        }
    })
