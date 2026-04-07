package com.pluxity.weekly.auth.config

import com.pluxity.weekly.auth.authentication.security.JwtAuthenticationFilter
import com.pluxity.weekly.teams.config.TeamsAuthFilter
import org.springframework.context.annotation.Configuration

@Configuration
class WeeklyReportPermitConfigurer(
    private val teamsAuthFilter: TeamsAuthFilter,
) : SecurityPermitConfigurer {
    override fun permitPaths(): List<String> = listOf("/teams/messages")

    override fun customFilters(): List<SecurityFilterRegistration> =
        listOf(
            SecurityFilterRegistration(
                filter = teamsAuthFilter,
                beforeFilter = JwtAuthenticationFilter::class.java,
            ),
        )
}
