package com.pluxity.weekly.auth.authentication.service

import com.pluxity.weekly.auth.authentication.dto.SignInRequest
import com.pluxity.weekly.auth.authentication.dto.SignUpRequest
import com.pluxity.weekly.auth.authentication.entity.RefreshToken
import com.pluxity.weekly.auth.authentication.repository.RefreshTokenRepository
import com.pluxity.weekly.auth.authentication.security.JwtProvider
import com.pluxity.weekly.auth.properties.CookieProperties
import com.pluxity.weekly.auth.properties.JwtProperties
import com.pluxity.weekly.auth.user.entity.User
import com.pluxity.weekly.auth.user.repository.UserRepository
import com.pluxity.weekly.core.constant.ErrorCode
import com.pluxity.weekly.core.exception.CustomException
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseCookie
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.util.WebUtils

@Service
class AuthenticationService(
    private val refreshTokenRepository: RefreshTokenRepository,
    private val userRepository: UserRepository,
    private val jwtProvider: JwtProvider,
    private val authenticationManager: AuthenticationManager,
    private val passwordEncoder: PasswordEncoder,
    private val jwtProperties: JwtProperties,
    private val cookieProperties: CookieProperties,
) {
    @Transactional
    fun signUp(signUpRequest: SignUpRequest): Long {
        validateUserDoesNotExist(signUpRequest.username)

        val user =
            User(
                username = signUpRequest.username,
                password = requireNotNull(passwordEncoder.encode(signUpRequest.password)),
                name = signUpRequest.name,
                code = signUpRequest.code,
            )

        return userRepository.save(user).requiredId
    }

    @Transactional
    fun signIn(
        signInRequest: SignInRequest,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ) {
        authenticateUser(signInRequest)
        val user = findUserByUsername(signInRequest.username)
        publishToken(user, request, response)
    }

    @Transactional
    fun signOut(
        request: HttpServletRequest,
        response: HttpServletResponse,
    ) {
        val refreshToken = jwtProvider.getJwtFromRequest(jwtProperties.refreshToken.name, request)
        refreshToken?.let {
            refreshTokenRepository
                .findByToken(it)
                ?.let { token -> refreshTokenRepository.delete(token) }
            clearAllCookies(request, response)
        }
    }

    @Transactional
    fun refreshToken(
        request: HttpServletRequest,
        response: HttpServletResponse,
    ) {
        val refreshToken =
            jwtProvider.getJwtFromRequest(jwtProperties.refreshToken.name, request)
                ?: throw CustomException(ErrorCode.INVALID_REFRESH_TOKEN)

        jwtProvider.validateRefreshToken(refreshToken)

        val username = jwtProvider.extractUsername(refreshToken, true)
        val user = findUserByUsername(username)
        publishToken(user, request, response)
    }

    private fun validateUserDoesNotExist(username: String) {
        if (userRepository.findByUsername(username) != null) {
            throw CustomException(ErrorCode.DUPLICATE_USERNAME, "사용자가 이미 존재합니다: $username")
        }
    }

    private fun authenticateUser(signInRequest: SignInRequest) {
        runCatching {
            authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken(signInRequest.username, signInRequest.password),
            )
        }.getOrElse {
            throw CustomException(ErrorCode.INVALID_ID_OR_PASSWORD)
        }
    }

    private fun findUserByUsername(username: String): User =
        userRepository
            .findByUsername(username)
            ?: throw CustomException(ErrorCode.NOT_FOUND_USER, username)

    private fun clearAllCookies(
        request: HttpServletRequest,
        response: HttpServletResponse,
    ) {
        deleteAuthCookie(jwtProperties.accessToken.name, request.contextPath, request, response)
        deleteAuthCookie(jwtProperties.refreshToken.name, "${request.contextPath}/", request, response)
        deleteExpiryCookie(request, response)
    }

    private fun publishToken(
        user: User,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ) {
        val newAccessToken = jwtProvider.generateAccessToken(user.username)
        val newRefreshToken = jwtProvider.generateRefreshToken(user.username)

        createAuthCookie(
            jwtProperties.accessToken.name,
            newAccessToken,
            jwtProperties.accessToken.expiration,
            request.contextPath,
            response,
        )
        createAuthCookie(
            jwtProperties.refreshToken.name,
            newRefreshToken,
            jwtProperties.refreshToken.expiration,
            "${request.contextPath}/",
            response,
        )
        createExpiryCookie(request, response)

        refreshTokenRepository.save(
            RefreshToken(
                user.username,
                newRefreshToken,
                jwtProperties.refreshToken.expiration.toInt(),
            ),
        )
    }

    private fun createAuthCookie(
        name: String,
        value: String,
        expiry: Long,
        path: String,
        response: HttpServletResponse,
    ) {
        val cookie =
            ResponseCookie
                .from(name, value)
                .secure(cookieProperties.secure)
                .httpOnly(true)
                .sameSite(cookieProperties.sameSite)
                .maxAge(expiry)
                .path(path.takeIf { it.isNotBlank() } ?: "/")
                .build()
                .toString()

        response.addHeader(HttpHeaders.SET_COOKIE, cookie)
    }

    private fun deleteAuthCookie(
        name: String,
        path: String,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ) {
        WebUtils.getCookie(request, name)?.apply {
            value = null
            maxAge = 0
            this.path = path
            response.addCookie(this)
        }
    }

    private fun createExpiryCookie(
        request: HttpServletRequest,
        response: HttpServletResponse,
    ) {
        val expiryTimeMillis = System.currentTimeMillis() + (jwtProperties.refreshToken.expiration * 1000L)
        val path = request.contextPath.takeIf { it.isNotEmpty() } ?: "/"

        val cookie =
            ResponseCookie
                .from("expiry", expiryTimeMillis.toString())
                .secure(cookieProperties.secure)
                .sameSite(cookieProperties.sameSite)
                .path(path)
                .build()
                .toString()

        response.addHeader(HttpHeaders.SET_COOKIE, cookie)
    }

    private fun deleteExpiryCookie(
        request: HttpServletRequest,
        response: HttpServletResponse,
    ) {
        WebUtils.getCookie(request, "expiry")?.apply {
            val path = request.contextPath.takeIf { it.isNotEmpty() } ?: "/"
            maxAge = 0
            this.path = path
            response.addCookie(this)
        }
    }
}
