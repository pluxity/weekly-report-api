package com.pluxity.weekly.epic.entity

import com.pluxity.weekly.auth.user.entity.User
import com.pluxity.weekly.project.entity.Project
import com.pluxity.weekly.project.entity.dummyProject
import com.pluxity.weekly.test.entity.dummyUser
import com.pluxity.weekly.test.withAudit
import com.pluxity.weekly.test.withId
import java.time.LocalDate

fun dummyEpic(
    id: Long? = null,
    project: Project = dummyProject(id = 1L),
    name: String = "테스트 에픽",
    description: String? = null,
    status: EpicStatus = EpicStatus.TODO,
    startDate: LocalDate? = null,
    dueDate: LocalDate? = null,
) = Epic(
    project = project,
    name = name,
    description = description,
    status = status,
    startDate = startDate,
    dueDate = dueDate,
).withId(id).withAudit()

fun dummyEpicAssignment(
    id: Long? = null,
    epic: Epic = dummyEpic(id = 1L),
    user: User = dummyUser(id = 1L),
) = EpicAssignment(
    epic = epic,
    user = user,
).withId(id).withAudit()
