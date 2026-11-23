package com.pluxity.weekly.auth.authentication.service

import com.pluxity.weekly.auth.authentication.dto.MicrosoftSignInRequest
import com.pluxity.weekly.auth.authentication.entity.RefreshToken
import com.pluxity.weekly.auth.authentication.repository.RefreshTokenRepository
import com.pluxity.weekly.auth.authentication.security.JwtProvider
import com.pluxity.weekly.auth.authentication.security.MicrosoftIdTokenVerifier
import com.pluxity.weekly.auth.authentication.security.MicrosoftIdentity
import com.pluxity.weekly.auth.properties.CookieProperties
import com.pluxity.weekly.auth.properties.JwtProperties
import com.pluxity.weekly.auth.properties.TokenProperties
import com.pluxity.weekly.auth.user.repository.UserRepository
import com.pluxity.weekly.auth.user.service.UserService
import com.pluxity.weekly.core.constant.ErrorCode
import com.pluxity.weekly.core.exception.CustomException
import com.pluxity.weekly.test.entity.dummyUser
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.crypto.password.PasswordEncoder

class AuthenticationServiceMicrosoftTest :
    BehaviorSpec({

        val refreshTokenRepository: RefreshTokenRepository = mockk(relaxed = true)
        val userRepository: UserRepository = mockk()
        val jwtProvider: JwtProvider = mockk()
        val microsoftIdTokenVerifier: MicrosoftIdTokenVerifier = mockk()
        val userService: UserService = mockk()

        val service =
            AuthenticationService(
                refreshTokenRepository = refreshTokenRepository,
                userRepository = userRepository,
                jwtProvider = jwtProvider,
                authenticationManager = mockk<AuthenticationManager>(),
                passwordEncoder = mockk<PasswordEncoder>(),
                jwtProperties =
                    JwtProperties(
                        accessToken = TokenProperties(name = "AccessToken", secret = "access", expiration = 3600),
                        refreshToken = TokenProperties(name = "RefreshToken", secret = "refresh", expiration = 86400),
                    ),
                cookieProperties = CookieProperties(),
                microsoftIdTokenVerifier = microsoftIdTokenVerifier,
                userService = userService,
            )

        val identity = MicrosoftIdentity(objectId = "aad-1", name = "홍길동", email = "hong@pluxity.com")
        val signInRequest = MicrosoftSignInRequest(idToken = "id-token")

        beforeEach {
            clearMocks(userRepository, userService, refreshTokenRepository, answers = false)
            every { microsoftIdTokenVerifier.verify("id-token") } returns identity
            every { jwtProvider.generateAccessToken(any(), any()) } returns "access-jwt"
            every { jwtProvider.generateRefreshToken(any()) } returns "refresh-jwt"
            every { refreshTokenRepository.save(any<RefreshToken>()) } answers { firstArg() }
        }

        fun newHttp(): Pair<HttpServletRequest, HttpServletResponse> =
            mockk<HttpServletRequest> { every { contextPath } returns "" } to mockk(relaxed = true)

        Given("Microsoft 로그인") {
            When("aadObjectId로 이미 연결된 사용자면") {
                every { userRepository.findByAadObjectId("aad-1") } returns dummyUser(username = "hong")

                Then("자동 가입 없이 바로 그 사용자로 쿠키를 발급한다") {
                    val (request, response) = newHttp()
                    service.signInWithMicrosoft(signInRequest, request, response)

                    verify(exactly = 0) { userService.provisionFromMicrosoft(any(), any(), any()) }
                    verify { jwtProvider.generateAccessToken("hong", any()) }
                    verify { response.addHeader(HttpHeaders.SET_COOKIE, match { it.startsWith("AccessToken=access-jwt") }) }
                    verify { response.addHeader(HttpHeaders.SET_COOKIE, match { it.startsWith("RefreshToken=refresh-jwt") }) }
                }
            }

            When("연결된 사용자가 없으면") {
                every { userRepository.findByAadObjectId("aad-1") } returns null
                every { userService.provisionFromMicrosoft("aad-1", "홍길동", "hong@pluxity.com") } returns
                    dummyUser(username = "hong", email = "hong@pluxity.com")

                Then("email 매칭/자동 가입한 사용자로 쿠키를 발급한다") {
                    val (request, response) = newHttp()
                    service.signInWithMicrosoft(signInRequest, request, response)

                    verify(exactly = 1) { userService.provisionFromMicrosoft("aad-1", "홍길동", "hong@pluxity.com") }
                    verify { jwtProvider.generateAccessToken("hong", any()) }
                }
            }

            When("퇴사(삭제) 처리된 사용자면") {
                every { userRepository.findByAadObjectId("aad-1") } returns null
                every { userService.provisionFromMicrosoft(any(), any(), any()) } throws
                    CustomException(ErrorCode.RETIRED_USER)

                Then("RETIRED_USER로 거절하고 쿠키를 발급하지 않는다") {
                    val (request, response) = newHttp()
                    shouldThrow<CustomException> {
                        service.signInWithMicrosoft(signInRequest, request, response)
                    }.code shouldBe ErrorCode.RETIRED_USER

                    verify(exactly = 0) { response.addHeader(HttpHeaders.SET_COOKIE, any()) }
                }
            }

            When("회사 도메인이 아니라 등록할 수 없으면") {
                every { userRepository.findByAadObjectId("aad-1") } returns null
                every { userService.provisionFromMicrosoft(any(), any(), any()) } returns null

                Then("403으로 거절하고 쿠키를 발급하지 않는다") {
                    val (request, response) = newHttp()
                    shouldThrow<CustomException> {
                        service.signInWithMicrosoft(signInRequest, request, response)
                    }.code shouldBe ErrorCode.MICROSOFT_SIGN_IN_NOT_ALLOWED

                    verify(exactly = 0) { response.addHeader(HttpHeaders.SET_COOKIE, any()) }
                }
            }
        }
    })
