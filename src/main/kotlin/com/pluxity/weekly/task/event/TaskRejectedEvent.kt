package com.pluxity.weekly.task.event

data class TaskRejectedEvent(
    val userId: Long,
    val taskName: String,
    val reason: String?,
)
