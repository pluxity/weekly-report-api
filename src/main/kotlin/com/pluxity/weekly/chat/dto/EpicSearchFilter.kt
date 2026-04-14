package com.pluxity.weekly.chat.dto

import com.pluxity.weekly.epic.entity.EpicStatus
import java.time.LocalDate

data class EpicSearchFilter(
    val status: EpicStatus? = null,
    val name: String? = null,
    val projectId: Long? = null,
    val assigneeId: Long? = null,
    val dueDateFrom: LocalDate? = null,
    val dueDateTo: LocalDate? = null,
    val epicIds: List<Long>? = null,
    val excludeDone: Boolean = false,
    val scopeStartDate: LocalDate? = null,
)
