package com.pluxity.weekly.teams.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Bot Framework Activity 모델
 * https://learn.microsoft.com/ko-kr/azure/bot-service/rest-api/bot-framework-rest-connector-api-reference
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class Activity(
    /** message, conversationUpdate, installationUpdate 등 */
    val type: String? = null,
    /** Activity 고유 ID */
    val id: String? = null,
    /** 생성 시간 */
    val timestamp: String? = null,
    /** 메시지 본문 (type=message) */
    val text: String? = null,
    /** 보낸 사람 */
    val from: ChannelAccount? = null,
    /** 받는 사람 (봇 또는 사용자) */
    val recipient: ChannelAccount? = null,
    /** 대화 정보 */
    val conversation: ConversationAccount? = null,
    /** 응답을 보낼 Bot Service 콜백 URL */
    val serviceUrl: String? = null,
    /** 채널 식별자 (msteams, emulator 등) */
    val channelId: String? = null,
    /** Adaptive Card submit 시 폼 데이터 */
    val value: Any? = null,
    /** installationUpdate 시 add/remove */
    val action: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ChannelAccount(
    /** 사용자/봇 고유 ID */
    val id: String? = null,
    /** 표시 이름 */
    val name: String? = null,
    /** Azure AD 오브젝트 ID */
    @param:JsonProperty("aadObjectId")
    val aadObjectId: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ConversationAccount(
    /** 대화 채널 ID */
    val id: String? = null,
    /** 대화 이름 */
    val name: String? = null,
    /** 그룹 대화 여부 */
    val isGroup: Boolean? = null,
    /** Azure 테넌트 ID */
    val tenantId: String? = null,
)
