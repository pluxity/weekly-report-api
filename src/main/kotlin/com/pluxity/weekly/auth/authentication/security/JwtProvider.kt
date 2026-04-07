package com.pluxity.weekly.auth.authentication.security

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.MACSigner
import com.nimbusds.jose.crypto.MACVerifier
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import com.pluxity.weekly.auth.authentication.repository.RefreshTokenRepository
import com.pluxity.weekly.auth.properties.JwtProperties
import com.pluxity.weekly.core.constant.ErrorCode
import com.pluxity.weekly.core.exception.CustomException
import jakarta.servlet.http.HttpServletRequest
import org.springframework.stereotype.Service
import org.springframework.web.util.WebUtils
import java.util.Base64
import java.util.Date

@Service
class JwtProvider(
    private val refreshTokenRepository: RefreshTokenRepository,
    private val jwtProperties: JwtProperties,
) {
    fun extractUsername(
        token: String,
        isRefreshToken: Boolean = false,
    ): String {
        val claimsSet = extractAllClaims(token, isRefreshToken)
        return claimsSet.subject
    }

    private fun extractAllClaims(
        token: String,
        isRefreshToken: Boolean,
    ): JWTClaimsSet {
        val errorCode = if (isRefreshToken) ErrorCode.INVALID_REFRESH_TOKEN else ErrorCode.INVALID_ACCESS_TOKEN
        try {
            val signedJWT = SignedJWT.parse(token)
            val verifier = MACVerifier(getSecretKeyBytes(isRefreshToken))
            if (!signedJWT.verify(verifier)) {
                throw CustomException(errorCode)
            }
            val claims = signedJWT.jwtClaimsSet
            val expirationTime = claims.expirationTime
            if (expirationTime != null && expirationTime.before(Date())) {
                throw CustomException(
                    if (isRefreshToken) ErrorCode.EXPIRED_REFRESH_TOKEN else ErrorCode.EXPIRED_ACCESS_TOKEN,
                )
            }
            return claims
        } catch (e: CustomException) {
            throw e
        } catch (_: Exception) {
            throw CustomException(errorCode)
        }
    }

    fun generateAccessToken(
        username: String,
        extraClaims: Map<String, Any> = emptyMap(),
    ): String = buildToken(extraClaims, username, jwtProperties.accessToken.expiration, false)

    fun generateRefreshToken(username: String): String = buildToken(emptyMap(), username, jwtProperties.refreshToken.expiration, true)

    private fun buildToken(
        extraClaims: Map<String, Any>,
        username: String,
        expiration: Long,
        isRefreshToken: Boolean,
    ): String {
        val now = System.currentTimeMillis()
        val claimsBuilder =
            JWTClaimsSet
                .Builder()
                .subject(username)
                .issueTime(Date(now))
                .expirationTime(Date(now + expiration * 1000))

        extraClaims.forEach { (key, value) -> claimsBuilder.claim(key, value) }

        val signedJWT = SignedJWT(JWSHeader(JWSAlgorithm.HS256), claimsBuilder.build())
        signedJWT.sign(MACSigner(getSecretKeyBytes(isRefreshToken)))
        return signedJWT.serialize()
    }

    fun validateRefreshToken(token: String?) {
        if (token.isNullOrBlank()) throw CustomException(ErrorCode.INVALID_REFRESH_TOKEN)

        val refreshToken =
            refreshTokenRepository
                .findByToken(token)
                ?: throw CustomException(ErrorCode.INVALID_REFRESH_TOKEN)

        if (!refreshToken.isValidToken()) {
            throw CustomException(ErrorCode.INVALID_REFRESH_TOKEN)
        }

        extractAllClaims(refreshToken.token, true)
    }

    private fun getSecretKeyBytes(isRefreshToken: Boolean): ByteArray {
        val key = if (isRefreshToken) jwtProperties.refreshToken.secretKey else jwtProperties.accessToken.secretKey
        return Base64.getDecoder().decode(key)
    }

    fun getAccessTokenFromRequest(request: HttpServletRequest): String? = getJwtFromRequest(jwtProperties.accessToken.name, request)

    fun getJwtFromRequest(
        name: String,
        request: HttpServletRequest,
    ): String? = WebUtils.getCookie(request, name)?.value
}
