package com.pluxity.weekly.teams.config

import com.pluxity.weekly.auth.authentication.security.CustomUserDetails
import com.pluxity.weekly.auth.user.entity.User
import com.pluxity.weekly.auth.user.repository.UserRepository
import com.pluxity.weekly.teams.repository.TeamsAccountRepository
import com.pluxity.weekly.teams.service.TeamsApiClient
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.servlet.FilterChain
import jakarta.servlet.ReadListener
import jakarta.servlet.ServletInputStream
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletRequestWrapper
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import java.io.ByteArrayInputStream

private val log = KotlinLogging.logger {}

/**
 * Teams Activity body의 from.name으로 사용자를 조회하여 SecurityContext를 생성하는 필터.
 * /api/messages 요청에만 적용된다.
 */
@Component
class TeamsAuthFilter(
    private val objectMapper: ObjectMapper,
    private val userRepository: UserRepository,
    private val teamsAccountRepository: TeamsAccountRepository,
    private val teamsApiClient: TeamsApiClient,
) : OncePerRequestFilter() {
    override fun shouldNotFilter(request: HttpServletRequest): Boolean = !request.requestURI.endsWith("/teams/messages")

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val authHeader = request.getHeader("Authorization")
        if (authHeader.isNullOrBlank() || !teamsApiClient.verifyBotToken(authHeader)) {
            log.warn { "Teams JWT 검증 실패" }
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED)
            return
        }

        val body = request.inputStream.readAllBytes()
        val aadObjectId =
            try {
                val node: JsonNode = objectMapper.readTree(body)
                node.path("from").path("aadObjectId").asString()
            } catch (_: Exception) {
                null
            }

        if (!aadObjectId.isNullOrBlank()) {
            val user = resolveUser(aadObjectId)
            if (user != null) {
                setSecurityContext(user)
            } else {
                log.warn { "Teams 인증 실패 - 매칭되는 사용자 없음 (aadObjectId: $aadObjectId)" }
            }
        } else {
            log.warn { "Teams 인증 실패 - aadObjectId 없음" }
        }

        filterChain.doFilter(CachedBodyRequestWrapper(request, body), response)
    }

    private fun setSecurityContext(user: User) {
        val userDetails = CustomUserDetails(user)
        val auth = UsernamePasswordAuthenticationToken(userDetails, null, userDetails.authorities)
        SecurityContextHolder.getContext().authentication = auth
    }

    private fun resolveUser(aadObjectId: String): User? {
        // 1. TeamsAccount에서 매핑된 사용자 조회
        val account = teamsAccountRepository.findByAadObjectId(aadObjectId)
        if (account != null) {
            return userRepository.findWithGraphById(account.userId)
        }

        // 2. Graph API로 사용자 정보 조회 → username(이메일) 매칭
        val graphUser = teamsApiClient.getGraphUser(aadObjectId)
        if (graphUser == null) {
            log.warn { "Graph API 사용자 조회 실패 - aadObjectId: $aadObjectId" }
            return null
        }

        log.info { "Graph API 사용자 조회 성공 - mail: ${graphUser.mail}, displayName: ${graphUser.displayName}" }

        val email = graphUser.mail
        if (email.isNullOrBlank()) {
            log.warn { "Graph API 응답에 mail 없음 - aadObjectId: $aadObjectId" }
            return null
        }

        val user = userRepository.findByEmail(email)
        if (user == null) {
            log.warn { "매칭되는 사용자 없음 - email: $email" }
            return null
        }

        log.info { "Teams 사용자 매칭 성공 - aadObjectId: $aadObjectId → userId: ${user.requiredId}" }
        return user
    }

    /**
     * HTTP body를 byte[]로 캐싱하여 InputStream을 재사용 가능하게 하는 래퍼.
     *
     * HTTP body(InputStream)는 한 번 읽으면 소비되어 재사용이 불가능하다.
     * Teams 요청은 JWT가 아닌 body(Activity JSON)의  from.aadObjectId으로 사용자를 식별하므로
     * 필터에서 body를 먼저 읽어야 하고, Controller(@RequestBody)에서도 읽을 수 있도록 캐싱한다.
     */
    private class CachedBodyRequestWrapper(
        request: HttpServletRequest,
        private val cachedBody: ByteArray,
    ) : HttpServletRequestWrapper(request) {
        override fun getInputStream(): ServletInputStream {
            val byteArrayInputStream = ByteArrayInputStream(cachedBody)
            return object : ServletInputStream() {
                override fun read(): Int = byteArrayInputStream.read()

                override fun isFinished(): Boolean = byteArrayInputStream.available() == 0

                override fun isReady(): Boolean = true

                override fun setReadListener(listener: ReadListener?) {}
            }
        }
    }
}
