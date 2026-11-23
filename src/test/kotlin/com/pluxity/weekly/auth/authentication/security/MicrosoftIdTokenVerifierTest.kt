package com.pluxity.weekly.auth.authentication.security

import com.nimbusds.jose.JOSEObjectType
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
import com.nimbusds.jose.jwk.source.ImmutableJWKSet
import com.nimbusds.jose.proc.SecurityContext
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import com.pluxity.weekly.auth.properties.EntraProperties
import com.pluxity.weekly.core.constant.ErrorCode
import com.pluxity.weekly.core.exception.CustomException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import java.util.Date

private const val CLIENT_ID = "11111111-1111-1111-1111-111111111111"
private const val TENANT_ID = "22222222-2222-2222-2222-222222222222"
private const val OTHER_TENANT_ID = "33333333-3333-3333-3333-333333333333"

class MicrosoftIdTokenVerifierTest :
    BehaviorSpec({

        val signingKey: RSAKey = RSAKeyGenerator(2048).keyID("kid-1").generate()
        val properties = EntraProperties(clientId = CLIENT_ID, tenantId = TENANT_ID)
        val verifier =
            MicrosoftIdTokenVerifier(properties, ImmutableJWKSet<SecurityContext>(JWKSet(signingKey.toPublicJWK())))

        fun claims(
            tenantId: String = TENANT_ID,
            audience: String = CLIENT_ID,
            expiresAt: Date = Date(System.currentTimeMillis() + 60 * 60 * 1000),
            email: String? = "Hong@pluxity.com",
        ): JWTClaimsSet =
            JWTClaimsSet
                .Builder()
                .issuer("https://login.microsoftonline.com/$tenantId/v2.0")
                .audience(audience)
                .subject("pairwise-sub")
                .issueTime(Date())
                .expirationTime(expiresAt)
                .claim("tid", tenantId)
                .claim("oid", "aad-object-id")
                .claim("name", "홍길동")
                .claim("preferred_username", "hong@pluxity.com")
                .apply { if (email != null) claim("email", email) }
                .build()

        fun sign(
            claims: JWTClaimsSet,
            key: RSAKey = signingKey,
        ): String =
            SignedJWT(
                JWSHeader
                    .Builder(JWSAlgorithm.RS256)
                    .keyID(key.keyID)
                    .type(JOSEObjectType.JWT)
                    .build(),
                claims,
            ).apply { sign(RSASSASigner(key)) }
                .serialize()

        fun shouldFailWith(
            code: ErrorCode,
            block: () -> Unit,
        ) {
            shouldThrow<CustomException> { block() }.code shouldBe code
        }

        Given("회사 테넌트에서 우리 앱용으로 발급된 유효한 토큰") {
            When("검증하면") {
                val identity = verifier.verify(sign(claims()))

                Then("oid, 이름, email 클레임을 돌려준다") {
                    identity.objectId shouldBe "aad-object-id"
                    identity.name shouldBe "홍길동"
                    identity.email shouldBe "Hong@pluxity.com"
                }
            }

            When("email 클레임이 없으면") {
                val identity = verifier.verify(sign(claims(email = null)))

                Then("preferred_username(UPN)을 email로 쓴다") {
                    identity.email shouldBe "hong@pluxity.com"
                }
            }
        }

        Given("검증에 실패해야 하는 토큰") {
            Then("다른 테넌트에서 발급된 토큰은 거절") {
                shouldFailWith(ErrorCode.INVALID_MICROSOFT_TOKEN) {
                    verifier.verify(sign(claims(tenantId = OTHER_TENANT_ID)))
                }
            }

            Then("다른 앱용(aud) 토큰은 거절") {
                shouldFailWith(ErrorCode.INVALID_MICROSOFT_TOKEN) {
                    verifier.verify(sign(claims(audience = "another-app")))
                }
            }

            Then("만료된 토큰은 거절") {
                shouldFailWith(ErrorCode.INVALID_MICROSOFT_TOKEN) {
                    verifier.verify(sign(claims(expiresAt = Date(System.currentTimeMillis() - 10 * 60 * 1000))))
                }
            }

            Then("다른 키로 서명된(위조) 토큰은 거절") {
                val forgedKey = RSAKeyGenerator(2048).keyID("kid-1").generate()
                shouldFailWith(ErrorCode.INVALID_MICROSOFT_TOKEN) {
                    verifier.verify(sign(claims(), key = forgedKey))
                }
            }

            Then("JWT 형식이 아니면 거절") {
                shouldFailWith(ErrorCode.INVALID_MICROSOFT_TOKEN) {
                    verifier.verify("not-a-jwt")
                }
            }
        }

        Given("entra 설정이 비어 있으면") {
            val unconfigured =
                MicrosoftIdTokenVerifier(EntraProperties(), ImmutableJWKSet<SecurityContext>(JWKSet(signingKey.toPublicJWK())))

            Then("Microsoft 로그인을 사용할 수 없다고 응답") {
                shouldFailWith(ErrorCode.MICROSOFT_SIGN_IN_UNAVAILABLE) {
                    unconfigured.verify(sign(claims()))
                }
            }
        }
    })
