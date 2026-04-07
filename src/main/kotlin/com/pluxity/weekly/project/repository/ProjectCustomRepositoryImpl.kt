package com.pluxity.weekly.project.repository

import com.linecorp.kotlinjdsl.support.spring.data.jpa.repository.KotlinJdslJpqlExecutor
import com.pluxity.weekly.chat.dto.ProjectSearchFilter
import com.pluxity.weekly.core.utils.findAllNotNull
import com.pluxity.weekly.project.entity.Project

class ProjectCustomRepositoryImpl(
    private val executor: KotlinJdslJpqlExecutor,
) : ProjectCustomRepository {
    override fun findByFilter(filter: ProjectSearchFilter): List<Project> =
        executor.findAllNotNull {
            select(entity(Project::class))
                .from(entity(Project::class))
                .whereAnd(
                    filter.status?.let { path(Project::status).eq(it) },
                    filter.name?.let { path(Project::name).like("%$it%") },
                    filter.pmId?.let { path(Project::pmId).eq(it) },
                    filter.dueDateFrom?.let { path(Project::dueDate).greaterThanOrEqualTo(it) },
                    filter.dueDateTo?.let { path(Project::dueDate).lessThanOrEqualTo(it) },
                    filter.projectIds?.let { path(Project::id).`in`(it) },
                )
        }
}
