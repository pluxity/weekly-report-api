package com.pluxity.weekly.epic.entity

enum class EpicStatus {
    TODO,
    IN_PROGRESS,
    DONE,
    ;

    companion object {
        val initStates: List<EpicStatus> = listOf(TODO, IN_PROGRESS)

        val transitionStates: List<EpicStatus> = listOf(TODO, IN_PROGRESS, DONE)
    }
}
