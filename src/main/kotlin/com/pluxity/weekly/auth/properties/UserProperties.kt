package com.pluxity.weekly.auth.properties

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "user")
data class UserProperties(
    val initPassword: String,
    val allowedEmailDomains: List<String> = emptyList(),
)
