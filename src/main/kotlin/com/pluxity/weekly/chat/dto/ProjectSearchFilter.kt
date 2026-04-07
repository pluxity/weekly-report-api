package com.pluxity.weekly.chat.dto

import com.pluxity.weekly.project.entity.ProjectStatus
import java.time.LocalDate

data class ProjectSearchFilter(
    val status: ProjectStatus? = null,
    val name: String? = null,
    val pmId: Long? = null,
    val dueDateFrom: LocalDate? = null,
    val dueDateTo: LocalDate? = null,
    val projectIds: List<Long>? = null,
)
