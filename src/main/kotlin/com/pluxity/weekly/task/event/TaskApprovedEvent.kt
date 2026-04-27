package com.pluxity.weekly.task.event

data class TaskApprovedEvent(
    val userId: Long,
    val taskName: String,
)
