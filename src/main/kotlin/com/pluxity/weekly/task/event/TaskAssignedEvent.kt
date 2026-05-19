package com.pluxity.weekly.task.event

data class TaskAssignedEvent(
    val userId: Long,
    val taskName: String,
)
