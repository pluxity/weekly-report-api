package com.pluxity.weekly.auth.config

import com.pluxity.weekly.auth.authentication.security.CustomUserDetails
import com.pluxity.weekly.auth.authentication.security.JwtAuthenticationFilter
import com.pluxity.weekly.auth.authentication.security.JwtProvider
import com.pluxity.weekly.auth.properties.JwtProperties
import com.pluxity.weekly.auth.properties.UserProperties
import com.pluxity.weekly.auth.user.repository.UserRepository
import com.pluxity.weekly.core.constant.ErrorCode
import com.pluxity.weekly.core.exception.CustomException
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.config.Customizer
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.annotation.web.configurers.SessionManagementConfigurer
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(JwtProperties::class, UserProperties::class)
class SecurityConfig(
    private val repository: UserRepository,
    private val jwtProvider: JwtProvider,
    private val securityPermitConfigurer: SecurityPermitConfigurer?,
) {
    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()

    @Bean
    fun defaultSecurityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .cors(Customizer.withDefaults())
            .authorizeHttpRequests { auth ->
                auth
                    .requestMatchers(
                        *buildPermitPaths(),
                    ).permitAll()
                    .requestMatchers(HttpMethod.GET)
                    .permitAll()
                    .requestMatchers("/auth/**")
                    .permitAll()
                    .anyRequest()
                    .authenticated()
            }.addFilterBefore(jwtAuthenticationFilter(), UsernamePasswordAuthenticationFilter::class.java)
            .also { http ->
                securityPermitConfigurer?.customFilters()?.forEach { registration ->
                    http.addFilterBefore(registration.filter, registration.beforeFilter)
                }
            }.sessionManagement { sessionManagement: SessionManagementConfigurer<HttpSecurity> ->
                sessionManagement.sessionCreationPolicy(
                    SessionCreationPolicy.STATELESS,
                )
            }

        return http.build()
    }

    @Bean
    fun authenticationManager(config: AuthenticationConfiguration): AuthenticationManager = config.authenticationManager

    @Bean
    fun userDetailsService(): UserDetailsService =
        UserDetailsService { username: String ->
            repository
                .findByUsername(username)
                ?.let { CustomUserDetails(it) }
                ?: throw CustomException(ErrorCode.NOT_FOUND_USER, username)
        }

    @Bean
    fun jwtAuthenticationFilter(): JwtAuthenticationFilter = JwtAuthenticationFilter(jwtProvider, userDetailsService())

    private fun buildPermitPaths(): Array<String> {
        val defaultPaths =
            listOf(
                "/actuator/**",
                "/health",
                "/info",
                "/prometheus",
                "/error",
                "/swagger-ui/**",
                "/swagger-ui.html",
                "/api-docs/**",
                "/swagger-config/**",
                "/docs/**",
            )
        val appPaths = securityPermitConfigurer?.permitPaths().orEmpty()
        return (defaultPaths + appPaths).toTypedArray()
    }

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val configuration = CorsConfiguration()
        configuration.allowedOriginPatterns = mutableListOf("http://localhost:*", "http://192.168.*.*:*", "https://*.pluxity.com")
        configuration.allowedMethods = mutableListOf("GET", "PATCH", "POST", "PUT", "DELETE", "OPTIONS")
        configuration.allowedHeaders = mutableListOf("*")
        configuration.allowCredentials = true
        configuration.maxAge = 3600L

        val source = UrlBasedCorsConfigurationSource()
        source.registerCorsConfiguration("/**", configuration)
        return source
    }
}
