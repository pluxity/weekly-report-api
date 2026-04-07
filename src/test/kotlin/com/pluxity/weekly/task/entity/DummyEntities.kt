package com.pluxity.weekly.task.entity

import com.pluxity.weekly.epic.entity.Epic
import com.pluxity.weekly.epic.entity.dummyEpic
import com.pluxity.weekly.test.withAudit
import com.pluxity.weekly.test.withId
import java.time.LocalDate

fun dummyTask(
    id: Long? = null,
    epic: Epic = dummyEpic(id = 1L),
    name: String = "테스트 태스크",
    description: String? = null,
    status: TaskStatus = TaskStatus.TODO,
    progress: Int = 0,
    startDate: LocalDate? = null,
    dueDate: LocalDate? = null,
) = Task(
    epic = epic,
    name = name,
    description = description,
    status = status,
    progress = progress,
    startDate = startDate,
    dueDate = dueDate,
).withId(id).withAudit()
