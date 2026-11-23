package com.pluxity.weekly.auth.authentication.security

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.KeySourceException
import com.nimbusds.jose.jwk.source.JWKSource
import com.nimbusds.jose.jwk.source.JWKSourceBuilder
import com.nimbusds.jose.proc.JWSVerificationKeySelector
import com.nimbusds.jose.proc.SecurityContext
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier
import com.nimbusds.jwt.proc.DefaultJWTProcessor
import com.pluxity.weekly.auth.properties.EntraProperties
import com.pluxity.weekly.core.constant.ErrorCode
import com.pluxity.weekly.core.exception.CustomException
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import java.net.URI

private val log = KotlinLogging.logger {}

/** Microsoft ID 토큰에서 꺼낸, 로그인에 필요한 사용자 정보 */
data class MicrosoftIdentity(
    /** Entra 사용자 고유 ID(oid). users.aad_object_id와 같은 값이다. */
    val objectId: String,
    val name: String?,
    val email: String?,
)

/**
 * 프론트(MSAL)가 받아 온 Microsoft ID 토큰을 검증한다.
 *
 * - 서명: 회사 테넌트의 공개키(JWKS)로 RS256 검증. 공개키는 Nimbus가 캐시하고 회전 시 자동 갱신한다.
 * - iss, tid: 회사 테넌트에서 발급된 토큰인지
 * - aud: 우리 앱(클라이언트 ID)을 위해 발급된 토큰인지
 * - exp, nbf: 유효 기간(기본 60초 허용 오차)
 */
@Component
class MicrosoftIdTokenVerifier internal constructor(
    private val entraProperties: EntraProperties,
    jwkSource: JWKSource<SecurityContext>,
) {
    @Autowired
    constructor(entraProperties: EntraProperties) : this(
        entraProperties,
        JWKSourceBuilder.create<SecurityContext>(URI.create(entraProperties.jwksUri).toURL()).build(),
    )

    private val processor =
        DefaultJWTProcessor<SecurityContext>().apply {
            jwsKeySelector = JWSVerificationKeySelector(JWSAlgorithm.RS256, jwkSource)
            jwtClaimsSetVerifier =
                DefaultJWTClaimsVerifier(
                    entraProperties.clientId,
                    JWTClaimsSet
                        .Builder()
                        .issuer(entraProperties.issuer)
                        .claim(CLAIM_TENANT_ID, entraProperties.tenantId)
                        .build(),
                    setOf("sub", "exp", "iat", CLAIM_OBJECT_ID),
                )
        }

    fun verify(idToken: String): MicrosoftIdentity {
        if (!entraProperties.enabled) {
            log.error { "Microsoft 로그인 설정 누락 - entra.client-id / entra.tenant-id 확인 필요" }
            throw CustomException(ErrorCode.MICROSOFT_SIGN_IN_UNAVAILABLE)
        }

        val claims =
            try {
                processor.process(idToken, null)
            } catch (e: KeySourceException) {
                log.error(e) { "Microsoft 공개키(JWKS) 조회 실패" }
                throw CustomException(ErrorCode.MICROSOFT_SIGN_IN_UNAVAILABLE)
            } catch (e: Exception) {
                log.warn { "Microsoft ID 토큰 검증 실패 - ${e.message}" }
                throw CustomException(ErrorCode.INVALID_MICROSOFT_TOKEN)
            }

        return MicrosoftIdentity(
            objectId = claims.getStringClaim(CLAIM_OBJECT_ID),
            name = claims.getStringClaim("name"),
            // email 클레임(회사 메일)이 우선, 없으면 로그인 ID(UPN)
            email = claims.getStringClaim("email") ?: claims.getStringClaim("preferred_username"),
        )
    }

    companion object {
        private const val CLAIM_OBJECT_ID = "oid"
        private const val CLAIM_TENANT_ID = "tid"
    }
}
