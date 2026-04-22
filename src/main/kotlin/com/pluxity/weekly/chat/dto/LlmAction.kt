package com.pluxity.weekly.chat.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties(ignoreUnknown = true)
data class LlmAction(
    val action: String,
    val target: String? = null,
    val id: Long? = null,
    val name: String? = null,
    val description: String? = null,
    val status: String? = null,
    val progress: Int? = null,
    @param:JsonProperty("project_id")
    val projectId: Long? = null,
    @param:JsonProperty("epic_id")
    val epicId: Long? = null,
    @param:JsonProperty("pm_id")
    val pmId: Long? = null,
    @param:JsonProperty("team_id")
    val teamId: Long? = null,
    @param:JsonProperty("assignee_id")
    val assigneeId: Long? = null,
    @param:JsonProperty("leader_id")
    val leaderId: Long? = null,
    @param:JsonProperty("start_date")
    val startDate: String? = null,
    @param:JsonProperty("due_date")
    val dueDate: String? = null,
    val filters: LlmActionFilters? = null,
    @param:JsonProperty("user_ids")
    val userIds: List<Long>? = null,
    @param:JsonProperty("remove_user_ids")
    val removeUserIds: List<Long>? = null,
    val message: String? = null,
    @param:JsonProperty("missing_fields")
    val missingFields: List<String>? = null,
    val candidates: List<Long>? = null,
)

fun LlmAction.hasValueFor(field: String): Boolean =
    when (field) {
        "id" -> id != null
        "project_id" -> projectId != null
        "epic_id" -> epicId != null
        "user_ids" -> !userIds.isNullOrEmpty()
        "remove_user_ids" -> !removeUserIds.isNullOrEmpty()
        else -> false
    }
