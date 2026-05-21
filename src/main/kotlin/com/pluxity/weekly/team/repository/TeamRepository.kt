package com.pluxity.weekly.team.repository

import com.pluxity.weekly.team.entity.Team
import org.springframework.data.jpa.repository.JpaRepository

interface TeamRepository :
    JpaRepository<Team, Long>,
    TeamCustomRepository {
    fun existsByLeaderId(leaderId: Long): Boolean

    fun findByLeaderId(leaderId: Long): List<Team>

    fun existsByIdAndLeaderId(
        id: Long,
        leaderId: Long,
    ): Boolean
}
