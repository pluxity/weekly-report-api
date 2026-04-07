package com.pluxity.weekly.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "pluxity.logbook")
data class LogbookProperties(
    val excludePaths: List<String> = emptyList(),
)
