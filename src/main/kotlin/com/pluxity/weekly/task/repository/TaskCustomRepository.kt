package com.pluxity.weekly.task.repository

import com.pluxity.weekly.chat.dto.TaskSearchFilter
import com.pluxity.weekly.task.entity.Task

interface TaskCustomRepository {
    fun findByFilter(filter: TaskSearchFilter): List<Task>
}
