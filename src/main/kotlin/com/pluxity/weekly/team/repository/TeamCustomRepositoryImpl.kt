package com.pluxity.weekly.team.repository

import com.linecorp.kotlinjdsl.support.spring.data.jpa.repository.KotlinJdslJpqlExecutor
import com.pluxity.weekly.chat.dto.TeamSearchFilter
import com.pluxity.weekly.core.utils.findAllNotNull
import com.pluxity.weekly.team.entity.Team

class TeamCustomRepositoryImpl(
    private val executor: KotlinJdslJpqlExecutor,
) : TeamCustomRepository {
    override fun findByFilter(filter: TeamSearchFilter): List<Team> =
        executor.findAllNotNull {
            select(entity(Team::class))
                .from(entity(Team::class))
                .whereAnd(
                    filter.name?.let { path(Team::name).like("%$it%") },
                )
        }
}
