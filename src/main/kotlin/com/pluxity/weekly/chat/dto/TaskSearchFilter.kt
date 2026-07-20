package com.pluxity.weekly.chat.dto

import com.pluxity.weekly.task.entity.TaskStatus
import java.time.LocalDate

data class TaskSearchFilter(
    val taskId: Long? = null,
    val status: TaskStatus? = null,
    val epicId: Long? = null,
    val projectId: Long? = null,
    val assigneeId: Long? = null,
    /** 담당자 다건 — "우리 팀 태스크"(팀 멤버 userId 목록)의 team 스코프 조회용 */
    val assigneeIds: List<Long>? = null,
    val name: String? = null,
    val dueDateFrom: LocalDate? = null,
    val dueDateTo: LocalDate? = null,
    /** 완료일(completed_at) 범위 — "이번주/저번주 한 일" 회고 조회용. null이면 미완료라 자연히 제외됨 */
    val completedFrom: LocalDate? = null,
    val completedTo: LocalDate? = null,
    val epicIds: List<Long>? = null,
    val excludeDone: Boolean = false,
    val scopeStartDate: LocalDate? = null,
)
