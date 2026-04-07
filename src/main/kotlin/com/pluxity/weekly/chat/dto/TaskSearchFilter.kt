package com.pluxity.weekly.chat.dto

import com.pluxity.weekly.task.entity.TaskStatus
import java.time.LocalDate

data class TaskSearchFilter(
    val status: TaskStatus? = null,
    val epicId: Long? = null,
    val projectId: Long? = null,
    val assigneeId: Long? = null,
    val name: String? = null,
    val dueDateFrom: LocalDate? = null,
    val dueDateTo: LocalDate? = null,
    val epicIds: List<Long>? = null,
)
