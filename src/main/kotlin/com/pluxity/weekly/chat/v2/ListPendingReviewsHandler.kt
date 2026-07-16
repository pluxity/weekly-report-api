package com.pluxity.weekly.chat.v2

import com.pluxity.weekly.task.service.TaskReviewService
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

/**
 * list_pending_reviews 실행부 — 리뷰 대기(IN_REVIEW) 태스크 목록.
 * 스코프·요청 시각 등은 [TaskReviewService.findPendingReviews]가 담당(PM 스코프).
 */
@Component
class ListPendingReviewsHandler(
    private val taskReviewService: TaskReviewService,
    private val objectMapper: ObjectMapper,
) {
    fun handle(idRegistry: ChatV2IdRegistry): String {
        val pending =
            taskReviewService
                .findPendingReviews()
                .take(MAX_RESULTS)
                .onEach { idRegistry.register(ChatV2EntityType.TASK, it.taskId) }
                .map {
                    mapOf(
                        "id" to it.taskId,
                        "name" to it.taskName,
                        "project" to it.projectName,
                        "epic" to it.epicName,
                        "assignee" to it.assigneeName,
                    )
                }
        return objectMapper.writeValueAsString(mapOf("pending_reviews" to pending))
    }

    companion object {
        private const val MAX_RESULTS = 20
    }
}
