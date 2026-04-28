package com.pluxity.weekly.teams.converter

import org.springframework.stereotype.Component

/**
 * Task 리뷰 요청 시 PM 에게 발송되는 Adaptive Card 빌더.
 * 승인/반려 버튼이 포함되며, 반려는 사유 입력 후 Submit.
 * 버튼 클릭 시 Teams → 봇으로 Action.Submit 이벤트가 전달되어
 * AsyncChatHandler.handleFormSubmit 에서 task approve/reject 로 라우팅된다.
 */
@Component
class TaskReviewCardBuilder {
    fun build(
        taskId: Long,
        taskName: String,
        projectName: String,
        epicName: String,
        requesterName: String,
    ): Map<String, Any> =
        mapOf(
            "type" to "AdaptiveCard",
            "version" to "1.2",
            "body" to
                listOf(
                    mapOf(
                        "type" to "TextBlock",
                        "text" to "리뷰 요청",
                        "weight" to "bolder",
                        "size" to "medium",
                        "color" to "accent",
                    ),
                    factRow("프로젝트", projectName, separator = true),
                    factRow("업무 그룹", epicName, separator = true),
                    factRow("태스크", taskName, separator = true),
                    factRow("요청자", requesterName, separator = true),
                ),
            "actions" to
                listOf(
                    mapOf(
                        "type" to "Action.Submit",
                        "title" to "승인",
                        "style" to "positive",
                        "data" to
                            mapOf(
                                "action" to "approve",
                                "target" to "task",
                                "taskId" to taskId,
                            ),
                    ),
                    mapOf(
                        "type" to "Action.ShowCard",
                        "title" to "반려",
                        "card" to
                            mapOf(
                                "type" to "AdaptiveCard",
                                "body" to
                                    listOf(
                                        mapOf(
                                            "type" to "Input.Text",
                                            "id" to "reason",
                                            "label" to "반려 사유 (선택)",
                                            "isMultiline" to true,
                                            "placeholder" to "반려 사유를 입력하세요",
                                        ),
                                    ),
                                "actions" to
                                    listOf(
                                        mapOf(
                                            "type" to "Action.Submit",
                                            "title" to "반려 확인",
                                            "style" to "destructive",
                                            "data" to
                                                mapOf(
                                                    "action" to "reject",
                                                    "target" to "task",
                                                    "taskId" to taskId,
                                                ),
                                        ),
                                    ),
                            ),
                    ),
                ),
        )

    private fun factRow(
        label: String,
        value: String,
        separator: Boolean = false,
    ): Map<String, Any> =
        mapOf(
            "type" to "TextBlock",
            "text" to "**$label** : $value",
            "wrap" to true,
            "separator" to separator,
            "spacing" to "medium",
        )
}
