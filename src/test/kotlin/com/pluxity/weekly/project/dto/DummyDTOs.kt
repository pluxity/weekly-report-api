package com.pluxity.weekly.project.dto

import com.pluxity.weekly.project.entity.ProjectStatus
import java.time.LocalDate

fun dummyProjectRequest(
    name: String = "테스트 프로젝트",
    description: String? = null,
    status: ProjectStatus = ProjectStatus.TODO,
    startDate: LocalDate? = null,
    dueDate: LocalDate? = null,
    pmId: Long? = null,
) = ProjectRequest(
    name = name,
    description = description,
    status = status,
    startDate = startDate,
    dueDate = dueDate,
    pmId = pmId,
)

fun dummyProjectUpdateRequest(
    name: String? = null,
    description: String? = null,
    status: ProjectStatus? = null,
    startDate: LocalDate? = null,
    dueDate: LocalDate? = null,
    pmId: Long? = null,
) = ProjectUpdateRequest(
    name = name,
    description = description,
    status = status,
    startDate = startDate,
    dueDate = dueDate,
    pmId = pmId,
)
