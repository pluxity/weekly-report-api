package com.pluxity.weekly.teams.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "teams")
data class TeamsProperties(
    val appId: String = "",
    val appPassword: String = "",
    val tenantId: String = "",
)
