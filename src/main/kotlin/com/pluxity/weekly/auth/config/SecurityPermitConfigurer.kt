package com.pluxity.weekly.auth.config

import jakarta.servlet.Filter

/**
 * 앱별 Security 설정을 커스터마이징하기 위한 인터페이스.
 * 각 앱에서 @Configuration으로 구현하면 CommonSecurityConfig에서 자동으로 반영됩니다.
 */
interface SecurityPermitConfigurer {
    /** 추가로 permitAll 처리할 경로 목록 */
    fun permitPaths(): List<String> = emptyList()

    /** 추가로 등록할 커스텀 필터 목록 */
    fun customFilters(): List<SecurityFilterRegistration> = emptyList()
}

/**
 * Security 필터 체인에 등록할 커스텀 필터 정보.
 *
 * @property filter 등록할 필터 인스턴스
 * @property beforeFilter 이 필터 앞에 삽입할 대상 필터 클래스 (addFilterBefore 기준)
 */
data class SecurityFilterRegistration(
    val filter: Filter,
    val beforeFilter: Class<out Filter>,
)
