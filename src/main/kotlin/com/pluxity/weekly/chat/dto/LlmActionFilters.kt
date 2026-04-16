package com.pluxity.weekly.chat.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import java.time.LocalDate

@JsonIgnoreProperties(ignoreUnknown = true)
data class LlmActionFilters(
    val status: String? = null,
    @param:JsonProperty("epic_id")
    val epicId: Long? = null,
    @param:JsonProperty("project_id")
    val projectId: Long? = null,
    @param:JsonProperty("assignee_id")
    val assigneeId: Long? = null,
    @param:JsonProperty("pm_id")
    val pmId: Long? = null,
    val name: String? = null,
    @param:JsonProperty("due_date_from")
    val dueDateFrom: LocalDate? = null,
    @param:JsonProperty("due_date_to")
    val dueDateTo: LocalDate? = null,
)
