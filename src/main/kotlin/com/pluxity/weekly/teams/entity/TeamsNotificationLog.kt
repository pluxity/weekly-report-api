package com.pluxity.weekly.teams.entity

import com.pluxity.weekly.core.entity.IdentityIdEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table

@Entity
@Table(name = "teams_notification_logs")
class TeamsNotificationLog(
    @Column(name = "user_id", nullable = false)
    val userId: Long,
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 32)
    val type: TeamsNotificationType,
    @Column(name = "message", nullable = false, length = 2000)
    val message: String,
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    var status: TeamsNotificationStatus = TeamsNotificationStatus.PENDING,
    @Column(name = "fail_reason", length = 1000)
    var failReason: String? = null,
) : IdentityIdEntity() {
    fun markSent() {
        status = TeamsNotificationStatus.SENT
        failReason = null
    }

    fun markFailed(reason: String?) {
        status = TeamsNotificationStatus.FAILED
        failReason = reason?.take(1000)
    }
}
