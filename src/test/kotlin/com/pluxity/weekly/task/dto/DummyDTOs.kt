package com.pluxity.weekly.task.dto

import com.pluxity.weekly.task.entity.TaskStatus
import java.time.LocalDate

fun dummyTaskRequest(
    epicId: Long = 1L,
    name: String = "테스트 태스크",
    description: String? = null,
    status: TaskStatus = TaskStatus.TODO,
    progress: Int = 0,
    startDate: LocalDate? = null,
    dueDate: LocalDate? = null,
    assigneeId: Long? = null,
) = TaskRequest(
    epicId = epicId,
    name = name,
    description = description,
    status = status,
    progress = progress,
    startDate = startDate,
    dueDate = dueDate,
    assigneeId = assigneeId,
)

fun dummyTaskUpdateRequest(
    name: String? = null,
    description: String? = null,
    status: TaskStatus? = null,
    progress: Int? = null,
    startDate: LocalDate? = null,
    dueDate: LocalDate? = null,
    assigneeId: Long? = null,
) = TaskUpdateRequest(
    name = name,
    description = description,
    status = status,
    progress = progress,
    startDate = startDate,
    dueDate = dueDate,
    assigneeId = assigneeId,
)
