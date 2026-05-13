package com.pluxity.weekly.teams.repository

import com.pluxity.weekly.teams.entity.TeamsNotificationLog
import com.pluxity.weekly.teams.entity.TeamsNotificationStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

interface TeamsNotificationLogRepository : JpaRepository<TeamsNotificationLog, Long> {
    fun findByUserId(
        userId: Long,
        pageable: Pageable,
    ): Page<TeamsNotificationLog>

    fun findByStatus(
        status: TeamsNotificationStatus,
        pageable: Pageable,
    ): Page<TeamsNotificationLog>
}
