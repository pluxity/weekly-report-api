package com.pluxity.weekly.epic.event

data class EpicUnassignedEvent(
    val userId: Long,
    val epicId: Long,
    val epicName: String,
)
