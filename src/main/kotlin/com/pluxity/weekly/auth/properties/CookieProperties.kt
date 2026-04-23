package com.pluxity.weekly.auth.properties

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "cookie")
data class CookieProperties(
    val sameSite: String = "Lax",
    val secure: Boolean = false,
)
