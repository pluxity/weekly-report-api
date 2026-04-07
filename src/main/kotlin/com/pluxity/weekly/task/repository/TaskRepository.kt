package com.pluxity.weekly.task.repository

import com.pluxity.weekly.epic.entity.Epic
import com.pluxity.weekly.task.entity.Task
import org.springframework.data.jpa.repository.JpaRepository

interface TaskRepository :
    JpaRepository<Task, Long>,
    TaskCustomRepository {
    fun findByEpicInAndAssigneeId(
        epics: List<Epic>,
        assigneeId: Long,
    ): List<Task>

    fun existsByEpicIdAndName(
        epicId: Long,
        name: String,
    ): Boolean

    fun findByAssigneeId(assigneeId: Long): List<Task>

    fun findByAssigneeIdIn(assigneeIds: List<Long>): List<Task>

    fun findByEpicIn(epics: List<Epic>): List<Task>
}
