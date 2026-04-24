package com.pluxity.weekly.teams.repository

import com.pluxity.weekly.teams.entity.TeamsNotificationLog
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

interface TeamsNotificationLogRepository : JpaRepository<TeamsNotificationLog, Long> {
    fun findByUserId(
        userId: Long,
        pageable: Pageable,
    ): Page<TeamsNotificationLog>
}
