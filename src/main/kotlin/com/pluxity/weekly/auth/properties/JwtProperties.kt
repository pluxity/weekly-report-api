package com.pluxity.weekly.auth.properties

import org.springframework.boot.context.properties.ConfigurationProperties
import java.util.Base64

@ConfigurationProperties(prefix = "jwt")
data class JwtProperties(
    val accessToken: TokenProperties,
    val refreshToken: TokenProperties,
)

data class TokenProperties(
    val name: String,
    val secret: String,
    val expiration: Long,
) {
    val secretKey: String = Base64.getEncoder().encodeToString(secret.toByteArray())
}
