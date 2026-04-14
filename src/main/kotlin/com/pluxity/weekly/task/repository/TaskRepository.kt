package com.pluxity.weekly.task.repository

import com.pluxity.weekly.epic.entity.Epic
import com.pluxity.weekly.task.entity.Task
import com.pluxity.weekly.task.entity.TaskStatus
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository

interface TaskRepository :
    JpaRepository<Task, Long>,
    TaskCustomRepository {
    fun existsByEpicIdAndName(
        epicId: Long,
        name: String,
    ): Boolean

    fun findByAssigneeId(assigneeId: Long): List<Task>

    fun findByAssigneeIdIn(assigneeIds: List<Long>): List<Task>

    fun findByEpicIn(epics: List<Epic>): List<Task>

    fun findByEpicId(epicId: Long): List<Task>

    @EntityGraph(attributePaths = ["epic", "epic.project", "assignee"])
    fun findByStatus(status: TaskStatus): List<Task>

    @EntityGraph(attributePaths = ["epic", "epic.project", "assignee"])
    fun findByStatusAndEpicProjectIdIn(
        status: TaskStatus,
        projectIds: List<Long>,
    ): List<Task>

    fun deleteByEpicIdAndAssigneeId(epicId: Long, assigneeId: Long)
}
