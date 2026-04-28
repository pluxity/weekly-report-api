package com.pluxity.weekly.teams.converter

import com.pluxity.weekly.chat.dto.ChatActionResponse
import com.pluxity.weekly.chat.dto.ChatActionType
import com.pluxity.weekly.chat.dto.ChatDto
import com.pluxity.weekly.chat.dto.ChatReadResponse
import com.pluxity.weekly.chat.dto.ChatTarget
import com.pluxity.weekly.chat.dto.EpicChatDto
import com.pluxity.weekly.chat.dto.ProjectChatDto
import com.pluxity.weekly.chat.dto.SelectField
import com.pluxity.weekly.epic.dto.EpicResponse
import com.pluxity.weekly.project.dto.ProjectResponse
import com.pluxity.weekly.task.dto.PendingReviewResponse
import com.pluxity.weekly.task.dto.TaskResponse
import com.pluxity.weekly.team.dto.TeamResponse
import org.springframework.stereotype.Component

@Component
class AdaptiveCardConverter {
    fun toTeamsResponse(response: ChatActionResponse): Map<String, Any> {
        // 서버 실행 완료 (id 있음)
        if (response.id != null) {
            return textMessage(formatExecutionResult(response))
        }

        // 조회 결과 - 리뷰 대기 목록은 카드 여러 개로 분리
        if (response.readResult?.pendingReviews?.isNotEmpty() == true) {
            return buildPendingReviewsMessage(response.readResult.pendingReviews)
        }

        if (response.readResult != null) {
            return adaptiveCard(buildReadCard(response.readResult, response.target))
        }

        // 생성/수정 폼
        if (response.dto != null) {
            return adaptiveCard(buildFormCard(response))
        }

        return textMessage("처리가 완료되었습니다.")
    }

    fun textMessage(text: String): Map<String, Any> =
        mapOf(
            "type" to "message",
            "text" to text,
        )

    fun adaptiveCard(card: Map<String, Any>): Map<String, Any> =
        mapOf(
            "type" to "message",
            "attachments" to
                listOf(
                    mapOf(
                        "contentType" to "application/vnd.microsoft.card.adaptive",
                        "content" to card,
                    ),
                ),
        )

    private fun formatExecutionResult(response: ChatActionResponse): String {
        val actionLabel =
            when (ChatActionType.fromOrNull(response.action)) {
                ChatActionType.CREATE -> "생성"
                ChatActionType.UPDATE -> "수정"
                ChatActionType.DELETE -> "삭제"
                else -> response.action
            }
        val targetLabel = targetLabel(response.target)
        return "$targetLabel ${actionLabel}이 완료되었습니다. (ID: ${response.id})"
    }

    private fun buildReadCard(
        readResult: ChatReadResponse,
        target: String,
    ): Map<String, Any> {
        val facts =
            when {
                !readResult.tasks.isNullOrEmpty() -> readResult.tasks.map { it.toFact() }
                !readResult.projects.isNullOrEmpty() -> readResult.projects.map { it.toFact() }
                !readResult.epics.isNullOrEmpty() -> readResult.epics.map { it.toFact() }
                !readResult.teams.isNullOrEmpty() -> readResult.teams.map { it.toFact() }
                else -> return emptyCard("조회 결과가 없습니다.")
            }

        return mapOf(
            "type" to "AdaptiveCard",
            "version" to "1.2",
            "body" to
                listOf(
                    mapOf(
                        "type" to "TextBlock",
                        "text" to "${targetLabel(target)} 조회 결과",
                        "weight" to "bolder",
                        "size" to "medium",
                    ),
                    mapOf(
                        "type" to "FactSet",
                        "facts" to facts,
                    ),
                ),
        )
    }

    private fun buildPendingReviewsMessage(pendingReviews: List<PendingReviewResponse>): Map<String, Any> {
        val attachments =
            pendingReviews.map { review ->
                mapOf(
                    "contentType" to "application/vnd.microsoft.card.adaptive",
                    "content" to buildPendingReviewCard(review),
                )
            }

        return mapOf(
            "type" to "message",
            "text" to "리뷰 대기 태스크 ${pendingReviews.size}건",
            "attachmentLayout" to "list",
            "attachments" to attachments,
        )
    }

    private fun buildPendingReviewCard(review: PendingReviewResponse): Map<String, Any> =
        mapOf(
            "type" to "AdaptiveCard",
            "version" to "1.2",
            "body" to
                listOf(
                    mapOf(
                        "type" to "TextBlock",
                        "text" to review.taskName,
                        "weight" to "bolder",
                        "size" to "medium",
                        "wrap" to true,
                    ),
                    mapOf(
                        "type" to "FactSet",
                        "facts" to
                            listOfNotNull(
                                mapOf("title" to "프로젝트", "value" to review.projectName),
                                mapOf("title" to "업무 그룹", "value" to review.epicName),
                                review.assigneeName?.let { mapOf("title" to "담당자", "value" to it) },
                                review.dueDate?.let { mapOf("title" to "마감일", "value" to it.toString()) },
                                mapOf("title" to "요청 시각", "value" to review.reviewRequestedAt.toString()),
                            ),
                    ),
                ),
            "actions" to
                listOf(
                    mapOf(
                        "type" to "Action.Submit",
                        "title" to "승인",
                        "data" to
                            mapOf(
                                "action" to "approve",
                                "target" to "task",
                                "taskId" to review.taskId,
                            ),
                    ),
                    mapOf(
                        "type" to "Action.ShowCard",
                        "title" to "반려",
                        "card" to
                            mapOf(
                                "type" to "AdaptiveCard",
                                "version" to "1.2",
                                "body" to
                                    listOf(
                                        mapOf(
                                            "type" to "Input.Text",
                                            "id" to "reason",
                                            "label" to "반려 사유",
                                            "isMultiline" to true,
                                            "placeholder" to "반려 사유를 입력하세요",
                                        ),
                                    ),
                                "actions" to
                                    listOf(
                                        mapOf(
                                            "type" to "Action.Submit",
                                            "title" to "반려 확정",
                                            "data" to
                                                mapOf(
                                                    "action" to "reject",
                                                    "target" to "task",
                                                    "taskId" to review.taskId,
                                                ),
                                        ),
                                    ),
                            ),
                    ),
                ),
        )

