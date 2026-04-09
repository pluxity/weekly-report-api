package com.pluxity.weekly.task.repository

import com.pluxity.weekly.task.entity.TaskApprovalAction
import com.pluxity.weekly.task.entity.TaskApprovalLog
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime

data class TaskReviewRequestedAt(
    val taskId: Long,
    val requestedAt: LocalDateTime,
)

interface TaskApprovalLogRepository : JpaRepository<TaskApprovalLog, Long> {
    fun findByTaskIdOrderByIdAsc(taskId: Long): List<TaskApprovalLog>

    @Query(
        """
        SELECT new com.pluxity.weekly.task.repository.TaskReviewRequestedAt(l.task.id, MAX(l.createdAt))
        FROM TaskApprovalLog l
        WHERE l.task.id IN :taskIds AND l.action = :action
        GROUP BY l.task.id
        """,
    )
    fun findLatestCreatedAtByTaskIdsAndAction(
        @Param("taskIds") taskIds: List<Long>,
        @Param("action") action: TaskApprovalAction,
    ): List<TaskReviewRequestedAt>
}
