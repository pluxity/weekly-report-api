package com.pluxity.weekly.auth.properties

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Microsoft(Entra ID) 회사 계정 로그인 설정.
 * 프론트(MSAL)와 같은 앱 등록의 클라이언트 ID, 회사 테넌트 ID를 쓴다. 둘 다 비밀값이 아니다.
 */
@ConfigurationProperties(prefix = "entra")
data class EntraProperties(
    val clientId: String = "",
    val tenantId: String = "",
) {
    val enabled: Boolean
        get() = clientId.isNotBlank() && tenantId.isNotBlank()

    /** 테넌트 전용 v2.0 issuer. 다른 테넌트에서 발급된 토큰을 걸러낸다. */
    val issuer: String
        get() = "https://login.microsoftonline.com/$tenantId/v2.0"

    val jwksUri: String
        get() = "https://login.microsoftonline.com/$tenantId/discovery/v2.0/keys"
}
