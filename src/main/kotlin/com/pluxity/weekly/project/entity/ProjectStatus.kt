package com.pluxity.weekly.project.entity

enum class ProjectStatus {
    TODO,
    IN_PROGRESS,
    DONE,
    ;

    companion object {
        val initStates: List<ProjectStatus> = listOf(TODO, IN_PROGRESS)

        val transitionStates: List<ProjectStatus> = listOf(TODO, IN_PROGRESS, DONE)
    }
}
