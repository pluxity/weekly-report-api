package com.pluxity.weekly.teams.entity

import com.pluxity.weekly.core.entity.IdentityIdEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table

/**
 * Microsoft Teams 사용자와 내부 시스템 사용자 간의 매핑 정보.
 *
 * Teams 앱 설치 시 생성되며, 이후 Teams 메시지 수신 시 사용자 식별과
 * 봇이 사용자에게 DM을 보낼 때 사용된다.
 */
@Entity
@Table(name = "teams_accounts")
class TeamsAccount(
    /** Microsoft Entra ID(Azure AD) 사용자 고유 식별자. Teams 메시지의 from.aadObjectId로 전달된다. */
    @Column(name = "aad_object_id", nullable = false, unique = true)
    val aadObjectId: String,
    /** 내부 시스템(users 테이블)의 사용자 ID. Graph API의 mail → username 매칭으로 결정된다. */
    @Column(name = "user_id", nullable = false)
    val userId: Long,
    /** Teams 1:1 대화방 ID. 봇이 사용자에게 DM을 보낼 때 필요하다. */
    @Column(name = "conversation_id", nullable = false)
    var conversationId: String,
    /** Teams Bot Framework API의 지역별 엔드포인트 URL. 봇이 메시지를 전송할 때 기본 URL로 사용된다. */
    @Column(name = "service_url", nullable = false)
    var serviceUrl: String,
) : IdentityIdEntity() {
    fun update(
        serviceUrl: String,
        conversationId: String,
    ) {
        this.serviceUrl = serviceUrl
        this.conversationId = conversationId
    }
}
