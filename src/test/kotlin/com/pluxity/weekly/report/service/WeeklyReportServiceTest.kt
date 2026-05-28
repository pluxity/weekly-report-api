package com.pluxity.weekly.report.service

import com.pluxity.weekly.auth.authorization.AuthorizationService
import com.pluxity.weekly.core.constant.ErrorCode
import com.pluxity.weekly.core.exception.CustomException
import com.pluxity.weekly.report.dto.FormattedReport
import com.pluxity.weekly.report.dto.ReportItem
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
import io.mockk.verify
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

        Given("findForChat") {
            every { authorizationService.currentUser() } returns leaderUser

            When("리더 팀에 해당 주차 보고가 있으면") {
                every { teamRepository.findByLeaderId(2L) } returns listOf(team10)
                every { weeklyReportRepository.findByTeamIdAndWeekStart(10L, any()) } returns
                    dummyWeeklyReport(id = 1L, team = team10)

                val result = service.findForChat("this")

                Then("해당 팀 보고 단건이 반환된다") {
                    result?.teamId shouldBe 10L
                }
            }

            When("해당 주차 보고가 없으면") {
                every { teamRepository.findByLeaderId(2L) } returns listOf(team10)
                every { weeklyReportRepository.findByTeamIdAndWeekStart(10L, any()) } returns null

                val result = service.findForChat("last")

                Then("null이 반환된다") {
                    result shouldBe null
                }
            }

            When("leader인 팀이 없으면") {
                every { teamRepository.findByLeaderId(2L) } returns emptyList()

                val result = service.findForChat("this")

                Then("null이 반환된다") {
                    result shouldBe null
                }
            }

            When("다중 팀 리더면 첫 팀 보고를 사용한다") {
                val team30 = dummyTeam(id = 30L, name = "QA팀", leaderId = 2L)
                every { teamRepository.findByLeaderId(2L) } returns listOf(team10, team30)
                every { weeklyReportRepository.findByTeamIdAndWeekStart(10L, any()) } returns
                    dummyWeeklyReport(id = 1L, team = team10)

                val result = service.findForChat("this")

                Then("첫 팀(team10) 보고가 반환된다") {
                    result?.teamId shouldBe 10L
                }
            }
        }

        Given("findPrevWeekNextItems") {
            // 이번주 월요일 5/25 기준 → 지난주 월요일 5/18 조회
            val prevMonday = LocalDate.of(2026, 5, 18)

            When("지난주 보고가 있으면 그 보고의 nextWeek 항목을 반환한다") {
                val prev =
                    dummyWeeklyReport(
                        id = 9L,
                        team = team10,
                        weekStart = prevMonday,
                        formatted =
                            FormattedReport(
                                nextWeek = listOf(ReportItem("홍길동", "PMS", "API 설계", null, null)),
                            ),
                    )
                every { weeklyReportRepository.findByTeamIdAndWeekStart(10L, prevMonday) } returns prev

                val result = service.findPrevWeekNextItems(team10, LocalDate.of(2026, 5, 25))

                Then("nextWeek 항목이 반환된다") {
                    result.size shouldBe 1
                    result[0].text shouldBe "API 설계"
                }
            }

            When("지난주 보고가 없으면 빈 리스트") {
                every { weeklyReportRepository.findByTeamIdAndWeekStart(10L, prevMonday) } returns null

                val result = service.findPrevWeekNextItems(team10, LocalDate.of(2026, 5, 25))

                Then("빈 리스트가 반환된다") {
                    result shouldBe emptyList()
                }
            }

            When("비월요일 currWeekStart를 줘도 그 주 월요일 기준 지난주를 조회한다") {
                // 5/27(수) → 이번주 월 5/25 → 지난주 월 5/18
                every { weeklyReportRepository.findByTeamIdAndWeekStart(10L, prevMonday) } returns null

                val result = service.findPrevWeekNextItems(team10, LocalDate.of(2026, 5, 27))

                Then("5/18로 조회되어 빈 리스트") {
                    result shouldBe emptyList()
                    verify { weeklyReportRepository.findByTeamIdAndWeekStart(10L, prevMonday) }
                }
            }
        }

        Given("delete") {
            val weekStart = LocalDate.of(2026, 5, 25)

            When("해당 (팀, 주차) 보고가 존재하면") {
                val report = dummyWeeklyReport(id = 5L, team = team10, weekStart = weekStart)
                every { weeklyReportRepository.findByTeamIdAndWeekStart(10L, weekStart) } returns report
                every { weeklyReportRepository.delete(report) } just runs

                val result = service.delete(team10, weekStart)

                Then("삭제하고 id를 반환한다") {
                    result shouldBe 5L
                    verify(exactly = 1) { weeklyReportRepository.delete(report) }
                }
            }

            When("해당 (팀, 주차) 보고가 없으면") {
                every { weeklyReportRepository.findByTeamIdAndWeekStart(10L, weekStart) } returns null

                val result = service.delete(team10, weekStart)

                Then("null을 반환하고 삭제를 호출하지 않는다") {
                    result shouldBe null
                    verify(exactly = 0) { weeklyReportRepository.delete(any()) }
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

            When("비월요일 날짜를 입력하면") {
                // 5/13 (수) → 5/11 (월), 5/20 (수) → 5/18 (월) 로 정규화돼야 함
                val inputStart = LocalDate.of(2026, 5, 13)
                val inputEnd = LocalDate.of(2026, 5, 20)
                val expectedStart = LocalDate.of(2026, 5, 11)
                val expectedEnd = LocalDate.of(2026, 5, 18)

                every { authorizationService.currentUser() } returns adminUser
                every { authorizationService.requireAdminOrLeader(adminUser) } just runs
                every { authorizationService.visibleTeamIds(adminUser) } returns null
                every { teamRepository.findAll() } returns listOf(team10)
                every { weeklyReportRepository.findSummaryRows(expectedStart, expectedEnd) } returns emptyList()

                val result = service.findSummary(inputStart, inputEnd)

                Then("월요일로 정규화된 주차 슬롯이 반환된다") {
                    result.size shouldBe 2
                    result.map { it.weekStart } shouldBe listOf(expectedStart, expectedEnd)
                }
            }

            When("시작일이 종료일보다 늦으면") {
                val laterStart = LocalDate.of(2026, 6, 1)
                val earlierEnd = LocalDate.of(2026, 5, 18)

                every { authorizationService.currentUser() } returns adminUser
                every { authorizationService.requireAdminOrLeader(adminUser) } just runs

                val result = service.findSummary(laterStart, earlierEnd)

                Then("빈 응답이 반환된다") {
                    result shouldBe emptyList()
                }
            }
        }
    })
