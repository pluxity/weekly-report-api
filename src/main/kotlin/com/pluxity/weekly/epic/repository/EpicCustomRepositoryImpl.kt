package com.pluxity.weekly.epic.repository

import com.linecorp.kotlinjdsl.support.spring.data.jpa.repository.KotlinJdslJpqlExecutor
import com.pluxity.weekly.auth.user.entity.User
import com.pluxity.weekly.chat.dto.EpicSearchFilter
import com.pluxity.weekly.core.utils.findAllNotNull
import com.pluxity.weekly.epic.entity.Epic
import com.pluxity.weekly.epic.entity.EpicAssignment
import com.pluxity.weekly.epic.entity.EpicStatus
import com.pluxity.weekly.project.entity.Project

class EpicCustomRepositoryImpl(
    private val executor: KotlinJdslJpqlExecutor,
) : EpicCustomRepository {
    override fun findByFilter(filter: EpicSearchFilter): List<Epic> =
        executor.findAllNotNull {
            selectDistinct(entity(Epic::class))
                .from(
                    entity(Epic::class),
                    leftJoin(Epic::assignments),
                ).whereAnd(
                    filter.status?.let { path(Epic::status).eq(it) },
                    filter.name?.let { path(Epic::name).like("%$it%") },
                    filter.projectId?.let { path(Epic::project)(Project::id).eq(it) },
                    filter.assigneeId?.let { path(EpicAssignment::user)(User::id).eq(it) },
                    filter.dueDateFrom?.let { path(Epic::dueDate).greaterThanOrEqualTo(it) },
                    filter.dueDateTo?.let { path(Epic::dueDate).lessThanOrEqualTo(it) },
                    filter.epicIds?.let { path(Epic::id).`in`(it) },
                    if (filter.excludeDone) path(Epic::status).notEqual(EpicStatus.DONE) else null,
                    filter.scopeStartDate?.let {
                        or(
                            path(Epic::startDate).greaterThanOrEqualTo(it),
                            path(Epic::status).notEqual(EpicStatus.DONE),
                        )
                    },
                )
        }
}
