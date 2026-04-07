package com.pluxity.weekly.project.entity

import com.pluxity.weekly.test.withAudit
import com.pluxity.weekly.test.withId
import java.time.LocalDate

fun dummyProject(
    id: Long? = null,
    name: String = "테스트 프로젝트",
    description: String? = null,
    status: ProjectStatus = ProjectStatus.TODO,
    startDate: LocalDate? = null,
    dueDate: LocalDate? = null,
    pmId: Long? = null,
) = Project(
    name = name,
    description = description,
    status = status,
    startDate = startDate,
    dueDate = dueDate,
    pmId = pmId,
).withId(id).withAudit()
