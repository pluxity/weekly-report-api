package com.pluxity.weekly.auth.user.service

import com.pluxity.weekly.auth.authentication.repository.RefreshTokenRepository
import com.pluxity.weekly.auth.properties.UserProperties
import com.pluxity.weekly.auth.user.entity.RoleType
import com.pluxity.weekly.auth.user.entity.User
import com.pluxity.weekly.auth.user.repository.RoleRepository
import com.pluxity.weekly.auth.user.repository.UserRepository
import com.pluxity.weekly.auth.user.repository.UserRoleRepository
import com.pluxity.weekly.core.constant.ErrorCode
import com.pluxity.weekly.core.exception.CustomException
import com.pluxity.weekly.project.repository.ProjectRepository
import com.pluxity.weekly.team.repository.TeamRepository
import com.pluxity.weekly.test.entity.dummyRole
import com.pluxity.weekly.test.entity.dummyUser
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.data.repository.findByIdOrNull
import org.springframework.security.crypto.password.PasswordEncoder

class UserServiceTest :
    BehaviorSpec({

        val userRepository: UserRepository = mockk(relaxed = true)
        val roleRepository: RoleRepository = mockk(relaxed = true)
        val passwordEncoder: PasswordEncoder = mockk()
        val refreshTokenRepository: RefreshTokenRepository = mockk(relaxed = true)
        val userRoleRepository: UserRoleRepository = mockk(relaxed = true)
        val userProperties: UserProperties = mockk()
        val projectRepository: ProjectRepository = mockk()
        val teamRepository: TeamRepository = mockk()

        val service =
            UserService(
                userRepository,
                roleRepository,
                passwordEncoder,
                refreshTokenRepository,
                userRoleRepository,
                userProperties,
                projectRepository,
                teamRepository,
            )

        every { passwordEncoder.encode(any()) } answers { "encoded:${firstArg<String>()}" }

        Given("UserService.delete - 인수인계 강제") {
            val targetUser = dummyUser(id = 10L, username = "u@pluxity.com", email = "u@pluxity.com")
            every { userRepository.findWithGraphById(10L) } returns targetUser

            When("사용자가 어떤 프로젝트의 PM이면") {
                every { projectRepository.existsByPmId(10L) } returns true
                every { teamRepository.existsByLeaderId(10L) } returns false

                Then("USER_HAS_ACTIVE_RESPONSIBILITY 예외") {
                    val ex = shouldThrow<CustomException> { service.delete(10L) }
                    ex.code shouldBe ErrorCode.USER_HAS_ACTIVE_RESPONSIBILITY
                    verify(exactly = 0) { userRepository.delete(any<User>()) }
                }
            }

            When("사용자가 어떤 팀의 리더이면") {
                every { projectRepository.existsByPmId(10L) } returns false
                every { teamRepository.existsByLeaderId(10L) } returns true

                Then("USER_HAS_ACTIVE_RESPONSIBILITY 예외") {
                    shouldThrow<CustomException> { service.delete(10L) }
                        .code shouldBe ErrorCode.USER_HAS_ACTIVE_RESPONSIBILITY
                }
            }

            When("PM/리더 자리가 모두 없으면") {
                every { projectRepository.existsByPmId(10L) } returns false
                every { teamRepository.existsByLeaderId(10L) } returns false
                every { refreshTokenRepository.findByIdOrNull(any<String>()) } returns null

                Then("정상 soft delete") {
                    service.delete(10L)
                    verify(exactly = 1) { userRepository.delete(targetUser) }
                }
            }
        }

        Given("UserService.provisionFromTeams") {
            every { userProperties.allowedEmailDomains } returns listOf("pluxity.com")
            every { userProperties.initPassword } returns "pluxity123!@#"
            every { roleRepository.findByName(RoleType.USER.roleName) } returns
                dummyRole(id = 2L, name = RoleType.USER.roleName)

            When("허용되지 않은 도메인이면") {
                Then("null 반환, 저장 없음") {
                    val result =
                        service.provisionFromTeams(
                            aadObjectId = "aad-1",
                            displayName = "외부사용자",
                            email = "x@external.com",
                            teamsServiceUrl = "https://teams",
                            teamsConversationId = "conv-1",
                        )
                    result.shouldBeNull()
                    verify(exactly = 0) { userRepository.save(any<User>()) }
                }
            }

            When("email 또는 displayName이 누락되면") {
                Then("null 반환") {
                    service
                        .provisionFromTeams("aad-1", displayName = null, email = "a@pluxity.com", null, null)
                        .shouldBeNull()
                    service
                        .provisionFromTeams("aad-1", displayName = "n", email = null, null, null)
                        .shouldBeNull()
                }
            }

            When("aadObjectId 기준 기존 사용자가 soft-deleted 상태이면") {
                val existing = dummyUser(id = 30L, username = "a@pluxity.com", email = "a@pluxity.com")
                existing.aadObjectId = "aad-30"
                every { userRepository.findByAadObjectIdIncludingDeleted("aad-30") } returns existing
                every { userRepository.restoreById(30L) } returns 1
                every { userRepository.findByIdOrNull(30L) } returns existing

                Then("restoreById 호출 + 사용자 반환") {
                    val result =
                        service.provisionFromTeams(
                            aadObjectId = "aad-30",
                            displayName = "복원이름",
                            email = "a@pluxity.com",
                            teamsServiceUrl = "https://teams",
                            teamsConversationId = "conv-30",
                        )
                    result.shouldNotBeNull()
                    result.name shouldBe "복원이름"
                    verify(exactly = 1) { userRepository.restoreById(30L) }
                }
            }

            When("기존 사용자가 없고 email도 매칭 안 되면") {
                every { userRepository.findByAadObjectIdIncludingDeleted("aad-new") } returns null
                every { userRepository.findByEmail("new@pluxity.com") } returns null
                val saveSlot = mutableListOf<User>()
                every { userRepository.save(capture(saveSlot)) } answers { firstArg() }

                Then("신규 가입 + USER role 부여") {
                    val result =
                        service.provisionFromTeams(
                            aadObjectId = "aad-new",
                            displayName = "신규",
                            email = "new@pluxity.com",
                            teamsServiceUrl = "https://teams",
                            teamsConversationId = "conv-new",
                        )
                    result.shouldNotBeNull()
                    val saved = saveSlot.first()
                    saved.username shouldBe "new@pluxity.com"
                    saved.email shouldBe "new@pluxity.com"
                    saved.aadObjectId shouldBe "aad-new"
                    saved.userRoles.any { it.role.name == RoleType.USER.roleName } shouldBe true
                }
            }
        }
    })
