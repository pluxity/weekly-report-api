package com.pluxity.weekly.epic.event

data class EpicAssignedEvent(
    val userId: Long,
    val epicId: Long,
    val epicName: String,
)