    private fun buildFormCard(response: ChatActionResponse): Map<String, Any> {
        val dto = response.dto!!
        val inputs = buildInputs(dto, response.selectFields.orEmpty())
        val actionLabel = if (ChatActionType.fromOrNull(response.action) == ChatActionType.CREATE) "생성" else "수정"

        return mapOf(
            "type" to "AdaptiveCard",
            "version" to "1.2",
            "body" to listOf(
                mapOf(
                    "type" to "TextBlock",
                    "text" to "${targetLabel(response.target)} $actionLabel",
                    "weight" to "bolder",
                    "size" to "medium",
                ),
            ) + inputs,
            "actions" to
                listOf(
                    mapOf(
                        "type" to "Action.Submit",
                        "title" to actionLabel,
                        "data" to
                            mapOf(
                                "action" to response.action,
                                "target" to response.target,
                            ),
                    ),
                ),
        )
    }

    private fun buildInputs(
        dto: ChatDto,
        selectFields: List<SelectField>,
    ): List<Map<String, Any>> {
        val selectFieldMap = selectFields.associateBy { it.field }
        val inputs = mutableListOf<Map<String, Any>>()

        when (dto) {
            is ProjectChatDto -> {
                inputs += textInput("name", "프로젝트명", dto.name)
                inputs += textInput("description", "설명", dto.description)
                inputs += selectOrText("pmId", "PM", selectFieldMap, false, dto.pmId?.toString())
                inputs += dateInput("startDate", "시작일", dto.startDate)
                inputs += dateInput("dueDate", "마감일", dto.dueDate)
            }
            is EpicChatDto -> {
                inputs += textInput("name", "업무 그룹명", dto.name)
                inputs += selectOrText("projectId", "프로젝트", selectFieldMap, false, dto.projectId.toString())
                inputs += textInput("description", "설명", dto.description)
                inputs += selectOrText("userIds", "담당자", selectFieldMap, isMultiSelect = true)
                inputs += dateInput("startDate", "시작일", dto.startDate)
                inputs += dateInput("dueDate", "마감일", dto.dueDate)
            }
            else -> {}
        }

        return inputs
    }

    private fun textInput(
        id: String,
        label: String,
        value: String?,
    ): Map<String, Any> {
        val input =
            mutableMapOf<String, Any>(
                "type" to "Input.Text",
                "id" to id,
                "label" to label,
            )
        if (value != null) input["value"] = value
        return input
    }

    private fun dateInput(
        id: String,
        label: String,
        value: String?,
    ): Map<String, Any> {
        val input =
            mutableMapOf<String, Any>(
                "type" to "Input.Date",
                "id" to id,
                "label" to label,
            )
        if (value != null) input["value"] = value
        return input
    }

    private fun selectOrText(
        fieldId: String,
        label: String,
        selectFieldMap: Map<String, SelectField>,
        isMultiSelect: Boolean = false,
        defaultValue: String? = null,
    ): Map<String, Any> {
        val selectField = selectFieldMap[fieldId]
        return if (selectField != null && selectField.candidates.isNotEmpty()) {
            buildMap {
                put("type", "Input.ChoiceSet")
                put("id", fieldId)
                put("label", label)
                put("value", defaultValue ?: selectField.candidates.first().id)
                put(
                    "choices",
                    selectField.candidates.map { candidate ->
                        mapOf("title" to candidate.name, "value" to candidate.id)
                    },
                )
                if (isMultiSelect) put("isMultiSelect", true)
            }
        } else {
            textInput(fieldId, label, null)
        }
    }

    private fun emptyCard(message: String): Map<String, Any> =
        mapOf(
            "type" to "AdaptiveCard",
            "version" to "1.2",
            "body" to
                listOf(
                    mapOf("type" to "TextBlock", "text" to message),
                ),
        )

    private fun targetLabel(target: String): String =
        when (ChatTarget.fromOrNull(target)) {
            ChatTarget.PROJECT -> "프로젝트"
            ChatTarget.EPIC -> "업무 그룹"
            ChatTarget.TASK -> "태스크"
            ChatTarget.TEAM -> "팀"
            ChatTarget.REVIEW -> "리뷰"
            null -> target
        }

    private fun TaskResponse.toFact(): Map<String, String> = mapOf("title" to name, "value" to "$status ($progress%)")

    private fun ProjectResponse.toFact(): Map<String, String> = mapOf("title" to name, "value" to "$status")

    private fun EpicResponse.toFact(): Map<String, String> = mapOf("title" to name, "value" to "$status")

    private fun TeamResponse.toFact(): Map<String, String> = mapOf("title" to name, "value" to (leaderName ?: "-"))
}
