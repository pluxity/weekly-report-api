package com.pluxity.weekly.team.repository

import com.pluxity.weekly.chat.dto.TeamSearchFilter
import com.pluxity.weekly.team.entity.Team

interface TeamCustomRepository {
    fun findByFilter(filter: TeamSearchFilter): List<Team>
}
