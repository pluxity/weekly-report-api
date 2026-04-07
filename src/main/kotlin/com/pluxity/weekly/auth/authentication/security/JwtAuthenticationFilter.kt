package com.pluxity.weekly.auth.authentication.security

import com.pluxity.weekly.core.exception.CustomException
import com.pluxity.weekly.core.response.ErrorResponseBody
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource
import org.springframework.web.filter.OncePerRequestFilter
import tools.jackson.databind.json.JsonMapper

class JwtAuthenticationFilter(
    private val jwtProvider: JwtProvider,
    private val userDetailsService: UserDetailsService,
) : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        runCatching {
            if (authenticationRequired(request)) {
                authenticateRequest(request)
            }
        }.onFailure { exception ->
            if (exception is CustomException) {
                handleAuthenticationError(response, exception)
                return
            }
        }

        filterChain.doFilter(request, response)
    }

    private fun authenticateRequest(request: HttpServletRequest) {
        val token = jwtProvider.getAccessTokenFromRequest(request)

        if (token != null) {
            val username = jwtProvider.extractUsername(token)
            val userDetails = userDetailsService.loadUserByUsername(username)
            setAuthenticationContext(request, userDetails)
        }
    }

    private fun handleAuthenticationError(
        response: HttpServletResponse,
        exception: CustomException,
    ) {
        val objectMapper = JsonMapper()
        response.status = exception.code.getHttpStatus().value()
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        response.characterEncoding = "UTF-8"

        val errorResponse =
            ErrorResponseBody(
                status = exception.code.getHttpStatus(),
                message = exception.message,
                code =
                    exception.code
                        .getHttpStatus()
                        .value()
                        .toString(),
                error = exception.code.getCodeName(),
            )
        response.writer.write(objectMapper.writeValueAsString(errorResponse))
    }

    private fun setAuthenticationContext(
        request: HttpServletRequest,
        userDetails: UserDetails,
    ) {
        val authToken = UsernamePasswordAuthenticationToken(userDetails, null, userDetails.authorities)
        authToken.details = WebAuthenticationDetailsSource().buildDetails(request)
        SecurityContextHolder.getContext().authentication = authToken
    }

    private fun authenticationRequired(request: HttpServletRequest): Boolean {
        val path = request.requestURI.substring(request.contextPath.length)
        return WhiteListPath.entries.none { whiteListPath ->
            path.startsWith("/${whiteListPath.path}")
        }
    }
}
