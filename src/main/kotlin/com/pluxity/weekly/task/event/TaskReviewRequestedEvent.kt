package com.pluxity.weekly.task.event

data class TaskReviewRequestedEvent(
    val taskId: Long,
    val taskName: String,
    val projectName: String,
    val epicName: String,
    val requesterName: String,
    val pmId: Long,
)
