package com.pluxity.weekly.task.entity

enum class TaskStatus {
    TODO,
    IN_PROGRESS,
    IN_REVIEW,
    DONE,
    ;

    companion object {
        val initStates: List<TaskStatus> = listOf(TODO, IN_PROGRESS)

        // DONE은 REVIEW_REQUEST 흐름에서만 전이, IN_REVIEW는 REVIEW_REQUEST 액션이 설정
        val transitionStates: List<TaskStatus> = listOf(TODO, IN_PROGRESS)
    }
}
