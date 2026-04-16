package com.pluxity.weekly.chat.context

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonPropertyOrder

@JsonPropertyOrder("today", "today_day_of_week", "user", "projects", "teams", "users")
sealed class ChatContext {
    abstract val today: String

    @get:JsonProperty("today_day_of_week")
    abstract val todayDayOfWeek: String

    abstract val user: UserRef
}

data class ProjectContext(
    override val today: String,
    override val todayDayOfWeek: String,
    override val user: UserRef,
    val projects: List<ProjectSimple>,
    val users: List<UserRef>,
) : ChatContext()

data class EpicContext(
    override val today: String,
    override val todayDayOfWeek: String,
    override val user: UserRef,
    val projects: List<ProjectWithEpics>,
    val users: List<UserRef>,
) : ChatContext()

data class TaskCreateContext(
    override val today: String,
    override val todayDayOfWeek: String,
    override val user: UserRef,
    val projects: List<ProjectWithEpics>,
) : ChatContext()

data class TaskContext(
    override val today: String,
    override val todayDayOfWeek: String,
    override val user: UserRef,
    val projects: List<ProjectWithEpicsAndTasks>,
    val users: List<UserRef>,
) : ChatContext()

data class TeamContext(
    override val today: String,
    override val todayDayOfWeek: String,
    override val user: UserRef,
    val teams: List<TeamRef>,
    val users: List<UserRef>,
) : ChatContext()

data class UserRef(
    val id: Long,
    val name: String,
)

data class TeamRef(
    val id: Long,
    val name: String,
)

data class ProjectSimple(
    val id: Long,
    val name: String,
    val status: String,
)

data class EpicRef(
    val id: Long,
    val name: String,
)

data class ProjectWithEpics(
    val id: Long,
    val name: String,
    val epics: List<EpicRef>,
)

data class TaskRef(
    val id: Long,
    val name: String,
    val status: String,
    val progress: Int,
)

data class EpicWithTasks(
    val id: Long,
    val name: String,
    val tasks: List<TaskRef>,
)

data class ProjectWithEpicsAndTasks(
    val id: Long,
    val name: String,
    val epics: List<EpicWithTasks>,
)
