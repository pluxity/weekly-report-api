package com.pluxity.weekly.task.repository

import com.linecorp.kotlinjdsl.support.spring.data.jpa.repository.KotlinJdslJpqlExecutor
import com.pluxity.weekly.auth.user.entity.User
import com.pluxity.weekly.chat.dto.TaskSearchFilter
import com.pluxity.weekly.core.utils.findAllNotNull
import com.pluxity.weekly.epic.entity.Epic
import com.pluxity.weekly.project.entity.Project
import com.pluxity.weekly.task.entity.Task
import com.pluxity.weekly.task.entity.TaskStatus

class TaskCustomRepositoryImpl(
    private val executor: KotlinJdslJpqlExecutor,
) : TaskCustomRepository {
    override fun findByFilter(filter: TaskSearchFilter): List<Task> =
        executor.findAllNotNull {
            select(entity(Task::class))
                .from(entity(Task::class))
                .whereAnd(
                    filter.taskId?.let { path(Task::id).eq(it) },
                    filter.status?.let { path(Task::status).eq(it) },
                    filter.epicId?.let { path(Task::epic)(Epic::id).eq(it) },
                    filter.projectId?.let { path(Task::epic)(Epic::project)(Project::id).eq(it) },
                    filter.assigneeId?.let { path(Task::assignee)(User::id).eq(it) },
                    filter.name?.let { path(Task::name).like("%$it%") },
                    filter.dueDateFrom?.let { path(Task::dueDate).greaterThanOrEqualTo(it) },
                    filter.dueDateTo?.let { path(Task::dueDate).lessThanOrEqualTo(it) },
                    filter.epicIds?.let { path(Task::epic)(Epic::id).`in`(it) },
                    if (filter.excludeDone) path(Task::status).notEqual(TaskStatus.DONE) else null,
                    filter.scopeStartDate?.let {
                        or(
                            path(Task::startDate).greaterThanOrEqualTo(it),
                            path(Task::status).notEqual(TaskStatus.DONE),
                        )
                    },
                )
        }
}
