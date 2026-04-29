package com.pluxity.weekly.project.event

data class ProjectPmAssignedEvent(
    val pmId: Long,
    val projectId: Long,
    val projectName: String,
)
