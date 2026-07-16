package com.pluxity.weekly.chat.v2

import com.pluxity.weekly.chat.v2.dto.GetTaskHistoryArgs
import com.pluxity.weekly.task.service.TaskReviewService
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

/**
 * get_task_history 실행부 — 태스크 리뷰 이력(요청/승인/반려 + 사유).
 * 권한은 [TaskReviewService.findApprovalLogs] 내장 requireEpicAccess가 담당한다.
 */
@Component
class GetTaskHistoryHandler(
    private val taskReviewService: TaskReviewService,
    private val support: ChatV2ToolSupport,
    private val objectMapper: ObjectMapper,
) {
    fun handle(
        argumentsJson: String,
        idRegistry: ChatV2IdRegistry,
    ): String {
        val args = support.readArgs<GetTaskHistoryArgs>(argumentsJson)
        support.validateKnown(idRegistry, ChatV2EntityType.TASK, args.taskId, "task_id")?.let { return it }
        val history =
            taskReviewService.findApprovalLogs(args.taskId).map {
                mapOf(
                    "action" to it.action.name,
                    "actor" to it.actorName,
                    "reason" to it.reason,
                    "at" to it.createdAt.toString(),
                )
            }
        return objectMapper.writeValueAsString(mapOf("task_id" to args.taskId, "history" to history))
    }
}
