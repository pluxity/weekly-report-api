package com.pluxity.weekly.epic.dto

import com.pluxity.weekly.epic.entity.EpicStatus
import java.time.LocalDate

fun dummyEpicRequest(
    projectId: Long = 1L,
    name: String = "테스트 에픽",
    description: String? = null,
    status: EpicStatus = EpicStatus.TODO,
    startDate: LocalDate? = null,
    dueDate: LocalDate? = null,
) = EpicRequest(
    projectId = projectId,
    name = name,
    description = description,
    status = status,
    startDate = startDate,
    dueDate = dueDate,
)

fun dummyEpicUpdateRequest(
    name: String? = null,
    description: String? = null,
    status: EpicStatus? = null,
    startDate: LocalDate? = null,
    dueDate: LocalDate? = null,
    userIds: List<Long>? = null,
) = EpicUpdateRequest(
    name = name,
    description = description,
    status = status,
    startDate = startDate,
    dueDate = dueDate,
    userIds = userIds,
)
